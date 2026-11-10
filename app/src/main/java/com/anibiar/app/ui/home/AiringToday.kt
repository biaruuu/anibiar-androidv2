package com.anibiar.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.anibiar.app.data.Schedule
import com.anibiar.app.ui.theme.Ani
import com.anibiar.app.ui.theme.inter
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

/** "Airing today": today's popular releases in air-time order, with a live countdown to each one. */
@Composable
fun AiringToday(onOpen: (Int) -> Unit) {
    val list by produceState<List<Airing>>(emptyList()) { value = runCatching { Schedule.today() }.getOrDefault(emptyList()) }
    if (list.isEmpty()) return
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    val clock = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }

    Column(Modifier.fillMaxWidth().padding(top = 28.dp)) {
        Text("Airing today", style = inter(20f, FontWeight.SemiBold), modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(14.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list, key = { "${it.mediaId}-${it.episode}" }) { a ->
                val left = a.at - now
                val done = left <= 0
                val shape = RoundedCornerShape(16.dp)
                Row(
                    Modifier.width(250.dp).clip(shape).background(Ani.Panel).border(1.dp, Color(0x1FFFFFFF), shape).clickable { onOpen(a.mediaId) }.padding(10.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    PosterImage(url = a.cover, contentDescription = a.title, maxSide = 300, modifier = Modifier.width(58.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(Ani.Item))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.title, style = inter(14f, FontWeight.SemiBold, lineHeight = 18f), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("Episode ${a.episode}", style = inter(12.5f), color = Ani.Muted, modifier = Modifier.padding(top = 2.dp))
                        Text(
                            if (done) "Aired ${clock.format(Date(a.at))}" else "${clock.format(Date(a.at))} · in ${countdown(left)}",
                            style = inter(12.5f, FontWeight.SemiBold), color = if (done) Ani.Muted else Ani.Lavender, modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun countdown(ms: Long): String {
    val m = (ms / 60_000).coerceAtLeast(1)
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
}
