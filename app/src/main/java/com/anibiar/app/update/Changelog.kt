package com.anibiar.app.update

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.anibiar.app.ui.theme.Ani
import com.anibiar.app.ui.theme.inter

/** One changelog entry. Ids go up with every update, so the app knows which ones you haven't seen. */
data class ChangeEntry(val id: Int, val title: String, val date: String, val items: List<String>)

object Changelog {
    fun parse(text: String): List<ChangeEntry> {
        val out = ArrayList<ChangeEntry>()
        var head: List<String>? = null
        var items = ArrayList<String>()
        fun flush() {
            val h = head ?: return
            val id = h.getOrNull(0)?.toIntOrNull() ?: return
            out += ChangeEntry(id, h.getOrElse(1) { "Update" }, h.getOrElse(2) { "" }, items)
        }
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.startsWith("## ")) { flush(); head = line.removePrefix("## ").split("|").map { it.trim() }; items = ArrayList() }
            else if (line.startsWith("- ")) items.add(line.removePrefix("- "))
        }
        flush()
        return out.sortedByDescending { it.id }
    }

    /** The changelog that shipped inside this APK. */
    fun bundled(context: Context): List<ChangeEntry> =
        runCatching { parse(context.assets.open("changelog.md").bufferedReader().use { it.readText() }) }.getOrDefault(emptyList())

    private const val PREFS = "changelog"
    fun lastSeen(context: Context): Int = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("seen", 0)
    fun markSeen(context: Context, id: Int) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("seen", id).apply()
}

@Composable
fun ChangeList(entries: List<ChangeEntry>) {
    Column(Modifier.fillMaxWidth()) {
        entries.forEachIndexed { i, e ->
            if (i > 0) Spacer(Modifier.height(14.dp))
            Text(e.title, style = inter(15f, FontWeight.Bold), color = Color.White)
            if (e.date.isNotBlank()) Text(e.date, style = inter(11.5f), color = Ani.Muted)
            Spacer(Modifier.height(4.dp))
            e.items.forEach { Text("•  $it", style = inter(13.5f, lineHeight = 19f), color = Ani.Soft, modifier = Modifier.padding(top = 3.dp)) }
        }
    }
}

/** "What's new": shown after an update, and from Settings. */
@Composable
fun WhatsNewDialog(entries: List<ChangeEntry>, title: String = "What's new", onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Ani.Panel).padding(22.dp)) {
            Text(title, style = inter(19f, FontWeight.Bold), color = Color.White)
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) { ChangeList(entries) }
            Spacer(Modifier.height(16.dp))
            Text(
                "Got it", style = inter(14.5f, FontWeight.Bold), color = Color.Black,
                modifier = Modifier.align(androidx.compose.ui.Alignment.End).clip(RoundedCornerShape(50)).background(Ani.Lime).clickable(onClick = onClose).padding(horizontal = 24.dp, vertical = 11.dp),
            )
        }
    }
}
