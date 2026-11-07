package com.anibiar.app.ui.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anibiar.app.AniBiarApp
import com.anibiar.app.data.Media
import com.anibiar.app.data.displayTitle
import com.anibiar.app.data.posterUrl
import com.anibiar.app.ui.theme.Ani
import com.anibiar.app.ui.theme.inter
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The host picks another anime for the whole room. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimePickerSheet(onPick: (Media) -> Unit, onDismiss: () -> Unit, isHost: Boolean = true) {
    val app = LocalContext.current.applicationContext as AniBiarApp
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Media>>(emptyList()) }
    var busy by remember { mutableStateOf(true) }
    LaunchedEffect(q) {
        busy = true
        delay(if (q.isEmpty()) 0 else 350)
        val vars = buildJsonObject {
            put("page", 1); put("perPage", 20)
            if (q.isNotBlank()) put("search", q.trim())
            put("sort", JsonArray(listOf(JsonPrimitive(if (q.isBlank()) "TRENDING_DESC" else "SEARCH_MATCH"))))
            put("formats", JsonArray(listOf("TV", "TV_SHORT", "MOVIE", "OVA", "ONA", "SPECIAL").map { JsonPrimitive(it) }))
            put("adult", false)
        }
        results = runCatching { app.repository.search(vars).media }.getOrDefault(emptyList())
        busy = false
    }
    FullScreenPanel(if (isHost) "Change anime" else "Request an anime", onDismiss) {
        Column(Modifier.fillMaxWidth().weight(1f)) {
            Box(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(16.dp)).background(Ani.Item).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                if (q.isEmpty()) Text("Search anime…", style = inter(15f), color = Ani.Muted)
                BasicTextField(q, { q = it }, singleLine = true, textStyle = inter(15f).copy(color = Color.White), cursorBrush = SolidColor(Ani.Lime), modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(8.dp))
            if (busy && results.isEmpty()) Text("Searching…", style = inter(13.5f), color = Ani.Muted, modifier = Modifier.padding(12.dp))
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(results, key = { it.id }) { m ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(m) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(46.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).background(Ani.Item)) {
                            m.posterUrl?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth()) }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.displayTitle, style = inter(15f, FontWeight.SemiBold), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull(m.format, m.seasonYear?.toString(), m.episodes?.let { "$it eps" }).joinToString(" · "), style = inter(12.5f), color = Ani.Muted)
                        }
                    }
                }
            }
        }
    }
}
