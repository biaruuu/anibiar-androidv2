package com.anibiar.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonObject
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

    private class Cached<T>(val at: Long, val v: T)
    private val epsCache = java.util.concurrent.ConcurrentHashMap<Int, Cached<EpisodesPayload>>()
    private val watchCache = java.util.concurrent.ConcurrentHashMap<String, Cached<WatchPayload>>()

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

    // ---- Kiro-WebApp gateway: /api/src?k=eps|watch and the /api/stream proxy. Used first; the older API is the fallback. ----
    private val tokens = java.util.concurrent.ConcurrentHashMap<String, String>()
    @Volatile private var gatewaySkipUntil = 0L
    private fun label(alias: String) = alias.replaceFirstChar { it.uppercase() }

    private suspend fun gatewayEpisodes(id: Int): EpisodesPayload? {
        if (System.currentTimeMillis() < gatewaySkipUntil) return null
        return runCatching {
            val root = Json.parseToJsonElement(get("/api/src?k=eps&id=$id")).jsonObject
            val aliases = root.keys.toList()
            val out = mutableMapOf<String, java.util.TreeMap<Int, WatchEpisode>>("sub" to java.util.TreeMap(), "dub" to java.util.TreeMap())
            for (alias in aliases) for (audio in listOf("sub", "dub")) {
                val list = root[alias]?.jsonObject?.get("episodes")?.jsonObject?.get(audio)?.jsonArray ?: continue
                for (e in list) {
                    val o = e.jsonObject
                    val n = o["number"]?.jsonPrimitive?.intOrNull ?: continue
                    val tok = o["id"]?.jsonPrimitive?.contentOrNull ?: continue
                    tokens["$id|$audio|$n|$alias"] = tok
                    fun str(k: String) = o[k]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
                    val map = out.getValue(audio)
                    val old = map[n]
                    map[n] = if (old == null) WatchEpisode(n, str("title").orEmpty(), str("description"), str("image"), str("airDate"), o["duration"]?.jsonPrimitive?.doubleOrNull, o["filler"]?.jsonPrimitive?.booleanOrNull ?: false, listOf(alias))
                    else old.copy(
                        title = old.title.ifBlank { str("title").orEmpty() }, description = old.description ?: str("description"),
                        image = old.image ?: str("image"), airDate = old.airDate ?: str("airDate"), providers = old.providers + alias,
                    )
                }
            }
            if (out.getValue("sub").isEmpty() && out.getValue("dub").isEmpty()) error("empty")
            EpisodesPayload(null, out.getValue("sub").values.toList(), out.getValue("dub").values.toList(), aliases.map(::label))
        }.onFailure { gatewaySkipUntil = System.currentTimeMillis() + 60_000 }.getOrNull()
    }

    private suspend fun gatewayProvider(id: Int, audio: String, ep: Int, alias: String): List<StreamSource> {
        val tok = tokens["$id|$audio|$ep|$alias"] ?: return emptyList()
        val o = Json.parseToJsonElement(get("/api/src?k=watch&t=" + java.net.URLEncoder.encode(tok, "UTF-8"))).jsonObject
        fun subs(a: kotlinx.serialization.json.JsonElement?) = (a as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { t ->
            val to = t.jsonObject; val u = to["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            StreamSubtitle(to["label"]?.jsonPrimitive?.contentOrNull ?: to["srclang"]?.jsonPrimitive?.contentOrNull ?: "Subtitle", to["srclang"]?.jsonPrimitive?.contentOrNull ?: "und", u, to["default"]?.jsonPrimitive?.booleanOrNull ?: false)
        }
        fun range(a: kotlinx.serialization.json.JsonElement?): TimeRange? = (a as? JsonObject)?.let {
            val st = it["start"]?.jsonPrimitive?.doubleOrNull; val en = it["end"]?.jsonPrimitive?.doubleOrNull
            if (st != null && en != null && en > st) TimeRange(st, en) else null
        }
        val top = subs(o["subtitles"])
        val list = o["streams"]?.jsonArray.orEmpty().map { it.jsonObject }.filter { it["type"]?.jsonPrimitive?.contentOrNull != "embed" && it["url"]?.jsonPrimitive?.contentOrNull != null }
        return list.mapIndexed { i, s ->
            val own = subs(s["subtitles"]).ifEmpty { top }
            StreamSource(
                id = "$alias-$i", name = label(alias) + if (list.size > 1) " ${i + 1}" else "",
                kind = if (audio == "sub" && own.isEmpty()) "hard" else "soft",
                format = if (s["type"]?.jsonPrimitive?.contentOrNull == "mp4") "file" else "hls",
                src = s["url"]!!.jsonPrimitive.content, subtitles = own, intro = range(s["intro"]), outro = range(s["outro"]),
            )
        }
    }

    /** Asks several providers at once and returns as soon as the first one answers (plus a short grace for the rest). */
    private suspend fun gatewayWatch(id: Int, audio: String, ep: Int, servers: List<String>): WatchPayload? {
        val order = servers.map { it.lowercase() }.filter { tokens.containsKey("$id|$audio|$ep|$it") }.take(4)
        if (order.isEmpty()) return null
        return kotlinx.coroutines.coroutineScope {
            val ch = kotlinx.coroutines.channels.Channel<List<StreamSource>>(kotlinx.coroutines.channels.Channel.UNLIMITED)
            val jobs = order.map { a -> launch { ch.send(kotlinx.coroutines.withTimeoutOrNull(14_000) { runCatching { gatewayProvider(id, audio, ep, a) }.getOrNull() }.orEmpty()) } }
            val all = mutableListOf<StreamSource>(); var left = jobs.size; var deadline = 0L
            while (left > 0) {
                val got = if (deadline == 0L) ch.receive() else kotlinx.coroutines.withTimeoutOrNull((deadline - System.currentTimeMillis()).coerceAtLeast(1)) { ch.receive() } ?: break
                left--
                if (got.isNotEmpty()) { all += got; if (deadline == 0L) deadline = System.currentTimeMillis() + 1500 }
            }
            jobs.forEach { it.cancel() }
            val sorted = all.sortedBy { s -> order.indexOfFirst { s.id.startsWith("$it-") } }
            if (sorted.isEmpty()) null else WatchPayload(audio, ep, sorted, sorted.firstNotNullOfOrNull { it.intro }, sorted.firstNotNullOfOrNull { it.outro })
        }
    }

    suspend fun episodes(id: Int): EpisodesPayload {
        epsCache[id]?.takeIf { System.currentTimeMillis() - it.at < 120_000 }?.let { return it.v }
        val r = gatewayEpisodes(id) ?: json.decodeFromString(get("/api/anime/$id/episodes"))
        epsCache[id] = Cached(System.currentTimeMillis(), r)
        return r
    }

    suspend fun watch(id: Int, audio: String, ep: Int, servers: List<String>): WatchPayload {
        val key = "$id|$audio|$ep|${servers.firstOrNull()}"
        watchCache[key]?.takeIf { System.currentTimeMillis() - it.at < 240_000 }?.let { return it.v }
        val r = gatewayWatch(id, audio, ep, servers)
            ?: json.decodeFromString<WatchPayload>(get("/api/anime/$id/watch?audio=$audio&ep=$ep" + if (servers.isNotEmpty()) "&p=" + servers.joinToString(",") else ""))
        watchCache[key] = Cached(System.currentTimeMillis(), r)
        return r
    }

    /** Warms the cache for the next episode so pressing Next starts at once. */
    suspend fun prefetch(id: Int, audio: String, ep: Int, servers: List<String>) { runCatching { watch(id, audio, ep, servers) } }

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
