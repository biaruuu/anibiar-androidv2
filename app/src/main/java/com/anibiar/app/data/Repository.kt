package com.anibiar.app.data

import android.content.Context
import com.anibiar.app.BuildConfig
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
        val data = decodeDiscover(body) ?: throw IOException("Unexpected AniList response")
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
        buildLatest(listOfNotNull(firstBody, secondBody))
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

    // ---- TMDB artwork ----

    suspend fun art(media: Media): TmdbArt? = tmdb.artFor(media)

    private companion object {
        const val KEY_DISCOVER = "discover.json"
        const val KEY_LATEST_1 = "latest_1.json"
        const val KEY_LATEST_2 = "latest_2.json"
    }
}
