package com.anibiar.app.ui.tracker

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anibiar.app.AniBiarApp
import com.anibiar.app.data.displayTitle
import com.anibiar.app.data.posterUrl
import com.anibiar.app.ui.home.PosterImage
import com.anibiar.app.ui.theme.Ani
import com.anibiar.app.ui.theme.GlassBack
import com.anibiar.app.ui.theme.inter

private class TRow(val id: Int, val title: String, val image: String?, val sub: String, val behind: Int = 0, val idle: Boolean = false)

private const val IDLE_MS = 30L * 86_400_000L

/** Everything you follow, sorted by what needs attention: airing shows you're behind on, unfinished, idle, and plan-to-watch. */
@Composable
fun TrackerScreen(onBack: () -> Unit, onOpen: (Int) -> Unit, backEnabled: Boolean = true) {
    BackHandler(enabled = backEnabled, onBack = onBack)
    val app = LocalContext.current.applicationContext as AniBiarApp
    val version by app.history.version.collectAsStateWithLifecycle()
    val saved by app.watchlist.ids.collectAsStateWithLifecycle()
    val now = remember { System.currentTimeMillis() }

    val sections = remember(version, saved) {
        val hist = app.history.load()
        val behind = ArrayList<TRow>(); val keep = ArrayList<TRow>(); val idle = ArrayList<TRow>()
        for (h in hist) {
            val m = app.catalog.get(h.mediaId)
            val watched = if (h.progress >= 0.9f) h.episode else h.episode - 1
            val aired = m?.nextAiringEpisode?.episode?.minus(1) ?: m?.episodes
            val lag = if (aired != null) (aired - watched).coerceAtLeast(0) else 0
            val total = m?.episodes
            val finished = total != null && watched >= total
            val row = TRow(h.mediaId, h.title, h.image, "Episode ${h.episode}" + (total?.let { " of $it" } ?: ""), lag, h.at in 1 until now - IDLE_MS)
            when {
                finished -> {}
                row.idle -> idle += row
                m?.status == "RELEASING" && lag > 0 -> behind += row.copy2(sub = "$lag new episode${if (lag > 1) "s" else ""} to watch · ${row.sub}")
                else -> keep += row
            }
        }
        val plan = saved.filter { id -> hist.none { it.mediaId == id } }.map { id ->
            val m = app.catalog.get(id)
            TRow(id, m?.displayTitle ?: "Anime $id", m?.posterUrl, "In your list")
        }
        listOf("Airing, you're behind" to behind.sortedByDescending { it.behind }, "Keep watching" to keep, "Haven't watched in 30+ days" to idle, "Plan to watch" to plan)
    }

    Column(Modifier.fillMaxSize().background(Ani.Onyx).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassBack(onClick = onBack)
            Spacer(Modifier.width(12.dp))
            Text("My shows", style = inter(20f, FontWeight.SemiBold), color = Color.White)
        }
        if (sections.all { it.second.isEmpty() }) Text("Watch something or save an anime and it shows up here.", style = inter(15f), color = Ani.Muted, modifier = Modifier.padding(24.dp))
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp)) {
            sections.forEach { (title, rows) ->
                if (rows.isEmpty()) return@forEach
                item(key = "h-$title") { Text(title, style = inter(16f, FontWeight.SemiBold), color = Color.White, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp)) }
                rows.forEach { r ->
                    item(key = "$title-${r.id}") {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1B1B1F)).clickable { onOpen(r.id) }.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PosterImage(r.image, r.title, maxSide = 300, modifier = Modifier.width(48.dp).height(68.dp).clip(RoundedCornerShape(8.dp)).background(Ani.Item))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.title, style = inter(15f, FontWeight.Medium, lineHeight = 20f), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(r.sub, style = inter(13f), color = if (r.behind > 0) Ani.Lavender else Ani.Muted, maxLines = 2)
                            }
                            if (title.startsWith("Haven't")) {
                                Text("Drop", style = inter(13f), color = Color(0xFFFF8A80), modifier = Modifier.clickable { app.history.remove(r.id) }.padding(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun TRow.copy2(sub: String) = TRow(id, title, image, sub, behind, idle)
