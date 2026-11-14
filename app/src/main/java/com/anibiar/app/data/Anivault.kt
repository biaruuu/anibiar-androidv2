package com.anibiar.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * The AniBiar scraper API (source: github.com/biaruuu/Anivault-API, hosted on Render).
 *   /api/episodes?anilistId=&source=      episode list per site
 *   /api/watch/{source}/{anilistId}/{ep}/{sub|dub}   a playable stream (already proxied, so no Referer is needed)
 * Each site is one provider in the Server menu.
 */
object Anivault {
    const val BASE = "https://kiroapp-api.onrender.com"

    /** Provider alias in the app -> source name in the API. */
    private val SOURCE = linkedMapOf("anikoto" to "anikoto", "megaplay" to "megaplay", "heaven" to "animeheaven", "desidub" to "desidub")
    val aliases: List<String> = SOURCE.keys.toList()
    /** DesiDub only carries dubbed / multi-audio episodes. */
    private val DUB_ONLY = setOf("desidub")
    private val NO_DUB = setOf("heaven")

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    // Render's free plan sleeps when idle; the first call can take a minute while it wakes up.
    private val http by lazy { Http.plain.newBuilder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build() }
    @Volatile private var lastPing = 0L

    /** Wakes the server up early (fire and forget). */
    fun warmUp() {
        val now = System.currentTimeMillis()
        if (now - lastPing < 240_000) return
        lastPing = now
        Thread { runCatching { http.newCall(Request.Builder().url("$BASE/api/health").build()).execute().close() } }.start()
    }

    private suspend fun get(path: String): JsonObject = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(BASE + path).header("Accept", "application/json").build()).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw IOException(runCatching { json.parseToJsonElement(text).jsonObject["error"]?.jsonPrimitive?.contentOrNull }.getOrNull() ?: "HTTP ${r.code}")
            json.parseToJsonElement(text).jsonObject
        }
    }

    private fun str(o: JsonObject, k: String) = (o[k] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }

    suspend fun episodes(anilistId: Int, alias: String): LocalSources.Local {
        val src = SOURCE[alias] ?: return LocalSources.Local(emptyList(), emptyList())
        val root = get("/api/episodes?anilistId=$anilistId&source=$src")
        val seen = java.util.TreeMap<Int, WatchEpisode>()
        for (e in (root["episodes"] as? JsonArray).orEmpty()) {
            val o = e as? JsonObject ?: continue
            val n = o["num"]?.jsonPrimitive?.doubleOrNull ?: continue
            if (n % 1.0 != 0.0 || n < 0) continue
            val title = str(o, "title")?.takeUnless { it.matches(Regex("(?i)episode\\s*\\d+")) }.orEmpty()
            seen.putIfAbsent(n.toInt(), WatchEpisode(n.toInt(), title, providers = listOf(alias)))
        }
        val list = seen.values.toList()
        return when (alias) {
            in DUB_ONLY -> LocalSources.Local(emptyList(), list)
            in NO_DUB -> LocalSources.Local(list, emptyList())
            else -> LocalSources.Local(list, list)
        }
    }

    suspend fun watch(anilistId: Int, audio: String, ep: Int, alias: String): List<StreamSource> {
        val src = SOURCE[alias] ?: return emptyList()
        val o = get("/api/watch/$src/$anilistId/$ep/$audio")
        if (o["iframeOnly"]?.jsonPrimitive?.booleanOrNull == true) return emptyList()
        val hls = str(o, "hlsProxyUrl") ?: str(o, "m3u8")
        val mp4 = str(o, "mp4ProxyUrl") ?: str(o, "streamUrl") ?: str(o, "mp4")
        val url = hls ?: mp4 ?: return emptyList()
        val subs = (o["subtitles"] as? JsonArray).orEmpty().mapNotNull { t ->
            val to = t as? JsonObject ?: return@mapNotNull null
            val u = str(to, "url") ?: str(to, "file") ?: return@mapNotNull null
            val label = str(to, "label") ?: str(to, "lang") ?: str(to, "srclang") ?: "Subtitle"
            StreamSubtitle(label, str(to, "srclang") ?: if (label.startsWith("English", true)) "en" else "und", u, to["default"]?.jsonPrimitive?.booleanOrNull ?: false)
        }
        fun range(k: String): TimeRange? = (o[k] as? JsonObject)?.let {
            val a = it["start"]?.jsonPrimitive?.doubleOrNull; val b = it["end"]?.jsonPrimitive?.doubleOrNull
            if (a != null && b != null && b > a) TimeRange(a, b) else null
        }
        val label = alias.replaceFirstChar { it.uppercase() }
        val server = str(o, "server")
        return listOf(StreamSource(
            id = "$alias-0", name = label + (server?.takeIf { !it.equals(label, true) && !it.startsWith("MegaPlay", true) }?.let { " · $it" } ?: ""),
            kind = if (audio == "sub" && subs.isEmpty()) "hard" else "soft",
            format = if (hls != null) "hls" else "file", src = url, subtitles = subs, intro = range("intro"), outro = range("outro"),
        ))
    }
}
