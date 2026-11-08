package com.anibiar.app.ui.watch

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.Download
import com.anibiar.app.data.Offline
import com.anibiar.app.data.StreamSource
import com.anibiar.app.ui.theme.GlassPill
import com.anibiar.app.ui.theme.inter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** "Download" pill for the episode being watched: saves the playing stream (and its subtitles) for offline viewing. */
@Composable
fun DownloadButton(mediaId: Int, title: String, image: String?, episode: Int, audio: String, stream: StreamSource?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val key = Offline.key(mediaId, episode, audio)
    var label by remember(key) { mutableStateOf("Download") }
    var busy by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) {
        while (true) {
            val row = runCatching { Offline.rows(context).firstOrNull { it.item.key == key } }.getOrNull()
            label = when (row?.state) {
                null -> "Download"
                Download.STATE_COMPLETED -> "Saved"
                Download.STATE_DOWNLOADING -> "${(row.percent * 100).toInt()}%"
                Download.STATE_QUEUED, Download.STATE_RESTARTING -> "Queued"
                Download.STATE_STOPPED -> "Paused"
                else -> "Retry"
            }
            delay(1500)
        }
    }
    GlassPill(Modifier.clickable(enabled = stream != null && !busy) {
        val s = stream ?: return@clickable
        if (label == "Saved" || label == "Paused" || label.endsWith("%") || label == "Queued") {
            Toast.makeText(context, "Manage saved episodes in Downloads on Home", Toast.LENGTH_SHORT).show(); return@clickable
        }
        if (s.kind == "hard" || s.format != "hls" && s.format != "file") return@clickable
        busy = true
        Toast.makeText(context, "Downloading episode $episode", Toast.LENGTH_SHORT).show()
        scope.launch {
            runCatching { Offline.enqueue(context, key, mediaId, title, image, episode, s.src, s.format, s.subtitles) }
                .onFailure { Toast.makeText(context, "Couldn't start the download", Toast.LENGTH_SHORT).show() }
            busy = false
        }
    }) {
        Text(label, style = inter(13f, FontWeight.Medium), color = Color.White, modifier = Modifier.padding(horizontal = 12.dp))
    }
}
