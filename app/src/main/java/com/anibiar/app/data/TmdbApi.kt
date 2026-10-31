package com.anibiar.app.data

import android.content.SharedPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** Title logo + wide backdrop from TMDB, the same artwork Miruro shows in its hero. */
data class TmdbArt(val logo: String?, val backdrop: String?) {
    val isEmpty: Boolean get() = logo == null && backdrop == null
}

/**
 * Looks up artwork on TMDB. Results are cached in memory and in SharedPreferences,
 * so each title is looked up only once.
 */
class TmdbApi(
    private val client: OkHttpClient,
    private val apiKey: String,
    private val prefs: SharedPreferences,
) {
    private val memory = ConcurrentHashMap<Int, TmdbArt>()

    @Volatile
    private var keyRejected = false

    suspend fun artFor(media: Media): TmdbArt? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || keyRejected) return@withContext null

        memory[media.id]?.let { return@withContext it.takeUnless { art -> art.isEmpty } }
        prefs.getString(prefKey(media.id), null)?.let { stored ->
            val art = decode(stored)
            memory[media.id] = art
            return@withContext art.takeUnless { it.isEmpty }
        }

        val art = try {
            lookup(media)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withContext null // network/parse problem: don't cache, try again later
        }
        memory[media.id] = art
        prefs.edit().putString(prefKey(media.id), encode(art)).apply()
        art.takeUnless { it.isEmpty }
    }

    private fun lookup(media: Media): TmdbArt {
        val type = if (media.format == "MOVIE") "movie" else "tv"
        val names = listOfNotNull(media.title.english, media.title.romaji)
            .map { cleanTitle(it) }
            .filter { it.isNotBlank() }
            .distinct()

        for (name in names) {
            val results = get("/search/$type", mapOf("query" to name))?.optJSONArray("results") ?: continue
            val tmdbId = pickResult(results, media.year) ?: continue
            val images = get("/$type/$tmdbId/images", mapOf("include_image_language" to "en,ja,null")) ?: continue
            val logo = bestLogo(images.optJSONArray("logos"))
            val backdrop = bestBackdrop(images.optJSONArray("backdrops"))
            if (logo != null || backdrop != null) {
                return TmdbArt(
                    logo = logo?.let { LOGO_BASE + it },
                    backdrop = backdrop?.let { BACKDROP_BASE + it },
                )
            }
        }
        return TmdbArt(null, null)
    }

    /** Prefer Japanese-origin matches; among them prefer the one released in the same year. */
    private fun pickResult(results: JSONArray, year: Int?): Int? {
        val candidates = mutableListOf<JSONObject>()
        for (i in 0 until minOf(results.length(), 10)) {
            val item = results.optJSONObject(i) ?: continue
            if (item.optString("original_language") == "ja") candidates += item
        }
        if (candidates.isEmpty()) {
            // Fall back to anything tagged Animation (genre 16).
            for (i in 0 until minOf(results.length(), 10)) {
                val item = results.optJSONObject(i) ?: continue
                val genres = item.optJSONArray("genre_ids") ?: continue
                if ((0 until genres.length()).any { genres.optInt(it) == 16 }) candidates += item
            }
        }
        if (candidates.isEmpty()) return null
        if (year != null) {
            val sameYear = candidates.firstOrNull { item ->
                val date = item.optString("first_air_date").ifEmpty { item.optString("release_date") }
                val y = date.take(4).toIntOrNull()
                y != null && kotlin.math.abs(y - year) <= 1
            }
            if (sameYear != null) return sameYear.optInt("id")
        }
        return candidates.first().optInt("id")
    }

    private fun bestLogo(logos: JSONArray?): String? {
        if (logos == null) return null
        var best: String? = null
        var bestScore = -1.0
        for (i in 0 until logos.length()) {
            val logo = logos.optJSONObject(i) ?: continue
            val path = logo.optString("file_path")
            if (path.isEmpty() || path.endsWith(".svg", ignoreCase = true)) continue
            val lang = logo.optString("iso_639_1")
            val languageScore = when (lang) {
                "en" -> 100.0
                "ja" -> 50.0
                else -> 0.0
            }
            val score = languageScore + logo.optDouble("vote_average", 0.0)
            if (score > bestScore) {
                bestScore = score
                best = path
            }
        }
        return best
    }

    /** Textless backdrops (no language) look cleanest; fall back to anything available. */
    private fun bestBackdrop(backdrops: JSONArray?): String? {
        if (backdrops == null) return null
        var best: String? = null
        var bestScore = -1.0
        for (i in 0 until backdrops.length()) {
            val item = backdrops.optJSONObject(i) ?: continue
            val path = item.optString("file_path")
            if (path.isEmpty()) continue
            val textless = item.isNull("iso_639_1") || item.optString("iso_639_1").isEmpty()
            val score = (if (textless) 100.0 else 0.0) + item.optDouble("vote_average", 0.0)
            if (score > bestScore) {
                bestScore = score
                best = path
            }
        }
        return best
    }

    private fun cleanTitle(title: String): String = title
        .replace(
            Regex("(?i)\\s*(\\b\\d+(st|nd|rd|th) season|\\bseason \\d+|\\bpart \\d+|\\bcour \\d+).*$"),
            "",
        )
        .trim()

    /** Returns null on 401/404; throws IOException on other failures. */
    private fun get(path: String, params: Map<String, String>): JSONObject? {
        val urlBuilder = (BASE + path).toHttpUrl().newBuilder()
        params.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }
        // A v4 read token is a long JWT; a v3 key is 32 hex characters.
        val useBearer = apiKey.length > 40
        if (!useBearer) urlBuilder.addQueryParameter("api_key", apiKey)

        val request = Request.Builder().url(urlBuilder.build()).apply {
            if (useBearer) header("Authorization", "Bearer $apiKey")
        }.build()

        return client.newCall(request).execute().use { response ->
            when {
                response.code == 401 -> {
                    keyRejected = true
                    null
                }
                response.code == 404 -> null
                !response.isSuccessful -> throw IOException("TMDB error ${response.code}")
                else -> JSONObject(response.body?.string().orEmpty())
            }
        }
    }

    private fun encode(art: TmdbArt): String = art.logo.orEmpty() + "|" + art.backdrop.orEmpty()

    private fun decode(stored: String): TmdbArt {
        val parts = stored.split("|", limit = 2)
        return TmdbArt(
            logo = parts.getOrNull(0)?.takeIf { it.isNotEmpty() },
            backdrop = parts.getOrNull(1)?.takeIf { it.isNotEmpty() },
        )
    }

    private fun prefKey(id: Int) = "art2_$id"

    private companion object {
        const val BASE = "https://api.themoviedb.org/3"
        const val LOGO_BASE = "https://image.tmdb.org/t/p/w500"
        const val BACKDROP_BASE = "https://image.tmdb.org/t/p/w1280"
    }
}
