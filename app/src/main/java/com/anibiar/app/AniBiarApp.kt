package com.anibiar.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.anibiar.app.data.AnimeRepository
import com.anibiar.app.data.Http
import com.anibiar.app.data.WatchHistory
import com.anibiar.app.data.Watchlist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AniBiarApp : Application(), ImageLoaderFactory {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val repository: AnimeRepository by lazy { AnimeRepository(this) }
    val history: WatchHistory by lazy { WatchHistory(this) }
    val watchlist: Watchlist by lazy { Watchlist(this) }
    val stats: com.anibiar.app.data.WatchStats by lazy { com.anibiar.app.data.WatchStats(this, catalog) }
    val catalog: com.anibiar.app.data.Catalog by lazy { com.anibiar.app.data.Catalog(this) }
    val roomSession: com.anibiar.app.social.RoomSession by lazy { com.anibiar.app.social.RoomSession(this, social, appScope) }
    val social: com.anibiar.app.social.SocialRepository by lazy { com.anibiar.app.social.SocialRepository(this, appScope) }

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        com.anibiar.app.data.Diag.init(this)
        Http.init(this)
        com.anibiar.app.data.AppState.init(this)
        social.start()
        // "Continue watching" follows the account: push every change (at most one per anime every 20 s), pull when logging in.
        val lastPush = java.util.concurrent.ConcurrentHashMap<Int, Long>()
        history.listener = object : WatchHistory.Listener {
            override fun saved(item: com.anibiar.app.data.HistoryItem) {
                val now = System.currentTimeMillis()
                if (now - (lastPush[item.mediaId] ?: 0L) < 20_000 && item.progress < 0.95f) return
                lastPush[item.mediaId] = now
                appScope.launch { social.pushProgress(item) }
            }
            override fun removed(mediaId: Int) { appScope.launch { social.removeProgress(mediaId) } }
        }
        appScope.launch {
            social.authState.map { it?.uid }.distinctUntilChanged().collect { uid ->
                if (uid == null) return@collect
                val remote = social.pullProgress()
                history.merge(remote)
                // Anything only on this device goes up too.
                val known = remote.associateBy { it.mediaId }
                history.load().filter { (known[it.mediaId]?.at ?: -1L) < it.at }.forEach { social.pushProgress(it) }
            }
        }
        // Keep the home-screen widget in step with what you watch (at most every 10 s).
        appScope.launch { history.version.collect { ContinueWidget.refresh(this@AniBiarApp); kotlinx.coroutines.delay(10_000) } }
        // Connection back: send whatever progress couldn't go up while offline.
        appScope.launch { com.anibiar.app.data.AppState.netUp.collect { if (it > 0) social.flushPending() } }
        com.anibiar.app.data.NewEpisodeWorker.apply(this)
        appScope.launch { kotlinx.coroutines.delay(20_000); com.anibiar.app.data.Health.send(this@AniBiarApp, crashed = java.io.File(filesDir, "last-crash.txt").exists()) }
        // Build the network stack and warm image caches off the main thread while the UI starts.
        appScope.launch {
            Http.client
            com.anibiar.app.data.DnsWarm.run(listOf("graphql.anilist.co", "s4.anilist.co", "image.tmdb.org", "rfelzigknsclbiepzivl.supabase.co", "anibiartv.vercel.app"))
            repository.prewarm()
        }
    }

    /** Large memory + disk image caches keep posters instant and scrolling smooth. */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient { Http.client }
            .components {
                if (android.os.Build.VERSION.SDK_INT >= 28) add(coil.decode.ImageDecoderDecoder.Factory()) else add(coil.decode.GifDecoder.Factory())
            }
            .crossfade(150)
            .allowRgb565(false)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.30)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("images"))
                    .maxSizeBytes(384L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
}
