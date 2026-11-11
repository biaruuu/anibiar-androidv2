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

    val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 840
    val cardW = if (wide) 168.dp else 132.dp
    Column(Modifier.fillMaxWidth().padding(top = 28.dp)) {
        Text("Airing today", style = inter(20f, FontWeight.SemiBold), maxLines = 1, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        Spacer(Modifier.height(14.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list, key = { "${it.mediaId}-${it.episode}" }) { a ->
                val left = a.at - now
                val done = left <= 0
                Column(Modifier.width(cardW).clickable { onOpen(a.mediaId) }) {
                    Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(16.dp)).background(Ani.Item)) {
                        PosterImage(url = a.cover, contentDescription = a.title, maxSide = 400, modifier = Modifier.fillMaxSize())
                        Box(Modifier.align(androidx.compose.ui.Alignment.BottomCenter).fillMaxWidth().height(72.dp).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))))
                        Row(Modifier.align(androidx.compose.ui.Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 8.dp, vertical = 9.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("EP ${a.episode}", style = inter(11f, FontWeight.SemiBold), color = Color.White, maxLines = 1, softWrap = false)
                            Text(if (done) "Aired" else "in ${countdown(left)}", style = inter(11f, FontWeight.SemiBold), color = if (done) Color.White.copy(alpha = 0.8f) else Ani.Lavender, maxLines = 1, softWrap = false)
                        }
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(clock.format(Date(a.at)), style = inter(12f), color = Ani.Muted, maxLines = 1)
                    Spacer(Modifier.height(4.dp))
                    Text(a.title, style = inter(14f, FontWeight.Medium, lineHeight = 18f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private fun countdown(ms: Long): String {
    val m = (ms / 60_000).coerceAtLeast(1)
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
}
