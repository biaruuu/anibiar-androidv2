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

    /** MyAnimeList score (what Miruro lists next to the AniList score): Jikan first, then MAL's own page. */
    suspend fun malScore(idMal: Int): String? = withContext(Dispatchers.IO) {
        val key = "mal_$idMal.txt"
        cache.read(key)?.takeIf { it.isNotBlank() }?.let { return@withContext it }
        val score = jikanScore(idMal) ?: malPageScore(idMal)
        score?.also { cache.write(key, it) }
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
        const val KEY_LATEST_1 = "latest_1.json"
        const val KEY_LATEST_2 = "latest_2.json"
    }
}
