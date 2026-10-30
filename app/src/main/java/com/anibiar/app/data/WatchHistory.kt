package com.anibiar.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class HistoryItem(
    val mediaId: Int,
    val title: String,
    val image: String?,
    val episode: Int,
    val progress: Float,
)

/** Local "Continue Watching" list. The player (next milestone) will call [save]. */
class WatchHistory(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("watch_history", Context.MODE_PRIVATE)

    fun load(): List<HistoryItem> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val o = array.optJSONObject(index) ?: return@mapNotNull null
                HistoryItem(
                    mediaId = o.getInt("id"),
                    title = o.optString("title"),
                    image = o.optString("image").takeIf { it.isNotEmpty() },
                    episode = o.optInt("episode", 1),
                    progress = o.optDouble("progress", 0.0).toFloat(),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(item: HistoryItem) {
        val items = (listOf(item) + load().filter { it.mediaId != item.mediaId }).take(MAX_ITEMS)
        val array = JSONArray()
        items.forEach {
            array.put(
                JSONObject()
                    .put("id", it.mediaId)
                    .put("title", it.title)
                    .put("image", it.image.orEmpty())
                    .put("episode", it.episode)
                    .put("progress", it.progress.toDouble()),
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    fun remove(mediaId: Int) {
        val array = JSONArray()
        load().filter { it.mediaId != mediaId }.forEach {
            array.put(
                JSONObject()
                    .put("id", it.mediaId)
                    .put("title", it.title)
                    .put("image", it.image.orEmpty())
                    .put("episode", it.episode)
                    .put("progress", it.progress.toDouble()),
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "items"
        const val MAX_ITEMS = 20
    }
}
