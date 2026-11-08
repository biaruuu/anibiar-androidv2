package com.anibiar.app.ui.watch

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.anibiar.app.AniBiarApp
import com.anibiar.app.data.EpisodesPayload
import com.anibiar.app.data.HistoryItem
import com.anibiar.app.data.Media
import com.anibiar.app.data.SkipPayload
import com.anibiar.app.data.Streaming
import com.anibiar.app.data.WatchEpisode
import com.anibiar.app.data.WatchPayload
import com.anibiar.app.data.displayTitle
import com.anibiar.app.data.posterUrl
import com.anibiar.app.data.seasonLabel
import com.anibiar.app.ui.home.FavYellow
import com.anibiar.app.ui.home.rememberFav
import com.anibiar.app.ui.theme.Ani
import com.anibiar.app.ui.theme.glass
import com.anibiar.app.ui.theme.AniIcons
import com.anibiar.app.ui.theme.VidIcons
import com.anibiar.app.ui.theme.FeatherIcons
import com.anibiar.app.ui.watch.WebAccent
import androidx.compose.ui.unit.sp
import com.anibiar.app.ui.theme.inter
import kotlinx.coroutines.launch

private fun prettyDate(iso: String?): String? = iso?.take(10)?.let { d ->
    runCatching {
        val p = d.split("-"); val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        "${months[p[1].toInt() - 1]} ${p[2].toInt()}, ${p[0]}"
    }.getOrNull()
}

/** The Watch page: player on top, then episode, audio + server pickers, episode navigation and the anime card. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchScreen(id: Int, startEp: Int, onBack: () -> Unit, onOpenDetails: (Int) -> Unit, onWatch: (Int) -> Unit = {}, joinRoom: com.anibiar.app.social.Room? = null, onRoomMoved: (com.anibiar.app.social.Room) -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as AniBiarApp
    val scope = rememberCoroutineScope()
    val prefsStore = remember { context.getSharedPreferences("watch_prefs", Context.MODE_PRIVATE) }
    var autoplay by remember { mutableStateOf(prefsStore.getBoolean("autoplay", true)) }
    var autoNext by remember { mutableStateOf(prefsStore.getBoolean("autoNext", true)) }
    var autoSkip by remember { mutableStateOf(prefsStore.getBoolean("autoSkip", true)) }

    val anime by produceState<Media?>(null, id) {
        value = app.repository.cachedDetails(id)
        runCatching { app.repository.fetchDetails(id) }.onSuccess { value = it }
    }

    var epsRaw by remember { mutableStateOf<EpisodesPayload?>(null) }
    // Titles, thumbnails and air dates from ani.zip / Jikan fill whatever the streaming servers left blank (cached copy first, then live).
    val epsInfo by produceState<List<com.anibiar.app.data.EpisodeInfo>>(emptyList(), id, anime?.idMal) {
        app.repository.cachedEpisodes(id)?.let { value = it }
        runCatching { app.repository.fetchEpisodes(id, anime?.idMal) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { value = it }
    }
    val eps: EpisodesPayload? = remember(epsRaw, epsInfo) {
        val raw = epsRaw ?: return@remember null
        if (epsInfo.isEmpty()) return@remember raw
        val by = epsInfo.associateBy { it.number }
        fun fill(l: List<WatchEpisode>) = l.map { e ->
            val i = by[e.number] ?: return@map e
            val generic = e.title.isBlank() || Regex("^episode\\s*\\d+$", RegexOption.IGNORE_CASE).matches(e.title.trim())
            e.copy(title = if (generic && !i.title.isNullOrBlank()) i.title else e.title, description = e.description?.takeIf { it.isNotBlank() } ?: i.overview, image = e.image ?: i.image, airDate = e.airDate ?: i.date)
        }
        raw.copy(sub = fill(raw.sub), dub = fill(raw.dub))
    }
    var epsError by remember { mutableStateOf<String?>(null) }
    var epsTry by remember { mutableIntStateOf(0) }
    // Audio and server you used last time for this anime (not when joining someone's room, where the host decides).
    val savedPref = remember(id) { if (joinRoom == null) prefsStore.getString("pref_$id", null)?.split("|") else null }
    var audio by remember { mutableStateOf(joinRoom?.audio ?: savedPref?.getOrNull(0)?.takeIf { it == "sub" || it == "dub" } ?: "sub") }
    var ep by remember { mutableIntStateOf(startEp.coerceAtLeast(0)) }
    var watch by remember { mutableStateOf<WatchPayload?>(null) }
    var watchError by remember { mutableStateOf<String?>(null) }
    var watchTry by remember { mutableIntStateOf(0) }
    var streamName by remember { mutableStateOf<String?>(null) }
    var episodesOpen by remember { mutableStateOf(false) }
    var providerPref by remember { mutableStateOf<String?>(savedPref?.getOrNull(1)?.takeIf { it.isNotBlank() }) }
    var synopsisEpisode by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }

    // ---- Watch together (the room lives in RoomSession, so leaving this page doesn't end it) ----
    val social = app.social
    val session = app.roomSession
    val room = session.room?.takeIf { it.animeId == id }
    val together = if (room != null) session.together else null
    val voice = if (room != null) session.voice else null
    val roomProfiles = session.profiles
    var togetherOpen by remember { mutableStateOf(false) }
    var starting by remember { mutableStateOf(false) }
    val isHost = room?.hostId == social.myId
    val roomMembers by (together?.members ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptySet<String>()) }).collectAsStateWithLifecycle()
    val locked = room != null && !isHost
    fun hostOnly() { android.widget.Toast.makeText(context, "Only the host can change the episode", android.widget.Toast.LENGTH_SHORT).show() }
    var pickerOpen by remember { mutableStateOf(false) }
    // Voice call: starts by itself once I'm in a room (and have allowed the microphone).
    var micOk by remember { mutableStateOf(androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
    // The microphone has to be allowed before joining a call; once in, anyone can mute.
    var pendingJoin by remember { mutableStateOf<(() -> Unit)?>(null) }
    val micLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok ->
        micOk = ok
        val go = pendingJoin; pendingJoin = null
        if (ok) go?.invoke() else if (go != null) android.widget.Toast.makeText(context, "Allow the microphone to join the call. You can mute yourself once you're in.", android.widget.Toast.LENGTH_LONG).show()
    }
    fun withMic(action: () -> Unit) { if (micOk) action() else { pendingJoin = action; micLauncher.launch(android.Manifest.permission.RECORD_AUDIO) } }
    val vMuted by (voice?.muted ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) }).collectAsStateWithLifecycle()
    LaunchedEffect(joinRoom?.id) { joinRoom?.let { session.enter(it) } }
    // Once the microphone is allowed, ask whether to join with the mic on or muted (nothing is sent before they choose).
    var askMic by remember(together) { mutableStateOf(false) }
    LaunchedEffect(together, micOk) { if (together != null && micOk && session.voice == null) askMic = true }
    if (askMic) androidx.compose.material3.AlertDialog(
        onDismissRequest = {},
        containerColor = Color(0xFF1A1A1C),
        title = { Text("Join the voice call", style = inter(17f, FontWeight.Bold), color = Color.White) },
        text = { Text("Do you want your microphone on, or join muted? You can change it any time.", style = inter(14f, FontWeight.Normal), color = Color(0xFFCFCDD3)) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { askMic = false; session.ensureVoice(); session.voice?.setMuted(false) }) { Text("Mic on", style = inter(14f, FontWeight.SemiBold), color = Ani.Lavender) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { askMic = false; session.ensureVoice(); session.voice?.setMuted(true) }) { Text("Join muted", style = inter(14f, FontWeight.SemiBold), color = Color(0xFFCFCDD3)) } },
    )
    val speaking by (voice?.speaking ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptySet<String>()) }).collectAsStateWithLifecycle()
    val voiceLinks by (voice?.states ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptyMap<String, String>()) }).collectAsStateWithLifecycle()
    val mutedUsers by (together?.mutedUsers ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptySet<String>()) }).collectAsStateWithLifecycle()
    val memberList = roomMembers.toList()
    var driftMs by remember { mutableStateOf(0L) }
    LaunchedEffect(together) { val t = together ?: return@LaunchedEffect; while (true) { driftMs = t.driftMs; kotlinx.coroutines.delay(500) } }
    LaunchedEffect(together) {
        val t = together ?: return@LaunchedEffect
        t.changes.collect { c -> if (c.episode != ep) { ep = c.episode; synopsisEpisode = false; watch = null }; if (c.audio != audio) { audio = c.audio; streamName = null } }
    }
    // Any local change of episode or audio (buttons, auto-next, picker) is shared with the room.
    LaunchedEffect(ep, audio, together, isHost) {
        val t = together ?: return@LaunchedEffect
        if (!isHost) return@LaunchedEffect
        if (t.episode != ep || t.audio != audio) t.changeEpisode(ep, audio)
    }

    LaunchedEffect(id, epsTry) {
        epsRaw = null; epsError = null
        runCatching { Streaming.episodesLive(id) { epsRaw = it } }.onSuccess { }.onFailure { if (epsRaw == null) epsError = it.message ?: "Couldn't load episodes" }
    }
    // Airing shows get new episodes all the time: re-check every 3 minutes while the player is open.
    LaunchedEffect(id, anime?.status) {
        if (anime?.status != null && anime?.status != "RELEASING") return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(180_000)
            runCatching { Streaming.episodesLive(id) { epsRaw = it } }
        }
    }
    val list: List<WatchEpisode> = eps?.let { if (audio == "dub" && it.dub.isNotEmpty()) it.dub else it.sub }.orEmpty()
    LaunchedEffect(eps, audio) {
        val e = eps ?: return@LaunchedEffect
        if (audio == "dub" && e.dub.isEmpty()) audio = "sub"
        if (audio == "sub" && e.sub.isEmpty() && e.dub.isNotEmpty()) audio = "dub"
    }
    LaunchedEffect(list, ep) { if (list.isNotEmpty() && list.none { it.number == ep }) ep = (list.firstOrNull { it.number >= startEp } ?: list.first()).number }
    val current = list.firstOrNull { it.number == ep }
    val position = list.indexOfFirst { it.number == ep }
    val prev = list.getOrNull(position - 1)
    val next = if (position >= 0) list.getOrNull(position + 1) else null

    LaunchedEffect(id, audio, current?.number, watchTry, providerPref) {
        val c = current ?: return@LaunchedEffect
        watchError = null; watch = null
        if (watchTry > 0) Streaming.forget(id, audio, c.number)
        val first = providerPref?.let { p -> c.providers.firstOrNull { it.equals(p, true) } }
        val servers = if (first != null) listOf(first) + c.providers.filter { it != first } else c.providers
        runCatching { Streaming.watch(id, audio, c.number, servers) }
            .onSuccess { watch = it; if (streamName == null || it.streams.none { s -> s.name == streamName }) streamName = null }
            .onFailure { watchError = it.message ?: "Couldn't load this episode" }
    }
    LaunchedEffect(id, audio, providerPref, room) { if (room == null) prefsStore.edit().putString("pref_$id", "$audio|${providerPref.orEmpty()}").apply() }
    val failedStreams = remember(id, current?.number, audio) { mutableSetOf<String>() }
    val stream = watch?.streams?.let { all -> all.firstOrNull { it.name == streamName } ?: all.firstOrNull() }
    val ready = watch != null && watch?.episode == current?.number && watch?.audio == audio
    // Host shares its server; a guest switches to it once per episode so everyone sees the same captions.
    LaunchedEffect(together, stream?.name, isHost) { val t = together; if (t != null && isHost) t.srv = stream?.name.orEmpty() }
    var followedSrv by remember(together, current?.number) { mutableStateOf(false) }
    LaunchedEffect(together, locked, ready) {
        val t = together ?: return@LaunchedEffect
        if (!locked || !ready) return@LaunchedEffect
        t.commands.collect { c ->
            val n = c.srv
            if (!followedSrv && n != null) {
                followedSrv = true
                if (n != stream?.name && watch?.streams?.any { it.name == n } == true) streamName = n
                // The host is showing captions but my stream is flagged as burned-in: try a stream with separate captions.
                else if (c.csrc != null && stream?.kind == "hard") watch?.streams?.firstOrNull { it.kind == "soft" }?.let { streamName = it.name }
            }
        }
    }

    // Quietly load the next episode's streams while this one plays.
    LaunchedEffect(ready, next?.number, audio) {
        val n = next ?: return@LaunchedEffect
        if (ready) Streaming.prefetch(id, audio, n.number, n.providers)
    }

    // Backup intro/outro times (AniSkip) when the server has none.
    val backup by produceState<SkipPayload?>(null, anime?.idMal, current?.number) {
        value = null
        val mal = anime?.idMal; val n = current?.number
        if (mal != null && n != null && n > 0) value = Streaming.skip(mal, n)
    }
    val own = stream?.intro != null || stream?.outro != null
    val intro = if (own) stream?.intro else (watch?.intro ?: backup?.intro)
    val outro = if (own) stream?.outro else (watch?.outro ?: backup?.outro)

    val name = anime?.displayTitle ?: eps?.title ?: "Loading…"
    val seasons by produceState(emptyList<com.anibiar.app.ui.watch.SeasonEntry>(), anime?.id) {
        val m = anime ?: return@produceState
        value = runCatching { com.anibiar.app.ui.watch.buildSeasons(m, app.repository.fetchFranchise(m)) }.getOrDefault(emptyList())
    }
    val watchedNumbers = remember(id, ep) {
        val h = app.history.load().firstOrNull { it.mediaId == id }
        if (h == null) emptySet() else (1 until h.episode).toSet() + (if (h.progress >= 0.9f) setOf(h.episode) else emptySet())
    }
    val subNumbers = remember(eps) { eps?.sub?.map { it.number }?.toSet().orEmpty() }
    val dubNumbers = remember(eps) { eps?.dub?.map { it.number }?.toSet().orEmpty() }
    val poster = current?.image ?: anime?.bannerImage ?: anime?.posterUrl

    // Fullscreen: landscape + immersive.
    val activity = context as? Activity
    val view = LocalView.current
    DisposableEffect(fullscreen) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
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
    BackHandler(enabled = fullscreen) { if (locked) android.widget.Toast.makeText(context, "The host controls fullscreen", android.widget.Toast.LENGTH_SHORT).show() else { fullscreen = false; together?.shareFullscreen(false) } }
    LaunchedEffect(together, locked) { val t = together ?: return@LaunchedEffect; if (locked) t.fsEvents.collect { fullscreen = it } }

    var requests by remember { mutableStateOf<List<com.anibiar.app.social.RoomRequest>>(emptyList()) }
    LaunchedEffect(together) {
        val t = together ?: return@LaunchedEffect
        t.requests.collect { requests = requests + it; android.widget.Toast.makeText(context, "A friend sent a request", android.widget.Toast.LENGTH_SHORT).show() }
    }
    fun select(n: Int) {
        if (locked) {
            episodesOpen = false
            together?.request(null, name, null, n)
            android.widget.Toast.makeText(context, "Request sent to the host", android.widget.Toast.LENGTH_SHORT).show()
            return
        }; ep = n; synopsisEpisode = false; episodesOpen = false; watch = null }
    fun saveProgress(t: Double, d: Double, episode: Int) {
        if (d <= 0) return
        app.history.save(HistoryItem(id, name, anime?.bannerImage ?: anime?.posterUrl, episode, (t / d).toFloat().coerceIn(0f, 1f)))
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        if (!fullscreen) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().height(52.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                com.anibiar.app.ui.theme.GlassBack(onBack)
                Spacer(Modifier.width(12.dp))
                Text(name, style = inter(16f, FontWeight.SemiBold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val landscape = androidx.compose.ui.platform.LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }
        Box(if (fullscreen) Modifier.fillMaxSize() else if (landscape && room != null) Modifier.fillMaxWidth().weight(1.6f, fill = false).aspectRatio(16f / 9f, matchHeightConstraintsFirst = true).align(Alignment.CenterHorizontally).background(Color.Black) else Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
            KiroPlayer(
                src = if (ready) stream?.src else null, format = stream?.format ?: "hls",
                title = current?.let { "$name · Episode ${it.number}" } ?: name, poster = poster,
                subtitles = if (stream?.kind == "hard") emptyList() else stream?.subtitles.orEmpty(),
                intro = intro, outro = outro, skipLength = backup?.length,
                autoplay = autoplay, autoNext = autoNext, autoSkip = autoSkip,
                onAutoplay = { autoplay = it; prefsStore.edit().putBoolean("autoplay", it).apply() },
                onAutoNext = { autoNext = it; prefsStore.edit().putBoolean("autoNext", it).apply() },
                onAutoSkip = { autoSkip = it; prefsStore.edit().putBoolean("autoSkip", it).apply() },
                startAt = together?.resumeSeconds(current?.number),
                onTime = { t, d -> current?.let { saveProgress(t, d, it.number) } },
                onEnded = { if (autoNext && next != null && !locked) select(next.number) },
                onFail = {
                    // Move to the best stream that hasn't failed yet and say so.
                    stream?.let { failedStreams += it.id }
                    val next = watch?.streams.orEmpty().firstOrNull { it.id !in failedStreams }
                    if (next != null && next.id != stream?.id) {
                        streamName = next.name
                        android.widget.Toast.makeText(context, "Switched to ${next.name}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                fullscreen = fullscreen,
                onFullscreen = {
                    if (locked) android.widget.Toast.makeText(context, "Only the host can go fullscreen", android.widget.Toast.LENGTH_SHORT).show()
                    else { fullscreen = it; together?.shareFullscreen(it) }
                },
                modifier = Modifier.fillMaxSize(),
                together = together,
                readOnly = locked,
                resumeFraction = if (together == null) remember(id, current?.number) { app.history.load().firstOrNull { it.mediaId == id && it.episode == current?.number }?.progress ?: 0f } else 0f,
            )
            if (room != null && fullscreen) {
                com.anibiar.app.ui.watch.AvatarStrip(memberList, roomProfiles, speaking, mutedUsers, social.myId.orEmpty(), Modifier.align(Alignment.CenterEnd).padding(end = 10.dp))
            }
            if (!ready) {
                Box(Modifier.fillMaxSize().background(Color(0xD9000000)), contentAlignment = Alignment.Center) {
                    if (poster != null && watchError == null && epsError == null) AsyncImage(poster, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().background(Color.Black), alpha = 0.3f)
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        val err = watchError ?: epsError
                        if (err != null) {
                            Text("Couldn't load this episode", style = inter(14f, FontWeight.SemiBold), color = Color.White)
                            Spacer(Modifier.height(4.dp))
                            Text(err, style = inter(12f, lineHeight = 16f), color = Color(0xB3FFFFFF))
                            Spacer(Modifier.height(10.dp))
                            val ctx = LocalContext.current
                            Text("Copy report", style = inter(12.5f, FontWeight.SemiBold), color = Color.White,
                                modifier = Modifier.glass(RoundedCornerShape(50)).clickable {
                                    val report = "Kiro report\nanime=$id ep=$ep audio=$audio server=${providerPref ?: "auto"}\nstage=${if (epsError != null) "episodes" else "stream"}\nerror=$err\nplayer=${Streaming.lastPlayError ?: "-"}\nepisodesLoaded=${epsRaw?.sub?.size ?: 0}/${epsRaw?.dub?.size ?: 0}\ntime=${java.util.Date()}"
                                    (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("report", report))
                                    android.widget.Toast.makeText(ctx, "Report copied", android.widget.Toast.LENGTH_SHORT).show()
                                }.padding(horizontal = 18.dp, vertical = 8.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Try again", style = inter(12.5f, FontWeight.SemiBold), color = Color.Black,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White).clickable { if (epsError != null) epsTry++ else watchTry++ }.padding(horizontal = 18.dp, vertical = 8.dp))
                        } else {
                            CircularProgressIndicator(Modifier.size(28.dp), color = Color.White, strokeWidth = 2.5.dp)
                            Spacer(Modifier.height(8.dp))
                            Text(if (eps != null) "Finding the best stream…" else "Loading episodes…", style = inter(12f), color = Color(0xB3FFFFFF))
                        }
                    }
                }
            }
        }

        // In a room the page is the call: tiles fill the screen under the video, controls pinned below.
        if (!fullscreen && !episodesOpen && room != null) Column(Modifier.weight(1f).fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            requests.firstOrNull()?.let { rq ->
                val who = roomProfiles[rq.from]?.shownName ?: "Someone"
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(com.anibiar.app.ui.theme.GlassFill).border(1.dp, Ani.Lavender.copy(alpha = 0.6f), RoundedCornerShape(14.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("$who requests", style = inter(12f), color = Ani.Muted)
                        Text(if (rq.animeId != null) rq.title else "Episode ${rq.episode}", style = inter(14.5f, FontWeight.SemiBold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("Dismiss", style = inter(13f), color = Ani.Muted, modifier = Modifier.clickable { requests = requests.drop(1) }.padding(8.dp))
                    Text("Accept", style = inter(13.5f, FontWeight.Bold), color = Color.Black,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFB7A9FF)).clickable {
                            requests = requests.drop(1)
                            val r = room; val t = together
                            if (rq.animeId != null && r != null && t != null) {
                                val next = r.copy(animeId = rq.animeId, title = rq.title, cover = rq.cover, episode = 1, audio = "sub")
                                session.moveTo(next)
                                onRoomMoved(next)
                            } else if (rq.animeId == null) { ep = rq.episode; synopsisEpisode = false; watch = null }
                        }.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }
            com.anibiar.app.ui.watch.RoomPanel(
                members = memberList, profiles = roomProfiles, hostId = room?.hostId.orEmpty(), myId = social.myId.orEmpty(),
                speaking = speaking, muted = mutedUsers, micOk = micOk, myMuted = vMuted, links = voiceLinks,
                onMic = { if (!micOk) micLauncher.launch(android.Manifest.permission.RECORD_AUDIO) else voice?.setMuted(!vMuted) },
                onEpisodes = { episodesOpen = true },
                onAnime = { pickerOpen = true },
                onInvite = { togetherOpen = true },
                onLeave = { session.leave(isHost) },
                modifier = Modifier.weight(1f),
            )
        }
        if (!fullscreen && episodesOpen) {
            com.anibiar.app.ui.watch.EpisodeSheet(
                title = name, episodes = list, current = ep, watched = watchedNumbers,
                fallbackImage = anime?.bannerImage ?: anime?.posterUrl, subNumbers = subNumbers, dubNumbers = dubNumbers,
                onSelect = { select(it) }, onClose = { episodesOpen = false },
                modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 2.dp),
            )
        }
        if (!fullscreen && !episodesOpen && room == null) Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 14.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Episode heading
            val generic = current?.title.isNullOrBlank() || Regex("^episode\\s*\\d+$", RegexOption.IGNORE_CASE).matches(current?.title.orEmpty().trim())
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (current != null) "Episode ${current.number}" else " ", style = inter(12f), color = Ani.Muted)
                    prettyDate(current?.airDate)?.let { Text("·", style = inter(12f), color = Ani.Muted); Text(it, style = inter(12f), color = Ani.Muted) }
                    if (current?.filler == true) { Text("·", style = inter(12f), color = Ani.Muted); Text("Filler", style = inter(12f), color = Ani.Muted) }
                }
                Text(if (generic) name else current?.title.orEmpty(), style = inter(17.5f, FontWeight.Bold, lineHeight = 23f), color = Color.White, modifier = Modifier.padding(top = 6.dp))
            }

            // Audio + provider
            var audioOpen by remember { mutableStateOf(false) }
            var serverOpen by remember { mutableStateOf(false) }
            val menuBg = Modifier.background(Color(0xF716161C)).border(1.5.dp, Color(0x38FFFFFF), RoundedCornerShape(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) {
                    PickerButton(if (audio == "sub") VidIcons.ClosedCaptions else AniIcons.Mic, if (audio == "sub") "Sub" else "Dub", audioOpen) { audioOpen = true }
                    DropdownMenu(audioOpen, { audioOpen = false }, modifier = menuBg) {
                        MenuTitle("Audio")
                        listOf("sub" to "Sub", "dub" to "Dub").forEach { (k, label) ->
                            val available = if (k == "sub") !eps?.sub.isNullOrEmpty() else !eps?.dub.isNullOrEmpty()
                            DropdownMenuItem(
                                text = { Text(label + (if (available) "  ·  ${(if (k == "sub") eps?.sub?.size else eps?.dub?.size) ?: 0} eps" else "  ·  Unavailable"), style = inter(14.5f), color = if (audio == k) WebAccent else if (available) Color.White else Ani.Muted) },
                                enabled = available, onClick = { audioOpen = false; if (locked) hostOnly() else { audio = k; streamName = null } },
                                trailingIcon = { if (audio == k && available) Icon(VidIcons.Check, null, tint = WebAccent, modifier = Modifier.size(20.dp)) },
                            )
                        }
                    }
                }
                Box(Modifier.weight(1f)) {
                    PickerButton(FeatherIcons.Zap, (providerPref ?: stream?.name?.substringBefore(" ") ?: "Server").replaceFirstChar { it.uppercase() }, serverOpen) { serverOpen = true }
                    DropdownMenu(serverOpen, { serverOpen = false }, modifier = menuBg) {
                        MenuTitle("Provider")
                        val provs = list.flatMap { it.providers }.distinct()
                        val shown = (providerPref ?: stream?.name?.substringBefore(" ")).orEmpty()
                        if (provs.isEmpty()) DropdownMenuItem(text = { Text("Loading…", style = inter(14f), color = Ani.Muted) }, enabled = false, onClick = {})
                        provs.forEach { pv ->
                            val on = pv.equals(shown, true)
                            DropdownMenuItem(
                                text = { Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(pv.replaceFirstChar { it.uppercase() }, style = inter(14.5f), color = if (on) WebAccent else Color.White, modifier = Modifier.weight(1f, fill = false))
                                    Spacer(Modifier.width(10.dp))
                                    Text("${list.count { e -> e.providers.contains(pv) }} eps", style = inter(10.5f, FontWeight.SemiBold), color = Ani.Muted,
                                        modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(50)).background(Color(0x0DFFFFFF)).padding(horizontal = 8.dp, vertical = 2.dp))
                                } },
                                onClick = { serverOpen = false; if (locked) hostOnly() else { providerPref = pv; streamName = null } },
                                trailingIcon = { if (on) Icon(VidIcons.Check, null, tint = WebAccent, modifier = Modifier.size(20.dp)) },
                            )
                        }
                        val all = watch?.streams.orEmpty()
                        if (all.size > 1) {
                            MenuTitle("Server")
                            all.forEach { sv ->
                                DropdownMenuItem(
                                    text = { Text(sv.name + (if (audio == "sub") (if (sv.kind == "soft") "  ·  s-sub" else "  ·  h-sub") else "") + (if (sv.height > 0) "  ·  ${sv.height}p" else ""), style = inter(14.5f), color = if (stream?.id == sv.id) WebAccent else Color.White) },
                                    onClick = { streamName = sv.name; serverOpen = false },
                                    trailingIcon = { if (stream?.id == sv.id) Icon(VidIcons.Check, null, tint = WebAccent, modifier = Modifier.size(20.dp)) },
                                )
                            }
                        }
                    }
                }
            }

            // Watch together
            if (room == null) Row(
                Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1B1B1F))
                    .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(16.dp))
                    .clickable {
                        if (social.myId == null) android.widget.Toast.makeText(context, "Log in to watch together", android.widget.Toast.LENGTH_SHORT).show()
                        else if (!starting) withMic {
                            // Straight into the full-screen room; invite friends from there.
                            starting = true
                            scope.launch {
                                runCatching { social.createRoom(id, name, anime?.posterUrl, ep, audio) }
                                    .onSuccess { session.enter(it) }
                                    .onFailure { android.widget.Toast.makeText(context, it.message ?: "Couldn't start the room", android.widget.Toast.LENGTH_SHORT).show() }
                                starting = false
                            }
                        }
                    }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(WebAccent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) { Icon(AniIcons.PersonAdd, null, tint = WebAccent, modifier = Modifier.size(17.dp)) }
                Spacer(Modifier.width(12.dp))
                Text(
                    if (starting) "Starting…" else if (room == null) "Watch together" else "In a room · ${roomMembers.size} watching",
                    style = inter(14f, FontWeight.SemiBold), color = if (room != null) Ani.Lime else Color.White,
                    modifier = Modifier.weight(1f),
                )
                if (room != null) {
                    Text(
                        if (!micOk) "Mic off" else if (vMuted) "Muted" else "Mic on",
                        style = inter(12.5f, FontWeight.SemiBold), color = if (micOk && !vMuted) Color.Black else Color.White,
                        modifier = Modifier.then(if (micOk && !vMuted) Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFB7A9FF)) else Modifier.glass(RoundedCornerShape(50)))
                            .clickable { if (!micOk) micLauncher.launch(android.Manifest.permission.RECORD_AUDIO) else voice?.setMuted(!vMuted) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            // Previous / Episodes / Next
            val edge = Color(0x38FFFFFF)
            val barShape = RoundedCornerShape(13.dp)
            Row(Modifier.fillMaxWidth().height(42.dp).clip(barShape).background(Color(0x8C101014)).border(1.5.dp, edge, barShape)) {
                Row(Modifier.weight(1f).fillMaxSize().clickable(enabled = prev != null) { prev?.let { select(it.number) } }.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (prev != null) { Icon(VidIcons.ChevronLeft, null, tint = Color.White, modifier = Modifier.size(19.dp)); Text(" Ep ${prev.number}", style = inter(14f), color = Ani.Muted) }
                }
                Box(Modifier.width(1.5.dp).fillMaxSize().background(edge))
                Row(Modifier.fillMaxSize().weight(1.15f).clickable { episodesOpen = true }, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(FeatherIcons.List, null, tint = Color.White, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Episodes", style = inter(14f, FontWeight.Medium), color = Color.White)
                }
                Box(Modifier.width(1.5.dp).fillMaxSize().background(edge))
                Row(Modifier.weight(1f).fillMaxSize().clickable(enabled = next != null) { next?.let { select(it.number) } }.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    if (next != null) { Text("Ep ${next.number} ", style = inter(14f), color = Ani.Muted); Icon(VidIcons.ChevronRight, null, tint = Color.White, modifier = Modifier.size(19.dp)) }
                }
            }

            // Anime card
            com.anibiar.app.ui.watch.WebCard {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    val ps = RoundedCornerShape(14.dp)
                    Box(Modifier.width(50.dp).aspectRatio(2f / 3f).clip(ps).border(1.5.dp, Color(0x38FFFFFF), ps).background(Ani.Item).clickable { onOpenDetails(id) }) {
                        anime?.posterUrl?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(name, style = inter(15.2f, FontWeight.SemiBold, lineHeight = 20f), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { onOpenDetails(id) })
                        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            anime?.seasonLabel?.let { Text(it, style = inter(12f), color = Ani.Muted) }
                            if (subNumbers.isNotEmpty()) { Text("·", color = Ani.Muted); Icon(VidIcons.ClosedCaptions, null, tint = Ani.Muted, modifier = Modifier.size(14.dp)); Text("${subNumbers.size}", style = inter(12f), color = Ani.Muted) }
                            if (dubNumbers.isNotEmpty()) { Text("·", color = Ani.Muted); Icon(AniIcons.Mic, null, tint = Ani.Muted, modifier = Modifier.size(13.dp)); Text("${dubNumbers.size}", style = inter(12f), color = Ani.Muted) }
                        }
                    }
                    anime?.let { m ->
                        val (fav, toggle) = rememberFav(m)
                        val shape = RoundedCornerShape(16.dp)
                        Box(Modifier.size(44.dp).clip(shape).background(Color(0x0FFFFFFF)).border(1.dp, if (fav) FavYellow.copy(alpha = 0.6f) else Color(0x40FFFFFF), shape).clickable(onClick = toggle), contentAlignment = Alignment.Center) {
                            Icon(if (fav) AniIcons.BookmarkFilled else AniIcons.Bookmark, if (fav) "Remove from favorites" else "Add to favorites", tint = if (fav) FavYellow else Color.White, modifier = Modifier.size(22.dp))
                        }
                    }
                }
                val desc = remember(anime?.id) { anime?.description?.replace(Regex("<[^>]*>"), " ")?.replace(Regex("\\s+"), " ")?.trim().orEmpty() }
                val shown = if (synopsisEpisode && !current?.description.isNullOrBlank()) current?.description.orEmpty() else if (!current?.description.isNullOrBlank()) current?.description.orEmpty() else desc
                val isEpisode = !current?.description.isNullOrBlank()
                if (shown.isNotBlank()) {
                    Text(shown, style = inter(12.8f, lineHeight = 20.5f), color = Color(0xADFFFFFF), maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 14.dp))
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isEpisode) "Episode synopsis" else "Synopsis", style = inter(12.8f), color = Ani.Muted)
                        Spacer(Modifier.weight(1f))
                        Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable { expanded = !expanded }.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (expanded) "Show less" else "Show more", style = inter(13f, FontWeight.Medium), color = Color.White)
                            Spacer(Modifier.width(4.dp))
                            Icon(FeatherIcons.ChevronDown, null, tint = Color.White, modifier = Modifier.size(17.dp).rotate(if (expanded) 180f else 0f))
                        }
                    }
                }
            }

            if (seasons.size > 1) com.anibiar.app.ui.watch.SeasonsCard(seasons, id) { sid -> onWatch(sid) }
            com.anibiar.app.ui.watch.RelatedSections(anime) { aid -> onOpenDetails(aid) }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (togetherOpen && room != null) {
        com.anibiar.app.ui.watch.TogetherSheet(
            room = room, together = together, isHost = isHost, starting = starting,
            onStart = {
                withMic {
                    starting = true
                    scope.launch {
                        runCatching { social.createRoom(id, name, anime?.posterUrl, ep, audio) }
                            .onSuccess { session.enter(it) }
                            .onFailure { android.widget.Toast.makeText(context, it.message ?: "Couldn't start the room", android.widget.Toast.LENGTH_SHORT).show() }
                        starting = false
                    }
                }
            },
            onLeave = { session.leave(isHost); togetherOpen = false },
            onDismiss = { togetherOpen = false },
            onPickAnime = { togetherOpen = false; pickerOpen = true },
            audio = audio, dubAvailable = !eps?.dub.isNullOrEmpty(), onAudio = { audio = it; streamName = null },
            servers = watch?.streams?.map { it.name }.orEmpty(), server = stream?.name, onServer = { streamName = it },
        )
    }
    if (pickerOpen) {
        com.anibiar.app.ui.watch.AnimePickerSheet(
            onDismiss = { pickerOpen = false }, isHost = isHost,
            onPick = { m ->
                pickerOpen = false
                val r = room; val t = together
                if (r != null && t != null && isHost) {
                    val next = r.copy(animeId = m.id, title = m.displayTitle, cover = m.posterUrl, episode = 1, audio = "sub")
                    session.moveTo(next)
                    onRoomMoved(next)
                } else if (t != null) {
                    t.request(m.id, m.displayTitle, m.posterUrl, 1)
                    android.widget.Toast.makeText(context, "Request sent to the host", android.widget.Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
}

@Composable
private fun PickerButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, open: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.fillMaxWidth().height(40.dp).glass(shape).clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = inter(12.8f, FontWeight.Medium), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(8.dp))
        Icon(FeatherIcons.ChevronDown, null, tint = Ani.Muted, modifier = Modifier.size(16.dp).rotate(if (open) 180f else 0f))
    }
}

@Composable
private fun MenuTitle(t: String) {
    Text(t.uppercase(), style = inter(10.8f, FontWeight.SemiBold).copy(letterSpacing = 1.5.sp), color = Ani.Muted, modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 4.dp))
}

@Composable
private fun WebSwitch(on: Boolean) {
    Box(Modifier.width(35.dp).height(20.dp).clip(RoundedCornerShape(50)).background(if (on) WebAccent else Color(0x29FFFFFF))) {
        Box(Modifier.padding(start = if (on) 17.dp else 2.5.dp, top = 2.5.dp).size(15.dp).clip(CircleShape).background(Color.White))
    }
}

private fun Modifier.rotateHalf() = this.rotate(180f)
