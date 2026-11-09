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
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.runtime.key
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import com.anibiar.app.ui.theme.TrebuchetFamily
import com.anibiar.app.ui.theme.Ani
import com.anibiar.app.ui.theme.VidIcons
import com.anibiar.app.ui.theme.FeatherIcons
import com.anibiar.app.ui.theme.inter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Glass = Color(0x73000000)
private val GlassEdge = Color(0x24FFFFFF)
private val cueCache = HashMap<String, List<Cue>>()
private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
private val BOOST_MB = listOf(0, 600, 950, 1200)
private fun boostLabel(i: Int) = "${i * 100}%"
private fun isEnglishTrack(lang: String, label: String): Boolean {
    val l = lang.trim().lowercase(); val n = label.trim().lowercase()
    return l == "en" || l == "eng" || l.startsWith("en-") || l.startsWith("en_") || n.startsWith("english") || n == "eng" || n.startsWith("en ")
}
private val CAPTION_STYLES = listOf("Outline", "Dark box", "Yellow outline", "Soft shadow")
private val CAPTION_SIZES = listOf("Normal", "Large", "Extra large", "Small")
private val CAPTION_SCALE = listOf(1f, 1.2f, 1.45f, 0.85f)
private fun offsetLabel(ms: Int) = if (ms == 0) "In sync" else (if (ms > 0) "+" else "-") + "%.1f s".format(kotlin.math.abs(ms) / 1000f)

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
    /** Solo viewing: where you stopped last time (0..1). Asks "Continue watching?" before playing. */
    resumeFraction: Float = 0f,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val player = remember {
        // In-app providers need a Referer on every request (playlist and segments); our own server's streams don't.
        val upstream = androidx.media3.datasource.ResolvingDataSource.Factory(
            androidx.media3.datasource.DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(15_000).setReadTimeoutMs(30_000),
            androidx.media3.datasource.ResolvingDataSource.Resolver { spec ->
                val ref = Streaming.activeReferer
                if (ref == null || Streaming.isOwnHost(spec.uri.host)) spec
                else spec.withAdditionalHeaders(mapOf("Referer" to ref, "Origin" to ref.trimEnd('/'), "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"))
            },
        )
        // Read-only view of the offline cache: saved episodes play without a network, everything else streams as before.
        val http = androidx.media3.datasource.cache.CacheDataSource.Factory()
            .setCache(com.anibiar.app.data.Offline.cache(context))
            .setUpstreamDataSourceFactory(upstream)
            .setCacheWriteDataSinkFactory(null)
            .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(http))
            .setBandwidthMeter(
                androidx.media3.exoplayer.upstream.DefaultBandwidthMeter.Builder(context).setInitialBitrateEstimate(com.anibiar.app.data.NetEstimate.initial(context)).build().also { m ->
                    m.addEventListener(android.os.Handler(android.os.Looper.getMainLooper())) { _, _, est -> com.anibiar.app.data.NetEstimate.record(context, est) }
                },
            )
            .setLoadControl(androidx.media3.exoplayer.DefaultLoadControl.Builder().setBufferDurationsMs(30_000, 70_000, 1_500, 3_000).build())
            .build()
    }
    var textureView by remember { mutableStateOf<TextureView?>(null) }

    var playing by remember { mutableStateOf(false) }
    var hud by remember { mutableStateOf<String?>(null) }
    var holdFast by remember { mutableStateOf(false) }
    val activity = remember(context) { var c = context; while (c is android.content.ContextWrapper && c !is android.app.Activity) c = c.baseContext; c as? android.app.Activity }
    val inPip by com.anibiar.app.data.Pip.active.collectAsState()
    DisposableEffect(Unit) { onDispose { com.anibiar.app.data.Pip.canEnter = false } }
    LaunchedEffect(playing) { com.anibiar.app.data.Pip.canEnter = playing }
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
    val captionRetry = remember { IntArray(1) }
    var resumePrompt by remember { mutableStateOf(false) }
    var volOpen by remember { mutableStateOf(false) }
    var volTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(volOpen, volTick) { if (volOpen && !readOnly) { delay(3000); volOpen = false } }
    val resumeDecided = remember { BooleanArray(1) }
    val resumeFracNow by rememberUpdatedState(resumeFraction)
    var captionTry by remember { mutableIntStateOf(0) }
    var captionState by remember { mutableStateOf("") }
    // Caption look and the quality you chose last time are remembered between videos.
    val capPrefs = remember { context.getSharedPreferences("caption_prefs", Context.MODE_PRIVATE) }
    var captionSize by remember { mutableIntStateOf(capPrefs.getInt("size", 0).coerceIn(0, CAPTION_SIZES.lastIndex)) }
    var captionStyle by remember { mutableIntStateOf(capPrefs.getInt("style", 0).coerceIn(0, CAPTION_STYLES.lastIndex)) }
    /** Caption timing fix in ms: positive = captions appear later, negative = earlier. Resets for each video. */
    var capOffset by remember { mutableIntStateOf(0) }
    // Once you change captions yourself, the host's caption settings stop overriding yours.
    var capLocal by remember { mutableStateOf(false) }
    var cues by remember { mutableStateOf<List<Cue>>(emptyList()) }
    var menuOpen by remember { mutableStateOf(false) }
    var menuView by remember { mutableStateOf("main") }
    var notice by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var flash by remember { mutableStateOf<Pair<Boolean, Int>?>(null) }
    var skipTarget by remember { mutableStateOf<String?>(null) }
    var skipToast by remember { mutableStateOf<Pair<String, Double>?>(null) }
    val skipped = remember { HashSet<String>() }

    // In a watch party: the host's caption file, used when my own server gave me none.
    var hostSub by remember { mutableStateOf<StreamSubtitle?>(null) }
    val sortedSubs = remember(subtitles, hostSub) {
        (if (subtitles.isEmpty() && hostSub != null) listOf(hostSub!!) else subtitles).sortedBy { t -> if (isEnglishTrack(t.srclang, t.label)) 0 else if (t.default) 1 else 2 }
    }
    // English only by default: if the stream has no English track, captions start off instead of picking another language.
    LaunchedEffect(sortedSubs) {
        captionRetry[0] = 0
        val first = sortedSubs.firstOrNull()
        if (first != null && !readOnly && !isEnglishTrack(first.srclang, first.label) && captionTrack == 0) captionTrack = -1
    }
    fun say(text: String, err: Boolean = false) { notice = text to err }

    // ---- watch together: my actions go out, theirs come in ----
    val togetherNow by rememberUpdatedState(together)
    val readOnlyNow by rememberUpdatedState(readOnly)
    val remoteUntil = remember { LongArray(1) }
    val retries = remember { IntArray(1) }
    val playedOnce = remember { BooleanArray(1) }
    val bufSent = remember { LongArray(1) }
    val autoPaused = remember { BooleanArray(1) }
    val autoAt = remember { LongArray(1) }
    val pendingCmd = remember { arrayOfNulls<com.anibiar.app.social.SyncCommand>(1) }
    val driftJob = remember { arrayOfNulls<kotlinx.coroutines.Job>(1) }
    val lastHardSeek = remember { LongArray(1) }
    val speedNow by rememberUpdatedState(speed)
    // applyCmd runs from a long-lived collector, so it must read the *current* caption tracks, not the ones from when it started.
    val subsNow by rememberUpdatedState(sortedSubs)
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
        } else if (away > 1800 && nowMs - lastHardSeek[0] > 4000) {
            // Far off (slow connection, long buffer): jump, but never more than once every 4 s so it can't thrash.
            player.seekTo(target.coerceAtLeast(0)); lastHardSeek[0] = nowMs; ended = false
        } else if (c.playing && away > 100 && away <= 1800) {
            // Small drift: change speed gently instead of seeking, so nothing reloads.
            driftJob[0]?.cancel()
            player.setPlaybackSpeed(speedNow * if (drift > 0) 1.15f else 0.85f)
            driftJob[0] = scope.launch { delay((away * 7).coerceIn(150, 2500)); player.setPlaybackSpeed(speedNow) }
        }
        if (c.speed != speedNow) speed = c.speed
        if (c.boost != boost) boost = c.boost
        if (!capLocal && c.capSize != captionSize) captionSize = c.capSize.coerceIn(0, CAPTION_SIZES.lastIndex)
        if (!capLocal && c.capStyle != captionStyle) captionStyle = c.capStyle.coerceIn(0, CAPTION_STYLES.lastIndex)
        // Captions follow the host: off only when the host switched them off, otherwise on (matching track, else my English one).
        if (!capLocal) {
            if (c.capOn == false) { if (captionTrack != -1) captionTrack = -1 }
            else if (subsNow.isNotEmpty()) {
                val idx = c.cap?.let { label -> subsNow.indexOfFirst { it.label == label } } ?: -1
                if (idx >= 0) { if (idx != captionTrack) captionTrack = idx }
                else if (captionTrack < 0) captionTrack = subsNow.indexOfFirst { isEnglishTrack(it.srclang, it.label) }.coerceAtLeast(0)
            }
        }
        if (readOnlyNow) {
            c.csrc?.let { src -> if (hostSub?.src != src) hostSub = StreamSubtitle(c.clabel ?: "English", "en", src, true) }
            if (c.ui != null) showControls = c.ui
            c.menu?.let { m -> if (m.isEmpty()) menuOpen = false else { menuView = m; menuOpen = true } }
            c.vol?.let { volOpen = it }
        }
        if (player.playWhenReady != c.playing) player.playWhenReady = c.playing
    }
    // Host: someone's video is stalling, so wait for them; carry on when they're ready (or after 8 s).
    LaunchedEffect(together) {
        val t = together ?: return@LaunchedEffect
        t.waiting.collect { w ->
            if (readOnlyNow) return@collect
            if (w && player.playWhenReady && player.playbackState == Player.STATE_READY) {
                autoPaused[0] = true; autoAt[0] = System.currentTimeMillis(); player.playWhenReady = false
            } else if (!w && autoPaused[0]) {
                autoPaused[0] = false; autoAt[0] = System.currentTimeMillis(); player.playWhenReady = true
            }
        }
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
                if (System.currentTimeMillis() - autoAt[0] > 600) autoPaused[0] = false // the host chose this themselves
                if (!readOnlyNow && System.currentTimeMillis() > remoteUntil[0] && player.duration > 0) togetherNow?.announce(playWhenReady, player.currentPosition)
            }
            override fun onPositionDiscontinuity(o: Player.PositionInfo, n: Player.PositionInfo, reason: Int) {
                if (!readOnlyNow && reason == Player.DISCONTINUITY_REASON_SEEK && System.currentTimeMillis() > remoteUntil[0] && player.duration > 0)
                    togetherNow?.announce(player.playWhenReady, n.positionMs)
            }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    if (!resumeDecided[0] && resumeFracNow in 0.02f..0.95f && player.duration > 0) { resumeDecided[0] = true; resumePrompt = true; player.playWhenReady = false; player.seekTo((resumeFracNow * player.duration).toLong()) }
                    playedOnce[0] = true
                    if (bufSent[0] != 0L) { bufSent[0] = 0; if (readOnlyNow) togetherNow?.reportBuffering(false) }
                } else if (state == Player.STATE_BUFFERING && playedOnce[0] && readOnlyNow && bufSent[0] == 0L) {
                    bufSent[0] = System.currentTimeMillis(); togetherNow?.reportBuffering(true)
                }
                if (state == Player.STATE_ENDED) { ended = true; playing = false }
                if (state == Player.STATE_READY) { durationMs = player.duration.coerceAtLeast(0); error = null }
            }
            override fun onPlayerError(e: PlaybackException) {
                // A pinned quality that fails goes back to Auto; a hiccup gets two quiet retries before trying the next server.
                if (quality > 0) { userQuality = true; quality = -1 }
                if (retries[0] < 2) { retries[0]++; scope.launch { delay(700); player.prepare() }; return }
                val http = generateSequence<Throwable>(e) { it.cause }.filterIsInstance<androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException>().firstOrNull()
                val detail = (if (http != null) "HTTP ${http.responseCode} from ${http.dataSpec.uri.host}" else (e.cause?.message ?: e.message).orEmpty().take(140))
                com.anibiar.app.data.Streaming.lastPlayError = "${e.errorCodeName}: $detail (src=${src?.take(120)})"
                com.anibiar.app.data.Diag.log("player", "${e.errorCodeName} ${e.cause?.message ?: e.message} src=${src?.take(80)}")
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
        resumeDecided[0] = false; resumePrompt = false; hostSub = null
        error = null; ended = false; skipped.clear(); skipTarget = null; skipToast = null; quality = -1; userQuality = false; capOffset = 0; retries[0] = 0; playedOnce[0] = false; bufSent[0] = 0; timeMs = 0; durationMs = 0
        if (src == null) { player.stop(); player.clearMediaItems(); return@LaunchedEffect }
        Streaming.activeReferer = com.anibiar.app.data.LocalSources.referers[src]
        val item = MediaItem.Builder().setUri(Streaming.abs(src))
            .setMimeType(if (format == "hls") MimeTypes.APPLICATION_M3U8 else MimeTypes.VIDEO_MP4).build()
        player.setMediaItem(item, ((startAt ?: 0.0) * 1000).toLong())
        player.prepare()
        player.playWhenReady = autoplayNow
    }
    LaunchedEffect(boost) {
        togetherNow?.let { t -> t.boost = boost; if (!readOnlyNow && player.duration > 0 && System.currentTimeMillis() > remoteUntil[0]) t.announce(player.playWhenReady, player.currentPosition, hard = false) }
    }
    LaunchedEffect(captionTrack, captionSize, captionStyle, sortedSubs) {
        togetherNow?.let { t ->
            t.captionLabel = sortedSubs.getOrNull(captionTrack)?.label.orEmpty(); t.captionSrc = sortedSubs.getOrNull(captionTrack)?.src.orEmpty(); t.captionOn = captionTrack >= 0; t.captionSize = captionSize; t.captionStyle = captionStyle
            if (!readOnlyNow && player.duration > 0 && System.currentTimeMillis() > remoteUntil[0]) t.announce(player.playWhenReady, player.currentPosition, hard = false)
        }
    }
    LaunchedEffect(volume, muted) { player.volume = if (muted) 0f else volume }
    LaunchedEffect(speed) {
        player.setPlaybackSpeed(speed)
        togetherNow?.let { t -> t.speed = speed; if (!readOnlyNow && player.duration > 0 && System.currentTimeMillis() > remoteUntil[0]) t.announce(player.playWhenReady, player.currentPosition, hard = false) }
    }

    // Volume boost through the system loudness enhancer.
    val enhancer = remember { arrayOfNulls<android.media.audiofx.LoudnessEnhancer>(1) }
    DisposableEffect(Unit) { onDispose { runCatching { enhancer[0]?.release() } } }
    LaunchedEffect(boost, src, buffering, playing) {
        runCatching {
            val session = player.audioSessionId
            if (session == C.AUDIO_SESSION_ID_UNSET || session == 0) return@runCatching
            if (enhancer[0] == null) enhancer[0] = android.media.audiofx.LoudnessEnhancer(session)
            enhancer[0]?.setTargetGain(BOOST_MB[boost]); enhancer[0]?.enabled = boost > 0
        }
    }

    // HD without pinning: Auto, but starting from a high bandwidth estimate so it begins on the best quality.
    // Start on the quality you picked last time, when this stream has it.
    LaunchedEffect(heights, src) {
        val q = capPrefs.getInt("quality", -1)
        if (!userQuality && q > 0 && heights.isNotEmpty() && q in heights && quality != q) { userQuality = true; quality = q }
    }
    // Quality: pin the chosen height, or let the player adapt.
    LaunchedEffect(quality, heights) {
        val builder = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        if (quality > 0) {
            player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }.forEach { g ->
                (0 until g.length).firstOrNull { g.getTrackFormat(it).height == quality }?.let { builder.setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, it)) }
            }
        }
        // Data saver on a metered connection: never above 480p unless the viewer picked a quality themselves.
        if (quality <= 0 && com.anibiar.app.data.AppPrefs.saveData(context)) builder.setMaxVideoSize(854, 480)
        player.trackSelectionParameters = builder.build()
    }

    // Captions: fetch + parse the chosen track.
    LaunchedEffect(sortedSubs, captionTrack, captionTry) {
        val t = sortedSubs.getOrNull(captionTrack)
        if (t == null) { cues = emptyList(); captionState = ""; return@LaunchedEffect }
        val key = "$title|${t.label}"
        cueCache[key]?.let { cues = it; captionState = ""; return@LaunchedEffect }
        cues = emptyList(); captionState = "loading"
        // The subtitle file comes through a proxy that can hiccup: try a few times, then remember the result.
        for (attempt in 0 until 4) {
            val got = runCatching { withContext(Dispatchers.Default) { Captions.parse(Streaming.text(t.src)) } }.getOrNull()
            if (!got.isNullOrEmpty()) {
                if (cueCache.size > 40) cueCache.clear()
                cueCache[key] = got; cues = got; captionState = ""; return@LaunchedEffect
            }
            delay(600L * (attempt + 1))
        }
        captionState = "error"
        if (captionRetry[0] < 3) { captionRetry[0]++; delay(3000); captionTry++ }
    }
    LaunchedEffect(captionState) { if (captionState == "error") com.anibiar.app.data.Diag.log("captions", "failed to load or parse the selected track"); if (captionState == "error") say("Captions couldn't load. Open Settings, Captions to retry", true) }

    // Position, buffering, skip intro/outro, progress reporting.
    val onTimeNow by rememberUpdatedState(onTime)
    val autoSkipNow by rememberUpdatedState(autoSkip)
    LaunchedEffect(player) {
        var reported = 0L
        while (true) {
            if (scrubFrac == null) timeMs = player.currentPosition.coerceAtLeast(0)
            if (readOnlyNow && bufSent[0] != 0L && player.playbackState == Player.STATE_BUFFERING && System.currentTimeMillis() - bufSent[0] > 2000) {
                bufSent[0] = System.currentTimeMillis(); togetherNow?.reportBuffering(true)
            }
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
        if (showControls && playing && !menuOpen && scrubFrac == null && !readOnlyNow) { delay(3000); showControls = false }
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
    // Host: tell everyone when my controls appear or hide, so their screens show the same thing.
    LaunchedEffect(controlsVisible, together) {
        val t = togetherNow ?: return@LaunchedEffect
        if (readOnlyNow) return@LaunchedEffect
        t.controls = controlsVisible
        if (player.duration > 0) t.announce(player.playWhenReady, player.currentPosition, hard = false)
    }
    LaunchedEffect(menuOpen, menuView, volOpen, together) {
        val t = togetherNow ?: return@LaunchedEffect
        if (readOnlyNow) return@LaunchedEffect
        t.menu = if (menuOpen) menuView else ""; t.volOpen = volOpen
        if (player.duration > 0) t.announce(player.playWhenReady, player.currentPosition, hard = false)
    }
    val aCtl by animateFloatAsState(if (controlsVisible && !resumePrompt) 1f else 0f, label = "ctl")

    BoxWithConstraints(modifier.background(Color.Black)) {
        val w = maxWidth
        val h = maxHeight
        val pad = (w * 0.022f).coerce(8.dp, 20.dp)
        val btn = (w * 0.04f).coerce(26.dp, 44.dp)
        val ico = (w * 0.034f).coerce(19.dp, 33.dp)
        val pill = (w * 0.041f).coerce(26.dp, 42.dp)
        val playSize = (w * 0.063f).coerce(36.dp, 68.dp)
        val seekSize = (w * 0.057f).coerce(30.dp, 62.dp)
        val timeSp = (w.value * 0.018f).coerceIn(11f, 16f)
        val wide = w >= 520.dp

        // Video + gestures
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                var side = 0
                detectVerticalDragGestures(
                    onDragStart = { o -> side = if (o.x < size.width / 2) 0 else 1 },
                    onDragEnd = { hud = null }, onDragCancel = { hud = null },
                ) { _, dy ->
                    val d = -dy / size.height * 1.4f
                    if (side == 0) {
                        activity?.window?.let { w ->
                            val cur = w.attributes.screenBrightness.let { if (it < 0f) 0.5f else it }
                            val nv = (cur + d).coerceIn(0.02f, 1f)
                            w.attributes = w.attributes.also { a -> a.screenBrightness = nv }
                            hud = "Brightness ${(nv * 100).toInt()}%"
                        }
                    } else {
                        volume = (volume + d).coerceIn(0f, 1f); muted = false
                        hud = "Volume ${(volume * 100).toInt()}%"
                    }
                }
            }.pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        if (!readOnlyNow) {
                            val job = scope.launch { delay(500); holdFast = true; hud = "2x speed"; player.setPlaybackSpeed(2f) }
                            tryAwaitRelease()
                            job.cancel()
                            if (holdFast) { holdFast = false; hud = null; player.setPlaybackSpeed(speedNow) }
                        }
                    },
                    onTap = { if (readOnlyNow) Unit else if (menuOpen) menuOpen = false else showControls = !showControls },
                    onDoubleTap = { o ->
                        val f = o.x / size.width
                        if (f < 0.33f) { if (!readOnly) seekBy(-10) } else if (f > 0.67f) { if (!readOnly) seekBy(10) } else if (!readOnly) onFullscreen(!fullscreen)
                        if (!readOnly) showControls = true
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
        val active = Captions.activeLine(cues, (timeMs - capOffset) / 1000.0)
        if (active != null) {
            val (line, italic) = active
            val base = ((h.value * 0.058f).coerceIn(14.5f, 30f) * CAPTION_SCALE[captionSize]).coerceAtMost(44f)
            val face = inter(base, FontWeight.Bold, lineHeight = base * 1.28f).copy(
                fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal, textAlign = TextAlign.Center,
                color = if (captionStyle == 2) Color(0xFFFFE066) else Color.White, letterSpacing = (base * 0.008f).sp,
                shadow = if (captionStyle == 3) androidx.compose.ui.graphics.Shadow(Color(0xE6000000), androidx.compose.ui.geometry.Offset(2f, 2f), base * 0.35f) else null,
            )
            // Controls hidden: near the bottom edge. Controls showing: right above the seek slider, between the time and the fullscreen button.
            val lifted = aCtl > 0.5f
            val bottomPad by animateDpAsState(if (lifted) (w * 0.032f).coerce(10.dp, 34.dp) + 22.dp else (w * 0.02f).coerce(8.dp, 26.dp), label = "capBottom")
            val sidePad by animateDpAsState(if (lifted) (w * 0.22f).coerce(88.dp, 224.dp) else w * 0.05f, label = "capSide")
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = sidePad).padding(bottom = bottomPad), contentAlignment = Alignment.Center) {
                if (captionStyle == 0 || captionStyle == 2) {
                    // Outline: bold Inter, white fill with a clean dark outline (no shadow), scaled to the player.
                    val strokePx = with(androidx.compose.ui.platform.LocalDensity.current) { (base * 0.2f).sp.toPx() }
                    Text(line, style = face.copy(color = Color(0xFF0A0A0C), drawStyle = Stroke(width = strokePx, join = StrokeJoin.Round, miter = 2f)))
                    Text(line, style = face)
                } else if (captionStyle == 3) {
                    Text(line, style = face)
                } else {
                    Text(
                        line, style = face,
                        modifier = Modifier.clip(RoundedCornerShape((base * 0.45f).dp)).background(Color(0xCC101012))
                            .padding(horizontal = (base * 0.7f).dp, vertical = (base * 0.28f).dp),
                    )
                }
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
        if (resumePrompt) Box(Modifier.fillMaxSize().background(Color(0x59000000)).clickable(enabled = true, indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {}, contentAlignment = Alignment.Center) {
            val fs = (w.value * 0.024f).coerceIn(11f, 16f)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy((fs * 0.7f).dp)) {
                Text("Continue watching?", style = inter(fs * 1.15f, FontWeight.Bold), color = Color.White)
                Row(horizontalArrangement = Arrangement.spacedBy((fs * 0.8f).dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Continue from ${fmt(resumeFraction * player.duration / 1000.0)}", style = inter(fs, FontWeight.Medium), color = Color(0xFF111111), maxLines = 1,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xF2FFFFFF)).clickable {
                            resumePrompt = false; player.playWhenReady = true
                        }.padding(horizontal = (fs * 1.1f).dp, vertical = (fs * 0.5f).dp))
                    Text("Start from the beginning", style = inter(fs, FontWeight.Medium), color = Color(0xE6FFFFFF), maxLines = 1,
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable { resumePrompt = false; player.seekTo(0); player.playWhenReady = true }.padding(horizontal = 6.dp, vertical = (fs * 0.4f).dp))
                }
            }
        }
        error?.let { e ->
            Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(e, style = inter(14f, FontWeight.SemiBold), color = Color.White, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text("Try again", style = inter(13f, FontWeight.SemiBold), color = Color.Black,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White).clickable { error = null; player.prepare(); player.play() }.padding(horizontal = 18.dp, vertical = 8.dp))
                Spacer(Modifier.height(8.dp))
                Text("Copy report", style = inter(13f, FontWeight.SemiBold), color = Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, Color(0x80FFFFFF), RoundedCornerShape(50)).clickable {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("report", "Kiro player report\n$e\n${com.anibiar.app.data.Streaming.lastPlayError}\n${java.util.Date()}"))
                        android.widget.Toast.makeText(context, "Report copied", android.widget.Toast.LENGTH_SHORT).show()
                    }.padding(horizontal = 18.dp, vertical = 8.dp))
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

        togetherNow?.let { ReactionLayer(it, controlsVisible && !inPip) }
        hud?.let { text ->
            Text(
                text, style = inter(14f, FontWeight.SemiBold), color = Color.White,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 28.dp).clip(RoundedCornerShape(18.dp)).background(Color(0x99000000)).padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
        LaunchedEffect(hud) { if (hud != null && !holdFast) { delay(900); hud = null } }
        LaunchedEffect(inPip) { if (inPip) { showControls = false; menuOpen = false } }

        // ---------------- controls ----------------
        Box(Modifier.fillMaxSize().alpha(aCtl)) {
            val live = aCtl > 0.05f
            // Top bar
            Row(Modifier.fillMaxWidth().padding(pad), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                GlassPill {
                    BarButton(btn, if (muted || volume == 0f) VidIcons.Mute else if (volume < 0.5f) VidIcons.VolumeLow else VidIcons.VolumeHigh, ico, live) {
                        // First tap opens the volume slider; tapping again mutes / unmutes.
                        if (!volOpen) volOpen = true else { muted = !muted; volOpen = true }
                    }
                    val volW by animateDpAsState(if (volOpen && live) (w * 0.26f).coerce(80.dp, 120.dp) else 0.dp, label = "volW")
                    if (volW > 1.dp) Box(Modifier.width(volW).height(btn).padding(horizontal = 8.dp).clipToBounds()) {
                        TrackBar(if (muted) 0f else volume, 0f, live, Modifier.align(Alignment.Center), thumb = true, onSeek = { volume = it; muted = false; volTick++ }, onScrub = { volume = it; muted = false; volTick++ })
                    }
                    BarButton(btn, VidIcons.Chromecast, ico, live) { openCastSettings(context) }
                }
                GlassPill {
                    val sw = (w * 0.075f).coerce(25.dp, 38.dp); val sh = (w * 0.04f).coerce(13.dp, 20.dp)
                    GlassSwitch(autoplay, FeatherIcons.Play, sw, sh, live) { onAutoplay(!autoplay); say("Autoplay ${if (!autoplay) "on" else "off"}") }
                    GlassSwitch(autoSkip, FeatherIcons.FastForward, sw, sh, live) { onAutoSkip(!autoSkip); say("Auto skip ${if (!autoSkip) "on" else "off"}") }
                    GlassSwitch(autoNext, FeatherIcons.SkipForward, sw, sh, live) { onAutoNext(!autoNext); say("Auto next ${if (!autoNext) "on" else "off"}") }
                    BarButton(btn, VidIcons.Camera, ico, live) { screenshot(context, textureView) { say(it.first, it.second) } }
                    if (format == "file") BarButton(btn, VidIcons.Download, ico, live) {
                        if (src != null && format == "file") {
                            runCatching {
                                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                dm.enqueue(DownloadManager.Request(Uri.parse(Streaming.abs(src))).setTitle(title).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "${title.replace(Regex("[^A-Za-z0-9 ._-]"), "")}.mp4"))
                            }.onSuccess { say("Download started") }.onFailure { say("Couldn't start the download", true) }
                        } else say("This server only streams. Pick another one to download.", true)
                    }
                    if (sortedSubs.isNotEmpty()) BarButton(btn, if (captionTrack >= 0) VidIcons.ClosedCaptionsOn else VidIcons.ClosedCaptions, ico, live) {
                        if (readOnly) { say("Only the host controls captions"); return@BarButton }; capLocal = false; captionTrack = if (captionTrack >= 0) -1 else 0
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
            val panelW = (w * 0.6f).coerce(190.dp, 300.dp)
            val top = pad + btn + 10.dp
            Column(
                Modifier.padding(top = top, end = pad).width(panelW).heightIn(max = h - top - pad)
                    .clip(RoundedCornerShape(18.dp)).background(Color(0xD90C0C0F)).border(1.dp, GlassEdge, RoundedCornerShape(18.dp))
                    .pointerInput(Unit) { detectTapGestures { } }
                    .verticalScroll(rememberScrollState()).padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (menuView != "main") MenuBack(when (menuView) { "quality" -> "Quality"; "boost" -> "Volume boost"; "captions" -> "Captions"; "style" -> "Caption style"; "size" -> "Caption size"; "sync" -> "Caption timing"; else -> "Speed" }) { menuView = "main" }
                when (menuView) {
                    "main" -> {
                        if (heights.isNotEmpty()) MenuRow(VidIcons.Settings, "Quality", if (quality > 0) "${quality}p" else "Auto") { menuView = "quality" }
                        MenuRow(VidIcons.VolumeHigh, "Volume boost", boostLabel(boost) + if (readOnly) " · host" else "") { if (!readOnly) menuView = "boost" }
                        if (sortedSubs.isNotEmpty()) {
                            MenuRow(VidIcons.ClosedCaptions, "Captions", (if (captionState == "loading") "Loading…" else if (captionState == "error") "Failed · tap to retry" else sortedSubs.getOrNull(captionTrack)?.label ?: "Off")) { if (captionState == "error") captionTry++ else menuView = "captions" }
                            MenuRow(VidIcons.ClosedCaptionsOn, "Caption style", CAPTION_STYLES[captionStyle]) { menuView = "style" }
                            MenuRow(VidIcons.ClosedCaptionsOn, "Caption size", CAPTION_SIZES[captionSize]) { menuView = "size" }
                            MenuRow(VidIcons.ClosedCaptionsOn, "Caption timing", offsetLabel(capOffset)) { menuView = "sync" }
                        }
                        if (!readOnly) MenuRow(VidIcons.PlaybackSpeedCircle, "Speed", if (speed == 1f) "Normal" else "${speed}x") { menuView = "speed" }
                    }
                    "quality" -> {
                        OptionRow("Auto", quality == -1) { userQuality = true; quality = -1; capPrefs.edit().putInt("quality", -1).apply(); menuView = "main" }
                        heights.forEach { h -> OptionRow("${h}p", quality == h) { userQuality = true; quality = h; capPrefs.edit().putInt("quality", h).apply(); menuView = "main" } }
                    }
                    "boost" -> BOOST_MB.indices.forEach { i -> OptionRow(boostLabel(i), boost == i) { boost = i; menuView = "main" } }
                    "captions" -> {
                        OptionRow("Off", captionTrack < 0) { capLocal = false; captionTrack = -1; menuView = "main" }
                        sortedSubs.forEachIndexed { i, t -> OptionRow(t.label, captionTrack == i) { capLocal = false; captionTrack = i; menuView = "main" } }
                    }
                    "style" -> {
                        CAPTION_STYLES.forEachIndexed { i, s -> OptionRow(s, captionStyle == i) { capLocal = false; captionStyle = i; capPrefs.edit().putInt("style", i).apply() } }
                    }
                    "size" -> CAPTION_SIZES.forEachIndexed { i, s -> OptionRow(s, captionSize == i) { capLocal = false; captionSize = i; capPrefs.edit().putInt("size", i).apply() } }
                    "sync" -> {
                        OptionRow("Captions earlier  (-0.5 s)", false) { capOffset = (capOffset - 500).coerceAtLeast(-30_000) }
                        OptionRow("Captions later  (+0.5 s)", false) { capOffset = (capOffset + 500).coerceAtMost(30_000) }
                        OptionRow("Fine tune earlier  (-0.1 s)", false) { capOffset = (capOffset - 100).coerceAtLeast(-30_000) }
                        OptionRow("Fine tune later  (+0.1 s)", false) { capOffset = (capOffset + 100).coerceAtMost(30_000) }
                        OptionRow("Reset  ·  now ${offsetLabel(capOffset)}", capOffset == 0) { capOffset = 0 }
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
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Glass)
            .border(1.dp, GlassEdge, RoundedCornerShape(50)).padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** The web player's little glass switch with an icon on its knob. */
@Composable private fun GlassSwitch(on: Boolean, icon: ImageVector, w: Dp, h: Dp, enabled: Boolean, onClick: () -> Unit) {
    val knob = h - 4.dp
    val x by animateDpAsState(if (on) w - h + 2.dp else 2.dp, label = "knob")
    Box(Modifier.padding(horizontal = 3.dp).size(w, h).clip(RoundedCornerShape(50)).background(if (on) Color(0x80FFFFFF) else Color(0x66808080)).clickable(enabled = enabled, onClick = onClick)) {
        Box(Modifier.offset(x = x).align(Alignment.CenterStart).size(knob).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color(0xFF17171A), modifier = Modifier.size(knob * 0.62f))
        }
    }
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x14FFFFFF)).border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = inter(12.5f, FontWeight.Medium, lineHeight = 15f), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(value, style = inter(11.5f), color = Color(0x99FFFFFF), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 84.dp))
        if (chevron) { Spacer(Modifier.width(2.dp)); Icon(VidIcons.ChevronRight, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
    }
}

@Composable
private fun MenuBack(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onBack).padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(VidIcons.ChevronLeft, "Back", tint = Color.White, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(4.dp))
        Text(title, style = inter(13f, FontWeight.SemiBold), color = Color.White)
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (selected) Color(0x26FFFFFF) else Color(0x14FFFFFF)).border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = inter(12.5f, FontWeight.Medium), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (selected) Icon(VidIcons.Check, null, tint = Color.White, modifier = Modifier.size(17.dp))
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


private class FloatingEmoji(val id: Long, val emoji: String, val x: Float)

/** Room reactions: a row of emoji to tap (while controls show) and everyone's emoji drifting up over the picture. */
@Composable
private fun ReactionLayer(t: com.anibiar.app.social.WatchTogether, showPicker: Boolean) {
    val floating = remember { mutableStateListOf<FloatingEmoji>() }
    LaunchedEffect(t) {
        var n = 0L
        t.reactions.collect { e -> if (floating.size < 24) floating.add(FloatingEmoji(n++, e, 0.55f + (Math.random().toFloat() * 0.4f))) }
    }
    Box(Modifier.fillMaxSize()) {
        floating.forEach { f ->
            key(f.id) {
                val rise = remember { androidx.compose.animation.core.Animatable(0f) }
                LaunchedEffect(Unit) { rise.animateTo(1f, androidx.compose.animation.core.tween(2200, easing = androidx.compose.animation.core.LinearEasing)); floating.remove(f) }
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    Text(
                        f.emoji, fontSize = 30.sp,
                        modifier = Modifier.offset(x = maxWidth * f.x, y = maxHeight * (0.85f - 0.6f * rise.value)).alpha(1f - rise.value * rise.value),
                    )
                }
            }
        }
        if (showPicker) Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 6.dp).clip(RoundedCornerShape(20.dp)).background(Color(0x66000000)).padding(vertical = 4.dp),
        ) {
            listOf("😂", "😮", "❤️", "👏", "🔥").forEach { e ->
                Text(e, fontSize = 20.sp, modifier = Modifier.clickable { t.react(e) }.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
    }
}
