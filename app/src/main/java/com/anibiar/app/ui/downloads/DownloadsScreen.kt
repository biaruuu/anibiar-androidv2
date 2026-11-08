package com.anibiar.app.ui.downloads

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.exoplayer.offline.Download
import com.anibiar.app.data.DlRow
import com.anibiar.app.data.Offline
import com.anibiar.app.data.StreamSubtitle
import com.anibiar.app.ui.home.PosterImage
import com.anibiar.app.ui.theme.Ani
import com.anibiar.app.ui.theme.GlassBack
import com.anibiar.app.ui.theme.inter
import com.anibiar.app.ui.watch.KiroPlayer
import kotlinx.coroutines.delay

/** Saved episodes: play them without a connection, pause/resume or remove them. */
@Composable
fun DownloadsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var rows by remember { mutableStateOf<List<DlRow>>(emptyList()) }
    var playing by remember { mutableStateOf<DlRow?>(null) }
    var fullscreen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (true) { rows = runCatching { Offline.rows(context) }.getOrDefault(rows); delay(1000) } }

    val activity = context as? Activity
    val view = LocalView.current
    DisposableEffect(fullscreen) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        if (fullscreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED; controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    BackHandler { if (fullscreen) fullscreen = false else if (playing != null) playing = null else onBack() }

    Column(Modifier.fillMaxSize().background(if (playing != null) Color.Black else Ani.Onyx)) {
        val p = playing
        if (p != null) {
            if (!fullscreen) Row(Modifier.fillMaxWidth().statusBarsPadding().height(52.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassBack { playing = null }
                Spacer(Modifier.width(12.dp))
                Text("${p.item.title} · Episode ${p.item.episode}", style = inter(16f, FontWeight.SemiBold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(if (fullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                KiroPlayer(
                    src = p.item.src, format = p.item.format, title = "${p.item.title} · Episode ${p.item.episode}", poster = p.item.image,
                    subtitles = p.item.subs.map { StreamSubtitle(it.label, it.srclang, "file://" + it.path, it.default) },
                    intro = null, outro = null, skipLength = null,
                    autoplay = true, autoNext = false, autoSkip = false,
                    onAutoplay = {}, onAutoNext = {}, onAutoSkip = {},
                    startAt = null, onTime = { _, _ -> }, onEnded = {}, onFail = {},
                    fullscreen = fullscreen, onFullscreen = { fullscreen = it },
                )
            }
        } else {
            Row(Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassBack(onBack)
                Spacer(Modifier.width(12.dp))
                Text("Downloads", style = inter(20f, FontWeight.SemiBold), color = Color.White)
            }
            if (rows.isEmpty()) Text("Nothing saved yet. Open an episode and tap Download.", style = inter(15f), color = Ani.Muted, modifier = Modifier.padding(24.dp))
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp)) {
                items(rows, key = { it.item.key }) { r ->
                    val done = r.state == Download.STATE_COMPLETED
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1B1B1F))
                            .clickable(enabled = done) { playing = r }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PosterImage(r.item.image, r.item.title, maxSide = 400, modifier = Modifier.width(110.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(Ani.Item))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.item.title, style = inter(15f, FontWeight.Medium, lineHeight = 20f), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("Episode ${r.item.episode}", style = inter(13f), color = Ani.Muted)
                            val mb = r.bytes / 1_048_576
                            Text(
                                when (r.state) {
                                    Download.STATE_COMPLETED -> "Ready offline · $mb MB"
                                    Download.STATE_DOWNLOADING -> "Downloading ${(r.percent * 100).toInt()}% · $mb MB"
                                    Download.STATE_QUEUED, Download.STATE_RESTARTING -> "Queued"
                                    Download.STATE_STOPPED -> "Paused ${(r.percent * 100).toInt()}%"
                                    else -> "Failed"
                                },
                                style = inter(13f, FontWeight.Medium), color = if (done) Ani.Lavender else Ani.Muted,
                            )
                        }
                        if (r.state == Download.STATE_DOWNLOADING || r.state == Download.STATE_STOPPED)
                            Text(if (r.state == Download.STATE_STOPPED) "Resume" else "Pause", style = inter(13f), color = Color.White,
                                modifier = Modifier.clickable { Offline.pause(context, r.item.key, r.state == Download.STATE_STOPPED) }.padding(8.dp))
                        Text("Remove", style = inter(13f), color = Color(0xFFFF6B6B), modifier = Modifier.clickable { Offline.remove(context, r.item.key) }.padding(8.dp))
                    }
                }
            }
        }
    }
}
