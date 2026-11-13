package com.anibiar.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.border
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
    val glass = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.02f)))
    Box(Modifier.fillMaxSize().background(Ani.Onyx)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 112.dp, bottom = 96.dp),
        ) {
            when {
                all == null && !failed -> items(4) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.03f)).border(1.dp, glass, RoundedCornerShape(16.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(48.dp).height(68.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.05f)))
                        Spacer(Modifier.width(12.dp))
                        Column { Box(Modifier.width(140.dp).height(16.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.08f))); Spacer(Modifier.height(8.dp)); Box(Modifier.width(90.dp).height(12.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.04f))) }
                    }
                }
                all == null -> item { ErrorState("Couldn't load the schedule.", { reload++ }, Modifier.fillMaxWidth().padding(top = 80.dp)) }
                rows.isEmpty() -> item {
                    Column(Modifier.fillMaxWidth().padding(top = 120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No schedule on this date", style = inter(14f, FontWeight.Medium), color = Color.Gray)
                        Text(SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(days[day])), style = inter(12f), color = Color.DarkGray, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                else -> items(rows, key = { "${it.media.id}-${it.episode}" }) { e ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).clip(RoundedCornerShape(16.dp)).clickable { onOpen(e.media.id) }
                            .background(Color.White.copy(alpha = 0.03f)).border(1.dp, glass, RoundedCornerShape(16.dp)).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PosterImage(e.media.posterUrl, e.media.displayTitle, Modifier.width(48.dp).height(68.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.05f)), maxSide = 240)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(e.media.displayTitle, style = inter(15f, FontWeight.Bold, lineHeight = 19f), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(8.dp))
                                Text(timeFmt.format(Date(e.airingAt * 1000)), style = inter(10f, FontWeight.Bold), color = Color.White,
                                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.08f)).border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.06f)).border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(5.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                                    Spacer(Modifier.width(6.dp))
                                    Text("EPISODE ${e.episode}", style = inter(10f, FontWeight.Bold), color = Color.White.copy(alpha = 0.9f))
                                }
                                Text(e.media.format?.replace('_', ' ') ?: "TV", style = inter(10f, FontWeight.SemiBold), color = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.04f)).border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                }
            }
        }
        // Header floats over the list: dark translucent bar with the title and a glass pill of five days.
        Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.82f)).statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp)) {
            Text("Airing Schedule", style = inter(22f, FontWeight.Bold), color = Color.White, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.03f)).border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.03f))), RoundedCornerShape(16.dp)).padding(3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                days.take(5).forEachIndexed { i, d ->
                    val on = i == day
                    Box(Modifier.weight(1f).padding(2.dp).height(54.dp).clip(RoundedCornerShape(14.dp)).background(if (on) Color.White else Color.Transparent).clickable { day = i }, contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(dayFmt.format(Date(d)).uppercase(), style = inter(9f, FontWeight.Bold), color = if (on) Color.Black else Color.White.copy(alpha = 0.5f))
                            Spacer(Modifier.height(2.dp))
                            Text(numFmt.format(Date(d)), style = inter(14f, FontWeight.Bold), color = if (on) Color.Black else Color.White)
                        }
                    }
                }
            }
        }
    }
}
