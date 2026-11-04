package com.anibiar.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Talks to the AniBiar TV website's own streaming API (the same one the web player uses):
 *   /api/anime/{anilistId}/episodes
 *   /api/anime/{anilistId}/watch?audio=sub|dub&ep=N&p=servers
 *   /api/skip?mal=..&ep=..
 * Stream and subtitle links come back as relative /api/proxy?t=… paths, so they are made absolute here.
 */
object Streaming {
    const val BASE = "https://anibiartv.vercel.app"

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
    private val client by lazy {
        Http.plain.newBuilder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(75, TimeUnit.SECONDS).build()
    }

    fun abs(path: String) = if (path.startsWith("http")) path else BASE + path

    private suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(BASE + path).header("Accept", "application/json").build()
        client.newCall(request).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                val msg = runCatching { json.decodeFromString<ErrorBody>(text).error }.getOrNull()
                throw IOException(msg ?: "Streaming server error (HTTP ${r.code}).")
            }
            text
        }
    }

    suspend fun episodes(id: Int): EpisodesPayload = json.decodeFromString(get("/api/anime/$id/episodes"))

    suspend fun watch(id: Int, audio: String, ep: Int, servers: List<String>): WatchPayload =
        json.decodeFromString(get("/api/anime/$id/watch?audio=$audio&ep=$ep" + if (servers.isNotEmpty()) "&p=" + servers.joinToString(",") else ""))

    suspend fun skip(mal: Int, ep: Int): SkipPayload? =
        runCatching { json.decodeFromString<SkipPayload>(get("/api/skip?mal=$mal&ep=$ep")) }.getOrNull()

    /** Raw text of a (proxied) subtitle file. */
    suspend fun text(url: String): String = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(abs(url)).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Subtitle HTTP ${r.code}")
            r.body?.string().orEmpty()
        }
    }
}

@Serializable private data class ErrorBody(val error: String? = null)

@Serializable
data class WatchEpisode(
    val number: Int,
    val title: String = "",
    val description: String? = null,
    val image: String? = null,
    val airDate: String? = null,
    val duration: Double? = null,
    val filler: Boolean = false,
    val providers: List<String> = emptyList(),
)

@Serializable
data class EpisodesPayload(
    val title: String? = null,
    val sub: List<WatchEpisode> = emptyList(),
    val dub: List<WatchEpisode> = emptyList(),
    val sources: List<String> = emptyList(),
)

@Serializable data class StreamSubtitle(val label: String = "Subtitle", val srclang: String = "und", val src: String, val default: Boolean = false)
@Serializable data class TimeRange(val start: Double, val end: Double)

@Serializable
data class StreamSource(
    val id: String,
    val name: String,
    /** "soft" = separate subtitle tracks, "hard" = subtitles burned into the picture. */
    val kind: String = "soft",
    /** "hls" or "file". */
    val format: String = "hls",
    val src: String,
    val subtitles: List<StreamSubtitle> = emptyList(),
    val intro: TimeRange? = null,
    val outro: TimeRange? = null,
)

@Serializable
data class WatchPayload(
    val audio: String = "sub",
    val episode: Int = 0,
    val streams: List<StreamSource> = emptyList(),
    val intro: TimeRange? = null,
    val outro: TimeRange? = null,
)

@Serializable
data class SkipPayload(val intro: TimeRange? = null, val outro: TimeRange? = null, val length: Double? = null)
