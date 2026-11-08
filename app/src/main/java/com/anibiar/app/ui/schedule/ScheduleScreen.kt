package com.anibiar.app.ui.schedule

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.anibiar.app.AniBiarApp
import com.anibiar.app.data.LatestEpisode
import com.anibiar.app.data.displayTitle
import com.anibiar.app.data.posterUrl
import com.anibiar.app.ui.home.PosterImage
import com.anibiar.app.ui.theme.Ani
import com.anibiar.app.ui.theme.AniIcons
import com.anibiar.app.ui.theme.GlassBack
import com.anibiar.app.ui.theme.inter
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private fun countdown(ms: Long): String {
    if (ms <= 0) return "Aired"
    val m = ms / 60_000
    val d = m / 1440; val h = m % 1440 / 60; val mm = m % 60
    return when { d > 0 -> "in ${d}d ${h}h"; h > 0 -> "in ${h}h ${mm}m"; else -> "in ${mm}m" }
}

private fun startOfDay(ms: Long): Long = Calendar.getInstance().apply {
    timeInMillis = ms; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** Next 7 days of airing episodes in the viewer's own time zone, with live countdowns and a reminder bell. */
@Composable
fun ScheduleScreen(onBack: () -> Unit, onOpen: (Int) -> Unit, backEnabled: Boolean = true) {
    val context = LocalContext.current
    val app = context.applicationContext as AniBiarApp
    BackHandler(enabled = backEnabled, onBack = onBack)
    var items by remember { mutableStateOf<List<LatestEpisode>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var offline by remember { mutableStateOf(false) }
    val reminders by app.reminders.ids.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var day by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        items = app.repository.cachedSchedule()
        val fresh = runCatching { app.repository.fetchSchedule() }.getOrNull()
        if (fresh != null && fresh.isNotEmpty()) { items = fresh; app.reminders.plan(fresh) } else if (items.isEmpty() || fresh == null) offline = true
        loading = false
    }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }

    val askPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val today = startOfDay(now)
    val days = remember(today) { (0 until 7).map { today + it * 86_400_000L } }
    val dayFmt = remember { SimpleDateFormat("EEE d", Locale.getDefault()) }
    val timeFmt = remember { java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT) }
    val shown = remember(items, day, today) {
        val a = startOfDay(days[day]); val b = a + 86_400_000L
        items.filter { it.airingAt * 1000 in a until b }
    }

    Column(Modifier.fillMaxSize().background(Ani.Onyx).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassBack(onBack)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Airing schedule", style = inter(20f, FontWeight.SemiBold), color = Color.White)
                Text("Shown in your local time", style = inter(12f), color = Ani.Muted)
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(days.size) { i ->
                val sel = i == day
                Box(
                    Modifier.clip(RoundedCornerShape(20.dp)).background(if (sel) Ani.Lavender else Color(0xFF1B1B1F))
                        .clickable { day = i }.padding(horizontal = 16.dp, vertical = 9.dp),
                ) { Text(if (i == 0) "Today" else dayFmt.format(Date(days[i])), style = inter(14f, FontWeight.Medium), color = if (sel) Color.Black else Color.White) }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (offline && items.isNotEmpty()) Text("Offline. Showing the last saved schedule.", style = inter(13f), color = Ani.Muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        when {
            loading && items.isEmpty() -> Text("Loading…", style = inter(15f), color = Ani.Muted, modifier = Modifier.padding(24.dp))
            shown.isEmpty() -> Text(if (items.isEmpty()) "Couldn't load the schedule. Check your connection." else "Nothing airing this day.", style = inter(15f), color = Ani.Muted, modifier = Modifier.padding(24.dp))
            else -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 40.dp)) {
                items(shown, key = { "${it.media.id}-${it.episode}" }) { e ->
                    val m = e.media
                    val on = m.id in reminders
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1B1B1F))
                            .clickable { onOpen(m.id) }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PosterImage(m.posterUrl, m.displayTitle, maxSide = 300, modifier = Modifier.width(54.dp).height(78.dp).clip(RoundedCornerShape(8.dp)).background(Ani.Item))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.displayTitle, style = inter(15f, FontWeight.Medium, lineHeight = 20f), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("Episode ${e.episode}" + (m.episodes?.let { " of $it" } ?: ""), style = inter(13f), color = Ani.Muted)
                            Text(
                                timeFmt.format(Date(e.airingAt * 1000)) + "  ·  " + countdown(e.airingAt * 1000 - now),
                                style = inter(13f, FontWeight.Medium), color = if (e.airingAt * 1000 <= now) Ani.Muted else Ani.Lavender,
                            )
                        }
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).background(if (on) Ani.Lavender else Color(0x22FFFFFF)).clickable {
                                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) askPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                                val nowOn = app.reminders.toggle(m.id)
                                app.reminders.plan(items)
                                Toast.makeText(context, if (nowOn) "We'll notify you when new episodes air" else "Reminder removed", Toast.LENGTH_SHORT).show()
                            },
                            contentAlignment = Alignment.Center,
                        ) { Icon(AniIcons.Bell, if (on) "Reminder on" else "Remind me", tint = if (on) Color.Black else Color.White, modifier = Modifier.size(20.dp)) }
                    }
                }
            }
        }
    }
}
