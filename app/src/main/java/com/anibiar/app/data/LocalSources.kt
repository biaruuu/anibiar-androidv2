package com.anibiar.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Streaming sources that run inside the app itself (ported from the Anivexa-API providers), so episodes and streams
 * still load when the AniBiar server is slow, out of quota or down. Only providers that work over plain HTTP are ported.
 */
object LocalSources {
    // The in-app servers get their own names (shown in the Server menu).
    const val KAZE = "kaze"      // AniList-id based JSON API: sub + dub, several servers
    const val TSUKI = "tsuki"    // HLS with soft subtitles
    const val DUNYA = "sakura"   // MAL-id based, HLS with soft subtitles
    const val GG = "momiji"      // sub + dub, MP4/HLS
    const val HOSHI = "hoshi"    // JSON API, HLS
    const val YUKI = "yuki"      // sub + dub, vidmoly / nova players
    val aliases = listOf(KAZE, TSUKI, DUNYA, GG, HOSHI, YUKI)

    private const val KAZE_API = "https://animetsu.live/v2/api/anime"
    private const val KAZE_PROXY = "https://swiftstream.top/proxy"
    private const val KAZE_SITE = "https://animetsu.live/"
    private const val TSUKI_BASE = "https://anizone.to"

    private const val DUNYA_BASE = "https://anime-dunya.com"
    private const val GG_BASE = "https://www.animegg.org"
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val http by lazy { Http.plain.newBuilder().connectTimeout(6, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).build() }

    /** Streams from these providers need a Referer; the player looks it up here by URL. */
    val referers = ConcurrentHashMap<String, String>()

    class Local(val sub: List<WatchEpisode>, val dub: List<WatchEpisode>)

    // ---------------------------------------------------------------- shared helpers
    private suspend fun html(url: String, referer: String? = null): String = withContext(Dispatchers.IO) {
        val b = Request.Builder().url(url).header("User-Agent", UA).header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8").header("Accept-Language", "en-US,en;q=0.9")
        if (referer != null) b.header("Referer", referer)
        http.newCall(b.build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            r.body?.string().orEmpty()
        }
    }

    private fun decodeEntities(s: String) = s.replace(Regex("&#(\\d+);")) { it.groupValues[1].toInt().toChar().toString() }
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").trim()
    private fun stripTags(s: String) = decodeEntities(s.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " "))
    private fun attr(tag: String, name: String) = Regex("$name=[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)?.let(::decodeEntities).orEmpty()
    private fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")
    private fun dice(a: String, b: String): Double {
        val na = norm(a); val nb = norm(b)
        if (na == nb) return 1.0
        if (na.length < 2 || nb.length < 2) return 0.0
        val grams = HashMap<String, Int>()
        for (i in 0 until na.length - 1) na.substring(i, i + 2).let { grams[it] = (grams[it] ?: 0) + 1 }
        var hits = 0
        for (i in 0 until nb.length - 1) { val g = nb.substring(i, i + 2); val c = grams[g] ?: 0; if (c > 0) { grams[g] = c - 1; hits++ } }
        return 2.0 * hits / (na.length - 1 + nb.length - 1)
    }

    // ---------------------------------------------------------------- AniList info
    class Meta(val idMal: Int?, val titles: List<String>, val episodes: Int?, val format: String?)
    private val metaCache = ConcurrentHashMap<Int, Meta>()

    private val metaLock = kotlinx.coroutines.sync.Mutex()
    private suspend fun meta(id: Int): Meta {
        metaCache[id]?.let { return it }
        return metaLock.withLock { metaCache[id] ?: fetchMeta(id) }
    }

    private suspend fun fetchMeta(id: Int): Meta {
        val body = buildJsonObject {
            put("query", "query(\$id:Int){Media(id:\$id,type:ANIME){idMal title{english romaji} synonyms episodes format}}")
            put("variables", buildJsonObject { put("id", id) })
        }.toString()
        val text = withContext(Dispatchers.IO) {
            http.newCall(Request.Builder().url("https://graphql.anilist.co").post(body.toRequestBody("application/json".toMediaType())).header("Accept", "application/json").build())
                .execute().use { r -> if (!r.isSuccessful) throw IOException("AniList HTTP ${r.code}"); r.body?.string().orEmpty() }
        }
        val m = json.parseToJsonElement(text).jsonObject["data"]?.jsonObject?.get("Media")?.jsonObject ?: throw IOException("AniList: no media")
        val t = m["title"]?.jsonObject
        val titles = listOfNotNull(t?.get("english")?.jsonPrimitive?.contentOrNull, t?.get("romaji")?.jsonPrimitive?.contentOrNull) +
            (m["synonyms"] as? JsonArray).orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull }.filter { s -> s.all { it.code < 0x2E80 } }.take(3)
        return Meta(m["idMal"]?.jsonPrimitive?.intOrNull, titles.distinct(), m["episodes"]?.jsonPrimitive?.intOrNull, m["format"]?.jsonPrimitive?.contentOrNull).also { metaCache[id] = it }
    }

    // ---------------------------------------------------------------- AnimeDunya (by MAL id; sub, HLS, soft subtitles)
    private fun bracketSlice(h: String, pattern: Regex, open: Char, close: Char, startDepth: Int): String? {
        val m = pattern.find(h) ?: return null
        var depth = startDepth; var started = startDepth > 0
        val sb = StringBuilder(if (startDepth > 0) open.toString() else "")
        for (i in m.range.last + 1 until h.length) {
            val c = h[i]
            if (c == open) { depth++; started = true } else if (c == close) depth--
            if (started) { sb.append(c); if (depth == 0) return sb.toString() }
        }
        return null
    }
    private fun unescape(s: String) = s.replace("\\u0026", "&").replace("\\\"", "\"").replace("\\\\", "\\")

    private suspend fun dunyaEpisodes(id: Int): Local {
        val mal = meta(id).idMal ?: error("no MAL id")
        val page = html("$DUNYA_BASE/en/anime/$mal")
        val raw = bracketSlice(page, Regex("\\\\?\"episodes\\\\?\":\\s*\\["), '[', ']', 1) ?: error("no episodes")
        val thumb = Regex("(https?://[^\\s\"'`<>]+?/thumbnail/)([a-zA-Z0-9]+?)/((?:small|large)\\.jpg)").find(page)
        val cdn = thumb?.groupValues?.get(1) ?: "https://cdn.anime-dunya.com/thumbnail/"
        val ext = thumb?.groupValues?.get(3) ?: "small.jpg"
        val arr = json.parseToJsonElement(unescape(raw)).jsonArray
        val eps = arr.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val sid = o["streamId"]?.jsonPrimitive?.contentOrNull?.takeIf { it != "null" } ?: return@mapNotNull null
            val n = o["episodeNumber"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val tr = o["translations"]
            val title = when (tr) {
                is JsonArray -> tr.firstOrNull { it.jsonObject["language"]?.jsonPrimitive?.contentOrNull == "en" }?.jsonObject?.get("title")?.jsonPrimitive?.contentOrNull
                is JsonObject -> tr["title"]?.jsonPrimitive?.contentOrNull
                else -> null
            }
            WatchEpisode(n, title.orEmpty(), image = "$cdn$sid/$ext", filler = o["filler"]?.jsonPrimitive?.booleanOrNull ?: false, providers = listOf(DUNYA))
        }.sortedBy { it.number }
        return Local(eps, emptyList())
    }

    private suspend fun dunyaWatch(id: Int, ep: Int): List<StreamSource> {
        val mal = meta(id).idMal ?: error("no MAL id")
        val page = html("$DUNYA_BASE/en/play/$mal/$ep")
        val raw = bracketSlice(page, Regex("\\\\?\"stream\\\\?\":\\s*"), '{', '}', 0)
        val obj = raw?.let { runCatching { json.parseToJsonElement(unescape(it)).jsonObject }.getOrNull() }
        val source = obj?.get("source")?.jsonPrimitive?.contentOrNull
            ?: Regex("\"source\"\\s*:\\s*\"([^\"]+)\"").find(page)?.groupValues?.get(1)?.replace("\\u0026", "&")?.replace("\\/", "/")?.replace("\\", "") ?: error("no stream")
        val subs = (obj?.get("subtitles") as? JsonArray).orEmpty().mapNotNull { s ->
            val so = s as? JsonObject ?: return@mapNotNull null
            val u = so["src"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            referers[u] = "$DUNYA_BASE/"
            StreamSubtitle(so["label"]?.jsonPrimitive?.contentOrNull ?: "Subtitle", so["srclang"]?.jsonPrimitive?.contentOrNull ?: "und", u, so["default"]?.jsonPrimitive?.booleanOrNull ?: false)
        }
        referers[source] = "$DUNYA_BASE/"
        return listOf(StreamSource("$DUNYA-0", "Sakura", if (subs.isEmpty()) "hard" else "soft", if (source.contains(".m3u8")) "hls" else "file", source, subs))
    }

    // ---------------------------------------------------------------- AnimeGG (title search; sub + dub, MP4/HLS)
    private class GgEp(val number: Int, val title: String, val slug: String, val sub: Boolean, val dub: Boolean)
    private class GgSeries(val slug: String, val eps: List<GgEp>)
    private val ggCache = ConcurrentHashMap<Int, GgSeries>()

    private suspend fun ggSearch(q: String): List<Pair<String, String>> {
        val h = html("$GG_BASE/search/?q=${URLEncoder.encode(q, "UTF-8")}")
        return Regex("<a\\b[^>]*class=[\"'][^\"']*\\bmse\\b[^\"']*[\"'][^>]*>[\\s\\S]*?</a>", RegexOption.IGNORE_CASE).findAll(h).mapNotNull { m ->
            val tag = Regex("<a\\b[^>]*>", RegexOption.IGNORE_CASE).find(m.value)?.value.orEmpty()
            val slug = Regex("^/series/([^/?#]+)").find(attr(tag, "href"))?.groupValues?.get(1) ?: return@mapNotNull null
            val strong = Regex("<strong[^>]*>([\\s\\S]*?)</strong>", RegexOption.IGNORE_CASE).find(m.value)?.groupValues?.get(1)
            slug to (if (strong != null) stripTags(strong) else slug.replace("-", " "))
        }.toList()
    }

    private suspend fun ggEpisodesOf(slug: String): List<GgEp> {
        val h = html("$GG_BASE/series/$slug")
        val out = LinkedHashMap<Int, GgEp>()
        for (m in Regex("<li\\b[^>]*>([\\s\\S]*?)</li>", RegexOption.IGNORE_CASE).findAll(h)) {
            val block = m.groupValues[1]
            if (!Regex("\\banm_det_pop\\b").containsMatchIn(block)) continue
            val link = Regex("<a\\b[^>]*class=[\"'][^\"']*anm_det_pop[^\"']*[\"'][^>]*>", RegexOption.IGNORE_CASE).find(block)?.value.orEmpty()
            val href = attr(link, "href").replace(Regex("#.*$"), "").removePrefix("/")
            val strong = stripTags(Regex("<strong[^>]*>([\\s\\S]*?)</strong>", RegexOption.IGNORE_CASE).find(block)?.groupValues?.get(1).orEmpty())
            val num = (Regex("(\\d+)-(\\d+)\\s*$").find(strong) ?: Regex("(\\d+)\\s*$").find(strong))?.groupValues?.get(1)?.toIntOrNull()
            if (num == null || href.isEmpty() || out.containsKey(num)) continue
            val title = stripTags(Regex("<i\\b[^>]*class=[\"'][^\"']*anititle[^\"']*[\"'][^>]*>([\\s\\S]*?)</i>", RegexOption.IGNORE_CASE).find(block)?.groupValues?.get(1).orEmpty()).ifBlank { strong }
            out[num] = GgEp(num, title, href, Regex("\\bbtn-subbed\\b").containsMatchIn(block), Regex("\\bbtn-dubbed\\b").containsMatchIn(block))
        }
        return out.values.sortedBy { it.number }
    }

    private suspend fun ggSeries(id: Int): GgSeries {
        ggCache[id]?.let { return it }
        val m = meta(id)
        val movie = m.format.equals("MOVIE", true) || m.episodes == 1
        val found = LinkedHashMap<String, String>()
        for (t in m.titles.take(3)) {
            runCatching { ggSearch(t) }.getOrNull()?.forEach { (s, txt) -> found.putIfAbsent(s, txt) }
            val compact = t.split(Regex("\\s+")).first().replace(Regex("[^a-zA-Z0-9]"), "")
            if (compact.length >= 4 && !compact.equals(t, true)) runCatching { ggSearch(compact) }.getOrNull()?.forEach { (s, txt) -> found.putIfAbsent(s, txt) }
        }
        val best = found.entries.map { e -> e to (m.titles.maxOfOrNull { dice(it, e.value) } ?: 0.0) }.filter { it.second >= if (movie) 0.9 else 0.65 }
            .sortedByDescending { it.second }.take(3)
        // Prefer the candidate whose episode count is closest to what AniList expects.
        val picks = coroutineScope { best.map { (e, score) -> async { runCatching { Triple(e.key, score, ggEpisodesOf(e.key)) }.getOrNull() } }.awaitAll() }.filterNotNull().filter { it.third.isNotEmpty() }
        val chosen = picks.minByOrNull { (_, score, eps) -> (m.episodes?.let { kotlin.math.abs(eps.size - it) } ?: 0) - score * 2 } ?: error("no match")
        return GgSeries(chosen.first, chosen.third).also { ggCache[id] = it }
    }

    private suspend fun ggEpisodes(id: Int): Local {
        val s = ggSeries(id)
        fun map(f: (GgEp) -> Boolean) = s.eps.filter(f).map { WatchEpisode(it.number, it.title, providers = listOf(GG)) }
        return Local(map { it.sub }, map { it.dub })
    }

    private suspend fun ggWatch(id: Int, audio: String, ep: Int): List<StreamSource> {
        val e = ggSeries(id).eps.firstOrNull { it.number == ep } ?: error("episode not found")
        val h = html("$GG_BASE/${e.slug}", GG_BASE)
        val tabs = Regex("<a\\b[^>]*data-toggle=[\"']tab[\"'][^>]*>", RegexOption.IGNORE_CASE).findAll(h).mapNotNull { m ->
            val tag = m.value
            val embed = attr(tag, "data-id"); if (embed.isEmpty()) return@mapNotNull null
            val dub = attr(tag, "data-version").ifEmpty { "subbed" }.startsWith("dub")
            if ((audio == "dub") != dub) null else embed to attr(tag, "data-mirror").ifEmpty { "AnimeGG" }
        }.toList()
        val lists = coroutineScope {
            tabs.map { (embed, _) -> async { runCatching { ggEmbed(embed) }.getOrDefault(emptyList()) } }.awaitAll()
        }
        val out = mutableListOf<StreamSource>()
        lists.forEachIndexed { i, srcs ->
            srcs.forEachIndexed { j, (label, url) ->
                referers[url] = "$GG_BASE/"
                out += StreamSource("$GG-${out.size}", "Momiji" + if (tabs.size > 1 || srcs.size > 1) " ${out.size + 1}" else "", if (audio == "sub") "hard" else "soft", if (url.contains(".m3u8")) "hls" else "file", url)
            }
        }
        return out
    }

    private suspend fun ggEmbed(embedId: String): List<Pair<String, String>> {
        val h = html("$GG_BASE/embed/$embedId", GG_BASE)
        val raw = Regex("var\\s+videoSources\\s*=\\s*(\\[[\\s\\S]*?\\]);").find(h)?.groupValues?.get(1) ?: return emptyList()
        val fixed = raw.replace(Regex("([{,]\\s*)([a-zA-Z_][a-zA-Z0-9_]*)\\s*:"), "$1\"$2\":").replace(Regex(":\\s*'([^']*)'"), ": \"$1\"")
        val arr = runCatching { json.parseToJsonElement(fixed).jsonArray }.getOrNull() ?: return emptyList()
        return arr.mapNotNull { s ->
            val o = s as? JsonObject ?: return@mapNotNull null
            val f = o["file"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            (o["label"]?.jsonPrimitive?.contentOrNull ?: "auto") to (if (f.startsWith("http")) f else "$GG_BASE$f")
        }
    }

    // ---------------------------------------------------------------- Kaze (animetsu: keyed by the AniList id, sub + dub)
    private val kazeHeaders = mapOf("Origin" to "https://animetsu.live", "Referer" to KAZE_SITE)
    private suspend fun kazeGet(url: String): String = withContext(Dispatchers.IO) {
        val b = Request.Builder().url(url).header("User-Agent", UA).header("Accept", "application/json")
        kazeHeaders.forEach { (k, v) -> b.header(k, v) }
        http.newCall(b.build()).execute().use { r -> if (!r.isSuccessful) throw IOException("HTTP ${r.code}"); r.body?.string().orEmpty() }
    }
    private fun kazeUrl(u: String) = if (u.startsWith("/")) KAZE_PROXY + u else u

    private suspend fun kazeEpisodes(id: Int): Local {
        val arr = json.parseToJsonElement(kazeGet("$KAZE_API/eps/$id")).jsonArray
        val eps = arr.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val n = o["ep_num"]?.jsonPrimitive?.intOrNull ?: o["ep_num"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.toInt() ?: return@mapNotNull null
            WatchEpisode(n, o["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it != "null" }.orEmpty(), image = o["img"]?.jsonPrimitive?.contentOrNull?.takeIf { it != "null" }?.let(::kazeUrl),
                filler = o["is_filler"]?.jsonPrimitive?.booleanOrNull ?: false, providers = listOf(KAZE))
        }.distinctBy { it.number }.sortedBy { it.number }
        return Local(eps, eps)
    }

    private suspend fun kazeWatch(id: Int, audio: String, ep: Int): List<StreamSource> {
        val servers = json.parseToJsonElement(kazeGet("$KAZE_API/servers/$id/$ep")).jsonArray.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull }
        val got = coroutineScope {
            servers.map { sv -> async { runCatching { json.parseToJsonElement(kazeGet("$KAZE_API/oppai/$id/$ep?server=${URLEncoder.encode(sv, "UTF-8")}&source_type=$audio")).jsonObject }.getOrNull() } }.awaitAll()
        }
        val out = mutableListOf<StreamSource>()
        for (o in got.filterNotNull()) {
            val sources = (o["sources"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            // Prefer the multi-quality master playlist so the player can switch quality by itself.
            val pick = sources.firstOrNull { it["quality"]?.jsonPrimitive?.contentOrNull?.trim() == "master" } ?: sources.firstOrNull() ?: continue
            val url = pick["url"]?.jsonPrimitive?.contentOrNull?.let(::kazeUrl) ?: continue
            val subs = (o["subs"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            val sub = subs.firstOrNull { it["lang"]?.jsonPrimitive?.contentOrNull == "English" } ?: subs.firstOrNull()
            val subUrl = sub?.get("url")?.jsonPrimitive?.contentOrNull?.let(::kazeUrl)
            referers[url] = KAZE_SITE
            val tracks = if (subUrl != null) { referers[subUrl] = KAZE_SITE; listOf(StreamSubtitle(sub["lang"]?.jsonPrimitive?.contentOrNull ?: "English", "en", subUrl, true)) } else emptyList()
            out += StreamSource("$KAZE-${out.size}", "Kaze" + if (servers.size > 1) " ${out.size + 1}" else "", if (audio == "sub" && tracks.isEmpty()) "hard" else "soft", if (url.contains(".mp4")) "file" else "hls", url, tracks)
        }
        return out
    }

    // ---------------------------------------------------------------- Tsuki (anizone: HLS, soft English subtitles)
    private class ZoneEp(val url: String, val title: String, val image: String?, val filler: Boolean)
    private class ZoneSeries(val eps: List<ZoneEp>)
    private val zoneCache = ConcurrentHashMap<Int, ZoneSeries>()

    private fun zoneJson(page: String, after: String?, prefix: String): String? {
        val from = after?.let { page.indexOf(it).takeIf { i -> i >= 0 } } ?: 0
        val m = Regex("$prefix\\s*JSON\\.parse\\('(.+?)'\\)", RegexOption.DOT_MATCHES_ALL).find(page, from) ?: return null
        return decodeEntities(m.groupValues[1]).replace("\\u0022", "\"")
    }

    private suspend fun zoneSeries(id: Int): ZoneSeries {
        zoneCache[id]?.let { return it }
        val m = meta(id)
        val found = LinkedHashMap<String, String>()
        for (t in m.titles.take(2)) {
            val page = runCatching { html("$TSUKI_BASE/anime?search=${URLEncoder.encode(t, "UTF-8")}") }.getOrNull() ?: continue
            val raw = zoneJson(page, "<main", "") ?: continue
            val arr = runCatching { json.parseToJsonElement(raw).jsonArray }.getOrNull() ?: continue
            for (e in arr) {
                val o = e as? JsonObject ?: continue
                val title = o["main_title"]?.jsonPrimitive?.contentOrNull ?: continue
                val url = o["url"]?.jsonPrimitive?.contentOrNull?.replace("\\", "")?.replace("\\", "") ?: continue
                found.putIfAbsent(url, title)
            }
        }
        val movie = m.format.equals("MOVIE", true) || m.episodes == 1
        val best = found.entries.map { e -> e to (m.titles.maxOfOrNull { dice(it, e.value) } ?: 0.0) }.filter { it.second >= if (movie) 0.9 else 0.7 }
            .sortedByDescending { it.second }.take(3)
        val picks = coroutineScope { best.map { (e, score) -> async { runCatching { Triple(e.key, score, zoneEpisodesOf(e.key)) }.getOrNull() } }.awaitAll() }.filterNotNull().filter { it.third.isNotEmpty() }
        val chosen = picks.minByOrNull { (_, score, eps) -> (m.episodes?.let { kotlin.math.abs(eps.size - it) } ?: 0) - score * 2 } ?: error("no match")
        return ZoneSeries(chosen.third).also { zoneCache[id] = it }
    }

    private suspend fun zoneEpisodesOf(url: String): List<ZoneEp> {
        val page = html(url)
        val raw = zoneJson(page, "<main", "items:") ?: error("no episodes")
        val arr = json.parseToJsonElement(raw).jsonArray
        return arr.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val u = o["url"]?.jsonPrimitive?.contentOrNull?.replace("\\", "") ?: return@mapNotNull null
            val tl = o["title_list"]
            val title = when (tl) { is JsonObject -> tl["1"]?.jsonPrimitive?.contentOrNull; is JsonArray -> tl.firstOrNull()?.jsonPrimitive?.contentOrNull; else -> null }
            ZoneEp(u, title.orEmpty(), o["snapshot"]?.jsonPrimitive?.contentOrNull?.replace("\\", ""), o["type"]?.jsonPrimitive?.contentOrNull.equals("filler", true))
        }
    }

    private suspend fun zoneEpisodes(id: Int): Local {
        val s = zoneSeries(id)
        return Local(s.eps.mapIndexed { i, e -> WatchEpisode(i + 1, e.title, image = e.image, filler = e.filler, providers = listOf(TSUKI)) }, emptyList())
    }

    private suspend fun zoneWatch(id: Int, ep: Int): List<StreamSource> {
        val e = zoneSeries(id).eps.getOrNull(ep - 1) ?: error("episode not found")
        val page = html(e.url)
        val raw = zoneJson(page, "mb-8", "") ?: error("no stream data")
        val o = json.parseToJsonElement(raw).jsonObject
        val src = o["src"]?.jsonPrimitive?.contentOrNull?.replace("\\", "") ?: error("no stream")
        val tracks = (o["subtitles"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            .filter { it["language"]?.jsonPrimitive?.contentOrNull == "en" }
            .sortedByDescending { it["default"]?.jsonPrimitive?.booleanOrNull ?: false }
            .mapNotNull { t ->
                val f = t["file"]?.jsonPrimitive?.contentOrNull?.replace("\\", "") ?: return@mapNotNull null
                val fmt = t["format"]?.jsonPrimitive?.contentOrNull.orEmpty().lowercase()
                if (fmt.isNotEmpty() && fmt != "vtt" && fmt != "srt" && fmt != "webvtt") null else StreamSubtitle("English", "en", f, true)
            }.take(1)
        referers[src] = "$TSUKI_BASE/"
        tracks.forEach { referers[it.src] = "$TSUKI_BASE/" }
        return listOf(StreamSource("$TSUKI-0", "Tsuki", if (tracks.isEmpty()) "hard" else "soft", "hls", src, tracks))
    }

    // ---------------------------------------------------------------- Hoshi (kickassanime: JSON API, HLS, sub)
    private const val KAA_BASE = "https://kaa.lt"
    private const val KAA_HLS = "https://hls.krussdomi.com/manifest"
    private class KaaEp(val number: Int, val full: String, val title: String)
    private class KaaSeries(val slug: String, val eps: List<KaaEp>)
    private val kaaCache = ConcurrentHashMap<Int, KaaSeries>()

    private suspend fun kaaGet(path: String, body: String? = null): String = withContext(Dispatchers.IO) {
        val b = Request.Builder().url(KAA_BASE + path).header("User-Agent", UA).header("Accept", "application/json")
        if (body != null) b.post(body.toRequestBody("application/json".toMediaType()))
        http.newCall(b.build()).execute().use { r -> if (!r.isSuccessful) throw IOException("HTTP ${r.code}"); r.body?.string().orEmpty() }
    }

    private suspend fun kaaSeries(id: Int): KaaSeries {
        kaaCache[id]?.let { return it }
        val m = meta(id)
        val queries = LinkedHashSet<String>()
        for (t in m.titles.take(3)) {
            if (t.any { it.code >= 0x2E80 }) continue
            val words = t.replace(Regex("[^\\w\\s]"), " ").split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.isEmpty()) continue
            if (words.size <= 3) queries += words.joinToString(" ") else { queries += words.take(2).joinToString(" "); queries += words.take(3).joinToString(" ") }
        }
        val found = LinkedHashMap<String, JsonObject>()
        coroutineScope {
            queries.take(6).map { q -> async { runCatching { json.parseToJsonElement(kaaGet("/api/fsearch", buildJsonObject { put("page", 1); put("query", q) }.toString())).jsonObject["result"]?.jsonArray }.getOrNull() } }.awaitAll()
        }.filterNotNull().forEach { arr -> arr.forEach { r -> (r as? JsonObject)?.let { o -> o["slug"]?.jsonPrimitive?.contentOrNull?.let { found.putIfAbsent(it, o) } } } }
        val af = m.format.orEmpty().uppercase()
        val scored = found.values.map { c ->
            val te = c["title_en"]?.jsonPrimitive?.contentOrNull.orEmpty(); val tj = c["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
            var score = m.titles.maxOfOrNull { t -> maxOf(dice(t, te), dice(t, tj)) } ?: 0.0
            val type = c["type"]?.jsonPrimitive?.contentOrNull.orEmpty().lowercase()
            if (af == "MOVIE" && type != "movie") score *= 0.25 else if (af != "MOVIE" && type == "movie") score *= 0.25
            c["slug"]!!.jsonPrimitive.content to score
        }.filter { it.second >= 0.65 }.sortedByDescending { it.second }
        val slug = scored.firstOrNull()?.first ?: error("no match")
        val info = json.parseToJsonElement(kaaGet("/api/show/$slug")).jsonObject
        val eps: List<KaaEp> = if (info["type"]?.jsonPrimitive?.contentOrNull == "movie") {
            Regex("/(ep-(\\d+)-([a-f0-9]+))$", RegexOption.IGNORE_CASE).find(info["watch_uri"]?.jsonPrimitive?.contentOrNull.orEmpty())?.let { listOf(KaaEp(1, it.groupValues[1], "")) }.orEmpty()
        } else {
            fun page(root: JsonObject) = (root["result"] as? JsonArray).orEmpty().mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                val n = o["episode_number"]?.jsonPrimitive?.intOrNull ?: o["episode_number"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.toInt() ?: return@mapNotNull null
                val s = o["slug"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                KaaEp(n, "ep-$n-$s", o["title"]?.jsonPrimitive?.contentOrNull.orEmpty())
            }
            val first = json.parseToJsonElement(kaaGet("/api/show/$slug/episodes?ep=1&lang=ja-JP")).jsonObject
            val rest = coroutineScope {
                (first["pages"] as? JsonArray).orEmpty().drop(1).map { pg ->
                    async {
                        val start = ((pg as? JsonObject)?.get("eps") as? JsonArray)?.firstOrNull()?.jsonPrimitive?.contentOrNull ?: return@async emptyList<KaaEp>()
                        runCatching { page(json.parseToJsonElement(kaaGet("/api/show/$slug/episodes?ep=$start&lang=ja-JP")).jsonObject) }.getOrDefault(emptyList())
                    }
                }.awaitAll().flatten()
            }
            page(first) + rest
        }
        if (eps.isEmpty()) error("no episodes")
        return KaaSeries(slug, eps.distinctBy { it.number }.sortedBy { it.number }).also { kaaCache[id] = it }
    }

    private suspend fun kaaEpisodes(id: Int): Local =
        Local(kaaSeries(id).eps.map { WatchEpisode(it.number, it.title, providers = listOf(HOSHI)) }, emptyList())

    private suspend fun kaaWatch(id: Int, ep: Int): List<StreamSource> {
        val s = kaaSeries(id)
        val e = s.eps.firstOrNull { it.number == ep } ?: error("episode not found")
        val servers = (json.parseToJsonElement(kaaGet("/api/show/${s.slug}/episode/${e.full}")).jsonObject["servers"] as? JsonArray).orEmpty()
        val out = mutableListOf<StreamSource>()
        for (sv in servers) {
            val o = sv as? JsonObject ?: continue
            val vid = Regex("[?&]id=([^&]+)").find(o["src"]?.jsonPrimitive?.contentOrNull.orEmpty())?.groupValues?.get(1) ?: continue
            val url = "$KAA_HLS/$vid/master.m3u8"
            referers[url] = "https://krussdomi.com/"
            out += StreamSource("$HOSHI-${out.size}", "Hoshi" + if (servers.size > 1) " ${out.size + 1}" else "", "hard", "hls", url)
        }
        return out
    }

    // ---------------------------------------------------------------- Yuki (animenosub: sub + dub, vidmoly / nova players)
    private const val NS_BASE = "https://animenosub.to"
    private class NsEp(val number: Int, val url: String)
    private class NsSeries(val sub: List<NsEp>, val dub: List<NsEp>)
    private val nsCache = ConcurrentHashMap<Int, NsSeries>()

    private suspend fun nsSearch(q: String): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val body = FormBody.Builder().add("action", "ts_ac_do_search").add("ts_ac_query", q).build()
        val txt = http.newCall(Request.Builder().url("$NS_BASE/wp-admin/admin-ajax.php").post(body).header("User-Agent", UA).header("X-Requested-With", "XMLHttpRequest").header("Origin", NS_BASE).header("Referer", "$NS_BASE/").build())
            .execute().use { r -> if (!r.isSuccessful) throw IOException("HTTP ${r.code}"); r.body?.string().orEmpty() }
        val all = json.parseToJsonElement(txt).jsonObject["anime"]?.jsonArray?.firstOrNull()?.jsonObject?.get("all")?.jsonArray.orEmpty()
        all.mapNotNull { it as? JsonObject }.mapNotNull { o ->
            val slug = Regex("/anime/([^/]+)/?$").find(o["post_link"]?.jsonPrimitive?.contentOrNull.orEmpty())?.groupValues?.get(1) ?: return@mapNotNull null
            slug to (o["post_title"]?.jsonPrimitive?.contentOrNull ?: slug.replace("-", " "))
        }
    }

    private suspend fun nsEpisodesOf(slug: String): List<NsEp> {
        val h = html("$NS_BASE/anime/$slug/", NS_BASE)
        val seen = HashSet<Int>()
        val href = Regex("<a\\s+href=\"(https?://animenosub\\.to/[^\"]+)\"", RegexOption.IGNORE_CASE)
        val num = Regex("<div\\s+class=\"epl-num\">([^<]+)</div>", RegexOption.IGNORE_CASE)
        val out = ArrayList<NsEp>()
        // One small piece per <li> keeps matching linear even for 1000+ episode lists.
        for (chunk in h.split("data-index=\"").drop(1)) {
            val part = chunk.take(1500)
            val u = href.find(part)?.groupValues?.get(1) ?: continue
            val label = num.find(part)?.groupValues?.get(1)?.trim() ?: continue
            val n = if (label.equals("movie", true)) 1 else label.toDoubleOrNull()?.takeIf { it >= 1 }?.let { Math.round(it).toInt() }
            if (n != null && seen.add(n)) out += NsEp(n, decodeEntities(u))
        }
        return out.sortedBy { it.number }
    }

    private suspend fun nsSeries(id: Int): NsSeries {
        nsCache[id]?.let { return it }
        val m = meta(id)
        val found = LinkedHashMap<String, String>()
        for (t in m.titles.take(2)) runCatching { nsSearch(t) }.getOrNull()?.forEach { (s, txt) -> found.putIfAbsent(s, txt) }
        fun isDub(slug: String) = Regex("(^|[-\\s])dub($|[-\\s])", RegexOption.IGNORE_CASE).containsMatchIn(slug)
        fun score(e: Map.Entry<String, String>) = m.titles.maxOfOrNull { dice(it, e.value.replace(Regex("\\(?dub\\)?", RegexOption.IGNORE_CASE), "")) } ?: 0.0
        val cands = found.entries.map { it to score(it) }.filter { it.second >= 0.7 }.sortedByDescending { it.second }
        val subSlug = cands.firstOrNull { !isDub(it.first.key) }?.first?.key
        val dubSlug = cands.firstOrNull { isDub(it.first.key) }?.first?.key
        val (sub, dub) = coroutineScope {
            val a = async { subSlug?.let { runCatching { nsEpisodesOf(it) }.getOrNull() }.orEmpty() }
            val b = async { dubSlug?.let { runCatching { nsEpisodesOf(it) }.getOrNull() }.orEmpty() }
            a.await() to b.await()
        }
        if (sub.isEmpty() && dub.isEmpty()) error("no match")
        return NsSeries(sub, dub).also { nsCache[id] = it }
    }

    private suspend fun nsEpisodes(id: Int): Local {
        val s = nsSeries(id)
        fun map(l: List<NsEp>) = l.map { WatchEpisode(it.number, "", providers = listOf(YUKI)) }
        return Local(map(s.sub), map(s.dub))
    }

    private suspend fun nsWatch(id: Int, audio: String, ep: Int): List<StreamSource> {
        val s = nsSeries(id)
        val e = (if (audio == "dub") s.dub else s.sub).firstOrNull { it.number == ep } ?: error("episode not found")
        val h = html(e.url, "$NS_BASE/")
        val embeds = Regex("<option\\s+value=\"([A-Za-z0-9+/=]+)\"\\s+data-index=\"\\d+\"[^>]*>([^<]+)</option>", RegexOption.IGNORE_CASE).findAll(h).mapNotNull { m ->
            val name = m.groupValues[2].trim()
            if (name.isEmpty() || name.contains("select video server", true)) return@mapNotNull null
            val src = runCatching { String(android.util.Base64.decode(m.groupValues[1], android.util.Base64.DEFAULT)) }.getOrNull()
                ?.let { Regex("src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) } ?: return@mapNotNull null
            name to (if (src.startsWith("//")) "https:$src" else src)
        }.toList()
        val resolved = coroutineScope { embeds.map { (name, url) -> async { kotlinx.coroutines.withTimeoutOrNull(10_000) { runCatching { resolveEmbed(url) }.getOrNull() }?.let { name to it } } }.awaitAll() }.filterNotNull()
        val out = mutableListOf<StreamSource>()
        for ((_, r) in resolved) {
            referers[r.first] = r.second
            out += StreamSource("$YUKI-${out.size}", "Yuki" + if (resolved.size > 1) " ${out.size + 1}" else "", "hard", "hls", r.first)
        }
        return out
    }

    /** Turns a player page into a direct playlist URL plus the Referer it needs. Only players that work over plain HTTP are supported. */
    private suspend fun resolveEmbed(url: String): Pair<String, String> {
        if (Regex("vidmoly\\.(net|biz|to)", RegexOption.IGNORE_CASE).containsMatchIn(url)) {
            val h = html(url, "$NS_BASE/")
            val m3u8 = Regex("sources:\\s*\\[\\s*\\{\\s*file:\\s*['\"]([^'\"]+\\.m3u8[^'\"]*)['\"]").find(h)?.groupValues?.get(1) ?: error("no playlist")
            return m3u8 to "https://vidmoly.biz/"
        }
        if (url.contains("upn.one", true)) {
            val vid = Regex("upn\\.one/#([A-Za-z0-9]+)", RegexOption.IGNORE_CASE).find(url)?.groupValues?.get(1) ?: error("no id")
            val hex = withContext(Dispatchers.IO) {
                http.newCall(Request.Builder().url("https://nova.upn.one/api/v1/video?id=$vid&w=1920&h=1080&r=").header("User-Agent", UA).header("Referer", "https://nova.upn.one/").build())
                    .execute().use { r -> if (!r.isSuccessful) throw IOException("HTTP ${r.code}"); r.body?.string().orEmpty().trim() }
            }
            fun unhex(s: String) = ByteArray(s.length / 2) { i -> s.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
            val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, javax.crypto.spec.SecretKeySpec(unhex("6b69656d7469656e6d75613931316361"), "AES"), javax.crypto.spec.IvParameterSpec(unhex("313233343536373839306f6975797472")))
            val data = json.parseToJsonElement(String(cipher.doFinal(unhex(hex)))).jsonObject
            val u = data["cf"]?.jsonPrimitive?.contentOrNull ?: data["source"]?.jsonPrimitive?.contentOrNull ?: error("no playlist")
            return u to "https://nova.upn.one/"
        }
        error("unsupported player")
    }

    // ---------------------------------------------------------------- public
    /** Runs every in-app provider at once and reports each one the moment it answers (failures are skipped). */
    @Volatile private var lastFresh = 0L
    /** Series caches hold episode lists, so drop them every couple of minutes to pick up newly aired episodes. */
    private fun freshen() {
        val now = System.currentTimeMillis()
        if (now - lastFresh > 90_000) { lastFresh = now; ggCache.clear(); zoneCache.clear(); kaaCache.clear(); nsCache.clear() }
    }

    suspend fun episodesEach(id: Int, onEach: suspend (String, Local) -> Unit) = withContext(Dispatchers.Default) { episodesEachInner(id, onEach) }

    private suspend fun episodesEachInner(id: Int, onEach: suspend (String, Local) -> Unit) = coroutineScope {
        freshen()
        val jobs: List<Pair<String, suspend () -> Local>> = listOf(
            KAZE to { kazeEpisodes(id) }, HOSHI to { kaaEpisodes(id) }, TSUKI to { zoneEpisodes(id) },
            YUKI to { nsEpisodes(id) }, DUNYA to { dunyaEpisodes(id) }, GG to { ggEpisodes(id) },
        )
        jobs.map { (alias, f) -> async { runCatching { f() }.getOrNull()?.takeIf { it.sub.isNotEmpty() || it.dub.isNotEmpty() }?.let { onEach(alias, it) } } }.awaitAll()
        Unit
    }

    suspend fun episodes(id: Int): Map<String, Local> {
        val out = java.util.concurrent.ConcurrentHashMap<String, Local>()
        episodesEach(id) { a, l -> out[a] = l }
        return out
    }

    suspend fun watch(id: Int, audio: String, ep: Int, alias: String): List<StreamSource> = withContext(Dispatchers.Default) { watchInner(id, audio, ep, alias) }

    private suspend fun watchInner(id: Int, audio: String, ep: Int, alias: String): List<StreamSource> = when (alias) {
        KAZE -> kazeWatch(id, audio, ep)
        TSUKI -> if (audio == "sub") zoneWatch(id, ep) else emptyList()
        HOSHI -> if (audio == "sub") kaaWatch(id, ep) else emptyList()
        YUKI -> nsWatch(id, audio, ep)
        DUNYA -> if (audio == "sub") dunyaWatch(id, ep) else emptyList()
        GG -> ggWatch(id, audio, ep)
        else -> emptyList()
    }
}
