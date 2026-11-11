package com.anibiar.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anibiar.app.data.Airing
import com.anibiar.app.data.AiringSchedule
import com.anibiar.app.ui.theme.Ani
import com.anibiar.app.ui.theme.inter
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

/** "Airing today": today's popular releases in air-time order, with a live countdown to each one. */
@Composable
fun AiringToday(onOpen: (Int) -> Unit) {
    val list by produceState<List<Airing>>(emptyList()) { value = runCatching { AiringSchedule.today() }.getOrDefault(emptyList()) }
    if (list.isEmpty()) return
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    val clock = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }

    val cols = if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 840) 5 else 3
    val perPage = cols * 3
    var dub by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(0) }
    val pageCount = ((list.size + perPage - 1) / perPage).coerceAtLeast(1)
    val cur = page.coerceIn(0, pageCount - 1)
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        LatestHeader(
            title = "Airing today",
            dubMode = dub,
            onModeChange = { dub = it },
            page = cur,
            pageCount = pageCount,
            onPrev = { page = (cur - 1).coerceAtLeast(0) },
            onNext = { page = (cur + 1).coerceAtMost(pageCount - 1) },
        )
        Spacer(Modifier.height(14.dp))
        if (dub) {
            Text("Dubbed releases aren't available yet.", style = inter(15f), color = Ani.Muted, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 32.dp))
        } else Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            list.drop(cur * perPage).take(perPage).chunked(cols).forEach { rowItems ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                    rowItems.forEach { a ->
                        val left = a.at - now
                        val done = left <= 0
                        val soon = !done && left < 3_600_000
                        Column(Modifier.weight(1f).clickable { onOpen(a.mediaId) }) {
                            Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(16.dp)).background(Ani.Item)) {
                                PosterImage(url = a.cover, contentDescription = a.title, maxSide = 400, modifier = Modifier.fillMaxSize())
                                Box(Modifier.align(androidx.compose.ui.Alignment.BottomCenter).fillMaxWidth().height(72.dp).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))))
                                Row(Modifier.align(androidx.compose.ui.Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 8.dp, vertical = 9.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Text("EP ${a.episode}", style = inter(11f, FontWeight.SemiBold), color = Color.White, maxLines = 1, softWrap = false)
                                    Text(if (done) "Aired" else "in ${countdown(left)}", style = inter(10.5f, FontWeight.SemiBold), color = if (soon) Ani.Lavender else Color.White.copy(alpha = 0.92f), maxLines = 1, softWrap = false)
                                }
                            }
                            Spacer(Modifier.height(7.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                InfoBadge(text = clock.format(Date(a.at)), textSize = 9.5f, hPad = 5.dp, vPad = 4.dp, background = Ani.ButtonDark, textColor = Ani.Soft)
                                InfoBadge(text = if (done) "Out" else "Soon", textSize = 9.5f, hPad = 5.dp, vPad = 4.dp, background = if (done) Ani.ButtonDark else Ani.Lavender.copy(alpha = 0.22f), textColor = if (done) Ani.Soft else Ani.Lavender)
                            }
                            Spacer(Modifier.height(7.dp))
                            Text(a.title, style = inter(13.5f, FontWeight.Medium, lineHeight = 18f), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    repeat(cols - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

private fun countdown(ms: Long): String {
    val m = (ms / 60_000).coerceAtLeast(1)
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
}
