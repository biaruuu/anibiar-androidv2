package com.anibiar.app.ui.inbox

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anibiar.app.nativecore.Native
import com.anibiar.app.social.DmMessage
import com.anibiar.app.ui.theme.inter
import kotlinx.coroutines.delay
import java.io.File

class VoiceResult(val bytes: ByteArray, val seconds: Int, val bars: String)

/** Records AAC voice notes to the cache folder, sampling loudness for the waveform. */
class VoiceRecorder(private val context: Context) {
    private var rec: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L
    private val samples = ArrayList<Int>()

    fun start(): Boolean = runCatching {
        val f = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioChannels(1); r.setAudioSamplingRate(22_050); r.setAudioEncodingBitRate(48_000)
        r.setMaxDuration(180_000)
        r.setOutputFile(f.absolutePath)
        r.prepare(); r.start()
        rec = r; file = f; startedAt = System.currentTimeMillis(); samples.clear()
        true
    }.getOrDefault(false)

    val seconds: Int get() = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
    fun sample() { rec?.let { r -> runCatching { samples += r.maxAmplitude } } }

    fun finish(): VoiceResult? {
        val r = rec ?: return null; val f = file ?: return null
        rec = null
        val secs = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
        val ok = runCatching { r.stop() }.isSuccess
        runCatching { r.release() }
        val bytes = if (ok && secs >= 1) runCatching { f.readBytes() }.getOrNull() else null
        f.delete()
        return bytes?.let { VoiceResult(it, secs, Native.wave(samples, 20)) }
    }

    fun cancel() {
        rec?.let { r -> runCatching { r.stop() }; runCatching { r.release() } }
        rec = null; file?.delete()
    }
}

/** One voice note plays at a time. */
object VoicePlayer {
    var playingId by mutableStateOf<Long?>(null); private set
    var position by mutableIntStateOf(0); private set
    private var mp: MediaPlayer? = null

    fun toggle(id: Long, url: String) {
        if (playingId == id) { stop(); return }
        stop()
        runCatching {
            mp = MediaPlayer().apply {
                setDataSource(url)
                setOnPreparedListener { it.start(); playingId = id }
                setOnCompletionListener { stop() }
                setOnErrorListener { _, _, _ -> stop(); true }
                prepareAsync()
            }
            playingId = id
        }.onFailure { stop() }
    }

    fun tick() { position = runCatching { mp?.currentPosition ?: 0 }.getOrDefault(0) }

    fun stop() {
        runCatching { mp?.stop() }; runCatching { mp?.release() }
        mp = null; playingId = null; position = 0
    }
}

/** Play/pause, waveform and length of a voice note. */
@Composable
fun VoiceBubble(m: DmMessage, onLongPress: () -> Unit) {
    val style = m.style.orEmpty()
    val secs = style.take(3).toIntOrNull() ?: 0
    val bars = style.drop(3).map { (it - '0').coerceIn(0, 9) }.ifEmpty { List(20) { 3 } }
    val playing = VoicePlayer.playingId == m.id
    LaunchedEffect(playing) { while (playing) { VoicePlayer.tick(); delay(200) } }
    val frac = if (playing && secs > 0) (VoicePlayer.position / 1000f / secs).coerceIn(0f, 1f) else 0f
    Row(
        Modifier.clip(RoundedCornerShape(20.dp))
            .background(if (m.mine) Color(0xFF3D7BFF) else Color(0xFF2A2A2E))
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .androidxCombined(onLongPress) { m.mediaUrl?.let { VoicePlayer.toggle(m.id, it) } },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(Color(0x33FFFFFF)), contentAlignment = Alignment.Center) {
            Text(if (playing) "❚❚" else "▶", style = inter(12f, FontWeight.Bold), color = Color.White)
        }
        Spacer(Modifier.width(10.dp))
        Row(Modifier.height(26.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            bars.forEachIndexed { i, b ->
                Box(Modifier.width(3.dp).height((4 + b * 2).dp).clip(RoundedCornerShape(2.dp)).background(if (i / bars.size.toFloat() < frac) Color.White else Color(0x80FFFFFF)))
            }
        }
        Spacer(Modifier.width(10.dp))
        val shown = if (playing) (VoicePlayer.position / 1000) else secs
        Text("%d:%02d".format(shown / 60, shown % 60), style = inter(12.5f), color = Color.White)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun Modifier.androidxCombined(onLong: () -> Unit, onTap: () -> Unit): Modifier =
    this.then(Modifier.combinedClickable(onClick = onTap, onLongClick = onLong))

/** Shown instead of the text box while recording. */
@Composable
fun RecordingBar(recorder: VoiceRecorder, onCancel: () -> Unit, onSend: () -> Unit) {
    var secs by remember { mutableIntStateOf(0) }
    LaunchedEffect(recorder) { while (true) { recorder.sample(); secs = recorder.seconds; delay(100) } }
    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF2A2A2E)).clickable(onClick = onCancel), contentAlignment = Alignment.Center) { Text("✕", style = inter(14f, FontWeight.Bold), color = Color.White) }
        Spacer(Modifier.width(10.dp))
        Row(Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF1C1C1E)).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFFF4D4D)))
            Spacer(Modifier.width(10.dp))
            Text("Recording  %d:%02d".format(secs / 60, secs % 60), style = inter(14f), color = Color.White)
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF3D7BFF)).clickable(onClick = onSend), contentAlignment = Alignment.Center) { Text("➤", style = inter(15f), color = Color.White) }
    }
}
