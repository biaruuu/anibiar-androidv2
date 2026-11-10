package com.anibiar.app.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.util.Calendar

/** One episode that airs (or aired) today, from AniList's release schedule. */
data class Airing(val mediaId: Int, val title: String, val cover: String?, val episode: Int, val at: Long, val popularity: Int)

object Schedule {
    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var cache: Pair<Long, List<Airing>>? = null

    private const val QUERY = """
        query(${'$'}from: Int, ${'$'}to: Int) {
          Page(page: 1, perPage: 50) {
            airingSchedules(airingAt_greater: ${'$'}from, airingAt_lesser: ${'$'}to, sort: TIME) {
              episode airingAt
              media { id popularity isAdult countryOfOrigin title { romaji english } coverImage { large } }
            }
          }
        }
    """

    /** Today's releases (local midnight to midnight), the most popular first, then in air-time order. Cached 10 minutes. */
    suspend fun today(): List<Airing> {
        cache?.let { (t, v) -> if (System.currentTimeMillis() - t < 600_000) return v }
        val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val from = (cal.timeInMillis / 1000).toInt()
        val to = from + 86_400
        val body = AniListApi(Http.client).executeVars(QUERY, buildJsonObject { put("from", from); put("to", to) })
        val list = (json.parseToJsonElement(body).jsonObject["data"]?.jsonObject?.get("Page")?.jsonObject?.get("airingSchedules") as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e.jsonObject
            val m = o["media"]?.jsonObject ?: return@mapNotNull null
            if (m["isAdult"]?.jsonPrimitive?.contentOrNull == "true") return@mapNotNull null
            val t = m["title"]?.jsonObject
            Airing(
                mediaId = m["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null,
                title = t?.get("english")?.jsonPrimitive?.contentOrNull ?: t?.get("romaji")?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                cover = m["coverImage"]?.jsonObject?.get("large")?.jsonPrimitive?.contentOrNull,
                episode = o["episode"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null,
                at = (o["airingAt"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null) * 1000,
                popularity = m["popularity"]?.jsonPrimitive?.intOrNull ?: 0,
            )
        }
        val out = list.sortedByDescending { it.popularity }.take(14).sortedBy { it.at }
        cache = System.currentTimeMillis() to out
        return out
    }
}
