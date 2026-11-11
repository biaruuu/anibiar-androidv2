package com.anibiar.app.data

import android.content.Context
import com.anibiar.app.nativecore.Native
import java.io.File

/** Summary shown on the profile. Seconds everywhere. */
data class StatsSummary(val total: Long, val week: Long, val streak: Int, val bestStreak: Int, val shows: Int, val genres: List<Pair<String, Long>>, val maxDay: Long = 0L, val today: Long = 0L)

/** Watch time per day and anime, kept on-device. Aggregated in Rust ([Native.stats]). */
class WatchStats(context: Context, private val catalog: Catalog) {
    private val file = File(context.applicationContext.filesDir, "watch_stats.tsv")
    private val rows = LinkedHashMap<String, LongArray>() // "day\tmedia" -> [secs]
    private var loaded = false
    private var lastWrite = 0L
    private var lastKey: String? = null
    private var lastPos = -1.0

    @Synchronized private fun ensure() {
        if (loaded) return
        loaded = true
        runCatching {
            if (file.exists()) file.readLines().forEach { l ->
                val f = l.split('\t'); if (f.size >= 3) rows["${f[0]}\t${f[1]}"] = longArrayOf(f[2].toLongOrNull() ?: 0L)
            }
        }
    }

    /** Called with the playhead on every progress tick; counts only forward movement of up to 15 s (no seeks). */
    @Synchronized fun tick(mediaId: Int, episode: Int, positionSec: Double) {
        ensure()
        val key = "$mediaId:$episode"
        val delta = positionSec - lastPos
        val ok = key == lastKey && delta > 0 && delta <= 15
        lastKey = key; lastPos = positionSec
        if (!ok) return
        val day = System.currentTimeMillis() / 86_400_000L
        rows.getOrPut("$day\t$mediaId") { LongArray(1) }[0] += Math.round(delta)
        val now = System.currentTimeMillis()
        if (now - lastWrite > 30_000) flush(now)
    }

    @Synchronized fun flush(now: Long = System.currentTimeMillis()) {
        lastWrite = now
        runCatching { file.writeText(rows.entries.joinToString("\n") { "${it.key}\t${it.value[0]}" }) }
    }

    fun summary(): StatsSummary? {
        val snapshot = synchronized(this) { ensure(); rows.entries.map { it.key to it.value[0] } }
        if (snapshot.isEmpty()) return null
        val today = System.currentTimeMillis() / 86_400_000L
        val text = snapshot.joinToString("\n") { (k, secs) ->
            val (day, media) = k.split('\t')
            val g = catalog.get(media.toIntOrNull() ?: 0)?.genres.orEmpty().joinToString(",")
            "$day\t$media\t$secs\t$g"
        }
        val out = Native.stats(today, text) ?: kotlinFallback(today, snapshot)
        val lines = out.lines()
        val h = lines.first().split('\t')
        if (h.size < 5) return null
        return StatsSummary(
            h[0].toLong(), h[1].toLong(), h[2].toInt(), h[3].toInt(), h[4].toInt(),
            lines.drop(1).mapNotNull { l -> l.split('\t').takeIf { it.size == 2 }?.let { it[0] to (it[1].toLongOrNull() ?: 0L) } },
            maxDay = h.getOrNull(5)?.toLongOrNull() ?: snapshot.groupBy({ it.first.substringBefore('\t') }, { it.second }).values.maxOfOrNull { it.sum() } ?: 0L,
            today = snapshot.filter { it.first.substringBefore('\t') == today.toString() }.sumOf { it.second },
        )
    }

    private fun kotlinFallback(today: Long, snap: List<Pair<String, Long>>): String {
        var total = 0L; var week = 0L
        val days = HashSet<Long>(); val shows = HashSet<String>(); val genres = HashMap<String, Long>()
        for ((k, s) in snap) {
            val (d, m) = k.split('\t'); val day = d.toLong()
            total += s; if (day > today - 7) week += s
            days += day; shows += m
            catalog.get(m.toIntOrNull() ?: 0)?.genres?.forEach { genres[it] = (genres[it] ?: 0L) + s }
        }
        var streak = 0; var d = if (today in days) today else today - 1
        while (d in days) { streak++; d-- }
        var best = 0; var run = 0; var prev = -10L
        for (x in days.sorted()) { run = if (x == prev + 1) run + 1 else 1; best = maxOf(best, run); prev = x }
        return "$total\t$week\t$streak\t$best\t${shows.size}" + genres.entries.sortedByDescending { it.value }.take(6).joinToString("") { "\n${it.key}\t${it.value}" }
    }
}
