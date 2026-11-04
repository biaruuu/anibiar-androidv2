@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.anibiar.app.ui.watch

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.view.TextureView
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.anibiar.app.data.Streaming
import com.anibiar.app.data.StreamSubtitle
import com.anibiar.app.data.TimeRange
import com.anibiar.app.ui.theme.Ani
import com.anibiar.app.ui.theme.VidIcons
import com.anibiar.app.ui.theme.inter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Glass = Color(0x3A1B1B20)
private val GlassEdge = Color(0x26FFFFFF)
private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
private val BOOST_MB = listOf(0, 600, 950, 1200)
private fun boostLabel(i: Int) = "${i * 100}%"
private val CAPTION_SIZES = listOf("Small" to 0.88f, "Medium" to 1.1f, "Large" to 1.4f)
private val CAPTION_STYLES = listOf("Dark box", "Outline")

private fun fmt(sec: Double): String {
    if (!sec.isFinite() || sec < 0) return "0:00"
    val t = sec.toLong(); val h = t / 3600; val m = (t % 3600) / 60; val s = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun Dp.coerce(min: Dp, max: Dp) = if (this < min) min else if (this > max) max else this

/** AniBiar TV's player, natively: glass controls, settings menu, captions, skip intro/outro, gestures. */
@Composable
fun KiroPlayer(
    src: String?, format: String, title: String, poster: String?,
    subtitles: List<StreamSubtitle>, intro: TimeRange?, outro: TimeRange?, skipLength: Double?,
    autoplay: Boolean, autoNext: Boolean, autoSkip: Boolean,
    onAutoplay: (Boolean) -> Unit, onAutoNext: (Boolean) -> Unit, onAutoSkip: (Boolean) -> Unit,
    startAt: Double?, onTime: (Double, Double) -> Unit, onEnded: () -> Unit, onFail: () -> Unit,
    fullscreen: Boolean, onFullscreen: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    together: com.anibiar.app.social.WatchTogether? = null,
    /** Guests in a room: no play/pause, seeking, skipping or speed (the host controls those). */
    readOnly: Boolean = false,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val player = remember {
        ExoPlayer.Builder(context)
            .setBandwidthMeter(androidx.media3.exoplayer.upstream.DefaultBandwidthMeter.Builder(context).setInitialBitrateEstimate(12_000_000L).build())
            .setLoadControl(androidx.media3.exoplayer.DefaultLoadControl.Builder().setBufferDurationsMs(30_000, 70_000, 1_500, 3_000).build())
            .build()
    }
    var textureView by remember { mutableStateOf<TextureView?>(null) }

    var playing by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var ended by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var timeMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var bufferedMs by remember { mutableLongStateOf(0L) }
    var videoRatio by remember { mutableFloatStateOf(16f / 9f) }
    var showControls by remember { mutableStateOf(true) }
    var scrubFrac by remember { mutableStateOf<Float?>(null) }
    var volume by remember { mutableFloatStateOf(1f) }
    var muted by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var boost by remember { mutableIntStateOf(0) }
    var quality by remember { mutableIntStateOf(-1) }
    var userQuality by remember { mutableStateOf(false) }
    var heights by remember { mutableStateOf<List<Int>>(emptyList()) }
    var captionTrack by remember { mutableIntStateOf(0) }
    var captionSize by remember { mutableIntStateOf(0) }
    var captionStyle by remember { mutableIntStateOf(0) }
    var cues by remember { mutableStateOf<List<Cue>>(emptyList()) }
    var menuOpen by remember { mutableStateOf(false) }
    var menuView by remember { mutableStateOf("main") }
    var notice by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var flash by remember { mutableStateOf<Pair<Boolean, Int>?>(null) }
    var skipTarget by remember { mutableStateOf<String?>(null) }
    var skipToast by remember { mutableStateOf<Pair<String, Double>?>(null) }
    val skipped = remember { HashSet<String>() }

    val sortedSubs = remember(subtitles) {
        subtitles.sortedBy { t -> if (t.srclang.startsWith("en", true) || t.label.contains("english", true)) 0 else if (t.default) 1 else 2 }
    }
    fun say(text: String, err: Boolean = false) { notice = text to err }

    // ---- watch together: my actions go out, theirs come in ----
    val togetherNow by rememberUpdatedState(together)
    val readOnlyNow by rememberUpdatedState(readOnly)
    val remoteUntil = remember { LongArray(1) }
    val retries = remember { IntArray(1) }
    val pendingCmd = remember { arrayOfNulls<com.anibiar.app.social.SyncCommand>(1) }
    val driftJob = remember { arrayOfNulls<kotlinx.coroutines.Job>(1) }
    val lastHardSeek = remember { LongArray(1) }
    val speedNow by rememberUpdatedState(speed)
    fun applyCmd(c: com.anibiar.app.social.SyncCommand) {
        remoteUntil[0] = System.currentTimeMillis() + 1000
        val nowMs = System.currentTimeMillis()
        // A command that waited for the video to be ready is older now: move its target forward.
        val target = c.positionMs + if (c.playing) (nowMs - c.at).coerceAtLeast(0) else 0
        val drift = target - player.currentPosition
        val away = kotlin.math.abs(drift)
        togetherNow?.driftMs = drift
        if (c.hard) {
            if (away > 500) { player.seekTo(target.coerceAtLeast(0)); lastHardSeek[0] = nowMs; ended = false }
        } else if (away > 4000 && nowMs - lastHardSeek[0] > 8000) {
            // Far off (slow connection, long buffer): jump, but never more than once every 8 s so it can't thrash.
            player.seekTo(target.coerceAtLeast(0)); lastHardSeek[0] = nowMs; ended = false
        } else if (c.playing && away > 250 && away <= 4000) {
            // Small drift: change speed gently instead of seeking, so nothing reloads.
            driftJob[0]?.cancel()
            player.setPlaybackSpeed(speedNow * if (drift > 0) 1.12f else 0.88f)
            driftJob[0] = scope.launch { delay((away * 8).coerceIn(200, 3000)); player.setPlaybackSpeed(speedNow) }
        }
        if (c.speed != speedNow) speed = c.speed
        if (c.boost != boost) boost = c.boost
        if (player.playWhenReady != c.playing) player.playWhenReady = c.playing
    }
    LaunchedEffect(together) {
        val t = together ?: return@LaunchedEffect
        t.commands.collect { c -> if (player.playbackState == Player.STATE_READY) applyCmd(c) else pendingCmd[0] = c }
    }

    // ---- player wiring ----
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!readOnlyNow && System.currentTimeMillis() > remoteUntil[0] && player.duration > 0) togetherNow?.announce(playWhenReady, player.currentPosition)
            }
            override fun onPositionDiscontinuity(o: Player.PositionInfo, n: Player.PositionInfo, reason: Int) {
                if (!readOnlyNow && reason == Player.DISCONTINUITY_REASON_SEEK && System.currentTimeMillis() > remoteUntil[0] && player.duration > 0)
                    togetherNow?.announce(player.playWhenReady, n.positionMs)
            }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_ENDED) { ended = true; playing = false }
                if (state == Player.STATE_READY) { durationMs = player.duration.coerceAtLeast(0); error = null }
            }
            override fun onPlayerError(e: PlaybackException) {
                // A pinned quality that fails goes back to Auto; a hiccup gets two quiet retries before trying the next server.
                if (quality > 0) { userQuality = true; quality = -1 }
                if (retries[0] < 2) { retries[0]++; scope.launch { delay(700); player.prepare() }; return }
                error = "This server couldn't play the video (${e.errorCodeName})."; onFail()
            }
            override fun onVideoSizeChanged(v: VideoSize) { if (v.width > 0 && v.height > 0) videoRatio = v.width * v.pixelWidthHeightRatio / v.height }
            override fun onTracksChanged(t: Tracks) {
                heights = t.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
                    .flatMap { g -> (0 until g.length).map { g.getTrackFormat(it).height } }
                    .filter { it > 0 }.distinct().sortedDescending()
            }
        }
        player.addListener(l)
        onDispose {
            togetherNow?.let { it.resumeEp = it.episode; it.resumePos = player.currentPosition.coerceAtLeast(0); it.resumePlaying = player.playWhenReady && player.playbackState == Player.STATE_READY; it.resumeAt = System.currentTimeMillis() }
            player.removeListener(l); player.release()
        }
    }
    DisposableEffect(playing) { view.keepScreenOn = playing; onDispose { view.keepScreenOn = false } }
    LaunchedEffect(textureView) { textureView?.let { player.setVideoTextureView(it) } }

    val autoplayNow by rememberUpdatedState(autoplay)
    LaunchedEffect(src) {
        error = null; ended = false; skipped.clear(); skipTarget = null; skipToast = null; quality = -1; userQuality = false; retries[0] = 0; timeMs = 0; durationMs = 0
        if (src == null) { player.stop(); player.clearMediaItems(); return@LaunchedEffect }
        val item = MediaItem.Builder().setUri(Streaming.abs(src))
            .setMimeType(if (format == "hls") MimeTypes.APPLICATION_M3U8 else MimeTypes.VIDEO_MP4).build()
        player.setMediaItem(item, ((startAt ?: 0.0) * 1000).toLong())
        player.prepare()
        player.playWhenReady = autoplayNow
    }
    LaunchedEffect(boost) {
        togetherNow?.let { t -> t.boost = boost; if (!readOnlyNow && player.duration > 0 && System.currentTimeMillis() > remoteUntil[0]) t.announce(player.playWhenReady, player.currentPosition, hard = false) }
    }
    LaunchedEffect(volume, muted) { player.volume = if (muted) 0f else volume }
    LaunchedEffect(speed) {
        player.setPlaybackSpeed(speed)
        togetherNow?.let { t -> t.speed = speed; if (!readOnlyNow && player.duration > 0 && System.currentTimeMillis() > remoteUntil[0]) t.announce(player.playWhenReady, player.currentPosition, hard = false) }
    }

    // Volume boost through the system loudness enhancer.
    val enhancer = remember { arrayOfNulls<android.media.audiofx.LoudnessEnhancer>(1) }
    DisposableEffect(Unit) { onDispose { runCatching { enhancer[0]?.release() } } }
    LaunchedEffect(boost, src, buffering) {
        runCatching {
            val session = player.audioSessionId
            if (session == C.AUDIO_SESSION_ID_UNSET || session == 0) return@runCatching
            if (enhancer[0] == null) enhancer[0] = android.media.audiofx.LoudnessEnhancer(session)
            enhancer[0]?.setTargetGain(BOOST_MB[boost]); enhancer[0]?.enabled = boost > 0
        }
    }

    // HD without pinning: Auto, but starting from a high bandwidth estimate so it begins on the best quality.
    // Quality: pin the chosen height, or let the player adapt.
    LaunchedEffect(quality, heights) {
        val builder = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        if (quality > 0) {
            player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }.forEach { g ->
                (0 until g.length).firstOrNull { g.getTrackFormat(it).height == quality }?.let { builder.setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, it)) }
            }
        }
        player.trackSelectionParameters = builder.build()
    }

    // Captions: fetch + parse the chosen track.
    LaunchedEffect(sortedSubs, captionTrack) {
        cues = emptyList()
        val t = sortedSubs.getOrNull(captionTrack) ?: return@LaunchedEffect
        cues = runCatching { withContext(Dispatchers.Default) { Captions.parse(Streaming.text(t.src)) } }.getOrDefault(emptyList())
    }

    // Position, buffering, skip intro/outro, progress reporting.
    val onTimeNow by rememberUpdatedState(onTime)
    val autoSkipNow by rememberUpdatedState(autoSkip)
    LaunchedEffect(player) {
        var reported = 0L
        while (true) {
            if (scrubFrac == null) timeMs = player.currentPosition.coerceAtLeast(0)
            togetherNow?.let { tg ->
                if (player.playbackState == Player.STATE_READY) {
                    pendingCmd[0]?.let { pendingCmd[0] = null; applyCmd(it) }
                    tg.tick(player.playWhenReady, player.currentPosition)
                }
            }
            bufferedMs = player.bufferedPosition
            if (player.duration > 0) durationMs = player.duration
            val t = timeMs / 1000.0
            fun inside(r: TimeRange?) = r != null && t >= r.start && t < r.end - 1
            val kind = if (inside(intro)) "intro" else if (inside(outro)) "outro" else null
            if (kind != null && autoSkipNow && !readOnlyNow && kind !in skipped && player.isPlaying) {
                val r = if (kind == "intro") intro!! else outro!!
                skipped += kind; skipToast = kind to t; skipTarget = null
                remoteUntil[0] = System.currentTimeMillis() + 1000
                player.seekTo((r.end * 1000).toLong())
            } else if (skipToast == null) skipTarget = if (readOnlyNow) null else kind
            if (player.isPlaying && timeMs - reported > 5000) { reported = timeMs; onTimeNow(t, durationMs / 1000.0) }
            delay(200)
        }
    }
    LaunchedEffect(skipToast) { if (skipToast != null) { delay(5000); skipToast = null } }
    LaunchedEffect(notice) { if (notice != null) { delay(2600); notice = null } }
    LaunchedEffect(flash) { if (flash != null) { delay(500); flash = null } }
    val endedNow by rememberUpdatedState(onEnded)
    LaunchedEffect(ended) { if (ended) { onTimeNow(durationMs / 1000.0, durationMs / 1000.0); endedNow() } }

    // Auto-hide the controls while playing.
    LaunchedEffect(showControls, playing, menuOpen, scrubFrac, timeMs / 3000) {
        if (showControls && playing && !menuOpen && scrubFrac == null) { delay(3000); showControls = false }
    }

    fun seekBy(sec: Int) {
        if (readOnly) { say("Only the host controls playback"); return }
        val to = (player.currentPosition + sec * 1000L).coerceIn(0, if (player.duration > 0) player.duration else Long.MAX_VALUE)
        player.seekTo(to); timeMs = to; ended = false
        flash = (sec < 0) to (flash?.second ?: 0) + 1
    }
    fun togglePlay() {
        if (readOnly) { say("Only the host controls playback"); return }
        if (ended) { player.seekTo(0); ended = false; player.play() } else if (player.isPlaying) player.pause() else player.play()
    }

    val controlsVisible = showControls || !playing || menuOpen
    val aCtl by animateFloatAsState(if (controlsVisible) 1f else 0f, label = "ctl")

    BoxWithConstraints(modifier.background(Color.Black)) {
        val w = maxWidth
        val h = maxHeight
        val pad = (w * 0.022f).coerce(8.dp, 20.dp)
        val btn = (w * 0.04f).coerce(32.dp, 44.dp)
        val ico = btn * 0.76f
        val pill = (w * 0.041f).coerce(30.dp, 42.dp)
        val playSize = (w * 0.063f).coerce(46.dp, 68.dp)
        val seekSize = (w * 0.057f).coerce(38.dp, 62.dp)
        val timeSp = (w.value * 0.018f).coerceIn(12f, 16f)
        val wide = w >= 520.dp

        // Video + gestures
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures(
                    onTap = { if (menuOpen) menuOpen = false else showControls = !showControls },
                    onDoubleTap = { o ->
                        val f = o.x / size.width
                        if (f < 0.33f) { if (!readOnly) seekBy(-10) } else if (f > 0.67f) { if (!readOnly) seekBy(10) } else onFullscreen(!fullscreen)
                        showControls = true
                    },
                )
            },
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { c -> TextureView(c).apply { layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT); textureView = this } },
                modifier = Modifier.aspectRatio(videoRatio),
            )
        }
        if (poster != null && (durationMs == 0L || buffering && timeMs == 0L)) {
            AsyncImage(poster, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(0.35f))
        }

        // Captions
        val line = Captions.active(cues, timeMs / 1000.0)
        if (line != null) {
            val base = (w.value * 0.026f).coerceIn(13.6f, 40f) * CAPTION_SIZES[captionSize].second
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = w * 0.05f).padding(bottom = (w * 0.065f).coerce(34.dp, 80.dp)), contentAlignment = Alignment.Center) {
                Text(
                    line, textAlign = TextAlign.Center, color = Color.White,
                    style = inter(base, FontWeight.Bold, lineHeight = base * 1.35f).copy(
                        shadow = if (captionStyle == 1) Shadow(Color.Black, Offset(0f, 1f), 7f) else null,
                    ),
                    modifier = Modifier.then(if (captionStyle == 0) Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xBF000000)).padding(horizontal = 8.dp, vertical = 2.dp) else Modifier),
                )
            }
        }

        // Seek flash
        flash?.let { (back, _) ->
            Box(Modifier.align(if (back) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = w * 0.12f).size((w * 0.1f).coerce(48.dp, 80.dp)).clip(CircleShape).background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                Icon(if (back) VidIcons.SeekBackward10 else VidIcons.SeekForward10, null, tint = Color.White, modifier = Modifier.fillMaxSize(0.8f))
            }
        }

        // Buffering / error
        if (buffering && playing.not() && error == null && src != null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(playSize * 0.6f), color = Color.White, strokeWidth = 2.5.dp)
        }
        error?.let { e ->
            Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(e, style = inter(14f, FontWeight.SemiBold), color = Color.White, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text("Try again", style = inter(13f, FontWeight.SemiBold), color = Color.Black,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White).clickable { error = null; player.prepare(); player.play() }.padding(horizontal = 18.dp, vertical = 8.dp))
            }
        }

        // Skip intro / outro + undo toast
        Column(Modifier.align(Alignment.BottomEnd).padding(end = pad).padding(bottom = (w * 0.115f).coerce(64.dp, 120.dp)), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            skipToast?.let { (kind, from) ->
                Row(Modifier.clip(RoundedCornerShape(50)).background(Color(0xB3000000)).border(1.dp, GlassEdge, RoundedCornerShape(50)).padding(start = 14.dp, end = 6.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (kind == "intro") "Intro skipped" else "Outro skipped", style = inter(12.5f, FontWeight.Medium), color = Color.White)
                    Spacer(Modifier.width(10.dp))
                    Text("Undo", style = inter(12.5f, FontWeight.SemiBold), color = Color.White,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0x26FFFFFF)).clickable { player.seekTo((from * 1000).toLong()); skipToast = null }.padding(horizontal = 12.dp, vertical = 5.dp))
                }
            }
            if (skipToast == null) skipTarget?.let { kind ->
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(Color.White).clickable {
                        val r = if (kind == "intro") intro else outro
                        if (r != null) { skipped += kind; skipToast = null; player.seekTo((r.end * 1000).toLong()); skipTarget = null }
                    }.padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (kind == "intro") "Skip Intro" else "Skip Outro", style = inter(13.5f, FontWeight.SemiBold), color = Color.Black)
                    Spacer(Modifier.width(6.dp))
                    Icon(VidIcons.SeekForward10, null, tint = Color.Black, modifier = Modifier.size(22.dp))
                }
            }
        }

        // Notice
        notice?.let { (text, err) ->
            Text(text, style = inter(12.5f, FontWeight.Medium), color = Color.White, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = pad + pill + 14.dp).clip(RoundedCornerShape(50)).background(if (err) Color(0xFFDC2626) else Color(0xD9000000)).padding(horizontal = 16.dp, vertical = 8.dp))
        }

        // ---------------- controls ----------------
        Box(Modifier.fillMaxSize().alpha(aCtl)) {
            val live = aCtl > 0.05f
            // Top bar
            Row(Modifier.fillMaxWidth().padding(pad), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                GlassPill {
                    BarButton(btn, if (muted || volume == 0f) VidIcons.Mute else if (volume < 0.5f) VidIcons.VolumeLow else VidIcons.VolumeHigh, ico, live) { muted = !muted }
                    if (wide) {
                        Box(Modifier.width(96.dp).height(btn).padding(horizontal = 8.dp)) {
                            TrackBar(if (muted) 0f else volume, 0f, live, Modifier.align(Alignment.Center), thumb = false, onSeek = { volume = it; muted = false })
                        }
                    }
                    BarButton(btn, VidIcons.Chromecast, ico, live) { openCastSettings(context) }
                }
                GlassPill {
                    BarButton(btn, VidIcons.Camera, ico, live) { screenshot(context, textureView) { say(it.first, it.second) } }
                    BarButton(btn, VidIcons.Download, ico, live) {
                        if (src != null && format == "file") {
                            runCatching {
                                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                dm.enqueue(DownloadManager.Request(Uri.parse(Streaming.abs(src))).setTitle(title).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "${title.replace(Regex("[^A-Za-z0-9 ._-]"), "")}.mp4"))
                            }.onSuccess { say("Download started") }.onFailure { say("Couldn't start the download", true) }
                        } else say("This server only streams. Pick another one to download.", true)
                    }
                    if (sortedSubs.isNotEmpty()) BarButton(btn, if (captionTrack >= 0) VidIcons.ClosedCaptionsOn else VidIcons.ClosedCaptions, ico, live) {
                        captionTrack = if (captionTrack >= 0) -1 else 0
                    }
                    BarButton(btn, VidIcons.Settings, ico, live, rotate = menuOpen) { menuView = "main"; menuOpen = !menuOpen }
                }
            }

            // Centre controls
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.align(Alignment.Center).offset(x = -(w * 0.25f)).size(seekSize).clip(CircleShape).background(Glass).border(1.dp, GlassEdge, CircleShape)
                    .clickable(enabled = live) { seekBy(-10); showControls = true }, contentAlignment = Alignment.Center) {
                    Icon(VidIcons.SeekBackward10, "Back 10 seconds", tint = Color.White, modifier = Modifier.size(seekSize * 0.74f))
                }
                Box(Modifier.align(Alignment.Center).size(playSize).clip(CircleShape).background(Glass).border(1.dp, GlassEdge, CircleShape)
                    .clickable(enabled = live) { togglePlay(); showControls = true }, contentAlignment = Alignment.Center) {
                    if (buffering && !playing && src != null && error == null) CircularProgressIndicator(Modifier.size(playSize * 0.5f), color = Color.White, strokeWidth = 2.5.dp)
                    else Icon(if (playing) VidIcons.Pause else VidIcons.Play, if (playing) "Pause" else "Play", tint = Color.White, modifier = Modifier.size(playSize * 0.74f))
                }
                Box(Modifier.align(Alignment.Center).offset(x = (w * 0.25f)).size(seekSize).clip(CircleShape).background(Glass).border(1.dp, GlassEdge, CircleShape)
                    .clickable(enabled = live) { seekBy(10); showControls = true }, contentAlignment = Alignment.Center) {
                    Icon(VidIcons.SeekForward10, "Forward 10 seconds", tint = Color.White, modifier = Modifier.size(seekSize * 0.74f))
                }
            }

            // Bottom: time + fullscreen, seek bar
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = (w * 0.035f).coerce(12.dp, 48.dp)).padding(bottom = (w * 0.032f).coerce(10.dp, 34.dp))) {
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(Modifier.weight(1f).height(pill), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            buildTime(timeMs / 1000.0, durationMs / 1000.0, if (w >= 420.dp) title else null),
                            style = inter(timeSp, FontWeight.Medium).copy(shadow = Shadow(Color(0x99000000), Offset(0f, 1f), 3f)),
                            color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.height(pill).width((w * 0.056f).coerce(40.dp, 56.dp)).clip(RoundedCornerShape((w * 0.024f).coerce(14.dp, 22.dp))).background(Glass).border(1.dp, GlassEdge, RoundedCornerShape((w * 0.024f).coerce(14.dp, 22.dp)))
                        .clickable(enabled = live) { onFullscreen(!fullscreen) }, contentAlignment = Alignment.Center) {
                        Icon(if (fullscreen) VidIcons.FullscreenExit else VidIcons.Fullscreen, "Fullscreen", tint = Color.White, modifier = Modifier.size(ico * 0.9f))
                    }
                }
                TrackBar(
                    (scrubFrac ?: if (durationMs > 0) timeMs.toFloat() / durationMs else 0f), if (durationMs > 0) bufferedMs.toFloat() / durationMs else 0f, live && !readOnly,
                    Modifier.fillMaxWidth(), thumb = scrubFrac != null,
                    onScrub = { scrubFrac = it; timeMs = (it * durationMs).toLong() },
                    onSeek = { f -> scrubFrac = null; if (readOnly) return@TrackBar; val to = (f * durationMs).toLong(); player.seekTo(to); timeMs = to; ended = false },
                    seekMode = true,
                )
            }
        }

        // ---------------- settings menu ----------------
        AnimatedVisibility(menuOpen, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopEnd)) {
            val panelW = (w * 0.62f).coerce(228.dp, 340.dp)
            val top = pad + btn + 10.dp
            Column(
                Modifier.padding(top = top, end = pad).width(panelW).heightIn(max = h - top - pad)
                    .clip(RoundedCornerShape(22.dp)).background(Color(0xCC0C0C0F)).border(1.dp, GlassEdge, RoundedCornerShape(22.dp))
                    .pointerInput(Unit) { detectTapGestures { } }
                    .verticalScroll(rememberScrollState()).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (menuView != "main") MenuBack(when (menuView) { "quality" -> "Quality"; "boost" -> "Volume boost"; "captions" -> "Captions"; "style" -> "Caption style"; else -> "Speed" }) { menuView = "main" }
                when (menuView) {
                    "main" -> {
                        if (heights.isNotEmpty()) MenuRow(VidIcons.Settings, "Quality", if (quality > 0) "${quality}p" else "Auto") { menuView = "quality" }
                        MenuRow(VidIcons.VolumeHigh, "Volume boost", boostLabel(boost) + if (readOnly) " · host" else "") { if (!readOnly) menuView = "boost" }
                        if (sortedSubs.isNotEmpty()) {
                            MenuRow(VidIcons.ClosedCaptions, "Captions", sortedSubs.getOrNull(captionTrack)?.label ?: "Off") { menuView = "captions" }
                            MenuRow(VidIcons.ClosedCaptionsOn, "Caption style", CAPTION_SIZES[captionSize].first) { menuView = "style" }
                        }
                        if (!readOnly) MenuRow(VidIcons.PlaybackSpeedCircle, "Speed", if (speed == 1f) "Normal" else "${speed}x") { menuView = "speed" }
                        MenuRow(VidIcons.Play, "Autoplay", if (autoplay) "On" else "Off", chevron = false) { onAutoplay(!autoplay) }
                        MenuRow(VidIcons.SeekForward10, "Auto skip intro/outro", if (intro == null && outro == null) "No data" else if (autoSkip) "On" else "Off", chevron = false) { onAutoSkip(!autoSkip) }
                        MenuRow(VidIcons.SeekForward10, "Auto next episode", if (autoNext) "On" else "Off", chevron = false) { onAutoNext(!autoNext) }
                    }
                    "quality" -> {
                        OptionRow("Auto", quality == -1) { userQuality = true; quality = -1; menuView = "main" }
                        heights.forEach { h -> OptionRow("${h}p", quality == h) { userQuality = true; quality = h; menuView = "main" } }
                    }
                    "boost" -> BOOST_MB.indices.forEach { i -> OptionRow(boostLabel(i), boost == i) { boost = i; menuView = "main" } }
                    "captions" -> {
                        OptionRow("Off", captionTrack < 0) { captionTrack = -1; menuView = "main" }
                        sortedSubs.forEachIndexed { i, t -> OptionRow(t.label, captionTrack == i) { captionTrack = i; menuView = "main" } }
                    }
                    "style" -> {
                        CAPTION_SIZES.forEachIndexed { i, s -> OptionRow(s.first, captionSize == i) { captionSize = i } }
                        Spacer(Modifier.height(2.dp))
                        CAPTION_STYLES.forEachIndexed { i, s -> OptionRow(s, captionStyle == i) { captionStyle = i } }
                    }
                    else -> SPEEDS.forEach { s -> OptionRow(if (s == 1f) "Normal" else "${s}x", speed == s) { speed = s; menuView = "main" } }
                }
            }
        }
    }
}

private fun buildTime(t: Double, d: Double, title: String?) =
    androidx.compose.ui.text.buildAnnotatedString {
        append(fmt(t))
        pushStyle(androidx.compose.ui.text.SpanStyle(color = Color(0xBFFFFFFF))); append(" / ${fmt(d)}")
        if (title != null) append(" • $title")
        pop()
    }

@Composable private fun GlassPill(content: @Composable () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(Glass).border(1.dp, GlassEdge, RoundedCornerShape(50)).padding(2.dp), verticalAlignment = Alignment.CenterVertically) { content() }
}

@Composable private fun BarButton(size: Dp, icon: ImageVector, iconSize: Dp, enabled: Boolean, rotate: Boolean = false, visible: Boolean = true, onClick: () -> Unit) {
    if (!visible) return
    val rot by animateFloatAsState(if (rotate) 45f else 0f, label = "rot")
    Box(Modifier.size(size).clip(CircleShape).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(iconSize).rotate(rot))
    }
}

/** Thin seek/volume bar: faint track, brighter buffered part, white progress. */
@Composable
private fun TrackBar(
    frac: Float, buffered: Float, enabled: Boolean, modifier: Modifier, thumb: Boolean,
    onScrub: (Float) -> Unit = {}, onSeek: (Float) -> Unit, seekMode: Boolean = false,
) {
    val f = frac.coerceIn(0f, 1f)
    val fNow by rememberUpdatedState(f)
    Box(
        modifier.height(22.dp)
            .pointerInput(enabled, seekMode) {
                if (enabled) detectTapGestures { o -> val v = (o.x / size.width).coerceIn(0f, 1f); if (seekMode) onScrub(v); onSeek(v) }
            }
            .pointerInput(enabled, seekMode) {
                if (enabled) detectHorizontalDragGestures(
                    onDragStart = { o -> val v = (o.x / size.width).coerceIn(0f, 1f); if (seekMode) onScrub(v) else onSeek(v) },
                    onDragEnd = { if (seekMode) onSeek(fNow) },
                    onDragCancel = { if (seekMode) onSeek(fNow) },
                    onHorizontalDrag = { change, _ -> val v = (change.position.x / size.width).coerceIn(0f, 1f); if (seekMode) onScrub(v) else onSeek(v) },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color(0x4DFFFFFF)))
        if (buffered > 0f) Box(Modifier.fillMaxWidth(buffered.coerceIn(0f, 1f)).height(4.dp).clip(CircleShape).background(Color(0x80FFFFFF)))
        Box(Modifier.fillMaxWidth(f).height(4.dp).clip(CircleShape).background(Color.White))
        if (thumb) BoxWithConstraints(Modifier.fillMaxWidth()) { Box(Modifier.offset(x = (maxWidth * f) - 6.dp).size(12.dp).clip(CircleShape).background(Color.White)) }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, value: String, chevron: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x14FFFFFF)).border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, style = inter(14f, FontWeight.Medium, lineHeight = 17f), color = Color.White, modifier = Modifier.weight(1f))
        Text(value, style = inter(13f), color = Color(0x99FFFFFF), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 100.dp))
        if (chevron) { Spacer(Modifier.width(4.dp)); Icon(VidIcons.ChevronRight, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
    }
}

@Composable
private fun MenuBack(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onBack).padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(VidIcons.ChevronLeft, "Back", tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(6.dp))
        Text(title, style = inter(14.5f, FontWeight.SemiBold), color = Color.White)
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (selected) Color(0x26FFFFFF) else Color(0x14FFFFFF)).border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = inter(14f, FontWeight.Medium), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (selected) Icon(VidIcons.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

private fun screenshot(context: Context, tv: TextureView?, done: (Pair<String, Boolean>) -> Unit) {
    val bmp: Bitmap? = runCatching { tv?.bitmap }.getOrNull()
    if (bmp == null) { done("Couldn't capture a frame" to true); return }
    runCatching {
        val name = "Kiro-${System.currentTimeMillis()}.png"
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name); put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Kiro")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("no uri")
            context.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } else {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: error("no dir")
            java.io.File(dir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }.onSuccess { done("Screenshot saved" to false) }.onFailure { done("Couldn't save the screenshot" to true) }
}

/** The cast button's native counterpart: opens the system "Cast / screen mirroring" settings. */
fun openCastSettings(context: Context) {
    runCatching { context.startActivity(Intent(Settings.ACTION_CAST_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
