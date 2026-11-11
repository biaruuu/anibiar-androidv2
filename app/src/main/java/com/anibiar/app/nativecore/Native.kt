package com.anibiar.app.nativecore

/**
 * Bridge to the Rust library (rust/kiro_core). The library is optional: if it isn't packaged for this device's CPU,
 * [available] is false and every caller keeps using its plain Kotlin implementation.
 */
object Native {
    val available: Boolean = try { System.loadLibrary("kiro_core"); true } catch (_: Throwable) { false }

    @JvmStatic private external fun parseCaptions(raw: String): String?
    @JvmStatic private external fun fuzzyRank(query: String, items: String): IntArray?
    @JvmStatic private external fun version(): String?
    @JvmStatic private external fun dice(a: String, b: String): Double
    @JvmStatic private external fun htmlText(s: String, mode: Int): String?

    @JvmStatic private external fun hlsMaxHeight(text: String): Int

    /** Tallest RESOLUTION in an HLS master playlist (0 = none). Null when Rust isn't usable. */
    fun hlsHeight(text: String): Int? = if (available) runCatching { hlsMaxHeight(text) }.getOrNull()?.takeIf { it >= 0 } else null

    @JvmStatic private external fun rankStreams(subAudio: Int, rows: String): IntArray?

    /** Row per stream: soft, english, tracks, hls (0/1) and picture height. Returns indices best first, or null. */
    fun rank(subAudio: Boolean, rows: List<IntArray>): IntArray? =
        if (available && rows.isNotEmpty()) runCatching { rankStreams(if (subAudio) 1 else 0, rows.joinToString("\n") { it.joinToString(",") }) }.getOrNull()?.takeIf { it.size == rows.size } else null

    /** Bigram similarity of two titles, 0.0..1.0. Null when Rust isn't usable. */
    fun diceOrNull(a: String, b: String): Double? =
        if (available) runCatching { dice(a, b) }.getOrNull()?.takeIf { it >= 0.0 } else null

    /** mode 0: anime description, 1: scraped HTML snippet, 2: entities only. Null when Rust isn't usable. */
    fun html(s: String, mode: Int): String? = if (available) runCatching { htmlText(s, mode) }.getOrNull() else null

    @JvmStatic private external fun engageSummary(total: Long, best: Int, shows: Int, maxDay: Long): String?

    /** xp, level, floor, next, badges (see rust/kiro_core/src/engage.rs). Null when Rust isn't usable. */
    fun engage(total: Long, best: Int, shows: Int, maxDay: Long): String? =
        if (available) runCatching { engageSummary(total, best, shows, maxDay) }.getOrNull() else null

    fun versionOrNull(): String? = if (available) runCatching { version() }.getOrNull() else null

    /** Records split by U+001E, fields by U+001F: start, end, italic, text. Null when Rust isn't usable. */
    fun captions(raw: String): String? = if (available) runCatching { parseCaptions(raw) }.getOrNull() else null

    /** Indices of [items] matching [query], best first. Null when Rust isn't usable. */
    fun rank(query: String, items: List<String>): IntArray? =
        if (available) runCatching { fuzzyRank(query, items.joinToString("\n") { it.replace('\n', ' ') }) }.getOrNull() else null

    @JvmStatic private external fun typoRank(query: String, items: String): IntArray?
    @JvmStatic private external fun recoRank(taste: String, cands: String): IntArray?
    @JvmStatic private external fun statsSummary(today: Long, rows: String): String?

    /** Typo-tolerant title match (every query word must match). Kotlin fallback is a plain contains check. */
    fun typo(query: String, items: List<String>): List<Int> {
        if (available) runCatching { typoRank(query, items.joinToString("\n") { it.replace('\n', ' ') }) }.getOrNull()?.let { return it.toList() }
        val q = query.trim().lowercase()
        return items.indices.filter { items[it].lowercase().contains(q) }
    }

    /** taste: genre to weight. cands: score and genres. Indices of candidates, best first. */
    fun reco(taste: Map<String, Double>, cands: List<Pair<Int, List<String>>>): List<Int> {
        if (taste.isEmpty()) return emptyList()
        if (available) runCatching {
            recoRank(taste.entries.joinToString("\n") { "${it.key}\t${it.value}" }, cands.joinToString("\n") { "${it.first}\t${it.second.joinToString(",")}" })
        }.getOrNull()?.let { return it.toList() }
        val tn = Math.sqrt(taste.values.sumOf { it * it })
        return cands.withIndex().mapNotNull { (i, c) ->
            val dot = c.second.sumOf { taste[it] ?: 0.0 }
            if (c.second.isEmpty() || dot == 0.0) null else i to (dot / (tn * Math.sqrt(c.second.size.toDouble())) + c.first / 400.0)
        }.sortedByDescending { it.second }.map { it.first }
    }

    /** See rust/kiro_core/src/stats.rs for the format. Null when Rust isn't usable. */
    fun stats(today: Long, rows: String): String? = if (available) runCatching { statsSummary(today, rows) }.getOrNull() else null

    @JvmStatic private external fun netEstimate(samples: String): Double

    /** Starting bandwidth (bits/s) from past samples, or null when there's too little data. */
    fun bandwidth(samples: List<Double>): Double? {
        if (samples.size < 3) return null
        if (available) runCatching { netEstimate(samples.joinToString(",")) }.getOrNull()?.takeIf { it > 0 }?.let { return it }
        val v = samples.sorted(); val lo = v.size / 10; val hi = maxOf(v.size - v.size / 5, lo + 1)
        return v.subList(lo, hi).average() * 0.8
    }

    @JvmStatic private external fun waveBars(samples: String, n: Int): String?

    /** Loudness over time as [n] digits 0-9, for drawing a voice-message waveform. */
    fun wave(samples: List<Int>, n: Int): String {
        if (samples.isEmpty()) return "0".repeat(n)
        if (available) runCatching { waveBars(samples.joinToString(","), n) }.getOrNull()?.takeIf { it.length == n }?.let { return it }
        val peaks = (0 until n).map { i -> val a = i * samples.size / n; samples.subList(a, maxOf(a + 1, (i + 1) * samples.size / n).coerceAtMost(samples.size)).maxOrNull() ?: 0 }
        val top = maxOf(1, peaks.max())
        return peaks.joinToString("") { Math.round(Math.sqrt(it.toDouble() / top) * 9).toString() }
    }

    @JvmStatic private external fun balanceLines(text: String, cap: Int): String?

    /** Breaks a subtitle into the fewest balanced lines that each fit [cap] characters (Netflix-style). */
    fun balance(text: String, cap: Int): String {
        if ('\n' in text || text.length <= cap) return text
        if (available) runCatching { balanceLines(text, cap) }.getOrNull()?.let { return it }
        val cut = text.indices.filter { text[it] == ' ' }.minByOrNull { kotlin.math.abs(it - text.length / 2) } ?: return text
        return text.substring(0, cut) + "\n" + text.substring(cut + 1)
    }

    @JvmStatic private external fun planImageNative(w: Int, h: Int, bytes: Long, metered: Boolean): String?

    /** How to shrink a photo before sending: sample, out width, out height, JPEG quality, send-untouched. */
    class ImagePlan(val sample: Int, val width: Int, val height: Int, val quality: Int, val passthrough: Boolean)

    fun planImage(w: Int, h: Int, bytes: Long, metered: Boolean): ImagePlan {
        if (available) runCatching { planImageNative(w, h, bytes, metered) }.getOrNull()?.split(',')?.map { it.toInt() }?.takeIf { it.size == 5 }?.let { return ImagePlan(it[0], it[1], it[2], it[3], it[4] == 1) }
        val maxSide = if (metered) 1024 else 1280
        val big = maxOf(w, h, 1)
        if (big <= maxSide && bytes <= 300_000) return ImagePlan(1, w, h, if (metered) 74 else 82, true)
        val k = minOf(1.0, maxSide.toDouble() / big)
        var sample = 1
        while (big / (sample * 2) >= maxSide) sample *= 2
        return ImagePlan(sample, maxOf(1, Math.round(w * k).toInt()), maxOf(1, Math.round(h * k).toInt()), if (metered) 74 else 82, false)
    }
}
