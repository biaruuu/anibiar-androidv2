package com.anibiar.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.anibiar.app.BuildConfig
import com.anibiar.app.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

data class UpdateInfo(val build: Int, val url: String, val size: Long, val notes: String, val entries: List<ChangeEntry> = emptyList())

/**
 * Checks the GitHub release for a newer build, downloads Kiro.apk and hands it to the system installer.
 * Every build is signed with the same key, so it installs over the current app and keeps your login and data.
 */
object Updater {
    private const val LATEST = "https://api.github.com/repos/biaruuu/anibiar-androidv2/releases/latest"
    private val json = Json { ignoreUnknownKeys = true }
    private val client by lazy { Http.plain.newBuilder().readTimeout(60, TimeUnit.SECONDS).build() }

    private const val CHANGELOG = "https://raw.githubusercontent.com/biaruuu/anibiar-androidv2/main/app/src/main/assets/changelog.md"

    /** What changed since the version installed here (newer entries from the online changelog). */
    private fun newEntries(context: Context?): List<ChangeEntry> = runCatching {
        val have = context?.let { Changelog.bundled(it).maxOfOrNull { e -> e.id } } ?: 0
        client.newCall(Request.Builder().url(CHANGELOG).build()).execute().use { r ->
            if (!r.isSuccessful) emptyList() else Changelog.parse(r.body!!.string()).filter { it.id > have }
        }
    }.getOrDefault(emptyList())

    suspend fun check(context: Context? = null): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(LATEST).header("Accept", "application/vnd.github+json").build()
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val o = json.parseToJsonElement(r.body!!.string()).jsonObject
                val build = o["tag_name"]?.jsonPrimitive?.contentOrNull?.removePrefix("build-")?.toIntOrNull() ?: return@use null
                if (build <= BuildConfig.VERSION_CODE) return@use null
                val asset = o["assets"]?.jsonArray?.map { it.jsonObject }?.firstOrNull { it["name"]?.jsonPrimitive?.contentOrNull == "Kiro.apk" } ?: return@use null
                UpdateInfo(
                    build, asset["browser_download_url"]!!.jsonPrimitive.content,
                    asset["size"]?.jsonPrimitive?.longOrNull ?: 0L, o["body"]?.jsonPrimitive?.contentOrNull.orEmpty(), newEntries(context),
                )
            }
        }.getOrNull()
    }

    /** Downloads the APK into the app cache, reporting 0f..1f. */
    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "Kiro-${info.build}.apk")
        val req = Request.Builder().url(info.url).build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("Download failed (${r.code})")
            val body = r.body ?: throw java.io.IOException("Empty download")
            val total = body.contentLength().takeIf { it > 0 } ?: info.size
            var done = 0L; var last = 0L
            body.byteStream().use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        o.write(buf, 0, n); done += n
                        val now = System.currentTimeMillis()
                        if (total > 0 && now - last > 120) { last = now; onProgress((done.toFloat() / total).coerceIn(0f, 1f)) }
                    }
                }
            }
        }
        onProgress(1f)
        out
    }

    /** True if the system installer was opened; false if the user must first allow installs from this app. */
    fun install(context: Context, apk: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return true
    }
}
