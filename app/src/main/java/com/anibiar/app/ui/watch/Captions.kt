package com.anibiar.app.ui.watch

/** One caption line, in seconds. */
data class Cue(val start: Double, val end: Double, val text: String, val italic: Boolean = false)

/** Parses WebVTT, SRT and ASS/SSA subtitle files (the formats the AniBiar TV servers hand out). */
object Captions {
    fun parse(raw: String): List<Cue> {
        com.anibiar.app.nativecore.Native.captions(raw)?.let { packed ->
            val out = ArrayList<Cue>()
            if (packed.isNotEmpty()) for (rec in packed.split('\u001E')) {
                val f = rec.split('\u001F', limit = 4)
                if (f.size == 4) out += Cue(f[0].toDouble(), f[1].toDouble(), f[3], f[2] == "1")
            }
            return out
        }
        return parseKotlin(raw)
    }

    /** The original pure-Kotlin parser, kept as a fallback when the Rust library isn't available. */
    internal fun parseKotlin(raw: String): List<Cue> {
        val text = raw.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n')
        val cues = if (text.contains("[Events]", ignoreCase = true)) ass(text) else vttOrSrt(text)
        return cues.filter { it.text.isNotBlank() }.sortedBy { it.start }
    }

    private fun clean(s: String) = s
        .replace(Regex("<[^>]*>"), "")
        .replace(Regex("\\{[^}]*\\}"), "")
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")
        .replace("\\N", "\n").replace("\\n", "\n").replace("\\h", " ")
        .trim()

    private val arrow = Regex("""(\d+:)?(\d+):(\d+)[.,](\d+)\s*-->\s*(\d+:)?(\d+):(\d+)[.,](\d+)""")

    private fun time(h: String?, m: String, s: String, f: String): Double =
        (h?.trimEnd(':')?.toIntOrNull() ?: 0) * 3600.0 + m.toInt() * 60.0 + s.toInt() + ("0.$f").toDouble()

    private fun vttOrSrt(text: String): List<Cue> {
        val out = ArrayList<Cue>()
        for (block in text.split(Regex("\n\\s*\n"))) {
            val lines = block.split('\n')
            val i = lines.indexOfFirst { it.contains("-->") }
            if (i < 0) continue
            val m = arrow.find(lines[i]) ?: continue
            val g = m.groupValues
            val rawBody = lines.drop(i + 1).joinToString("\n")
            out += Cue(time(g[1].ifEmpty { null }, g[2], g[3], g[4]), time(g[5].ifEmpty { null }, g[6], g[7], g[8]), clean(rawBody), Regex("<i[ >]").containsMatchIn(rawBody))
        }
        return out
    }

    private fun assTime(s: String): Double {
        val p = s.trim().split(':', '.')
        if (p.size < 4) return 0.0
        return p[0].toInt() * 3600.0 + p[1].toInt() * 60.0 + p[2].toInt() + ("0." + p[3]).toDouble()
    }

    private fun ass(text: String): List<Cue> {
        val out = ArrayList<Cue>()
        var format: List<String> = emptyList()
        var inEvents = false
        for (line in text.split('\n')) {
            val t = line.trim()
            if (t.startsWith("[")) { inEvents = t.equals("[Events]", ignoreCase = true); continue }
            if (!inEvents) continue
            if (t.startsWith("Format:", ignoreCase = true)) format = t.substringAfter(':').split(',').map { it.trim().lowercase() }
            else if (t.startsWith("Dialogue:", ignoreCase = true) && format.isNotEmpty()) {
                val parts = t.substringAfter(':').trimStart().split(',', limit = format.size)
                val s = format.indexOf("start"); val e = format.indexOf("end"); val tx = format.indexOf("text")
                if (s < 0 || e < 0 || tx < 0 || parts.size <= tx) continue
                runCatching { out += Cue(assTime(parts[s]), assTime(parts[e]), clean(parts[tx]), parts[tx].contains("\\i1")) }
            }
        }
        return out
    }

    /** The caption to show now, and whether it is an italic line (thoughts, narration). */
    fun activeLine(cues: List<Cue>, t: Double): Pair<String, Boolean>? {
        if (cues.isEmpty()) return null
        val now = cues.filter { t >= it.start && t < it.end }
        if (now.isEmpty()) return null
        return now.joinToString("\n") { it.text }.take(400) to now.all { it.italic }
    }

    fun active(cues: List<Cue>, t: Double): String? {
        if (cues.isEmpty()) return null
        val now = cues.filter { t >= it.start && t < it.end }
        return if (now.isEmpty()) null else now.joinToString("\n") { it.text }.take(400)
    }
}
