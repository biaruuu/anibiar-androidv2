package com.anibiar.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    const val DUNYA = "dunya"
    const val GG = "animegg"
    val aliases = listOf(DUNYA, GG)

    private const val DUNYA_BASE = "https://anime-dunya.com"
    private const val GG_BASE = "https://www.animegg.org"
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val http by lazy { Http.plain.newBuilder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).build() }

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

    private suspend fun meta(id: Int): Meta {
        metaCache[id]?.let { return it }
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
            ?: Regex("\"source\"\\s*:\\s*\"([^\"]+)\"").find(page)?.groupValues?.get(1)?.replace("\\", "") ?: error("no stream")
        val subs = (obj?.get("subtitles") as? JsonArray).orEmpty().mapNotNull { s ->
            val so = s as? JsonObject ?: return@mapNotNull null
            val u = so["src"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            referers[u] = "$DUNYA_BASE/"
            StreamSubtitle(so["label"]?.jsonPrimitive?.contentOrNull ?: "Subtitle", so["srclang"]?.jsonPrimitive?.contentOrNull ?: "und", u, so["default"]?.jsonPrimitive?.booleanOrNull ?: false)
        }
        referers[source] = "$DUNYA_BASE/"
        return listOf(StreamSource("$DUNYA-0", "Dunya", if (subs.isEmpty()) "hard" else "soft", if (source.contains(".m3u8")) "hls" else "file", source, subs))
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
                out += StreamSource("$GG-${out.size}", "AnimeGG" + if (tabs.size > 1 || srcs.size > 1) " ${out.size + 1}" else "", if (audio == "sub") "hard" else "soft", if (url.contains(".m3u8")) "hls" else "file", url)
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

    // ---------------------------------------------------------------- public
    /** Episode lists from every local provider that answers (each failure is simply skipped). */
    suspend fun episodes(id: Int): Map<String, Local> = coroutineScope {
        listOf(DUNYA to async { runCatching { dunyaEpisodes(id) }.getOrNull() }, GG to async { runCatching { ggEpisodes(id) }.getOrNull() })
            .mapNotNull { (a, d) -> d.await()?.takeIf { it.sub.isNotEmpty() || it.dub.isNotEmpty() }?.let { a to it } }.toMap()
    }

    suspend fun watch(id: Int, audio: String, ep: Int, alias: String): List<StreamSource> = when (alias) {
        DUNYA -> if (audio == "sub") dunyaWatch(id, ep) else emptyList()
        GG -> ggWatch(id, audio, ep)
        else -> emptyList()
    }
}
