package com.anibiar.app.data

import com.anibiar.app.AniBiarApp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap

/**
 * Anime and stream links added from the Kiro admin panel. Rows are public once published; the admin account also sees its drafts.
 * Custom anime get app ids from [BASE] up; stream links can also be attached to ordinary AniList ids.
 */
object AdminContent {
    const val BASE = 9_000_000
    const val ALIAS = "kiro"
    private val json = Json { ignoreUnknownKeys = true }

    fun isCustom(id: Int) = id >= BASE

    /** Forget cached lists after the admin panel changed something. */
    fun refresh() { customCache = null; rowsCache.clear() }

    private class Cached<T>(val at: Long, val v: T)
    private var customCache: Cached<List<Media>>? = null
    private val rowsCache = ConcurrentHashMap<Int, Cached<List<kotlinx.serialization.json.JsonObject>>>()

    private fun social() = (Http.context?.applicationContext as? AniBiarApp)?.social

    private suspend fun rows(path: String): List<kotlinx.serialization.json.JsonObject> {
        val s = social() ?: return emptyList()
        val (code, text) = s.rest("GET", path)
        if (code !in 200..299) return emptyList()
        return (json.parseToJsonElement(text) as? JsonArray).orEmpty().map { it.jsonObject }
    }

    private fun str(o: kotlinx.serialization.json.JsonObject, k: String) = o[k]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }

    private fun toMedia(o: kotlinx.serialization.json.JsonObject): Media {
        val id = BASE + (o["id"]?.jsonPrimitive?.intOrNull ?: 0)
        val title = str(o, "title").orEmpty()
        return Media(
            id = id, title = Title(romaji = title, english = title), description = str(o, "description"),
            episodes = o["episodes_total"]?.jsonPrimitive?.intOrNull, format = str(o, "format"), status = str(o, "status"),
            seasonYear = o["year"]?.jsonPrimitive?.intOrNull, genres = (o["genres"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull },
            averageScore = o["score"]?.jsonPrimitive?.intOrNull, bannerImage = str(o, "banner_url"),
            coverImage = Cover(extraLarge = str(o, "cover_url"), large = str(o, "cover_url")), type = "ANIME", isAdult = false,
        )
    }

    /** The anime added by hand, newest first. */
    suspend fun custom(): List<Media> {
        customCache?.takeIf { System.currentTimeMillis() - it.at < 180_000 }?.let { return it.v }
        val list = runCatching { rows("/rest/v1/admin_anime?select=*&order=id.desc&limit=40").map(::toMedia) }.getOrNull() ?: return customCache?.v.orEmpty()
        customCache = Cached(System.currentTimeMillis(), list)
        return list
    }

    suspend fun media(id: Int): Media? = runCatching { rows("/rest/v1/admin_anime?id=eq.${id - BASE}&select=*").firstOrNull()?.let(::toMedia) }.getOrNull()

    private suspend fun sources(ref: Int): List<kotlinx.serialization.json.JsonObject> {
        rowsCache[ref]?.takeIf { System.currentTimeMillis() - it.at < 120_000 }?.let { return it.v }
        val r = rows("/rest/v1/admin_sources?anime_ref=eq.$ref&select=*&order=episode.asc,id.asc")
        rowsCache[ref] = Cached(System.currentTimeMillis(), r)
        return r
    }

    /** Episodes that have at least one admin link, or null when this anime has none. */
    suspend fun episodes(id: Int): LocalSources.Local? {
        val r = runCatching { sources(id) }.getOrNull().orEmpty()
        if (r.isEmpty()) return null
        fun eps(audio: String) = r.filter { str(it, "audio") == audio }.mapNotNull { it["episode"]?.jsonPrimitive?.intOrNull }.distinct().sorted().map { WatchEpisode(number = it, title = "Episode $it") }
        return LocalSources.Local(eps("sub"), eps("dub"))
    }

    suspend fun watch(id: Int, audio: String, ep: Int): List<StreamSource> =
        runCatching { sources(id) }.getOrNull().orEmpty()
            .filter { str(it, "audio") == audio && it["episode"]?.jsonPrimitive?.intOrNull == ep }
            .mapNotNull { o ->
                val url = str(o, "url") ?: return@mapNotNull null
                str(o, "referer")?.let { LocalSources.referers[url] = it }
                val sub = str(o, "subtitle_url")
                sub?.let { u -> str(o, "referer")?.let { LocalSources.referers[u] = it } }
                StreamSource(
                    id = "kiro-${o["id"]?.jsonPrimitive?.contentOrNull}", name = str(o, "label") ?: "Kiro",
                    kind = if (sub != null) "soft" else "hard", format = if (str(o, "format") == "hls") "hls" else "file", src = url,
                    subtitles = if (sub != null) listOf(StreamSubtitle("English", "en", sub, true)) else emptyList(),
                )
            }
}
