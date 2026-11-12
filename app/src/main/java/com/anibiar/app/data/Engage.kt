package com.anibiar.app.data

import com.anibiar.app.nativecore.Native

/** XP, level and badges from the watch stats. The maths lives in Rust (engage.rs); this is the same formula as a fallback. */
data class Engage(val xp: Long, val level: Int, val floor: Long, val next: Long, val badges: List<String>) {
    val progress: Float get() = ((xp - floor).toFloat() / (next - floor).coerceAtLeast(1)).coerceIn(0f, 1f)
}

object EngageCalc {
    fun xpFor(level: Int): Long = if (level <= 1) 0 else Math.round(100.0 * Math.pow((level - 1).toDouble(), 1.5))
    fun levelOf(xp: Long): Int { var l = 1; while (xpFor(l + 1) <= xp && l < 200) l++; return l }

    fun compute(s: StatsSummary): Engage {
        Native.engage(s.total, s.bestStreak, s.shows, s.maxDay)?.split('\t')?.takeIf { it.size >= 4 }?.let { f ->
            return Engage(f[0].toLong(), f[1].toInt(), f[2].toLong(), f[3].toLong(), f.getOrNull(4).orEmpty().split(',').filter { it.isNotBlank() })
        }
        val mins = s.total / 60
        val xp = mins + minOf(s.shows * 10L + s.bestStreak * 15L, mins / 2 + 30)
        val l = levelOf(xp)
        val h = s.total / 3600
        val b = buildList {
            if (s.total > 0) add("first")
            if (s.bestStreak >= 3) add("streak3"); if (s.bestStreak >= 7) add("streak7"); if (s.bestStreak >= 30) add("streak30")
            if (s.shows >= 10) add("shows10"); if (s.shows >= 25) add("shows25"); if (s.shows >= 100) add("shows100")
            if (h >= 24) add("hours24"); if (h >= 100) add("hours100"); if (h >= 500) add("hours500")
            if (s.maxDay >= 4 * 3600) add("binge"); if (s.maxDay >= 8 * 3600) add("marathon")
            if (l >= 10) add("lv10"); if (l >= 25) add("lv25")
        }
        return Engage(xp, l, xpFor(l), xpFor(l + 1), b)
    }
}
