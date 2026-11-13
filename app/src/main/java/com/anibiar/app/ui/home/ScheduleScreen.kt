package com.anibiar.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anibiar.app.AniBiarApp
import com.anibiar.app.data.LatestEpisode
import com.anibiar.app.data.displayTitle
import com.anibiar.app.data.posterUrl
import com.anibiar.app.ui.theme.Ani
import com.anibiar.app.ui.theme.SkeletonBox
import com.anibiar.app.ui.theme.inter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Airing schedule for the next seven days: pick a day, see what comes out and when (your local time). */
@Composable
fun ScheduleScreen(onOpen: (Int) -> Unit) {
    val app = LocalContext.current.applicationContext as AniBiarApp
    var all by remember { mutableStateOf<List<LatestEpisode>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(reload) {
        failed = false
        all = runCatching { app.repository.fetchSchedule() }.onFailure { failed = true }.getOrNull()
    }
    val days = remember {
        List(7) { i -> Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, i); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis }
    }
    var day by remember { mutableStateOf(0) }
    val dayFmt = remember { SimpleDateFormat("EEE", Locale.getDefault()) }
    val numFmt = remember { SimpleDateFormat("d", Locale.getDefault()) }
    val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val rows = remember(all, day) {
        val from = days[day]; val to = from + 86_400_000L
        all.orEmpty().filter { it.airingAt * 1000 in from until to }.sortedBy { it.airingAt }
    }
    Column(Modifier.fillMaxSize().background(Ani.Onyx).statusBarsPadding()) {
        Text("Schedule", style = inter(26f, FontWeight.ExtraBold), color = Color.White, modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 12.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(days.size) { i ->
                val on = i == day
                Column(
                    Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) Color.White else Ani.Panel).clickable { day = i }.padding(horizontal = 16.dp, vertical = 9.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(if (i == 0) "Today" else dayFmt.format(Date(days[i])), style = inter(11.5f, FontWeight.SemiBold), color = if (on) Color.Black else Ani.Muted)
                    Text(numFmt.format(Date(days[i])), style = inter(17f, FontWeight.Bold), color = if (on) Color.Black else Color.White)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        when {
            all == null && !failed -> Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { repeat(6) { SkeletonBox(Modifier.fillMaxWidth().height(84.dp), 14) } }
            all == null -> ErrorState("Couldn't load the schedule.", { reload++ }, Modifier.fillMaxWidth())
            rows.isEmpty() -> Text("Nothing airs on this day.", style = inter(14f), color = Ani.Muted, modifier = Modifier.padding(20.dp))
            else -> LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 90.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(rows, key = { "${it.media.id}-${it.episode}" }) { e ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Ani.Panel).clickable { onOpen(e.media.id) }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PosterImage(e.media.posterUrl, e.media.displayTitle, Modifier.width(52.dp).height(72.dp).clip(RoundedCornerShape(10.dp)).background(Ani.Item), maxSide = 240)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.media.displayTitle, style = inter(14.5f, FontWeight.SemiBold), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("Episode ${e.episode}", style = inter(12f), color = Ani.Muted, modifier = Modifier.padding(top = 3.dp))
                        }
                        Text(timeFmt.format(Date(e.airingAt * 1000)), style = inter(12.5f, FontWeight.SemiBold), color = Color.White, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Ani.Chip).padding(horizontal = 9.dp, vertical = 5.dp))
                    }
                }
            }
        }
    }
}
