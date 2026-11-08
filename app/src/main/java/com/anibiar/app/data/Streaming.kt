package com.anibiar.app.data

import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

    /** The same web app, deployed a second time. Used automatically when the main server is out of quota (HTTP 402), rate limited or down. */
    private const val BACKUP = "https://anibiar-beta.vercel.app"
    @Volatile private var failoverHost: String? = null
    @Volatile private var failoverUntil = 0L
    private fun host(): String = failoverHost?.takeIf { System.currentTimeMillis() < failoverUntil } ?: BASE

    fun abs(path: String) = if (path.startsWith("http")) path else host() + path

    private class Cached<T>(val at: Long, val v: T)
    private val epsCache = java.util.concurrent.ConcurrentHashMap<Int, Cached<EpisodesPayload>>()
    private val watchCache = java.util.concurrent.ConcurrentHashMap<String, Cached<WatchPayload>>()

    private class ServerError(val code: Int, msg: String) : IOException(msg)

    private suspend fun getFrom(base: String, path: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(base + path).header("Accept", "application/json").build()
        client.newCall(request).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                val msg = runCatching { json.decodeFromString<ErrorBody>(text).error }.getOrNull()
                throw ServerError(r.code, msg ?: "Streaming server error (HTTP ${r.code}).")
            }
            text
        }
    }

    /** Asks the current server; if it is out of quota (402), rate limited (429), erroring (5xx) or unreachable, switches to the backup for 10 minutes. */
    private suspend fun get(path: String): String {
        val first = host()
        return try { getFrom(first, path) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: IOException) {
            val code = (e as? ServerError)?.code
            if (code != null && code in 400..499 && code != 402 && code != 429) throw e
            val other = if (first == BASE) BACKUP else BASE
            val r = try { getFrom(other, path) } catch (e2: kotlinx.coroutines.CancellationException) { throw e2 } catch (_: IOException) { throw e }
            failoverHost = other; failoverUntil = System.currentTimeMillis() + 600_000
            tokens.clear(); epsCache.clear(); watchCache.clear()
            r
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
        val order = servers.map { it.lowercase() }.filter { tokens.containsKey("$id|$audio|$ep|$it") }.take(10)
        if (order.isEmpty()) return null
        return kotlinx.coroutines.coroutineScope {
            val ch = kotlinx.coroutines.channels.Channel<List<StreamSource>>(kotlinx.coroutines.channels.Channel.UNLIMITED)
            val jobs = order.map { a -> launch { ch.send(kotlinx.coroutines.withTimeoutOrNull(14_000) { runCatching { gatewayProvider(id, audio, ep, a) }.getOrNull() }.orEmpty()) } }
            val all = mutableListOf<StreamSource>(); var left = jobs.size; var deadline = 0L
            while (left > 0) {
                val got = if (deadline == 0L) ch.receive() else kotlinx.coroutines.withTimeoutOrNull((deadline - System.currentTimeMillis()).coerceAtLeast(1)) { ch.receive() } ?: break
                left--
                if (got.isNotEmpty()) { all += got; if (deadline == 0L) deadline = System.currentTimeMillis() + PROVIDER_GRACE_MS }
            }
            jobs.forEach { it.cancel() }
            val sorted = all.sortedBy { s -> order.indexOfFirst { s.id.startsWith("$it-") } }
            if (sorted.isEmpty()) null else WatchPayload(audio, ep, sorted, sorted.firstNotNullOfOrNull { it.intro }, sorted.firstNotNullOfOrNull { it.outro })
        }
    }

    /** Adds the in-app providers' episodes to the server's list (or stands in for it when the server is down). */
    private fun merge(base: EpisodesPayload?, local: Map<String, LocalSources.Local>): EpisodesPayload {
        val sub = java.util.TreeMap<Int, WatchEpisode>(); val dub = java.util.TreeMap<Int, WatchEpisode>()
        base?.sub?.forEach { sub[it.number] = it }; base?.dub?.forEach { dub[it.number] = it }
        for ((alias, l) in local) for ((map, eps) in listOf(sub to l.sub, dub to l.dub)) for (e in eps) {
            val old = map[e.number]
            map[e.number] = if (old == null) e.copy(providers = listOf(alias)) else if (alias in old.providers) old else old.copy(
                title = old.title.ifBlank { e.title }, image = old.image ?: e.image, providers = old.providers + alias,
            )
        }
        return EpisodesPayload(base?.title, sub.values.toList(), dub.values.toList(), ((base?.sources.orEmpty()) + local.keys.map(::label)).distinct())
    }

    private fun diskFile(id: Int): java.io.File? = Http.context?.let { java.io.File(it.cacheDir, "eps-$id.json") }

    private fun readDisk(id: Int): EpisodesPayload? = runCatching {
        val f = diskFile(id) ?: return null
        if (!f.exists() || System.currentTimeMillis() - f.lastModified() > 7L * 24 * 3600_000) return null
        json.decodeFromString<EpisodesPayload>(f.readText())
    }.getOrNull()

    private fun writeDisk(id: Int, p: EpisodesPayload) {
        runCatching { diskFile(id)?.writeText(json.encodeToString(EpisodesPayload.serializer(), p)) }
    }

    /**
     * Same result as [episodes], but reports early: a saved copy shows instantly, then the list is refreshed
     * every time the server or any in-app provider answers, so the player can start on the first partial result.
     */
    suspend fun episodesLive(id: Int, onUpdateRaw: (EpisodesPayload) -> Unit) = withContext(Dispatchers.Default) { episodesLiveInner(id, onUpdateRaw) }

    private suspend fun episodesLiveInner(id: Int, onUpdateRaw: (EpisodesPayload) -> Unit) {
        val onUpdate: (EpisodesPayload) -> Unit = { p -> runCatching { onUpdateRaw(p) } }
        epsCache[id]?.takeIf { System.currentTimeMillis() - it.at < 30_000 }?.let { onUpdate(it.v); return }
        val saved = readDisk(id)?.also { onUpdate(it) }
        val lock = kotlinx.coroutines.sync.Mutex()
        var base: EpisodesPayload? = null
        val locals = LinkedHashMap<String, LocalSources.Local>()
        var last: EpisodesPayload? = null
        var serverErr: Throwable? = null
        kotlinx.coroutines.coroutineScope {
            launch {
                val r = runCatching { gatewayEpisodes(id) ?: json.decodeFromString<EpisodesPayload>(get("/api/anime/$id/episodes")) }
                lock.withLock {
                    base = r.getOrNull(); serverErr = r.exceptionOrNull()
                    if (base != null) { last = merge(base, locals); onUpdate(last!!) }
                }
            }
            launch {
                LocalSources.episodesEach(id) { alias, l ->
                    lock.withLock {
                        locals[alias] = l
                        last = merge(base, locals); onUpdate(last!!)
                    }
                }
            }
        }
        // Nothing fresh (server and providers all failed, e.g. AniList rate limit): keep showing the saved list instead of an error.
        val done = last ?: if (saved != null) return else throw serverErr ?: IOException("No episodes found")
        epsCache[id] = Cached(System.currentTimeMillis(), done)
        writeDisk(id, done)
    }

    suspend fun episodes(id: Int): EpisodesPayload = withContext(Dispatchers.Default) { episodesInner(id) }

    private suspend fun episodesInner(id: Int): EpisodesPayload {
        epsCache[id]?.takeIf { System.currentTimeMillis() - it.at < 30_000 }?.let { return it.v }
        val r = kotlinx.coroutines.coroutineScope {
            val srv = async { runCatching { gatewayEpisodes(id) ?: json.decodeFromString<EpisodesPayload>(get("/api/anime/$id/episodes")) } }
            val loc = async { runCatching { LocalSources.episodes(id) }.getOrNull().orEmpty() }
            val s = srv.await()
            val base = s.getOrNull()
            // With a healthy server don't wait long for the in-app sources; if the server failed, they are all we have.
            val l = if (base != null) kotlinx.coroutines.withTimeoutOrNull(2500) { loc.await() }.orEmpty() else loc.await()
            if (base == null && l.isEmpty()) throw s.exceptionOrNull() ?: IOException("No episodes found")
            merge(base, l)
        }
        epsCache[id] = Cached(System.currentTimeMillis(), r)
        return r
    }

    suspend fun watch(id: Int, audio: String, ep: Int, servers: List<String>): WatchPayload = withContext(Dispatchers.Default) { watchInner(id, audio, ep, servers) }

    /** Reads an HLS master playlist and returns the stream with its best resolution filled in (never throws, 5 s limit). */
    private suspend fun probe(s: StreamSource): StreamSource {
        if (s.format != "hls" || !s.src.startsWith("http")) return s
        val h = kotlinx.coroutines.withTimeoutOrNull(5_000) {
            runCatching {
                withContext(Dispatchers.IO) {
                    val b = Request.Builder().url(s.src).header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    LocalSources.referers[s.src]?.let { b.header("Referer", it).header("Origin", it.trimEnd('/')) }
                    Http.plain.newCall(b.build()).execute().use { r ->
                        val text = r.body?.string().orEmpty()
                        com.anibiar.app.nativecore.Native.hlsHeight(text) ?: (resolutionRe.findAll(text).maxOfOrNull { it.groupValues[1].toInt() } ?: 0)
                    }
                }
            }.getOrNull()
        } ?: 0
        return if (h > 0) s.copy(height = h) else s
    }

    /** After the first provider answers, the others get this long to show up (slow ones like Nami take several seconds). */
    private const val PROVIDER_GRACE_MS = 8_000L

    private fun isIdeal(s: StreamSource, audio: String) = s.height >= 1080 && (audio != "sub" || (s.kind == "soft" && s.subtitles.any(::isEnglish)))

    private val resolutionRe = Regex("RESOLUTION=\\d+x(\\d+)")
    private fun isEnglish(t: StreamSubtitle) = t.srclang.startsWith("en", true) || t.label.contains("english", true)

    /**
     * Best stream first: picture quality, then (for sub audio) soft subtitles before burned-in ones with English
     * tracks first. Done in Rust, with the same rules in Kotlin as backup.
     */
    internal fun rankStreams(audio: String, list: List<StreamSource>): List<StreamSource> {
        val sub = audio == "sub"
        val rows = list.map { intArrayOf(if (it.kind == "soft") 1 else 0, if (it.subtitles.any(::isEnglish)) 1 else 0, if (it.subtitles.isNotEmpty()) 1 else 0, if (it.format == "hls") 1 else 0, it.height) }
        com.anibiar.app.nativecore.Native.rank(sub, rows)?.let { order -> return order.map { list[it] } }
        fun tier(s: StreamSource) = if (!sub || s.kind != "soft") 0 else if (s.subtitles.any(::isEnglish)) 3 else if (s.subtitles.isNotEmpty()) 2 else 1
        fun bucket(h: Int) = when { h <= 0 -> 720; h < 300 -> 240; h < 450 -> 360; h < 700 -> 480; h < 1000 -> 720; h < 2000 -> 1080; else -> 2160 }
        return list.sortedWith(compareByDescending<StreamSource> { bucket(it.height) }.thenByDescending { tier(it) }.thenByDescending { it.height }.thenByDescending { it.format == "hls" })
    }

    private suspend fun probeAll(l: List<StreamSource>): List<StreamSource> = kotlinx.coroutines.coroutineScope { l.map { async { probe(it) } }.awaitAll() }

    private class Res(val base: WatchPayload?, val streams: List<StreamSource>, val err: Throwable?, val local: String?)

    /**
     * Starts every server at once. It returns when every server has answered, when a 1080p+ (soft-sub) stream has
     * arrived, or [PROVIDER_GRACE_MS] after the first answer, so slow providers are no longer cut off but a server that
     * never answers still cannot hold up playback.
     */
    private suspend fun watchInner(id: Int, audio: String, ep: Int, servers: List<String>): WatchPayload {
        val key = "$id|$audio|$ep|${servers.firstOrNull()}"
        watchCache[key]?.takeIf { System.currentTimeMillis() - it.at < 240_000 }?.let { return it.v }
        val local = servers.filter { it.lowercase() in LocalSources.aliases }
        val remote = servers.filter { it.lowercase() !in LocalSources.aliases }
        val ch = kotlinx.coroutines.channels.Channel<Res>(kotlinx.coroutines.channels.Channel.UNLIMITED)
        val r = kotlinx.coroutines.coroutineScope {
            val jobs = mutableListOf<kotlinx.coroutines.Job>()
            if (remote.isNotEmpty()) jobs += launch {
                val res = kotlinx.coroutines.withTimeoutOrNull(16_000) {
                    runCatching { gatewayWatch(id, audio, ep, remote) ?: json.decodeFromString<WatchPayload>(get("/api/anime/$id/watch?audio=$audio&ep=$ep" + "&p=" + remote.joinToString(","))) }
                }
                ch.send(Res(res?.getOrNull(), probeAll(res?.getOrNull()?.streams.orEmpty()), res?.exceptionOrNull(), null))
            }
            for (a in local) jobs += launch {
                val res = kotlinx.coroutines.withTimeoutOrNull(14_000) { runCatching { LocalSources.watch(id, audio, ep, a.lowercase()) } }
                ch.send(Res(null, probeAll(res?.getOrNull().orEmpty()), res?.exceptionOrNull(), a.lowercase()))
            }
            var left = jobs.size; var deadline = 0L
            var base: WatchPayload? = null; var err: Throwable? = null
            val remoteStreams = mutableListOf<StreamSource>(); val localStreams = mutableListOf<Pair<String, List<StreamSource>>>()
            while (left > 0) {
                val got = if (deadline == 0L) ch.receive() else kotlinx.coroutines.withTimeoutOrNull((deadline - System.currentTimeMillis()).coerceAtLeast(1)) { ch.receive() } ?: break
                left--
                if (got.err != null) err = got.err
                if (got.local == null) { if (got.base != null) base = got.base; remoteStreams += got.streams } else if (got.streams.isNotEmpty()) localStreams += got.local to got.streams
                if (got.streams.isNotEmpty() && deadline == 0L) deadline = System.currentTimeMillis() + PROVIDER_GRACE_MS
                // Stop early only when nothing better can turn up: 1080p+ (with soft English subs for sub audio).
                if ((remoteStreams + localStreams.flatMap { it.second }).any { isIdeal(it, audio) }) break
            }
            jobs.forEach { it.cancel() }
            val all = rankStreams(audio, remoteStreams + localStreams.sortedBy { servers.indexOfFirst { s -> s.equals(it.first, true) } }.flatMap { it.second })
            if (all.isEmpty()) throw err ?: IOException("No stream found for this episode")
            WatchPayload(audio, ep, all, base?.intro ?: all.firstNotNullOfOrNull { it.intro }, base?.outro ?: all.firstNotNullOfOrNull { it.outro })
        }
        watchCache[key] = Cached(System.currentTimeMillis(), r)
        return r
    }

    /** Drops the saved stream list for one episode so the next [watch] asks the providers again (used by Retry). */
    fun forget(id: Int, audio: String, ep: Int) { watchCache.keys.removeAll { it.startsWith("$id|$audio|$ep|") } }

    suspend fun prefetch(id: Int, audio: String, ep: Int, servers: List<String>) {
        val p = runCatching { watch(id, audio, ep, servers) }.getOrNull() ?: return
        p.streams.firstOrNull()?.let { warm(it) }
    }

    /** Fetches the playlist and the first piece of video so DNS, TLS and the CDN are warm when the next episode starts. Never throws. */
    private suspend fun warm(s: StreamSource) = withContext(Dispatchers.IO) {
        if (s.format != "hls" || !s.src.startsWith("http")) return@withContext
        runCatching {
            suspend fun fetch(url: String, limit: Long): String? {
                val b = Request.Builder().url(url)
                LocalSources.referers[s.src]?.let { b.header("Referer", it) }
                return client.newCall(b.build()).execute().use { r ->
                    if (!r.isSuccessful) null else r.body?.source()?.let { src -> src.request(limit); src.buffer.clone().readUtf8(minOf(limit, src.buffer.size)) }
                }
            }
            val master = fetch(s.src, 100_000) ?: return@runCatching
            val variant = master.lines().firstOrNull { it.isNotBlank() && !it.startsWith("#") } ?: return@runCatching
            val vUrl = java.net.URI(s.src).resolve(variant).toString()
            val media = fetch(vUrl, 100_000) ?: return@runCatching
            val seg = media.lines().firstOrNull { it.isNotBlank() && !it.startsWith("#") } ?: return@runCatching
            val b = Request.Builder().url(java.net.URI(vUrl).resolve(seg).toString())
            LocalSources.referers[s.src]?.let { b.header("Referer", it) }
            client.newCall(b.build()).execute().use { r -> r.body?.source()?.skip(minOf(400_000L, r.body?.contentLength()?.takeIf { it > 0 } ?: 400_000L)) }
        }
    }

    suspend fun skip(mal: Int, ep: Int): SkipPayload? =
        runCatching { json.decodeFromString<SkipPayload>(get("/api/skip?mal=$mal&ep=$ep")) }.getOrNull()

    /** Referer for the stream the player is playing now (only set for in-app providers). */
    @Volatile var activeReferer: String? = null
    /** Last player error, included in the Copy report button. */
    @Volatile var lastPlayError: String? = null
    fun isOwnHost(host: String?) = host != null && (BASE.endsWith(host) || BACKUP.endsWith(host))

    /** Raw text of a (proxied) subtitle file. */
    suspend fun text(url: String): String = withContext(Dispatchers.IO) {
        if (url.startsWith("file://")) return@withContext java.io.File(url.removePrefix("file://")).readText()
        client.newCall(Request.Builder().url(abs(url)).apply { LocalSources.referers[url]?.let { header("Referer", it).header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36") } }.build()).execute().use { r ->
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
    /** Highest picture height found in the stream's playlist, 0 when unknown. Used to pick the best server. */
    val height: Int = 0,
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
