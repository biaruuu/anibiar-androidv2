package com.anibiar.app.data

import android.content.Context
import com.anibiar.app.BuildConfig
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/** Tiny file cache: the last good API response is shown instantly on the next launch. */
class JsonDiskCache(private val dir: File) {
    init {
        dir.mkdirs()
    }

    fun read(key: String): String? = try {
        File(dir, key).takeIf { it.exists() }?.readText()
    } catch (e: Exception) {
        null
    }

    fun write(key: String, value: String) {
        try {
            File(dir, key).writeText(value)
        } catch (e: Exception) {
            // cache is best-effort
        }
    }
}

class AnimeRepository(context: Context) {
    private val appContext = context.applicationContext
    private val api = AniListApi(Http.client)
    private val cache = JsonDiskCache(File(appContext.cacheDir, "api"))
    private val tmdb = TmdbApi(
        Http.client,
        BuildConfig.TMDB_API_KEY,
        appContext.getSharedPreferences("tmdb_art", Context.MODE_PRIVATE),
    )
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    // ---- Discover: Top Airing + Upcoming (one request) ----

    suspend fun cachedDiscover(): DiscoverData? = withContext(Dispatchers.IO) {
        cache.read(KEY_DISCOVER)?.let { decodeDiscover(it) }
    }

    suspend fun fetchDiscover(): DiscoverData {
        val body = api.execute(Queries.DISCOVER)
        val data = withContext(Dispatchers.Default) { decodeDiscover(body) }
            ?: throw IOException("Unexpected AniList response")
        cache.write(KEY_DISCOVER, body)
        return data
    }

    private fun decodeDiscover(body: String): DiscoverData? = try {
        json.decodeFromString<GraphQlResponse<DiscoverData>>(body).data
    } catch (e: Exception) {
        null
    }

    // ---- Latest episodes (airing schedule, newest first, de-duplicated per anime) ----

    // ---- Dub: AnimeSchedule.net dub timetable (episodes that have actually aired), matched to AniList ----

    val dubConfigured: Boolean get() = BuildConfig.ANIMESCHEDULE_TOKEN.isNotBlank()
    private var dubFetchedAt = 0L

    suspend fun cachedDub(): List<DubEntry> = withContext(Dispatchers.IO) {
        cache.read(KEY_DUB)?.let { text ->
            runCatching {
                json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(DubEntry.serializer()), text)
            }.getOrNull()
        }.orEmpty()
    }

    /** Null = nothing new (not configured, or refreshed less than 5 minutes ago). Throws on network/API errors. */
    suspend fun fetchDub(force: Boolean = false): List<DubEntry>? = withContext(Dispatchers.IO) {
        if (!dubConfigured) return@withContext null
        if (!force && System.currentTimeMillis() - dubFetchedAt < 5 * 60_000L) return@withContext null

        class Aired(val route: String, val names: List<String>, var episode: Int, var at: Long)
        val byRoute = LinkedHashMap<String, Aired>()
        val now = System.currentTimeMillis()
        val dateParsers = listOf("yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
        fun parseTime(raw: String): Long? {
            val clean = raw.replace(Regex("""(\.\d{3})\d+"""), "$1")
            for (pattern in dateParsers) {
                val t = runCatching {
                    java.text.SimpleDateFormat(pattern, java.util.Locale.US).parse(clean)?.time
                }.getOrNull()
                if (t != null) return t
            }
            return null
        }

        // This week and the two before it: everything that has aired recently.
        for (back in 0..2) {
            val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                firstDayOfWeek = java.util.Calendar.MONDAY
                minimalDaysInFirstWeek = 4
                add(java.util.Calendar.WEEK_OF_YEAR, -back)
            }
            val url = "https://animeschedule.net/api/v3/timetables/dub?year=${cal.weekYear}&week=${cal.get(java.util.Calendar.WEEK_OF_YEAR)}&tz=UTC"
            val request = okhttp3.Request.Builder().url(url)
                .header("Authorization", "Bearer ${BuildConfig.ANIMESCHEDULE_TOKEN}")
                .header("Accept", "application/json").build()
            val body = Http.plain.newCall(request).execute().use { r ->
                if (!r.isSuccessful) throw IOException("AnimeSchedule HTTP ${r.code}")
                r.body?.string().orEmpty()
            }
            val arr = runCatching { org.json.JSONArray(body) }.getOrNull() ?: continue
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val route = o.optString("route").ifBlank { continue }
                val at = parseTime(o.optString("episodeDate")) ?: continue
                if (at > now) continue // not out yet
                val episode = o.optInt("episodeNumber", 0)
                if (episode <= 0) continue
                val names = listOf("romaji", "english", "title").map { o.optString(it) }.filter { it.isNotBlank() && it != "null" }.distinct()
                val cur = byRoute[route]
                if (cur == null) byRoute[route] = Aired(route, names, episode, at)
                else if (episode > cur.episode) { cur.episode = episode; cur.at = at }
            }
        }
        if (byRoute.isEmpty()) {
            dubFetchedAt = System.currentTimeMillis()
            cache.write(KEY_DUB, "[]")
            return@withContext emptyList()
        }

        // route -> AniList id (remembered between runs so each show is searched only once)
        val idsJson = runCatching { org.json.JSONObject(cache.read(KEY_DUB_IDS).orEmpty()) }.getOrElse { org.json.JSONObject() }
        fun norm(t: String) = t.lowercase().replace(Regex("[^a-z0-9 ]"), " ").trim().replace(Regex("\\s+"), " ")
        fun score(cands: List<String>, media: org.json.JSONObject): Double {
            val t = media.optJSONObject("title")
            val names = listOf("romaji", "english", "native").mapNotNull { t?.optString(it)?.takeIf { s -> s.isNotBlank() && s != "null" } }.map(::norm)
            var best = 0.0
            for (c in cands.map(::norm)) for (n in names) {
                if (c == n) return 1.0
                val a = c.split(" ").toSet(); val b = n.split(" ").toSet()
                val j = a.intersect(b).size.toDouble() / a.union(b).size.coerceAtLeast(1)
                if (j > best) best = j
            }
            return best
        }
        val unknown = byRoute.values.filter { !idsJson.has(it.route) }
        for (chunk in unknown.chunked(8)) {
            val queryText = Queries.searchMany(chunk.map { it.names.firstOrNull().orEmpty() })
            val resp = runCatching { api.execute(queryText) }.getOrNull() ?: continue
            val data = runCatching { org.json.JSONObject(resp).optJSONObject("data") }.getOrNull() ?: continue
            chunk.forEachIndexed { i, item ->
                val list = data.optJSONObject("m$i")?.optJSONArray("media") ?: return@forEachIndexed
                var bestId = 0; var bestScore = 0.0
                for (j in 0 until list.length()) {
                    val m = list.optJSONObject(j) ?: continue
                    val sc = score(item.names, m)
                    if (sc > bestScore) { bestScore = sc; bestId = m.optInt("id") }
                }
                if (bestId != 0 && bestScore >= 0.6) idsJson.put(item.route, bestId)
            }
        }
        cache.write(KEY_DUB_IDS, idsJson.toString())

        val wanted = byRoute.values.mapNotNull { a -> idsJson.optInt(a.route, 0).takeIf { it != 0 }?.let { it to a } }
        val medias = HashMap<Int, Media>()
        for (chunk in wanted.map { it.first }.distinct().chunked(50)) {
            val resp = runCatching { api.execute(Queries.byIds(chunk)) }.getOrNull() ?: continue
            runCatching {
                json.decodeFromString<GraphQlResponse<ByIdsData>>(resp).data?.page?.media.orEmpty()
            }.getOrNull()?.forEach { medias[it.id] = it }
        }
        // One entry per anime (a show can map from two routes; keep the furthest episode).
        val entries = HashMap<Int, DubEntry>()
        for ((id, a) in wanted) {
            val media = medias[id] ?: continue
            val cur = entries[id]
            if (cur == null || a.episode > cur.episode) entries[id] = DubEntry(media, a.episode, a.at / 1000)
        }
        val list = entries.values.sortedByDescending { it.airingAt }
        dubFetchedAt = System.currentTimeMillis()
        cache.write(KEY_DUB, json.encodeToString(kotlinx.serialization.builtins.ListSerializer(DubEntry.serializer()), list))
        list
    }

    // ---- Most Popular (AniList all-time popularity, top 60) ----

    suspend fun cachedPopular(): List<Media> = withContext(Dispatchers.IO) {
        cache.read(KEY_POPULAR)?.let { decodePopular(it) }.orEmpty()
    }

    suspend fun fetchPopular(): List<Media> {
        val body = api.execute(Queries.POPULAR)
        val list = withContext(Dispatchers.Default) { decodePopular(body) }.orEmpty()
        if (list.isNotEmpty()) cache.write(KEY_POPULAR, body)
        return list
    }

    private fun decodePopular(body: String): List<Media>? = try {
        json.decodeFromString<GraphQlResponse<PopularData>>(body).data?.popular?.media
    } catch (e: Exception) {
        null
    }

    suspend fun cachedLatest(): List<LatestEpisode> = withContext(Dispatchers.IO) {
        buildLatest(listOfNotNull(cache.read(KEY_LATEST_1), cache.read(KEY_LATEST_2)))
    }

    suspend fun fetchLatest(): List<LatestEpisode> = coroutineScope {
        val now = (System.currentTimeMillis() / 1000).toInt()
        val first = async { api.execute(Queries.LATEST, mapOf("now" to now, "page" to 1)) }
        val second = async {
            try {
                api.execute(Queries.LATEST, mapOf("now" to now, "page" to 2))
            } catch (e: IOException) {
                null
            }
        }
        val firstBody = first.await()
        val secondBody = second.await()

        cache.write(KEY_LATEST_1, firstBody)
        if (secondBody != null) cache.write(KEY_LATEST_2, secondBody)
        withContext(Dispatchers.Default) { buildLatest(listOfNotNull(firstBody, secondBody)) }
    }

    private fun buildLatest(bodies: List<String>): List<LatestEpisode> {
        val schedules = bodies.flatMap { body ->
            try {
                json.decodeFromString<GraphQlResponse<LatestData>>(body).data?.latest?.airingSchedules.orEmpty()
            } catch (e: Exception) {
                emptyList()
            }
        }
        val seen = HashSet<Int>()
        val result = ArrayList<LatestEpisode>()
        for (schedule in schedules.sortedByDescending { it.airingAt }) {
            val media = schedule.media ?: continue
            if (media.isAdult == true) continue
            val country = media.countryOfOrigin
            if (country != null && country != "JP") continue
            if (!seen.add(media.id)) continue
            result += LatestEpisode(media, schedule.episode, schedule.airingAt)
        }
        return result
    }

    // ---- Image warm-up ----

    /** Fills the disk cache with the first screen of artwork before the UI asks for it. */
    suspend fun prewarm() {
        val discover = cachedDiscover()
        val latest = cachedLatest()
        preloadImages(
            discover?.top?.media.orEmpty().take(4).flatMap { listOf(it.bannerImage, it.posterUrl) } +
                latest.take(12).map { it.media.posterUrl },
        )
    }

    /** Network-only preload (no memory cache) so it costs nothing while scrolling. */
    fun preloadImages(urls: List<String?>) {
        val loader = appContext.imageLoader
        urls.filterNotNull().distinct().forEach { url ->
            loader.enqueue(
                ImageRequest.Builder(appContext)
                    .data(url)
                    .size(240, 360)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .build(),
            )
        }
    }

    // ---- Details ----

    suspend fun cachedDetails(id: Int): Media? = withContext(Dispatchers.IO) {
        cache.read("detail_$id.json")?.let { decodeMedia(it) }
    }

    suspend fun cachedCharacters(id: Int): CharacterConn? = withContext(Dispatchers.IO) {
        cache.read("chars_$id.json")?.let { decodeMedia(it)?.characters }
    }

    suspend fun fetchDetails(id: Int): Media {
        val body = api.execute(Queries.DETAILS, mapOf("id" to id))
        val media = withContext(Dispatchers.Default) { decodeMedia(body) }
            ?: throw IOException("Unexpected AniList response")
        cache.write("detail_$id.json", body)
        return media
    }

    suspend fun fetchCharacters(id: Int): CharacterConn? {
        val body = api.execute(Queries.CHARACTERS, mapOf("id" to id))
        val chars = withContext(Dispatchers.Default) { decodeMedia(body)?.characters }
        if (chars != null) cache.write("chars_$id.json", body)
        return chars
    }

    private fun decodeMedia(body: String): Media? = try {
        json.decodeFromString<GraphQlResponse<MediaHolder>>(body).data?.media
    } catch (e: Exception) {
        null
    }

    // ---- Episode list: titles, thumbnails, release dates, runtime ----

    suspend fun cachedEpisodes(id: Int): List<EpisodeInfo>? = withContext(Dispatchers.IO) {
        cache.read("eps_$id.json")?.let { parseEpisodeCache(it) }
    }

    /** ani.zip (thumbnails, titles, air dates) first, Jikan/MAL fills any gap. Never throws. */
    suspend fun fetchEpisodes(id: Int, idMal: Int?): List<EpisodeInfo> = withContext(Dispatchers.IO) {
        val byNumber = sortedMapOf<Int, EpisodeInfo>()
        runCatching { aniZipEpisodes(id) }.getOrNull()?.forEach { byNumber[it.number] = it }
        val needsMore = byNumber.isEmpty() || byNumber.values.any { it.title.isNullOrBlank() || it.date == null }
        if (idMal != null && needsMore) {
            runCatching { jikanEpisodes(idMal) }.getOrNull()?.forEach { jk ->
                val cur = byNumber[jk.number]
                byNumber[jk.number] = if (cur == null) jk else cur.copy(
                    title = cur.title?.takeIf { it.isNotBlank() } ?: jk.title,
                    date = cur.date ?: jk.date,
                )
            }
        }
        val list = byNumber.values.toList()
        if (list.isNotEmpty()) {
            val arr = org.json.JSONArray()
            list.forEach {
                arr.put(
                    org.json.JSONObject().put("n", it.number).put("t", it.title ?: "").put("d", it.date ?: "")
                        .put("r", it.runtime ?: 0).put("i", it.image ?: ""),
                )
            }
            cache.write("eps_$id.json", arr.toString())
        }
        list
    }

    private fun parseEpisodeCache(text: String): List<EpisodeInfo>? = try {
        val arr = org.json.JSONArray(text)
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            EpisodeInfo(
                o.getInt("n"), o.optString("t").ifBlank { null }, o.optString("d").ifBlank { null },
                o.optInt("r", 0).takeIf { it > 0 }, o.optString("i").ifBlank { null },
            )
        }.takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        null
    }

    private fun getText(url: String): String? {
        val request = okhttp3.Request.Builder().url(url).header("Accept", "application/json").build()
        return Http.plain.newCall(request).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
    }

    private fun prettyDate(iso: String?): String? {
        val m = Regex("""(\d{4})-(\d{2})-(\d{2})""").find(iso.orEmpty()) ?: return null
        val months = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val month = m.groupValues[2].toIntOrNull()?.let { months.getOrNull(it - 1) } ?: return null
        return "$month ${m.groupValues[3].toInt()}, ${m.groupValues[1]}"
    }

    private fun aniZipEpisodes(id: Int): List<EpisodeInfo>? {
        val root = org.json.JSONObject(getText("https://api.ani.zip/mappings?anilist_id=$id") ?: return null)
        val eps = root.optJSONObject("episodes") ?: return null
        val out = mutableListOf<EpisodeInfo>()
        for (key in eps.keys()) {
            val n = key.toIntOrNull() ?: continue
            val e = eps.optJSONObject(key) ?: continue
            val t = e.optJSONObject("title")
            val title = listOf("en", "x-jat", "ja").firstNotNullOfOrNull { k -> t?.optString(k)?.takeIf { it.isNotBlank() && it != "null" } }
            val date = prettyDate(e.optString("airdate")) ?: prettyDate(e.optString("airDateUtc"))
            val runtime = e.optInt("runtime", 0).takeIf { it > 0 } ?: e.optInt("length", 0).takeIf { it > 0 }
            val image = e.optString("image").takeIf { it.startsWith("http") }
            out += EpisodeInfo(n, title, date, runtime, image)
        }
        return out.sortedBy { it.number }.takeIf { it.isNotEmpty() }
    }

    private fun jikanEpisodes(mal: Int): List<EpisodeInfo>? {
        val out = mutableListOf<EpisodeInfo>()
        for (page in 1..4) {
            val body = getText("https://api.jikan.moe/v4/anime/$mal/episodes?page=$page") ?: break
            val root = org.json.JSONObject(body)
            val data = root.optJSONArray("data") ?: break
            for (i in 0 until data.length()) {
                val e = data.getJSONObject(i)
                out += EpisodeInfo(
                    e.optInt("mal_id"), e.optString("title").takeIf { it.isNotBlank() && it != "null" },
                    prettyDate(e.optString("aired")), null, null,
                )
            }
            if (root.optJSONObject("pagination")?.optBoolean("has_next_page") != true) break
            Thread.sleep(400)
        }
        return out.takeIf { it.isNotEmpty() }
    }

    /** MyAnimeList score (what Miruro lists next to the AniList score): Jikan first, then MAL's own page. */
    suspend fun malScore(idMal: Int): String? = withContext(Dispatchers.IO) {
        val key = "mal_$idMal.txt"
        cache.read(key)?.takeIf { it.isNotBlank() }?.let { return@withContext it }
        // Every source at once, first real answer wins: the official MAL API (when a client id is set), then
        // Jikan, Shikimori and MAL's own page as fallbacks. A slow or blocked source can't hold the score back.
        val score = coroutineScope {
            val sources = buildList<kotlinx.coroutines.Deferred<String?>> {
                if (BuildConfig.MAL_CLIENT_ID.isNotBlank()) add(async { malApiScore(idMal) })
                add(async { jikanScore(idMal) })
                add(async { shikimoriScore(idMal) })
                add(async { malPageScore(idMal) })
            }
            val pending = sources.toMutableList()
            var found: String? = null
            while (pending.isNotEmpty() && found == null) {
                val done = kotlinx.coroutines.selects.select<kotlinx.coroutines.Deferred<String?>> {
                    pending.forEach { d -> d.onAwait { d } }
                }
                pending.remove(done)
                found = done.getCompleted()
            }
            sources.forEach { it.cancel() }
            found
        }
        score?.also { cache.write(key, it) }
    }

    /** Official MyAnimeList API v2 (needs the MAL_CLIENT_ID build secret). */
    private fun malApiScore(id: Int): String? = try {
        val request = okhttp3.Request.Builder()
            .url("https://api.myanimelist.net/v2/anime/$id?fields=mean")
            .header("X-MAL-CLIENT-ID", BuildConfig.MAL_CLIENT_ID)
            .header("Accept", "application/json")
            .build()
        Http.plain.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val mean = org.json.JSONObject(response.body?.string().orEmpty()).optDouble("mean", Double.NaN)
            fmtScore(mean)
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private fun fmtScore(value: Double): String? =
        if (value.isNaN() || value <= 0.0) null else String.format(java.util.Locale.US, "%.2f", value)

    private suspend fun jikanScore(id: Int): String? {
        val request = okhttp3.Request.Builder()
            .url("https://api.jikan.moe/v4/anime/$id")
            .header("Accept", "application/json")
            .header("User-Agent", UA)
            .build()
        for (attempt in 0..2) {
            var retry = false
            var result: String? = null
            try {
                Http.plain.newCall(request).execute().use { response ->
                    when {
                        response.code == 429 || response.code >= 500 -> retry = true
                        response.isSuccessful -> {
                            val score = org.json.JSONObject(response.body?.string().orEmpty())
                                .optJSONObject("data")?.optDouble("score", Double.NaN) ?: Double.NaN
                            result = fmtScore(score)
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                retry = true
            }
            if (result != null || !retry) return result
            kotlinx.coroutines.delay(1200)
        }
        return null
    }

    /** Shikimori mirrors MyAnimeList ids and scores; used when Jikan is unreachable. */
    private fun shikimoriScore(id: Int): String? = try {
        val request = okhttp3.Request.Builder()
            .url("https://shikimori.one/api/animes/$id")
            .header("Accept", "application/json")
            .header("User-Agent", "AniBiar")
            .build()
        Http.plain.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val score = org.json.JSONObject(response.body?.string().orEmpty()).optString("score").toDoubleOrNull()
            score?.let { fmtScore(it) }
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private fun malPageScore(id: Int): String? = try {
        val request = okhttp3.Request.Builder()
            .url("https://myanimelist.net/anime/$id")
            .header("User-Agent", UA)
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()
        Http.plain.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val html = response.body?.string().orEmpty()
            val match = Regex("\"ratingValue\"\\s*:\\s*\"?([0-9]+(?:\\.[0-9]+)?)")
                .find(html)
                ?: Regex("ratingValue[^>]*>\\s*([0-9]+(?:\\.[0-9]+)?)\\s*<").find(html)
            match?.groupValues?.get(1)?.toDoubleOrNull()?.let { fmtScore(it) }
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    suspend fun tmdbImages(media: Media): TmdbImages? = tmdb.imagesFor(media)

    suspend fun externalIds(media: Media): Pair<String?, String?>? = tmdb.externalIds(media)

    private fun chainNodes(m: Media?): List<Media> = m?.relations?.edges.orEmpty()
        .filter { (it.relationType == "PREQUEL" || it.relationType == "SEQUEL") && it.node?.type == "ANIME" }
        .mapNotNull { it.node }

    /** Walks PREQUEL / SEQUEL links so the Show section lists the whole franchise, not just direct neighbours. */
    suspend fun fetchFranchise(root: Media): List<Media> {
        val found = LinkedHashMap<Int, Media>()
        val visited = HashSet<Int>().apply { add(root.id) }
        var queue = ArrayList<Int>()
        chainNodes(root).forEach { if (visited.add(it.id)) { found[it.id] = it; queue.add(it.id) } }
        var fetches = 0
        while (queue.isNotEmpty() && fetches < 14) {
            val batch = queue.take(4)
            val rest = queue.drop(4)
            fetches += batch.size
            val results = coroutineScope {
                batch.map { id ->
                    async {
                        try {
                            decodeMedia(api.execute(Queries.FRANCHISE, mapOf("id" to id)))
                        } catch (e: IOException) {
                            null
                        }
                    }
                }.map { it.await() }
            }
            val next = ArrayList<Int>(rest)
            results.forEach { r ->
                chainNodes(r).forEach { if (visited.add(it.id)) { found[it.id] = it; next.add(it.id) } }
            }
            queue = next
        }
        return found.values.toList()
    }

    // ---- TMDB artwork ----

    suspend fun art(media: Media): TmdbArt? = tmdb.artFor(media)

    private companion object {
        const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
        const val KEY_DISCOVER = "discover.json"
        const val KEY_POPULAR = "popular.json"
        const val KEY_DUB = "dub.json"
        const val KEY_DUB_IDS = "dub_ids.json"
        const val KEY_LATEST_1 = "latest_1.json"
        const val KEY_LATEST_2 = "latest_2.json"
    }
}
