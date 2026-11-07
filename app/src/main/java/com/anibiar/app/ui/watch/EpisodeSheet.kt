package com.anibiar.app.ui.watch

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.anibiar.app.data.WatchEpisode
import com.anibiar.app.ui.theme.AniIcons
import com.anibiar.app.ui.theme.FeatherIcons
import com.anibiar.app.ui.theme.VidIcons
import com.anibiar.app.ui.theme.inter

val WebAccent = Color(0xFFB7A9FF)
val WebLine = Color(0x2EFFFFFF)
private val Muted = Color(0xFF9B98A0)

private fun fmtDate(iso: String?): String = iso?.take(10)?.let { d ->
    runCatching {
        val p = d.split("-"); val m = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        "${m[p[1].toInt() - 1]} ${p[2].toInt()}, ${p[0]}"
    }.getOrNull()
}.orEmpty()

@Composable
private fun ToolBtn(active: Boolean = false, onClick: () -> Unit, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.size(33.dp).clip(shape).border(1.5.dp, if (active) WebAccent else WebLine, shape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** The web app's episode sheet: handle, title + position, sort / spoiler / layout buttons, rich rows or a number grid. */
@Composable
fun EpisodeSheet(
    title: String,
    episodes: List<WatchEpisode>,
    current: Int,
    watched: Set<Int>,
    fallbackImage: String?,
    subNumbers: Set<Int>,
    dubNumbers: Set<Int>,
    onSelect: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val prefs = LocalContext.current.getSharedPreferences("watch_prefs", Context.MODE_PRIVATE)
    var grid by remember { mutableStateOf(prefs.getString("episode-view", "list") == "grid") }
    var desc by remember { mutableStateOf(prefs.getBoolean("episode-newest-first", false)) }
    var hidden by remember { mutableStateOf(prefs.getBoolean("episode-hide-spoilers", false)) }
    var range by remember { mutableIntStateOf(0) }
    val ordered = remember(episodes, desc) { episodes.distinctBy { it.number }.let { if (desc) it.reversed() else it } }
    val size = 100
    val ranges = remember(ordered) { ordered.chunked(size).mapIndexed { i, c -> Triple("${c.first().number}-${c.last().number}", i * size, i * size + c.size) } }
    LaunchedEffect(current, ordered) { ordered.indexOfFirst { it.number == current }.takeIf { it >= 0 }?.let { range = it / size } }
    val cur = ranges.getOrNull(range.coerceIn(0, (ranges.size - 1).coerceAtLeast(0)))
    // Only a first screenful of the range is built; more are added as the list is scrolled near its end.
    var shown by remember(range, desc) { mutableIntStateOf(24) }
    val visible = if (cur != null) ordered.subList(cur.second, minOf(cur.third, cur.second + shown)) else emptyList()
    val position = episodes.indexOfFirst { it.number == current } + 1
    val shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)

    Column(modifier.clip(shape).background(Color(0xFA1E1E22)).border(1.5.dp, WebLine, shape)) {
        Box(Modifier.fillMaxWidth().height(30.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.width(42.dp).height(4.5.dp).clip(RoundedCornerShape(50)).background(Color(0x47FFFFFF)))
        }
        Row(Modifier.fillMaxWidth().padding(start = 13.dp, end = 13.dp, bottom = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                Text(title.uppercase(), style = inter(13.8f, FontWeight.Bold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Text("  ${if (position > 0) position else "–"} / ${episodes.size}", style = inter(12.5f, FontWeight.Medium), color = Muted, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ToolBtn(desc, { desc = !desc; prefs.edit().putBoolean("episode-newest-first", desc).apply() }) { Icon(FeatherIcons.Repeat, "Reverse order", tint = if (desc) WebAccent else Color.White, modifier = Modifier.size(15.dp).rotate(90f)) }
                ToolBtn(false, { hidden = !hidden; prefs.edit().putBoolean("episode-hide-spoilers", hidden).apply() }) { Icon(if (hidden) FeatherIcons.EyeOff else FeatherIcons.Eye, "Spoilers", tint = Color.White, modifier = Modifier.size(15.dp)) }
                ToolBtn(false, { grid = !grid; prefs.edit().putString("episode-view", if (grid) "grid" else "list").apply() }) { Icon(if (grid) FeatherIcons.List else FeatherIcons.Grid, "Layout", tint = Color.White, modifier = Modifier.size(15.dp)) }
                ToolBtn(false, onClose) { Icon(FeatherIcons.X, "Close", tint = Color.White, modifier = Modifier.size(15.dp)) }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.5.dp).background(WebLine))
        if (ranges.size > 1) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ranges.forEachIndexed { i, r ->
                val on = i == range; val s = RoundedCornerShape(50)
                Text(r.first, style = inter(12f, FontWeight.SemiBold), color = if (on) WebAccent else Color.White,
                    modifier = Modifier.clip(s).border(1.5.dp, if (on) WebAccent else WebLine, s).background(if (on) Color(0x29B7A9FF) else Color.Transparent).clickable { range = i }.padding(horizontal = 12.dp, vertical = 5.dp))
            }
        }
        if (visible.isEmpty()) {
            Text("No episodes available for this selection.", style = inter(14f), color = Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp))
        } else if (grid) {
            val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
            LaunchedEffect(gridState, visible.size) {
                androidx.compose.runtime.snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                    .collect { if (it >= visible.size - 6) shown += 24 }
            }
            LazyVerticalGrid(GridCells.Fixed(4), state = gridState, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 12.dp, bottom = 16.dp + 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visible, key = { it.number }) { e ->
                    val on = e.number == current; val s = RoundedCornerShape(50)
                    Box(
                        Modifier.height(32.dp).clip(s).alpha(if (e.number in watched && !on) 0.55f else 1f)
                            .border(1.5.dp, if (on) Color.Transparent else if (e.filler) Color(0xFFC9A227) else WebLine, s)
                            .background(if (on) WebAccent else Color.Transparent).clickable { onSelect(e.number) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (on) Icon(AniIcons.Play, null, tint = Color(0xFF0B0B10), modifier = Modifier.size(15.dp))
                        else Text("${e.number}", style = inter(13.5f, FontWeight.Medium), color = Color.White)
                    }
                }
            }
        } else {
            val state = rememberLazyListState()
            LaunchedEffect(state, visible.size) {
                androidx.compose.runtime.snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                    .collect { if (it >= visible.size - 5) shown += 24 }
            }
            LaunchedEffect(current, range, desc) {
                val idx = ordered.indexOfFirst { it.number == current } - (cur?.second ?: 0)
                if (idx + 12 > shown) shown = idx + 12
            }
            val hasCurrent = visible.any { it.number == current }
            LaunchedEffect(current, range, desc, hasCurrent) { visible.indexOfFirst { it.number == current }.takeIf { it > 1 }?.let { state.scrollToItem(it - 1) } }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = state, contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(visible, key = { it.number }) { e ->
                    val on = e.number == current; val s = RoundedCornerShape(16.dp)
                    val generic = e.title.isBlank() || Regex("^episode\\s*\\d+$", RegexOption.IGNORE_CASE).matches(e.title.trim())
                    val img = e.image ?: fallbackImage
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 78.dp).height(IntrinsicSize.Min).clip(s)
                            .border(1.5.dp, if (on) WebAccent else WebLine, s)
                            .background(if (on) Color(0x42B7A9FF) else Color(0x0BFFFFFF)).clickable { onSelect(e.number) },
                    ) {
                        Box(Modifier.width(115.dp).fillMaxHeight().background(Color(0xFF2A2A2E))) {
                            if (img != null) AsyncImage(img, null, contentScale = ContentScale.Crop,
                                modifier = Modifier.matchParentSize().alpha(if (e.number in watched && !on) 0.6f else 1f).then(if (hidden) Modifier.blur(14.dp) else Modifier))
                            if (hidden) Box(Modifier.matchParentSize().background(Color(0x8C000000)), contentAlignment = Alignment.Center) {
                                Text("HIDDEN SPOILER", style = inter(9f, FontWeight.ExtraBold), color = Color(0xD9FFFFFF), textAlign = TextAlign.Center, letterSpacing = 1.4.sp)
                            }
                            Text("EP ${e.number}", style = inter(11f, FontWeight.Bold), color = Color.White,
                                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).clip(RoundedCornerShape(50)).background(Color(0xB8080A0A)).padding(horizontal = 9.dp, vertical = 4.dp))
                        }
                        Column(Modifier.weight(1f).padding(start = 10.dp, end = 11.dp, top = 7.dp, bottom = 6.dp), verticalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(if (generic) "Episode ${e.number}" else e.title, style = inter(13.5f, FontWeight.Medium, lineHeight = 17f), color = if (on) WebAccent else Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val text = e.description?.trim().orEmpty()
                                if (text.isNotEmpty()) Text(if (hidden) "Hidden description" else text, style = inter(11f, lineHeight = 13.5f), color = Muted, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                            }
                            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(VidIcons.ClosedCaptions, null, tint = Muted, modifier = Modifier.size(15.dp).alpha(if (e.number in subNumbers) 1f else 0.28f))
                                Icon(AniIcons.Mic, null, tint = Muted, modifier = Modifier.size(14.dp).alpha(if (e.number in dubNumbers) 1f else 0.28f))
                                Spacer(Modifier.weight(1f))
                                Text(fmtDate(e.airDate), style = inter(11f), color = Muted, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
