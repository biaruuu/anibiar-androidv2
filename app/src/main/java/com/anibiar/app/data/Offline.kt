package com.anibiar.app.data

import android.app.Notification
import android.content.Context
import android.content.Intent
import androidx.media3.common.MimeTypes
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import com.anibiar.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.Executors

@Serializable data class DlSub(val label: String, val srclang: String, val path: String, val default: Boolean = false)

/** One saved episode. [key] = "mediaId:episode:audio". */
@Serializable
data class DlItem(
    val key: String, val mediaId: Int, val title: String, val image: String?, val episode: Int,
    val src: String, val format: String, val referer: String?, val subs: List<DlSub> = emptyList(),
)

data class DlRow(val item: DlItem, val state: Int, val percent: Float, val bytes: Long)

/** Offline episodes: HLS/MP4 saved by Media3 into a private cache, plus subtitle files, and an index of what's saved. */
object Offline {
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var referer: String? = null
    private var cacheRef: SimpleCache? = null
    private var dbRef: StandaloneDatabaseProvider? = null
    private var managerRef: DownloadManager? = null

    @Synchronized private fun db(c: Context) = dbRef ?: StandaloneDatabaseProvider(c.applicationContext).also { dbRef = it }

    @Synchronized fun cache(c: Context): Cache =
        cacheRef ?: SimpleCache(File(c.applicationContext.filesDir, "offline_cache"), NoOpCacheEvictor(), db(c)).also { cacheRef = it }

    /** Network source that adds the provider's Referer to downloads (our own server needs none). */
    private fun upstream(): DataSource.Factory = ResolvingDataSource.Factory(
        DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(15_000).setReadTimeoutMs(30_000),
        ResolvingDataSource.Resolver { spec ->
            val ref = referer
            if (ref == null || Streaming.isOwnHost(spec.uri.host)) spec
            else spec.withAdditionalHeaders(mapOf("Referer" to ref, "Origin" to ref.trimEnd('/'), "User-Agent" to UA))
        },
    )

    @Synchronized fun manager(c: Context): DownloadManager {
        managerRef?.let { return it }
        val app = c.applicationContext
        val dm = DownloadManager(app, db(app), cache(app), upstream(), Executors.newFixedThreadPool(2))
        dm.maxParallelDownloads = 1
        dm.addListener(object : DownloadManager.Listener {
            override fun onDownloadChanged(downloadManager: DownloadManager, download: Download, finalException: Exception?) {
                if (download.state == Download.STATE_DOWNLOADING) referer = items(app).firstOrNull { it.key == download.request.id }?.referer
            }
        })
        managerRef = dm
        return dm
    }

    // ---- index ----
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("offline", Context.MODE_PRIVATE)
    @Synchronized fun items(c: Context): List<DlItem> =
        runCatching { json.decodeFromString(ListSerializer(DlItem.serializer()), prefs(c).getString("items", "[]").orEmpty()) }.getOrDefault(emptyList())
    @Synchronized private fun saveItems(c: Context, l: List<DlItem>) { prefs(c).edit().putString("items", json.encodeToString(ListSerializer(DlItem.serializer()), l)).apply() }

    fun key(mediaId: Int, episode: Int, audio: String) = "$mediaId:$episode:$audio"

    fun rows(c: Context): List<DlRow> {
        val dm = manager(c)
        return items(c).map { it ->
            val d = dm.downloadIndex.getDownload(it.key)
            DlRow(it, d?.state ?: Download.STATE_FAILED, d?.percentDownloaded?.takeIf { p -> p >= 0 }?.div(100f) ?: 0f, d?.bytesDownloaded ?: 0L)
        }
    }

    fun state(c: Context, key: String): Int? = manager(c).downloadIndex.getDownload(key)?.state

    /** Starts saving [src]. Subtitles are fetched first (they're small), then the video downloads in the background. */
    suspend fun enqueue(c: Context, key: String, mediaId: Int, title: String, image: String?, episode: Int, src: String, format: String, subs: List<StreamSubtitle>) {
        val app = c.applicationContext
        val ref = LocalSources.referers[src]
        val saved = withContext(Dispatchers.IO) {
            val dir = File(app.filesDir, "dl_subs").apply { mkdirs() }
            subs.mapIndexedNotNull { i, s ->
                runCatching {
                    val f = File(dir, "${key.replace(':', '_')}_$i.txt")
                    f.writeText(Streaming.text(s.src))
                    DlSub(s.label, s.srclang, f.absolutePath, s.default)
                }.getOrNull()
            }
        }
        val item = DlItem(key, mediaId, title, image, episode, Streaming.abs(src), format, ref, saved)
        saveItems(app, items(app).filter { it.key != key } + item)
        referer = ref
        val req = DownloadRequest.Builder(key, android.net.Uri.parse(item.src))
            .setMimeType(if (format == "hls") MimeTypes.APPLICATION_M3U8 else MimeTypes.VIDEO_MP4).build()
        manager(app) // make sure the manager exists before the service asks for it
        DownloadService.sendAddDownload(app, OfflineService::class.java, req, /* foreground = */ false)
    }

    fun remove(c: Context, key: String) {
        val app = c.applicationContext
        DownloadService.sendRemoveDownload(app, OfflineService::class.java, key, false)
        items(app).firstOrNull { it.key == key }?.subs?.forEach { runCatching { File(it.path).delete() } }
        saveItems(app, items(app).filter { it.key != key })
    }

    fun pause(c: Context, key: String, resume: Boolean) {
        DownloadService.sendSetStopReason(c.applicationContext, OfflineService::class.java, key, if (resume) Download.STOP_REASON_NONE else 1, false)
    }
}

class OfflineService : DownloadService(7001, DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL, "downloads", R.string.downloads_channel, 0) {
    override fun getDownloadManager(): DownloadManager = Offline.manager(this)
    override fun getScheduler(): Scheduler? = null
    override fun getForegroundNotification(downloads: MutableList<Download>, notMetRequirements: Int): Notification {
        val open = android.app.PendingIntent.getActivity(
            this, 0, packageManager.getLaunchIntentForPackage(packageName) ?: Intent(),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        return DownloadNotificationHelper(this, "downloads").buildProgressNotification(this, applicationInfo.icon, open, null, downloads, notMetRequirements)
    }
}
