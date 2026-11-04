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
import com.anibiar.app.ui.theme.AniIcons
import com.anibiar.app.ui.theme.VidIcons
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
fun WatchScreen(id: Int, startEp: Int, onBack: () -> Unit, onOpenDetails: (Int) -> Unit, joinRoom: com.anibiar.app.social.Room? = null) {
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

    var eps by remember { mutableStateOf<EpisodesPayload?>(null) }
    var epsError by remember { mutableStateOf<String?>(null) }
    var epsTry by remember { mutableIntStateOf(0) }
    var audio by remember { mutableStateOf(joinRoom?.audio ?: "sub") }
    var ep by remember { mutableIntStateOf(startEp.coerceAtLeast(0)) }
    var watch by remember { mutableStateOf<WatchPayload?>(null) }
    var watchError by remember { mutableStateOf<String?>(null) }
    var watchTry by remember { mutableIntStateOf(0) }
    var streamName by remember { mutableStateOf<String?>(null) }
    var episodesOpen by remember { mutableStateOf(false) }
    var synopsisEpisode by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }

    // ---- Watch together ----
    val social = app.social
    var room by remember { mutableStateOf(joinRoom) }
    var together by remember { mutableStateOf<com.anibiar.app.social.WatchTogether?>(null) }
    var togetherOpen by remember { mutableStateOf(false) }
    var starting by remember { mutableStateOf(false) }
    val isHost = room?.hostId == social.myId
    val roomEnded by (together?.ended ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) }).collectAsStateWithLifecycle()
    val roomMembers by (together?.members ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptySet<String>()) }).collectAsStateWithLifecycle()
    LaunchedEffect(room?.id) {
        val r = room ?: return@LaunchedEffect
        if (together == null) together = com.anibiar.app.social.WatchTogether(social, app.appScope, r).also { it.start() }
    }
    DisposableEffect(Unit) { onDispose { together?.leave(endForAll = room?.hostId == social.myId); together = null } }
    LaunchedEffect(together) {
        val t = together ?: return@LaunchedEffect
        t.changes.collect { c -> if (c.episode != ep) { ep = c.episode; synopsisEpisode = false; watch = null }; if (c.audio != audio) { audio = c.audio; streamName = null } }
    }
    LaunchedEffect(roomEnded) {
        if (roomEnded) { together?.let { /* host is gone; keep watching alone */ }; android.widget.Toast.makeText(context, "The host ended the room", android.widget.Toast.LENGTH_SHORT).show(); room = null; together = null }
    }
    // Any local change of episode or audio (buttons, auto-next, picker) is shared with the room.
    LaunchedEffect(ep, audio, together) {
        val t = together ?: return@LaunchedEffect
        if (t.episode != ep || t.audio != audio) t.changeEpisode(ep, audio)
    }

    LaunchedEffect(id, epsTry) {
        eps = null; epsError = null
        runCatching { Streaming.episodes(id) }.onSuccess { eps = it }.onFailure { epsError = it.message ?: "Couldn't load episodes" }
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

    LaunchedEffect(id, audio, current?.number, watchTry) {
        val c = current ?: return@LaunchedEffect
        watchError = null; watch = null
        runCatching { Streaming.watch(id, audio, c.number, c.providers) }
            .onSuccess { watch = it; if (streamName == null || it.streams.none { s -> s.name == streamName }) streamName = null }
            .onFailure { watchError = it.message ?: "Couldn't load this episode" }
    }
    val stream = watch?.streams?.let { all -> all.firstOrNull { it.name == streamName } ?: all.firstOrNull() }
    val ready = watch != null && watch?.episode == current?.number && watch?.audio == audio

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
    BackHandler(enabled = fullscreen) { fullscreen = false }

    fun select(n: Int) { ep = n; synopsisEpisode = false; episodesOpen = false; watch = null }
    fun saveProgress(t: Double, d: Double, episode: Int) {
        if (d <= 0) return
        app.history.save(HistoryItem(id, name, anime?.bannerImage ?: anime?.posterUrl, episode, (t / d).toFloat().coerceIn(0f, 1f)))
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        if (!fullscreen) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().height(52.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0x1FFFFFFF)).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                    Icon(AniIcons.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(name, style = inter(16f, FontWeight.SemiBold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(if (fullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
            KiroPlayer(
                src = if (ready) stream?.src else null, format = stream?.format ?: "hls",
                title = current?.let { "$name · Episode ${it.number}" } ?: name, poster = poster,
                subtitles = if (stream?.kind == "hard") emptyList() else stream?.subtitles.orEmpty(),
                intro = intro, outro = outro, skipLength = backup?.length,
                autoplay = autoplay, autoNext = autoNext, autoSkip = autoSkip,
                onAutoplay = { autoplay = it; prefsStore.edit().putBoolean("autoplay", it).apply() },
                onAutoNext = { autoNext = it; prefsStore.edit().putBoolean("autoNext", it).apply() },
                onAutoSkip = { autoSkip = it; prefsStore.edit().putBoolean("autoSkip", it).apply() },
                startAt = null,
                onTime = { t, d -> current?.let { saveProgress(t, d, it.number) } },
                onEnded = { if (autoNext && next != null) select(next.number) },
                onFail = {
                    val all = watch?.streams.orEmpty(); val at = all.indexOfFirst { it.id == stream?.id }
                    if (at in 0 until all.lastIndex) streamName = all[at + 1].name
                },
                fullscreen = fullscreen, onFullscreen = { fullscreen = it },
                modifier = Modifier.fillMaxSize(),
                together = together,
            )
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

        if (!fullscreen) Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 14.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Episode heading
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (current != null) "Episode ${current.number}" + (prettyDate(current.airDate)?.let { " · $it" } ?: "") else " ",
                    style = inter(14f), color = Color(0xFF949BA4),
                )
                Text(current?.title ?: "…", style = inter(22f, FontWeight.ExtraBold, lineHeight = 27f), color = Color.White)
            }

            // Audio + server
            var audioOpen by remember { mutableStateOf(false) }
            var serverOpen by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) {
                    PickerButton(if (audio == "sub") VidIcons.ClosedCaptions else null, if (audio == "sub") "Sub" else "Dub", audioOpen, audio == "dub") { audioOpen = true }
                    DropdownMenu(audioOpen, { audioOpen = false }, modifier = Modifier.background(Ani.Panel)) {
                        listOf("sub" to "Sub", "dub" to "Dub").forEach { (k, label) ->
                            val available = if (k == "sub") !eps?.sub.isNullOrEmpty() else !eps?.dub.isNullOrEmpty()
                            DropdownMenuItem(
                                text = { Text(label + if (!available) "  ·  Unavailable" else "", style = inter(15f), color = if (available) Color.White else Ani.Muted) },
                                enabled = available, onClick = { audio = k; streamName = null; audioOpen = false },
                                trailingIcon = { if (audio == k && available) Icon(VidIcons.Check, null, tint = Color.White, modifier = Modifier.size(20.dp)) },
                            )
                        }
                    }
                }
                Box(Modifier.weight(1f)) {
                    PickerButton(null, stream?.name ?: "Server", serverOpen, false, zap = true) { serverOpen = true }
                    DropdownMenu(serverOpen, { serverOpen = false }, modifier = Modifier.background(Ani.Panel)) {
                        val all = watch?.streams.orEmpty()
                        if (all.isEmpty()) DropdownMenuItem(text = { Text(if (watchError != null) "No server answered" else "Loading…", style = inter(14f), color = Ani.Muted) }, enabled = false, onClick = {})
                        all.forEach { s ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(s.name, style = inter(15f), color = Color.White)
                                        if (audio == "sub") {
                                            Spacer(Modifier.width(8.dp))
                                            Text(if (s.kind == "soft") "s-sub" else "h-sub", style = inter(10.5f, FontWeight.SemiBold), color = if (s.kind == "soft") Ani.Lime else Ani.Amber,
                                                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0x22FFFFFF)).padding(horizontal = 7.dp, vertical = 2.dp))
                                        }
                                    }
                                },
                                onClick = { streamName = s.name; serverOpen = false },
                                trailingIcon = { if (stream?.id == s.id) Icon(VidIcons.Check, null, tint = Color.White, modifier = Modifier.size(20.dp)) },
                            )
                        }
                    }
                }
            }

            // Watch together
            Row(
                Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (room != null) Ani.Lime.copy(alpha = 0.14f) else Color(0x1AFFFFFF))
                    .border(1.dp, if (room != null) Ani.Lime.copy(alpha = 0.5f) else Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                    .clickable {
                        if (social.myId == null) android.widget.Toast.makeText(context, "Log in to watch together", android.widget.Toast.LENGTH_SHORT).show()
                        else togetherOpen = true
                    }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AniIcons.Person, null, tint = if (room != null) Ani.Lime else Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    if (room == null) "Watch together" else "In a room · ${roomMembers.size} watching",
                    style = inter(14f, FontWeight.SemiBold), color = if (room != null) Ani.Lime else Color.White,
                )
            }

            // Previous / Episodes / Next
            val edge = Color(0x33FFFFFF)
            Row(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, edge, RoundedCornerShape(12.dp))) {
                Row(Modifier.weight(1f).fillMaxSize().clickable(enabled = prev != null) { prev?.let { select(it.number) } }.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(VidIcons.ChevronLeft, null, tint = if (prev != null) Color(0xFF949BA4) else Color(0x55949BA4), modifier = Modifier.size(20.dp))
                    if (prev != null) Text("Ep ${prev.number}", style = inter(14f), color = Color(0xFF949BA4))
                }
                Box(Modifier.width(1.dp).fillMaxSize().background(edge))
                Row(Modifier.fillMaxSize().weight(1.15f).clickable { episodesOpen = true }, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(AniIcons.Menu, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Episodes", style = inter(14f, FontWeight.SemiBold), color = Color.White)
                }
                Box(Modifier.width(1.dp).fillMaxSize().background(edge))
                Row(Modifier.weight(1f).fillMaxSize().clickable(enabled = next != null) { next?.let { select(it.number) } }.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    if (next != null) Text("Ep ${next.number}", style = inter(14f), color = Color(0xFF949BA4))
                    Icon(VidIcons.ChevronRight, null, tint = if (next != null) Color(0xFF949BA4) else Color(0x55949BA4), modifier = Modifier.size(20.dp))
                }
            }

            // Anime card
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFF0B0B0D)).padding(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.width(76.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).background(Ani.Item).clickable { onOpenDetails(id) }) {
                        anime?.posterUrl?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                    }
                    Column(Modifier.weight(1f).padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(name, style = inter(18f, FontWeight.Bold, lineHeight = 22f), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { onOpenDetails(id) })
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            anime?.seasonLabel?.let { Text(it, style = inter(13.5f), color = Color(0xFF949BA4)); Text("·", color = Color(0xFF949BA4)) }
                            eps?.let {
                                Icon(VidIcons.ClosedCaptions, null, tint = Color(0xFF949BA4), modifier = Modifier.size(16.dp))
                                Text("${it.sub.size}", style = inter(13.5f), color = Color(0xFF949BA4))
                                Text("·", color = Color(0xFF949BA4))
                                Icon(AniIcons.Person, null, tint = Color(0xFF949BA4), modifier = Modifier.size(15.dp))
                                Text("${it.dub.size}", style = inter(13.5f), color = Color(0xFF949BA4))
                            }
                        }
                    }
                    anime?.let { m ->
                        val (fav, toggle) = rememberFav(m)
                        val shape = RoundedCornerShape(12.dp)
                        Box(Modifier.size(44.dp).clip(shape).background(Color(0x0FFFFFFF)).border(1.dp, if (fav) FavYellow.copy(alpha = 0.6f) else Color(0x40FFFFFF), shape).clickable(onClick = toggle), contentAlignment = Alignment.Center) {
                            Icon(if (fav) AniIcons.BookmarkFilled else AniIcons.Bookmark, if (fav) "Remove from favorites" else "Add to favorites", tint = if (fav) FavYellow else Color.White, modifier = Modifier.size(22.dp))
                        }
                    }
                }
                val desc = remember(anime?.id) { anime?.description?.replace(Regex("<[^>]*>"), " ")?.replace(Regex("\\s+"), " ")?.trim().orEmpty() }
                val shown = if (synopsisEpisode && !current?.description.isNullOrBlank()) current?.description.orEmpty() else desc
                if (shown.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Text(shown, style = inter(14.5f, lineHeight = 23f), color = Color(0xFFC3BFC7), maxLines = if (expanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        val canSwitch = !current?.description.isNullOrBlank() && desc.isNotBlank()
                        Text(
                            if (synopsisEpisode) "Episode synopsis" else "Anime synopsis", style = inter(13.5f), color = if (canSwitch) Color(0xFFB9A1DF) else Color(0xFF6B6870),
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = canSwitch) { synopsisEpisode = !synopsisEpisode }.padding(vertical = 4.dp),
                        )
                        Spacer(Modifier.weight(1f))
                        Text(if (expanded) "Show less" else "Show more", style = inter(14f, FontWeight.Medium), color = Color.White,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { expanded = !expanded }.padding(vertical = 4.dp))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (togetherOpen) {
        com.anibiar.app.ui.watch.TogetherSheet(
            room = room, together = together, isHost = isHost, starting = starting,
            onStart = {
                starting = true
                scope.launch {
                    runCatching { social.createRoom(id, name, anime?.posterUrl, ep, audio) }
                        .onSuccess { room = it }
                        .onFailure { android.widget.Toast.makeText(context, it.message ?: "Couldn't start the room", android.widget.Toast.LENGTH_SHORT).show() }
                    starting = false
                }
            },
            onLeave = { together?.leave(isHost); together = null; room = null; togetherOpen = false },
            onDismiss = { togetherOpen = false },
        )
    }
    if (episodesOpen) {
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var reverse by remember { mutableStateOf(false) }
        val shown = if (reverse) list.reversed() else list
        ModalBottomSheet(onDismissRequest = { episodesOpen = false }, sheetState = state, containerColor = Ani.Panel) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Episodes", style = inter(18f, FontWeight.Bold), color = Color.White)
                Text("  ${list.size}", style = inter(14f), color = Ani.Muted)
                Spacer(Modifier.weight(1f))
                Text(if (reverse) "Newest first" else "Oldest first", style = inter(13f, FontWeight.Medium), color = Ani.Soft,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Ani.Item).clickable { reverse = !reverse }.padding(horizontal = 12.dp, vertical = 6.dp))
            }
            val listState = rememberLazyListState()
            LaunchedEffect(Unit) { shown.indexOfFirst { it.number == ep }.takeIf { it > 2 }?.let { listState.scrollToItem(it - 1) } }
            LazyColumn(state = listState, modifier = Modifier.navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(shown, key = { _, e -> e.number }) { _, e ->
                    val active = e.number == ep
                    Row(
                        Modifier.fillMaxWidth().clickable { select(e.number) }.background(if (active) Color(0x1FFFFFFF) else Color.Transparent).padding(horizontal = 18.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(112.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(Ani.Item)) {
                            (e.image ?: anime?.bannerImage ?: anime?.posterUrl)?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                            Text("${e.number}", style = inter(11f, FontWeight.Bold), color = Color.White,
                                modifier = Modifier.align(Alignment.BottomStart).padding(5.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xB3000000)).padding(horizontal = 6.dp, vertical = 1.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.title.ifBlank { "Episode ${e.number}" }, style = inter(14.5f, FontWeight.SemiBold, lineHeight = 18f), color = if (active) Ani.Lavender else Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val meta = listOfNotNull(prettyDate(e.airDate), if (e.filler) "Filler" else null).joinToString(" · ")
                            if (meta.isNotEmpty()) Text(meta, style = inter(12f), color = Ani.Muted, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerButton(icon: androidx.compose.ui.graphics.vector.ImageVector?, label: String, open: Boolean, mic: Boolean, zap: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().height(52.dp).clip(shape).border(1.dp, Color(0x40FFFFFF), shape).background(Color(0x0DFFFFFF)).clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (zap) Text("⚡", style = inter(17f)) else if (mic) Icon(AniIcons.Person, null, tint = Color.White, modifier = Modifier.size(20.dp)) else if (icon != null) Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, style = inter(15f, FontWeight.Medium), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Icon(AniIcons.ArrowDown, null, tint = Color.White, modifier = Modifier.size(20.dp).then(if (open) Modifier.rotateHalf() else Modifier))
    }
}

private fun Modifier.rotateHalf() = this.rotate(180f)
