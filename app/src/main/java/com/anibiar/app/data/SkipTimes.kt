package com.anibiar.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Intro / outro / recap times from AniSkip v2 (community-timed, keyed by MyAnimeList id and episode).
 * Passing the real length of the video lets AniSkip return the timing made for that same cut, and the answer is
 * checked again here: a result whose episode length differs from the video by more than a few seconds is ignored,
 * and every range is validated and clamped to the video.
 */
object SkipTimes {
    private val json = Json { ignoreUnknownKeys = true }
    private class Entry(val at: Long, val raw: List<JsonObject>?)
    private val cache = ConcurrentHashMap<String, Entry>()

    private fun range(o: JsonObject): TimeRange? {
        val i = o["interval"]?.jsonObject ?: return null
        val s = i["startTime"]?.jsonPrimitive?.doubleOrNull ?: return null
        val e = i["endTime"]?.jsonPrimitive?.doubleOrNull ?: return null
        return TimeRange(s, e)
    }

    /** Every submission AniSkip has for this episode, cached. null = the request failed, empty = it has none. */
    private suspend fun all(mal: Int, ep: Int): List<JsonObject>? {
        val key = "$mal/$ep"
        cache[key]?.let { e -> if (System.currentTimeMillis() - e.at < (if (e.raw == null) 60_000 else 3_600_000)) return e.raw }
        val raw = withContext(Dispatchers.IO) {
            runCatching {
                // Brackets are kept literal, exactly as the AniSkip site itself sends them.
                val url = "https://api.aniskip.com/v2/skip-times/$mal/$ep?types[]=op&types[]=ed&types[]=mixed-op&types[]=mixed-ed&types[]=recap&episodeLength=0".toHttpUrl()
                Http.plain.newCall(Request.Builder().url(url).header("Accept", "application/json").build()).execute().use { r ->
                    when {
                        r.code == 404 -> emptyList()
                        !r.isSuccessful -> null
                        else -> (json.parseToJsonElement(r.body!!.string()).jsonObject["results"] as? JsonArray).orEmpty().map { it.jsonObject }
                    }
                }
            }.getOrNull()
        }
        cache[key] = Entry(System.currentTimeMillis(), raw)
        return raw
    }

    /** [lengthSec] is the real video length, or 0 while it isn't known yet. */
    suspend fun fetch(mal: Int, ep: Int, lengthSec: Double): SkipPayload? {
        val results = all(mal, ep) ?: return null
        return pick(results, if (lengthSec > 60) lengthSec else 0.0)
    }

    private fun pick(results: List<JsonObject>, len: Double): SkipPayload? {
        fun best(vararg types: String, minLen: Double, maxLen: Double): TimeRange? {
            val c = results.filter { it["skipType"]?.jsonPrimitive?.contentOrNull in types }.mapNotNull { o ->
                val r = range(o) ?: return@mapNotNull null
                val el = o["episodeLength"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                // Timed for a very different cut of the episode (more than 45 s off): don't trust it.
                if (len > 0 && el > 0 && abs(el - len) > 45.0) return@mapNotNull null
                val end = if (len > 0) minOf(r.end, len) else r.end
                val start = r.start.coerceAtLeast(0.0)
                val dur = end - start
                if (dur < minLen || dur > maxLen) return@mapNotNull null
                Triple(TimeRange(start, end), if (len > 0 && el > 0) abs(el - len) else 3.0, o)
            }
            return c.minByOrNull { it.second }?.first
        }
        val intro = best("op", "mixed-op", minLen = 5.0, maxLen = 240.0)
        val outro = best("ed", "mixed-ed", minLen = 5.0, maxLen = 400.0)
        val recap = best("recap", minLen = 8.0, maxLen = 240.0)
        if (intro == null && outro == null && recap == null) return null
        return SkipPayload(intro = intro, outro = outro, recap = recap, length = len.takeIf { it > 0 })
    }
}
