package com.anibiar.app.data

import android.content.Context
import com.anibiar.app.nativecore.Native
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Every anime the app has seen (home, search, details), kept on disk. Feeds instant/typo-tolerant search and
 * recommendations without a network request.
 */
class Catalog(context: Context) {
    private val file = File(context.applicationContext.filesDir, "catalog.json")
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
    private val items = LinkedHashMap<Int, Media>()
    private var loaded = false

    @Synchronized private fun ensure() {
        if (loaded) return
        loaded = true
        runCatching {
            if (file.exists()) json.decodeFromString(ListSerializer(Media.serializer()), file.readText()).forEach { items[it.id] = it }
        }
    }

    /** Remember these (newest last). Slim copies only: no description or trailer. */
    @Synchronized fun add(list: List<Media>) {
        ensure()
        var changed = false
        for (m in list) {
            if (m.isAdult == true) continue
            val slim = m.copy(description = null, trailer = null, studios = null)
            if (items[m.id] != slim) { items.remove(m.id); items[m.id] = slim; changed = true }
        }
        while (items.size > MAX) items.remove(items.keys.first())
        if (changed) runCatching {
            val tmp = File(file.path + ".tmp")
            tmp.writeText(json.encodeToString(ListSerializer(Media.serializer()), items.values.toList()))
            tmp.renameTo(file)
        }
    }

    @Synchronized fun all(): List<Media> { ensure(); return items.values.toList() }
    @Synchronized fun get(id: Int): Media? { ensure(); return items[id] }

    private fun names(m: Media) = listOfNotNull(m.title.english, m.title.romaji, m.title.native).plus(m.synonyms.orEmpty()).joinToString(" ")

    /** Typo-tolerant local search, best first. */
    fun search(query: String, limit: Int = 24): List<Media> {
        val list = all()
        if (query.isBlank()) return emptyList()
        return Native.typo(query, list.map(::names)).take(limit).map { list[it] }
    }

    /** Genre taste from what the user watched and saved, newest weighted most. */
    fun taste(history: List<HistoryItem>, saved: Set<Int>): Map<String, Double> {
        val t = HashMap<String, Double>()
        history.forEachIndexed { i, h ->
            val w = 3.0 - (i * 0.1).coerceAtMost(2.0) + h.progress.coerceIn(0f, 1f)
            get(h.mediaId)?.genres?.forEach { g -> t[g] = (t[g] ?: 0.0) + w }
        }
        saved.forEach { id -> get(id)?.genres?.forEach { g -> t[g] = (t[g] ?: 0.0) + 1.5 } }
        return t
    }

    /** Picks for the viewer: best genre fit first, never something already watched/saved. */
    fun recommend(history: List<HistoryItem>, saved: Set<Int>, limit: Int = 20): List<Media> {
        val taste = taste(history, saved)
        if (taste.isEmpty()) return emptyList()
        val skip = history.map { it.mediaId }.toSet() + saved
        val cands = all().filter { it.id !in skip && it.genres?.isNotEmpty() == true }
        return Native.reco(taste, cands.map { (it.averageScore ?: 0) to it.genres.orEmpty() }).take(limit).map { cands[it] }
    }

    private companion object { const val MAX = 2500 }
}
