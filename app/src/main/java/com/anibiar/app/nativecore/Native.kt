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

    fun versionOrNull(): String? = if (available) runCatching { version() }.getOrNull() else null

    /** Records split by U+001E, fields by U+001F: start, end, italic, text. Null when Rust isn't usable. */
    fun captions(raw: String): String? = if (available) runCatching { parseCaptions(raw) }.getOrNull() else null

    /** Indices of [items] matching [query], best first. Null when Rust isn't usable. */
    fun rank(query: String, items: List<String>): IntArray? =
        if (available) runCatching { fuzzyRank(query, items.joinToString("\n") { it.replace('\n', ' ') }) }.getOrNull() else null
}
