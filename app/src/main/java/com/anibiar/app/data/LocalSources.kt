package com.anibiar.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.doubleOrNull
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
    const val NAMI = "nami"      // anikoto: sub + dub, MegaPlay
    val aliases = listOf(AdminContent.ALIAS, KAZE, TSUKI, DUNYA, GG, HOSHI, YUKI, NAMI)

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

    private val numEntity = Regex("&#(\\d+);")
    private val anyTag = Regex("<[^>]*>")
    private val spaces = Regex("\\s+")
    private val nonAlnum = Regex("[^a-z0-9]")
    private fun decodeEntities(s: String): String = com.anibiar.app.nativecore.Native.html(s, 2) ?: s.replace(numEntity) { it.groupValues[1].toInt().toChar().toString() }
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").trim()
    private fun stripTags(s: String): String = com.anibiar.app.nativecore.Native.html(s, 1) ?: decodeEntities(s.replace(anyTag, " ").replace(spaces, " "))
    private fun attr(tag: String, name: String) = Regex("$name=[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)?.let(::decodeEntities).orEmpty()
    private fun norm(s: String) = s.lowercase().replace(nonAlnum, "")
    private fun dice(a: String, b: String): Double = com.anibiar.app.nativecore.Native.diceOrNull(a, b) ?: diceKotlin(a, b)
    private fun diceKotlin(a: String, b: String): Double {
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

    private fun metaFile(id: Int): java.io.File? = Http.context?.let { java.io.File(it.cacheDir, "meta-$id.json") }

    /** Titles and ids almost never change, so a saved copy (30 days) means AniList is only asked once per anime. */
    private fun readMeta(id: Int): Meta? = runCatching {
        val f = metaFile(id) ?: return null
        if (!f.exists() || System.currentTimeMillis() - f.lastModified() > 30L * 24 * 3600_000) return null
        val o = org.json.JSONObject(f.readText())
        val t = o.getJSONArray("titles")
        Meta(o.optInt("mal", 0).takeIf { it > 0 }, List(t.length()) { t.getString(it) }, o.optInt("eps", 0).takeIf { it > 0 }, o.optString("fmt").ifBlank { null })
            .takeIf { it.titles.isNotEmpty() }
    }.getOrNull()

    private fun writeMeta(id: Int, m: Meta) {
        runCatching {
            metaFile(id)?.writeText(org.json.JSONObject().put("mal", m.idMal ?: 0).put("titles", org.json.JSONArray(m.titles)).put("eps", m.episodes ?: 0).put("fmt", m.format ?: "").toString())
        }
    }

    /** AniList with up to 3 tries on 429 / 5xx (honouring Retry-After). */
    private suspend fun aniListText(body: String): String {
        var last: Exception? = null
        for (attempt in 0..2) {
            try {
                val (code, text, wait) = withContext(Dispatchers.IO) {
                    http.newCall(Request.Builder().url("https://graphql.anilist.co").post(body.toRequestBody("application/json".toMediaType())).header("Accept", "application/json").build())
                        .execute().use { r -> Triple(r.code, r.body?.string().orEmpty(), r.header("Retry-After")?.toLongOrNull()) }
                }
                if (code in 200..299) return text
                last = IOException("AniList HTTP $code")
                if (code != 429 && code < 500) throw last
                kotlinx.coroutines.delay(((wait ?: (attempt + 1).toLong() * 2).coerceIn(1L, 6L)) * 1000)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: IOException) {
                last = e
                if (attempt < 2) kotlinx.coroutines.delay(1500)
            }
        }
        throw last ?: IOException("AniList unreachable")
    }

    /** AniList-free fallback (ani.zip knows the MAL id and titles), used when AniList keeps refusing. */
    private suspend fun aniZipMeta(id: Int): Meta? = runCatching {
        val text = withContext(Dispatchers.IO) {
            http.newCall(Request.Builder().url("https://api.ani.zip/mappings?anilist_id=$id").header("Accept", "application/json").build())
                .execute().use { r -> if (r.isSuccessful) r.body?.string().orEmpty() else "" }
        }
        val root = json.parseToJsonElement(text).jsonObject
        val map = root["mappings"] as? JsonObject
        val t = root["titles"] as? JsonObject
        val titles = listOfNotNull(t?.get("en")?.jsonPrimitive?.contentOrNull, t?.get("x-jat")?.jsonPrimitive?.contentOrNull).filter { it.isNotBlank() && it != "null" }.distinct()
        if (titles.isEmpty()) null
        else Meta(map?.get("mal_id")?.jsonPrimitive?.intOrNull, titles, root["episodeCount"]?.jsonPrimitive?.intOrNull ?: map?.get("episodeCount")?.jsonPrimitive?.intOrNull, map?.get("type")?.jsonPrimitive?.contentOrNull)
    }.getOrNull()

    private suspend fun fetchMeta(id: Int): Meta {
        readMeta(id)?.let { metaCache[id] = it; return it }
        val body = buildJsonObject {
            put("query", "query(\$id:Int){Media(id:\$id,type:ANIME){idMal title{english romaji} synonyms episodes format}}")
            put("variables", buildJsonObject { put("id", id) })
        }.toString()
        val text = try { aniListText(body) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            val fb = aniZipMeta(id) ?: throw e
            metaCache[id] = fb
            return fb
        }
        val m = json.parseToJsonElement(text).jsonObject["data"]?.jsonObject?.get("Media")?.jsonObject ?: throw IOException("AniList: no media")
        val t = m["title"]?.jsonObject
        val titles = listOfNotNull(t?.get("english")?.jsonPrimitive?.contentOrNull, t?.get("romaji")?.jsonPrimitive?.contentOrNull) +
            (m["synonyms"] as? JsonArray).orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull }.filter { s -> s.all { it.code < 0x2E80 } }.take(3)
        return Meta(m["idMal"]?.jsonPrimitive?.intOrNull, titles.distinct(), m["episodes"]?.jsonPrimitive?.intOrNull, m["format"]?.jsonPrimitive?.contentOrNull)
            .also { metaCache[id] = it; writeMeta(id, it) }
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

    // ---------------------------------------------------------------- Nami (anikoto: sub + dub, MegaPlay / mapper streams)
    private const val AK = "https://anikototv.to"
    private const val AK_MAPPER = "https://mapper.nekostream.site/api/mal"
    private const val AK_REF = "https://hianimes.re/"
    private class AkEp(val number: Int, val ids: String, val sub: Boolean, val dub: Boolean, val mal: Int?, val slug: String, val ts: String, val title: String)
    private class AkSeries(val eps: List<AkEp>)
    private val akCache = ConcurrentHashMap<Int, AkSeries>()

    private suspend fun akJson(url: String, referer: String = "$AK/"): JsonObject = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(url).header("User-Agent", UA).header("Accept", "application/json,*/*").header("X-Requested-With", "XMLHttpRequest").header("Referer", referer).build())
            .execute().use { r -> if (!r.isSuccessful) throw IOException("HTTP ${r.code}"); json.parseToJsonElement(r.body?.string().orEmpty()).jsonObject }
    }

    private suspend fun akSearch(q: String): List<Triple<String, String, String>> {
        val h = html("$AK/filter?keyword=${URLEncoder.encode(q, "UTF-8")}", "$AK/")
        val out = LinkedHashMap<String, Triple<String, String, String>>()
        for (chunk in h.split("<a ").drop(1)) {
            val tag = chunk.substringBefore(">")
            val slug = Regex("href=\"https?://anikototv\\.to/watch/([^\"/]+)", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1) ?: continue
            if (!tag.contains("d-title")) continue
            val name = decodeEntities(stripTags(chunk.substringAfter(">").substringBefore("</a>"))).trim()
            out.putIfAbsent(slug, Triple(slug, name, attr(tag, "data-jp")))
        }
        return out.values.toList()
    }

    private fun akRows(h: String): List<AkEp> = h.split("<a ").drop(1).mapNotNull { chunk ->
        val tag = chunk.substringBefore(">")
        val n = attr(tag, "data-num").toIntOrNull() ?: return@mapNotNull null
        val title = Regex("<span\\s+class=\"d-title\"[^>]*>([\\s\\S]*?)</span>", RegexOption.IGNORE_CASE).find(chunk.take(1500))?.groupValues?.get(1)?.let { decodeEntities(stripTags(it)).trim() }.orEmpty()
        AkEp(n, attr(tag, "data-ids"), attr(tag, "data-sub") == "1", attr(tag, "data-dub") == "1", attr(tag, "data-mal").toIntOrNull(), attr(tag, "data-slug"), attr(tag, "data-timestamp"), title.ifBlank { "Episode $n" })
    }

    private suspend fun akInspect(slug: String): List<AkEp>? {
        val page = html("$AK/watch/$slug", "$AK/")
        val showId = Regex("data-id=\"(\\d+)\"").find(page)?.groupValues?.get(1) ?: return null
        val list = akJson("$AK/ajax/episode/list/$showId", "$AK/watch/$slug")["result"]?.jsonPrimitive?.contentOrNull.orEmpty()
        return akRows(list).takeIf { it.isNotEmpty() }
    }

    private suspend fun akSeries(id: Int): AkSeries {
        akCache[id]?.let { return it }
        val m = meta(id)
        val queries = LinkedHashSet<String>()
        for (t in m.titles.take(3)) {
            if (t.any { it.code >= 0x2E80 }) continue
            val words = t.replace(Regex("[^\\w\\s]"), " ").split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.isEmpty()) continue
            queries += words.take(6).joinToString(" "); if (words.size > 3) queries += words.take(3).joinToString(" ")
        }
        val found = LinkedHashMap<String, Triple<String, String, String>>()
        coroutineScope { queries.take(6).map { q -> async { runCatching { akSearch(q) }.getOrDefault(emptyList()) } }.awaitAll() }.flatten().forEach { found.putIfAbsent(it.first, it) }
        val scored = found.values.map { c -> c to (m.titles.maxOfOrNull { t -> maxOf(dice(t, c.second), dice(t, c.third), dice(t, c.first.replace("-", " "))) } ?: 0.0) }
            .filter { it.second >= 0.3 }.sortedByDescending { it.second }.take(8)
        if (scored.isEmpty()) error("no match")
        val inspected = coroutineScope { scored.map { (c, sc) -> async { runCatching { akInspect(c.first) }.getOrNull()?.let { Triple(c, sc, it) } } }.awaitAll() }.filterNotNull()
        val mal = m.idMal
        val exact = if (mal != null) inspected.filter { t -> t.third.any { it.mal == mal } } else emptyList()
        val eps: List<AkEp> = if (exact.isNotEmpty()) {
            val want = m.episodes
            val best = exact.maxWith(compareBy({ t -> if (want != null && t.third.count { it.mal == mal } == want) 1 else 0 }, { t -> t.third.count { it.mal == mal } }, { t -> t.second }))
            val rows = best.third.filter { it.mal == mal }.toMutableList()
            val have = rows.map { it.number }.toMutableSet()
            for (o in exact) if (o !== best) { val r = o.third.filter { it.mal == mal }; if (r.isNotEmpty() && r.none { it.number in have }) { rows += r; have += r.map { it.number } } }
            rows
        } else {
            val ok = inspected.filter { it.second >= 0.72 && (mal == null || it.third.none { e -> e.mal != null }) }.sortedByDescending { it.second }
            val top = ok.firstOrNull() ?: error("no confident match")
            if (ok.size > 1 && top.second - ok[1].second < 0.08) error("ambiguous match")
            top.third
        }
        return AkSeries(eps.distinctBy { it.number }.sortedBy { it.number }).also { akCache[id] = it }
    }

    private suspend fun akEpisodes(id: Int): Local {
        val s = akSeries(id)
        fun map(l: List<AkEp>) = l.map { WatchEpisode(it.number, it.title, providers = listOf(NAMI)) }
        return Local(map(s.eps.filter { it.sub }), map(s.eps.filter { it.dub }))
    }

    private class Extracted(val sources: List<String>, val tracks: List<StreamSubtitle>, val intro: TimeRange?, val outro: TimeRange?)

    private fun akRange(o: JsonElement?): TimeRange? {
        val a = (o as? JsonObject) ?: return null
        val s = a["start"]?.jsonPrimitive?.doubleOrNull ?: 0.0; val e = a["end"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        return if (s > 0 || e > 0) TimeRange(s, e) else null
    }

    /** MegaPlay player: finds the source route in the player script, then reads or decrypts the playlist address. */
    private suspend fun megaplay(embed: String): Extracted? {
        val base = java.net.URI(embed)
        val origin = "${base.scheme}://${base.host}"
        val page = html(embed, AK_REF)
        val fileId = Regex("data-id=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(page)?.groupValues?.get(1) ?: return null
        val scripts = coroutineScope {
            Regex("<script[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).findAll(page).map { base.resolve(it.groupValues[1]).toString() }.toList()
                .map { u -> async { runCatching { html(u, embed) }.getOrNull() } }.awaitAll().filterNotNull()
        }
        val script = scripts.firstOrNull { it.contains("getSources", true) && it.contains("AES-CBC", true) } ?: return null
        val routes = Regex("[\"'](stream/getSources[\\w/-]*)[\"']", RegexOption.IGNORE_CASE).findAll(script).map { it.groupValues[1] }.distinct().sortedBy { it.length }.toList()
        val legacy = routes.firstOrNull() ?: return null
        val modern = routes.firstOrNull { it != legacy && it.startsWith(legacy) }
        val datas = coroutineScope {
            listOfNotNull(modern, legacy).map { r -> async { runCatching { akJson("$origin/$r?id=$fileId&id=$fileId", embed) }.getOrNull() } }.awaitAll().filterNotNull()
        }
        if (datas.isEmpty()) return null
        val urls = LinkedHashSet<String>()
        for (d in datas) {
            val file = (d["sources"] as? JsonObject)?.get("file")?.jsonPrimitive?.contentOrNull
                ?: d["enc"]?.jsonPrimitive?.contentOrNull?.let { megaDecrypt(it, script) }
            if (!file.isNullOrBlank()) urls += file
        }
        if (urls.isEmpty()) return null
        val meta = datas.first()
        val tracks = (meta["tracks"] as? JsonArray).orEmpty().mapNotNull { t ->
            val o = t as? JsonObject ?: return@mapNotNull null
            val kind = o["kind"]?.jsonPrimitive?.contentOrNull
            if (kind != null && kind != "captions" && kind != "subtitles") return@mapNotNull null
            val f = o["file"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            referers[f] = "$origin/"
            StreamSubtitle(o["label"]?.jsonPrimitive?.contentOrNull ?: "English", "en", f, o["default"]?.jsonPrimitive?.booleanOrNull ?: false)
        }
        urls.forEach { referers[it] = "$origin/" }
        return Extracted(urls.toList(), tracks, akRange(meta["intro"]), akRange(meta["outro"]))
    }

    private fun megaDecrypt(enc: String, script: String): String? {
        val raw = runCatching { android.util.Base64.decode(enc, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP) }.getOrNull() ?: return null
        if (raw.isEmpty() || raw.size % 16 != 0) return null
        val values = Regex("\"([^\"\\\\\\n]{1,32})\"|'([^'\\\\\\n]{1,32})'").findAll(script).map { it.groupValues[1].ifEmpty { it.groupValues[2] } }.filter { it.isNotEmpty() }.distinct().take(4000).toList()
        val ivs = values.filter { it.toByteArray().size == 16 }
        val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
        val deadline = System.currentTimeMillis() + 8000
        for (kv in values) {
            val key = ByteArray(32).also { kv.toByteArray().copyInto(it, 0, 0, minOf(32, kv.toByteArray().size)) }
            for (iv in ivs) {
                if (System.currentTimeMillis() > deadline) return null
                try {
                    cipher.init(javax.crypto.Cipher.DECRYPT_MODE, javax.crypto.spec.SecretKeySpec(key, "AES"), javax.crypto.spec.IvParameterSpec(iv.toByteArray()))
                    val o = json.parseToJsonElement(String(cipher.doFinal(raw))).jsonObject
                    val f = o["file"]?.jsonPrimitive?.contentOrNull ?: o["url"]?.jsonPrimitive?.contentOrNull ?: (o["sources"] as? JsonObject)?.get("file")?.jsonPrimitive?.contentOrNull
                        ?: (o["sources"] as? JsonArray)?.firstOrNull()?.jsonObject?.get("file")?.jsonPrimitive?.contentOrNull
                    if (!f.isNullOrBlank()) return f
                } catch (_: Exception) {}
            }
        }
        return null
    }

    private suspend fun akWatch(id: Int, audio: String, ep: Int): List<StreamSource> {
        val s = akSeries(id)
        val e = s.eps.firstOrNull { it.number == ep && (if (audio == "sub") it.sub else it.dub) } ?: error("episode not found")
        class Item(val link: String, val name: String, val hard: Boolean)
        val items = ArrayList<Item>()
        coroutineScope {
            val list = async { runCatching { akJson("$AK/ajax/server/list?servers=${URLEncoder.encode(e.ids, "UTF-8")}")["result"]?.jsonPrimitive?.contentOrNull.orEmpty() }.getOrDefault("") }
            val map = async { if (e.mal != null && e.slug.isNotEmpty() && e.ts.isNotEmpty()) runCatching { akJson("$AK_MAPPER/${e.mal}/${e.slug}/${e.ts}", "$AK/") }.getOrNull() else null }
            val listHtml = list.await()
            for (tm in Regex("<div class=\"type\" data-type=\"([^\"]+)\">([\\s\\S]*?)</ul>\\s*</div>").findAll(listHtml)) {
                val type = tm.groupValues[1]
                if (type != audio && !(audio == "sub" && type == "hsub")) continue
                for (li in Regex("<li\\s+([^>]*data-link-id[^>]*)>([\\s\\S]*?)</li>").findAll(tm.groupValues[2])) {
                    val link = Regex("data-link-id=\"([^\"]+)\"").find(li.groupValues[1])?.groupValues?.get(1) ?: continue
                    val name = stripTags(li.groupValues[2]).trim()
                    if (name.contains("download", true) || name.contains("kiwi", true)) continue
                    items += Item(link, name, type == "hsub")
                }
            }
            map.await()?.forEach { (k, v) ->
                if (k == "status") return@forEach
                val u = ((v as? JsonObject)?.get(audio) as? JsonObject)?.get("url")?.jsonPrimitive?.contentOrNull
                if (!u.isNullOrBlank()) items += Item(u, k.trim('-', '_', ' '), false)
            }
        }
        val seen = HashSet<String>()
        val uniq = items.filter { seen.add("${it.name}:${it.hard}") }
        val resolved = coroutineScope {
            uniq.take(6).map { it0 ->
                async {
                    kotlinx.coroutines.withTimeoutOrNull(12_000) {
                        runCatching {
                            val res = if (it0.link.startsWith("http")) null else akJson("$AK/ajax/server?get=${URLEncoder.encode(it0.link, "UTF-8")}")["result"] as? JsonObject
                            val embed = if (it0.link.startsWith("http")) it0.link else res?.get("url")?.jsonPrimitive?.contentOrNull ?: return@runCatching null
                            val skip = res?.get("skip_data") as? JsonObject
                            fun rng(k: String) = (skip?.get(k) as? JsonArray)?.takeIf { it.size == 2 }?.let { a -> TimeRange(a[0].jsonPrimitive.doubleOrNull ?: 0.0, a[1].jsonPrimitive.doubleOrNull ?: 0.0) }?.takeIf { it.start > 0 || it.end > 0 }
                            val direct = if (embed.contains("#aHR0c")) runCatching { String(android.util.Base64.decode(embed.substringAfter("#"), android.util.Base64.DEFAULT)) }.getOrNull()?.takeIf { it.contains(".m3u8") } else null
                            val ex = if (direct == null && embed.contains("megaplay", true)) megaplay(embed) else null
                            val origin = java.net.URI(embed).let { "${it.scheme}://${it.host}/" }
                            val urls = listOfNotNull(direct) + ex?.sources.orEmpty() + (if (direct == null && ex == null && embed.contains(".m3u8")) listOf(embed) else emptyList())
                            if (urls.isEmpty()) null else {
                                urls.forEach { u -> if (referers[u] == null) referers[u] = origin }
                                Triple(it0, urls.first(), Triple(ex?.tracks.orEmpty(), ex?.intro ?: rng("intro"), ex?.outro ?: rng("outro")))
                            }
                        }.getOrNull()
                    }
                }
            }.awaitAll().filterNotNull()
        }
        return resolved.mapIndexed { i, (item, url, extra) ->
            StreamSource("$NAMI-$i", "Nami" + if (resolved.size > 1) " ${i + 1}" else "", if (item.hard) "hard" else if (extra.first.isEmpty()) "hard" else "soft", "hls", url, extra.first, extra.second, extra.third)
        }
    }

    // ---------------------------------------------------------------- public
    /** Runs every in-app provider at once and reports each one the moment it answers (failures are skipped). */
    @Volatile private var lastFresh = 0L
    /** Series caches hold episode lists, so drop them every couple of minutes to pick up newly aired episodes. */
    private fun freshen() {
        val now = System.currentTimeMillis()
        if (now - lastFresh > 90_000) { lastFresh = now; ggCache.clear(); zoneCache.clear(); kaaCache.clear(); nsCache.clear(); akCache.clear() }
    }

    suspend fun episodesEach(id: Int, onEach: suspend (String, Local) -> Unit) = withContext(Dispatchers.Default) { episodesEachInner(id, onEach) }

    private suspend fun episodesEachInner(id: Int, onEach: suspend (String, Local) -> Unit) = coroutineScope {
        freshen()
        // Anime added in the admin panel only have the admin links; every other provider would be asking about an id it doesn't know.
        if (AdminContent.isCustom(id)) {
            AdminContent.episodes(id)?.let { onEach(AdminContent.ALIAS, it) }
            return@coroutineScope
        }
        val jobs: List<Pair<String, suspend () -> Local>> = listOf(
            AdminContent.ALIAS to { AdminContent.episodes(id) ?: Local(emptyList(), emptyList()) }, KAZE to { kazeEpisodes(id) }, HOSHI to { kaaEpisodes(id) }, TSUKI to { zoneEpisodes(id) },
            YUKI to { nsEpisodes(id) }, NAMI to { akEpisodes(id) }, DUNYA to { dunyaEpisodes(id) }, GG to { ggEpisodes(id) },
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
        AdminContent.ALIAS -> AdminContent.watch(id, audio, ep)
        KAZE -> kazeWatch(id, audio, ep)
        TSUKI -> if (audio == "sub") zoneWatch(id, ep) else emptyList()
        HOSHI -> if (audio == "sub") kaaWatch(id, ep) else emptyList()
        YUKI -> nsWatch(id, audio, ep)
        NAMI -> akWatch(id, audio, ep)
        DUNYA -> if (audio == "sub") dunyaWatch(id, ep) else emptyList()
        GG -> ggWatch(id, audio, ep)
        else -> emptyList()
    }
}
