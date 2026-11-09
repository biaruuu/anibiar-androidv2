package com.anibiar.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class HistoryItem(
    val mediaId: Int,
    val title: String,
    val image: String?,
    val episode: Int,
    val progress: Float,
    /** When it was last watched (ms since epoch); used to merge with the copy saved on the account. */
    val at: Long = 0L,
)

/** Local "Continue Watching" list. The player (next milestone) will call [save]. */
class WatchHistory(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("watch_history", Context.MODE_PRIVATE)
    private val _version = MutableStateFlow(0)
    /** Bumped whenever the list changes, so screens can refresh (e.g. after a sync from the account). */
    val version: StateFlow<Int> = _version
    /** Called after [save] / [remove] so the app can mirror the change to the account. */
    var listener: Listener? = null
    interface Listener { fun saved(item: HistoryItem); fun removed(mediaId: Int) }

    /** Where to pick up this show: the saved episode, or the next one if that was nearly finished; 1 when never watched. */
    fun resumeEpisode(mediaId: Int): Int {
        val h = load().firstOrNull { it.mediaId == mediaId } ?: return 1
        return if (h.progress >= 0.92f) h.episode + 1 else h.episode.coerceAtLeast(1)
    }

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
                    at = o.optLong("at", 0L),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun write(items: List<HistoryItem>) {
        val array = JSONArray()
        items.forEach {
            array.put(
                JSONObject().put("id", it.mediaId).put("title", it.title).put("image", it.image.orEmpty())
                    .put("episode", it.episode).put("progress", it.progress.toDouble()).put("at", it.at),
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
        _version.value++
    }

    fun save(item: HistoryItem) {
        val stamped = item.copy(at = System.currentTimeMillis())
        write((listOf(stamped) + load().filter { it.mediaId != item.mediaId }).take(MAX_ITEMS))
        listener?.saved(stamped)
    }

    fun remove(mediaId: Int) {
        write(load().filter { it.mediaId != mediaId })
        listener?.removed(mediaId)
    }

    /** Merges items from the account: for each anime the most recently watched copy wins. Does not call the listener. */
    fun merge(remote: List<HistoryItem>) {
        val local = load().associateBy { it.mediaId }.toMutableMap()
        var changed = false
        for (r in remote) {
            val l = local[r.mediaId]
            if (l == null || r.at > l.at) { local[r.mediaId] = r; changed = true }
        }
        if (changed) write(local.values.sortedByDescending { it.at }.take(MAX_ITEMS))
    }

    private companion object {
        const val KEY = "items"
        const val MAX_ITEMS = 20
    }
}
