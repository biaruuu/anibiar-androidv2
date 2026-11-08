package com.anibiar.app.ui.inbox

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.res.painterResource
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.anibiar.app.social.DmMessage
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anibiar.app.AniBiarApp
import com.anibiar.app.social.DmConversation
import com.anibiar.app.social.UserProfile
import com.anibiar.app.ui.profile.Avatar
import com.anibiar.app.ui.theme.AniIcons
import com.anibiar.app.ui.theme.GlassButton
import com.anibiar.app.ui.theme.NavState
import com.anibiar.app.ui.theme.glass
import com.anibiar.app.ui.theme.inter
import kotlinx.coroutines.launch

private val Bg = Color(0xFF0B0B0D)
private val Panel = Color(0xFF151517)
private val Item = Color(0xFF1A1A1C)
private val Accent = Color(0xFFB7A9FF)
private val Muted = Color(0xFF9B98A0)
private val Flame = Color(0xFFFF9A3C)

/** "now", "5m", "3h", "2d", or a short date. */
private fun ago(at: Long): String {
    if (at <= 0) return ""
    val s = (System.currentTimeMillis() - at) / 1000
    return when {
        s < 60 -> "now"
        s < 3600 -> "${s / 60}m"
        s < 86400 -> "${s / 3600}h"
        s < 7 * 86400 -> "${s / 86400}d"
        else -> java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(java.util.Date(at))
    }
}

/** A 🔥 that breathes, with the day count next to it. Hidden below 2 days. */
@Composable
fun StreakBadge(days: Int, modifier: Modifier = Modifier, big: Boolean = false) {
    if (days < 2) return
    val t = rememberInfiniteTransition(label = "streak")
    val pulse by t.animateFloat(0.9f, 1.18f, infiniteRepeatable(tween(750), RepeatMode.Reverse), label = "pulse")
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("🔥", fontSize = if (big) 20.sp else 15.sp, modifier = Modifier.scale(pulse))
        Spacer(Modifier.width(2.dp))
        Text("$days", style = inter(if (big) 16f else 13f, FontWeight.ExtraBold), color = Flame)
    }
}

private val Int.sp get() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)

/** The Inbox tab: Activity entry on top, then chats with unread badges and streaks. */
@Composable
fun InboxTab(onBack: () -> Unit, onOpenActivity: () -> Unit, onOpenChat: (String) -> Unit, onOpenUser: (String) -> Unit) {
    val app = LocalContext.current.applicationContext as AniBiarApp
    val social = app.social
    val chats by remember { social.conversations() }.collectAsStateWithLifecycle(emptyList())
    val notices by social.unread.collectAsStateWithLifecycle()
    val following by social.myFollowing.collectAsStateWithLifecycle()
    var people by remember { mutableStateOf<Map<String, UserProfile>>(emptyMap()) }
    var suggested by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val pinned by social.pinnedChats.collectAsStateWithLifecycle()
    val ids = chats.map { it.otherId }
    LaunchedEffect(ids) {
        val missing = ids.filter { it !in people }
        if (missing.isNotEmpty()) people = people + social.profiles(missing).associateBy { it.uid }
    }
    LaunchedEffect(following.size) { suggested = social.suggestions(ids.toSet(), 10) }
    LaunchedEffect(Unit) { social.dmStream().collect { if (it.event == "msg") social.nudge() } }

    Column(Modifier.fillMaxSize().background(Bg).windowInsetsPadding(WindowInsets.statusBars)) {
        Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            com.anibiar.app.ui.theme.GlassBack(onBack, Modifier.align(Alignment.CenterStart))
            Text("Inbox", style = inter(19f, FontWeight.ExtraBold), color = Color.White)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 90.dp)) {
            item {
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onOpenActivity).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(56.dp).glass(CircleShape), contentAlignment = Alignment.Center) {
                        Icon(AniIcons.Bell, null, tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Activity", style = inter(15.5f, FontWeight.Bold), color = Color.White)
                        Text("Follows, friend requests and invites", style = inter(13f), color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (notices > 0) Badge(notices)
                }
            }
            if (chats.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(AniIcons.Chat, null, tint = Muted, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("No messages yet", style = inter(16f, FontWeight.Bold), color = Color.White)
                        Spacer(Modifier.height(4.dp))
                        Text("Open someone's profile and tap Message. Follow each other and send a message every day to start a streak.", style = inter(13.5f, FontWeight.Normal, 19f), color = Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
            items(chats.sortedByDescending { it.otherId in pinned }, key = { it.otherId }) { c -> ChatRow(c, people[c.otherId], c.otherId in pinned, { onOpenChat(c.otherId) }, { onOpenUser(c.otherId) }) }
            if (suggested.isNotEmpty()) {
                item {
                    Text("Suggested accounts", style = inter(14f, FontWeight.Bold), color = Muted, modifier = Modifier.padding(start = 16.dp, top = 22.dp, bottom = 8.dp))
                }
                items(suggested.take(6), key = { "s" + it.uid }) { u ->
                    Row(Modifier.fillMaxWidth().clickable { onOpenUser(u.uid) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(u.avatarUrl, u.username, 46.dp, deco = u.avatarDeco)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(u.shownName, style = inter(14.5f, FontWeight.SemiBold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("@${u.username}", style = inter(12.5f), color = Muted, maxLines = 1)
                        }
                        Text(
                            "Follow", style = inter(13f, FontWeight.Bold), color = Color(0xFF0B0B0D),
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Accent)
                                .clickable { scope.launch { runCatching { social.follow(u.uid) }; suggested = suggested.filter { it.uid != u.uid } } }
                                .padding(horizontal = 18.dp, vertical = 7.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Badge(n: Int) {
    Box(Modifier.clip(CircleShape).background(Color(0xFFFF4D6A)).padding(horizontal = 7.dp, vertical = 2.dp).widthIn(min = 8.dp), contentAlignment = Alignment.Center) {
        Text(if (n > 99) "99+" else "$n", style = inter(11.5f, FontWeight.Bold), color = Color.White)
    }
}

@Composable
private fun ChatRow(c: DmConversation, p: UserProfile?, pinned: Boolean, onClick: () -> Unit, onAvatar: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(p?.avatarUrl, p?.username, 56.dp, Modifier.clickable(onClick = onAvatar), deco = p?.avatarDeco)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p?.shownName ?: "…", style = inter(16f, if (c.unread > 0) FontWeight.Bold else FontWeight.Medium), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (pinned) Text("★", fontSize = 13.sp, color = Color(0xFFFFD25A), modifier = Modifier.padding(start = 5.dp))
                StreakBadge(c.streak, Modifier.padding(start = 6.dp))
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (c.lastMine) "You: " else "") + c.lastBody,
                    style = inter(14f, if (c.unread > 0) FontWeight.Bold else FontWeight.Normal), color = if (c.unread > 0) Color.White else Muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                Text("  ·  " + ago(c.lastAt), style = inter(14f), color = Muted, maxLines = 1, softWrap = false)
            }
        }
        if (c.unread > 0) { Spacer(Modifier.width(10.dp)); Badge(c.unread) }
    }
}

/** One conversation: bubbles, a streak banner and a "Message…" bar. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(uid: String, onBack: () -> Unit, onOpenUser: (String) -> Unit) {
    BackHandler(onBack = onBack)
    DisposableEffect(Unit) { NavState.hidden = true; onDispose { NavState.hidden = false } }
    val context = LocalContext.current
    val app = context.applicationContext as AniBiarApp
    val social = app.social
    val scope = rememberCoroutineScope()
    var person by remember { mutableStateOf<UserProfile?>(null) }
    val msgs by remember(uid) { social.messages(uid) }.collectAsStateWithLifecycle(emptyList())
    val chats by remember { social.conversations() }.collectAsStateWithLifecycle(emptyList())
    val streak = chats.firstOrNull { it.otherId == uid }?.streak ?: 0
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var replyTo by remember { mutableStateOf<DmMessage?>(null) }
    var theirTyping by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var typingStamp by remember { mutableStateOf(0L) }
    DisposableEffect(uid) { social.openChat(uid); onDispose { social.sendDm(uid, "typing", mapOf("on" to "false")); social.closeChat(uid) } }
    LaunchedEffect(uid) {
        social.dmStream().collect { b ->
            when (b.event) {
                "msg" -> social.nudge()
                "typing" -> if (b.payload["from"]?.jsonPrimitive?.contentOrNull == uid) {
                    theirTyping = b.payload["on"]?.jsonPrimitive?.contentOrNull == "true"; typingStamp = System.currentTimeMillis()
                }
            }
        }
    }
    LaunchedEffect(typingStamp, theirTyping) { if (theirTyping) { kotlinx.coroutines.delay(4500); if (System.currentTimeMillis() - typingStamp >= 4400) theirTyping = false } }
    var typingSentAt by remember { mutableStateOf(0L) }
    LaunchedEffect(text) {
        val now = System.currentTimeMillis()
        if (text.isNotBlank()) { if (now - typingSentAt > 2000) { typingSentAt = now; social.sendDm(uid, "typing", mapOf("on" to "true")) } }
        else if (typingSentAt != 0L) { typingSentAt = 0L; social.sendDm(uid, "typing", mapOf("on" to "false")) }
    }
    var viewer by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    fun sendPicked(uri: android.net.Uri) {
        if (sending) return
        sending = true
        scope.launch {
            try {
                val prepared = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { prepareImage(context, uri) }
                    ?: throw IllegalStateException("Couldn't read that file.")
                social.sendMedia(uid, prepared.first, prepared.second, replyTo?.id); replyTo = null
            } catch (e: Exception) { Toast.makeText(context, e.message ?: "Couldn't send", Toast.LENGTH_LONG).show() }
            sending = false
        }
    }
    val pickPhoto = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { u -> if (u != null) sendPicked(u) }
    LaunchedEffect(theirTyping) { if (theirTyping) list.animateScrollToItem(msgs.size) }
    LaunchedEffect(uid) { person = social.fetchProfile(uid) }
    LaunchedEffect(msgs.size) {
        if (msgs.isNotEmpty()) list.animateScrollToItem(msgs.size - 1)
        if (msgs.any { !it.mine && !it.read }) social.markChatRead(uid)
    }

    fun send() {
        val t = text.trim()
        if (t.isEmpty() || sending) return
        sending = true
        scope.launch {
            try { social.sendMessage(uid, t, replyTo?.id); text = ""; replyTo = null }
            catch (e: Exception) { Toast.makeText(context, e.message ?: "Couldn't send", Toast.LENGTH_LONG).show() }
            sending = false
        }
    }

    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val glass = Brush.linearGradient(listOf(Color(0xB0404048), Color(0xB0202024), Color(0xB0303036)))
    val rim = Brush.linearGradient(listOf(Color(0x99FFFFFF), Color(0x1AFFFFFF), Color(0x66FFFFFF)))
    viewer?.let { url ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { viewer = null }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color(0xF2000000)).clickable { viewer = null }, contentAlignment = Alignment.Center) {
                coil.compose.AsyncImage(model = url, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(8.dp))
            }
        }
    }
    Column(Modifier.fillMaxSize().background(Color.Black).imePadding()) {
        Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).height(76.dp).padding(horizontal = 14.dp)) {
            Box(Modifier.align(Alignment.CenterStart).size(36.dp).clip(CircleShape).background(glass).border(1.dp, rim, CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                ChevronBack(Color.White, Modifier.size(20.dp))
            }
            Column(Modifier.align(Alignment.Center).clickable { onOpenUser(uid) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(person?.avatarUrl, person?.username, 40.dp, deco = person?.avatarDeco)
                Text(
                    person?.shownName ?: "…", style = inter(12f, FontWeight.SemiBold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.offset(y = (-6).dp).clip(RoundedCornerShape(50)).background(Color(0xE61E1E22)).border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 1.dp).widthIn(max = 140.dp),
                )
            }
            var menu by remember { mutableStateOf(false) }
            val pinned by social.pinnedChats.collectAsStateWithLifecycle()
            val isPinned = uid in pinned
            Row(
                Modifier.align(Alignment.CenterEnd).height(36.dp).clip(RoundedCornerShape(50)).background(glass).border(1.dp, rim, RoundedCornerShape(50)).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (streak >= 2) StreakBadge(streak, Modifier.padding(horizontal = 6.dp))
                Text(
                    if (isPinned) "★" else "☆", fontSize = 20.sp, color = if (isPinned) Color(0xFFFFD25A) else Color.White,
                    modifier = Modifier.clip(CircleShape).clickable {
                        social.togglePin(uid)
                        Toast.makeText(context, if (isPinned) "Unpinned" else "Pinned to top of Inbox", Toast.LENGTH_SHORT).show()
                    }.padding(horizontal = 9.dp),
                )
                Box {
                    Row(Modifier.clip(CircleShape).clickable { menu = true }.padding(horizontal = 9.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                        repeat(3) { Box(Modifier.size(4.dp).clip(CircleShape).background(Color.White)) }
                    }
                    com.anibiar.app.ui.theme.GlassMenu(expanded = menu, onDismiss = { menu = false }) {
                        com.anibiar.app.ui.theme.GlassMenuItem("Search in chat", AniIcons.Search) { menu = false; searching = true }
                        com.anibiar.app.ui.theme.GlassMenuItem("View profile", AniIcons.Person) { menu = false; onOpenUser(uid) }
                        com.anibiar.app.ui.theme.GlassMenuItem(if (isPinned) "Unpin chat" else "Pin chat", AniIcons.Pin) { menu = false; social.togglePin(uid) }
                    }
                }
            }
        }
        if (searching) {
            // Typo-tolerant search over this conversation (Rust), tap a result to jump to it.
            val hits = remember(query, msgs) {
                if (query.isBlank()) emptyList() else {
                    val idx = msgs.indices.filter { msgs[it].kind != "image" }
                    (com.anibiar.app.nativecore.Native.rank(query.trim(), idx.map { msgs[it].body })?.map { idx[it] }
                        ?: idx.filter { msgs[it].body.contains(query.trim(), ignoreCase = true) }).take(30)
                }
            }
            Column(Modifier.fillMaxWidth().background(Color(0xFF111114)).padding(horizontal = 14.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        textStyle = inter(15f).copy(color = Color.White), cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(Color(0xFF1C1C1E)).padding(horizontal = 14.dp, vertical = 10.dp),
                        decorationBox = { inner -> if (query.isEmpty()) Text("Search messages", style = inter(15f), color = Muted); inner() },
                    )
                    Text("Close", style = inter(14f), color = Color.White, modifier = Modifier.clickable { searching = false; query = "" }.padding(10.dp))
                }
                hits.forEach { i ->
                    Text(
                        msgs[i].body, style = inter(14f), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().clickable { scope.launch { list.animateScrollToItem(i) } }.padding(vertical = 8.dp),
                    )
                }
                if (query.isNotBlank() && hits.isEmpty()) Text("No messages found", style = inter(13f), color = Muted, modifier = Modifier.padding(8.dp))
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
            if (msgs.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(person?.avatarUrl, person?.username, 84.dp, deco = person?.avatarDeco)
                    Spacer(Modifier.height(10.dp))
                    Text(person?.shownName ?: "", style = inter(17f, FontWeight.Bold), color = Color.White)
                    Text("Say hi. Message each other every day to build a 🔥 streak.", style = inter(13.5f, FontWeight.Normal, 19f), color = Muted, modifier = Modifier.padding(horizontal = 36.dp, vertical = 6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            itemsIndexed(msgs, key = { _, m -> m.id }) { i, m ->
                val prev = msgs.getOrNull(i - 1); val next = msgs.getOrNull(i + 1)
                val gapBefore = prev == null || m.at - prev.at > 30 * 60 * 1000
                val gapAfter = next == null || next.at - m.at > 30 * 60 * 1000
                val firstInGroup = gapBefore || prev?.mine != m.mine
                val lastInGroup = gapAfter || next?.mine != m.mine
                if (gapBefore) Text(
                    stamp(m.at), style = inter(11.5f, FontWeight.Medium), color = Muted,
                    modifier = Modifier.fillMaxWidth().padding(top = if (i == 0) 0.dp else 14.dp, bottom = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                val shape = BubbleShape(m.mine, lastInGroup)
                val drag = remember { androidx.compose.animation.core.Animatable(0f) }
                val maxDrag = with(androidx.compose.ui.platform.LocalDensity.current) { 72.dp.toPx() }
                val dir = if (m.mine) -1f else 1f
                Box(
                    Modifier.fillMaxWidth().padding(top = if (firstInGroup) 0.dp else 2.dp)
                        .pointerInput(m.id) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    scope.launch {
                                        if (kotlin.math.abs(drag.value) > maxDrag * 0.72f) { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); replyTo = m }
                                        drag.animateTo(0f, androidx.compose.animation.core.spring(0.55f, 500f))
                                    }
                                },
                                onDragCancel = { scope.launch { drag.animateTo(0f) } },
                            ) { change, dx ->
                                change.consume()
                                val next = (drag.value + dx * 0.6f)
                                scope.launch { drag.snapTo(if (dir > 0) next.coerceIn(0f, maxDrag) else next.coerceIn(-maxDrag, 0f)) }
                            }
                        },
                ) {
                    val pull = (kotlin.math.abs(drag.value) / maxDrag).coerceIn(0f, 1f)
                    Icon(
                        AniIcons.Reply, null, tint = Color.White,
                        modifier = Modifier.align(if (m.mine) Alignment.CenterEnd else Alignment.CenterStart).padding(horizontal = 8.dp).size(20.dp)
                            .graphicsLayer { alpha = pull; scaleX = 0.6f + 0.5f * pull; scaleY = 0.6f + 0.5f * pull },
                    )
                    Row(
                        Modifier.fillMaxWidth().graphicsLayer { translationX = drag.value },
                        horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Bottom,
                    ) {
                        if (!m.mine) {
                            Box(Modifier.size(26.dp)) { if (lastInGroup) Avatar(person?.avatarUrl, person?.username, 26.dp, deco = person?.avatarDeco) }
                            Spacer(Modifier.width(8.dp))
                        }
                        Column(horizontalAlignment = if (m.mine) Alignment.End else Alignment.Start) {
                            var menuOpen by remember { mutableStateOf(false) }
                            Box {
                                val longPress = { menuOpen = true }
                                if (m.kind == "image") coil.compose.AsyncImage(
                                    model = m.mediaUrl, contentDescription = "Photo", contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
                                    modifier = Modifier.width(224.dp).heightIn(min = 100.dp, max = 340.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF1C1C1E))
                                        .combinedClickable(onClick = { viewer = m.mediaUrl }, onLongClick = longPress),
                                ) else Column(
                                    Modifier.widthIn(max = 276.dp).clip(shape)
                                        .background(if (m.mine) Brush.linearGradient(listOf(Color(0xFF3D7BFF), Color(0xFF2B58F0))) else Brush.linearGradient(listOf(Color(0xFF2A2A2E), Color(0xFF2A2A2E))))
                                        .combinedClickable(onClick = {}, onLongClick = longPress)
                                        .padding(start = if (m.mine) 12.dp else if (lastInGroup) 18.dp else 12.dp, end = if (m.mine) (if (lastInGroup) 18.dp else 12.dp) else 12.dp, top = 7.dp, bottom = 7.dp),
                                ) {
                                    m.replyTo?.let { rid ->
                                        val t = msgs.firstOrNull { it.id == rid }
                                        Row(
                                            Modifier.padding(bottom = 5.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x33000000)).padding(horizontal = 8.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(AniIcons.Reply, null, tint = Color(0xCCFFFFFF), modifier = Modifier.size(12.dp))
                                            Spacer(Modifier.width(5.dp))
                                            Text(t?.body ?: "Message unsent", style = inter(12f), color = Color(0xCCFFFFFF), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    Text(m.body, style = inter(14f, FontWeight.Normal, 19f), color = Color.White)
                                    com.anibiar.app.social.LinkPreview.firstUrl(m.body)?.let { u -> LinkCard(u) }
                                }
                                com.anibiar.app.ui.theme.GlassMenu(expanded = menuOpen, onDismiss = { menuOpen = false }) {
                                    Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        listOf("❤️", "😂", "😮", "😢", "👍", "🔥").forEach { e ->
                                            Text(
                                                e, fontSize = 22.sp,
                                                modifier = Modifier.clip(CircleShape).background(if (m.myReaction == e) Color(0x33FFFFFF) else Color.Transparent)
                                                    .clickable {
                                                        menuOpen = false
                                                        scope.launch { runCatching { social.react(m.id, e, uid, m.myReaction == e) }.onFailure { Toast.makeText(context, "Couldn't react", Toast.LENGTH_SHORT).show() } }
                                                    }.padding(6.dp),
                                            )
                                        }
                                    }
                                    com.anibiar.app.ui.theme.GlassMenuItem("Reply", AniIcons.Reply) { menuOpen = false; replyTo = m }
                                    com.anibiar.app.ui.theme.GlassMenuItem("Copy text", AniIcons.Copy) {
                                        menuOpen = false
                                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(m.body))
                                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                    }
                                    if (m.mine) com.anibiar.app.ui.theme.GlassMenuItem("Unsend", AniIcons.Delete, Color(0xFFFF6B6B)) {
                                        menuOpen = false
                                        scope.launch { runCatching { social.unsendMessage(m.id) }.onFailure { Toast.makeText(context, it.message ?: "Couldn't unsend", Toast.LENGTH_SHORT).show() } }
                                    }
                                }
                            }
                            if (m.reactions.isNotEmpty()) Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                m.reactions.forEach { (e, n) ->
                                    Row(
                                        Modifier.clip(RoundedCornerShape(12.dp)).background(if (m.myReaction == e) Color(0x553D7BFF) else Color(0xFF2A2A2E))
                                            .clickable { scope.launch { runCatching { social.react(m.id, e, uid, m.myReaction == e) } } }.padding(horizontal = 7.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) { Text(e, fontSize = 13.sp); if (n > 1) Text(" $n", style = inter(11.5f), color = Color.White) }
                                }
                            }
                            if (m.mine && m.id == msgs.lastOrNull { it.mine }?.id) Text(if (m.read) "Seen" else "Sent", style = inter(11f), color = Muted, modifier = Modifier.padding(top = 3.dp, end = 4.dp))
                        }
                    }
                }
            }
            if (theirTyping) item(key = "typing") {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
                    Avatar(person?.avatarUrl, person?.username, 26.dp, deco = person?.avatarDeco)
                    Spacer(Modifier.width(8.dp))
                    TypingBubble()
                }
            }
        }
        replyTo?.let { r ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(3.dp).height(44.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF3A3A3E)))
                Spacer(Modifier.width(10.dp))
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(Color(0xFF1C1C1E)).padding(start = 18.dp, end = 10.dp, top = 13.dp, bottom = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        (if (r.mine) "You" else person?.shownName ?: "") + ": " + r.body, style = inter(15f), color = Color(0xFF8E8E93),
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    Box(Modifier.size(26.dp).clip(CircleShape).background(Color(0xFF6A6A70)).clickable { replyTo = null }, contentAlignment = Alignment.Center) {
                        Text("✕", style = inter(12f, FontWeight.Bold), color = Color(0xFF1C1C1E))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.Bottom) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(glass).border(1.dp, rim, CircleShape).clickable { focus.clearFocus(); if (!sending) pickPhoto.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) }, contentAlignment = Alignment.Center) {
                androidx.compose.foundation.Canvas(Modifier.size(16.dp)) {
                    val st = 2.dp.toPx(); val c = size.width / 2f
                    drawLine(Color.White, androidx.compose.ui.geometry.Offset(0f, c), androidx.compose.ui.geometry.Offset(size.width, c), st, androidx.compose.ui.graphics.StrokeCap.Round)
                    drawLine(Color.White, androidx.compose.ui.geometry.Offset(c, 0f), androidx.compose.ui.geometry.Offset(c, size.height), st, androidx.compose.ui.graphics.StrokeCap.Round)
                }
            }
            Spacer(Modifier.width(8.dp))
            Row(Modifier.weight(1f).heightIn(min = 38.dp).clip(RoundedCornerShape(20.dp)).background(glass).border(1.dp, rim, RoundedCornerShape(20.dp)).padding(start = 16.dp, end = 7.dp, top = 3.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).padding(vertical = 4.dp)) {
                    if (text.isEmpty()) Text("Message…", style = inter(14f), color = Muted)
                    BasicTextField(
                        value = text, onValueChange = { if (it.length <= 2000) text = it },
                        textStyle = inter(14f).copy(color = Color.White), cursorBrush = SolidColor(Color(0xFF3D7BFF)), maxLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                val ready = text.isNotBlank() && !sending
                if (ready) Box(
                    Modifier.size(30.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF3D7BFF), Color(0xFF2B58F0)))).clickable { send() },
                    contentAlignment = Alignment.Center,
                ) { Icon(AniIcons.ArrowBack, "Send", tint = Color.White, modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = 90f }) }
            }
        }
    }
}

private fun stamp(at: Long): String {
    val day = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
    val time = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date(at))
    val d = day.format(java.util.Date(at)); val now = day.format(java.util.Date())
    val yest = day.format(java.util.Date(System.currentTimeMillis() - 86400000))
    return when (d) { now -> "Today $time"; yest -> "Yesterday $time"; else -> java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault()).format(java.util.Date(at)) }
}

/** A thin "<" chevron. */
@Composable
private fun ChevronBack(color: Color, modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width; val h = size.height
        val path = androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.62f, h * 0.2f); lineTo(w * 0.3f, h * 0.5f); lineTo(w * 0.62f, h * 0.8f) }
        drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.11f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

/** iMessage-style bubble: flat rounded body, with a small curled tail at the bottom outer corner on the last bubble of a group. */
private class BubbleShape(val mine: Boolean, val tail: Boolean) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): androidx.compose.ui.graphics.Outline {
        val t = with(density) { 6.dp.toPx() }
        val r = minOf(with(density) { 17.dp.toPx() }, size.height / 2f)
        val w = size.width; val h = size.height
        val path = androidx.compose.ui.graphics.Path()
        // Build as "mine" (tail on the right), mirror for theirs.
        val bw = if (tail) w - t else w
        path.moveTo(r, 0f)
        path.lineTo(bw - r, 0f)
        path.arcTo(androidx.compose.ui.geometry.Rect(bw - 2 * r, 0f, bw, 2 * r), -90f, 90f, false)
        if (tail) {
            path.lineTo(bw, h - 12f)
            path.cubicTo(bw, h - 4f, bw + 1.5f, h - 1f, w, h)
            path.cubicTo(w - 3f, h + 0.5f, bw - 3f, h, bw - 8f, h)
        } else {
            path.lineTo(bw, h - r)
            path.arcTo(androidx.compose.ui.geometry.Rect(bw - 2 * r, h - 2 * r, bw, h), 0f, 90f, false)
        }
        path.lineTo(r, h)
        path.arcTo(androidx.compose.ui.geometry.Rect(0f, h - 2 * r, 2 * r, h), 90f, 90f, false)
        path.lineTo(0f, r)
        path.arcTo(androidx.compose.ui.geometry.Rect(0f, 0f, 2 * r, 2 * r), 180f, 90f, false)
        path.close()
        if (!mine) {
            val m = android.graphics.Matrix().apply { setScale(-1f, 1f, w / 2f, 0f) }
            val ap = path.asAndroidPath(); ap.transform(m)
        }
        return androidx.compose.ui.graphics.Outline.Generic(path)
    }
}

/** Three bouncing dots in a bubble: "they're typing". */
@Composable
private fun TypingBubble() {
    val t = rememberInfiniteTransition(label = "typing")
    Row(
        Modifier.clip(BubbleShape(false, true)).background(Color(0xFF2A2A2E)).padding(start = 18.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { i ->
            val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(520, delayMillis = i * 140), RepeatMode.Reverse), label = "dot$i")
            Box(Modifier.size(7.dp).offset(y = (-4f * v).dp).clip(CircleShape).background(Color(0xFFB4B1BA).copy(alpha = 0.55f + 0.45f * v)))
        }
    }
}

/** Reads a picked image and shrinks it. GIF and WebP are sent as they are so they keep moving. */
private fun prepareImage(context: android.content.Context, uri: android.net.Uri): Pair<ByteArray, String>? {
    val cr = context.contentResolver
    val mime = cr.getType(uri) ?: "image/jpeg"
    val raw = cr.openInputStream(uri)?.use { it.readBytes() } ?: return null
    if (mime == "image/gif" || mime == "image/webp") return raw to mime
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
    val limit = 1600
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > limit * 2) sample *= 2
    var bmp = android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val big = maxOf(bmp.width, bmp.height)
    if (big > limit) { val k = limit.toFloat() / big; bmp = android.graphics.Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt().coerceAtLeast(1), (bmp.height * k).toInt().coerceAtLeast(1), true) }
    val out = java.io.ByteArrayOutputStream()
    return if (bmp.hasAlpha()) { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out); out.toByteArray() to "image/png" }
    else { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 86, out); out.toByteArray() to "image/jpeg" }
}


/** Small card under a message that contains a link: picture, title and site. */
@Composable
private fun LinkCard(url: String) {
    var preview by remember(url) { mutableStateOf<com.anibiar.app.social.Preview?>(null) }
    LaunchedEffect(url) { preview = com.anibiar.app.social.LinkPreview.load(url) }
    val p = preview ?: return
    val context = LocalContext.current
    Column(
        Modifier.padding(top = 6.dp).width(230.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x33000000))
            .clickable { runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) } },
    ) {
        p.image?.let {
            coil.compose.AsyncImage(model = it, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(120.dp))
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(p.title, style = inter(13f, FontWeight.SemiBold, 17f), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(p.host, style = inter(11.5f), color = Color(0xCCFFFFFF), maxLines = 1)
        }
    }
}
