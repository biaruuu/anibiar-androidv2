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

    fun versionOrNull(): String? = if (available) runCatching { version() }.getOrNull() else null

    /** Records split by U+001E, fields by U+001F: start, end, italic, text. Null when Rust isn't usable. */
    fun captions(raw: String): String? = if (available) runCatching { parseCaptions(raw) }.getOrNull() else null

    /** Indices of [items] matching [query], best first. Null when Rust isn't usable. */
    fun rank(query: String, items: List<String>): IntArray? =
        if (available) runCatching { fuzzyRank(query, items.joinToString("\n") { it.replace('\n', ' ') }) }.getOrNull() else null
}
