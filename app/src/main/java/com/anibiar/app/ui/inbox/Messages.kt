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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.Brush
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
fun InboxTab(onOpenActivity: () -> Unit, onOpenChat: (String) -> Unit, onOpenUser: (String) -> Unit) {
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

    Column(Modifier.fillMaxSize().background(Bg).windowInsetsPadding(WindowInsets.statusBars)) {
        Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
            Text("Inbox", style = inter(18f, FontWeight.Bold), color = Color.White)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 90.dp)) {
            item {
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onOpenActivity).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(52.dp).clip(CircleShape).background(Panel), contentAlignment = Alignment.Center) {
                        Icon(AniIcons.Bell, null, tint = Accent, modifier = Modifier.size(26.dp))
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
                        Icon(AniIcons.Mail, null, tint = Muted, modifier = Modifier.size(40.dp))
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
                        Avatar(u.avatarUrl, u.username, 46.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(u.shownName, style = inter(14.5f, FontWeight.SemiBold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("@${u.username}", style = inter(12.5f), color = Muted, maxLines = 1)
                        }
                        Text(
                            "Follow", style = inter(13f, FontWeight.Bold), color = Color(0xFF0B0B0D),
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Accent)
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
        Avatar(p?.avatarUrl, p?.username, 52.dp, Modifier.clickable(onClick = onAvatar))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p?.shownName ?: "…", style = inter(15.5f, if (c.unread > 0) FontWeight.ExtraBold else FontWeight.Bold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (pinned) Text("★", fontSize = 14.sp, color = Color(0xFFFFD25A), modifier = Modifier.padding(start = 5.dp))
                StreakBadge(c.streak, Modifier.padding(start = 6.dp))
            }
            Text(
                (if (c.lastMine) "You: " else "") + c.lastBody,
                style = inter(13.5f, if (c.unread > 0) FontWeight.SemiBold else FontWeight.Normal), color = if (c.unread > 0) Color.White else Muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(ago(c.lastAt), style = inter(12f), color = Muted)
            if (c.unread > 0) { Spacer(Modifier.height(4.dp)); Badge(c.unread) }
        }
    }
}

/** One conversation: bubbles, a streak banner and a "Message…" bar. */
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
    val list = rememberLazyListState()
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
            try { social.sendMessage(uid, t); text = "" }
            catch (e: Exception) { Toast.makeText(context, e.message ?: "Couldn't send", Toast.LENGTH_LONG).show() }
            sending = false
        }
    }

    val glass = Brush.verticalGradient(listOf(Color(0xCC2E2E34), Color(0xE61A1A1E)))
    val rim = Brush.verticalGradient(listOf(Color(0x66FFFFFF), Color(0x14FFFFFF)))
    Column(Modifier.fillMaxSize().background(Color.Black).imePadding()) {
        Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).height(76.dp).padding(horizontal = 14.dp)) {
            Box(Modifier.align(Alignment.CenterStart).size(36.dp).clip(CircleShape).background(glass).border(1.dp, rim, CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                ChevronBack(Color.White, Modifier.size(20.dp))
            }
            Column(Modifier.align(Alignment.Center).clickable { onOpenUser(uid) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(person?.avatarUrl, person?.username, 40.dp)
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
                    androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Color(0xFF1E1E22)) {
                        androidx.compose.material3.DropdownMenuItem(text = { Text("View profile", color = Color.White) }, onClick = { menu = false; onOpenUser(uid) })
                        androidx.compose.material3.DropdownMenuItem(text = { Text(if (isPinned) "Unpin chat" else "Pin chat", color = Color.White) }, onClick = { menu = false; social.togglePin(uid) })
                    }
                }
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
            if (msgs.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(person?.avatarUrl, person?.username, 84.dp)
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
                val tail = if (lastInGroup) 4.dp else 18.dp
                val shape = if (m.mine) RoundedCornerShape(18.dp, 18.dp, tail, 18.dp) else RoundedCornerShape(18.dp, 18.dp, 18.dp, tail)
                Row(Modifier.fillMaxWidth().padding(top = if (firstInGroup) 0.dp else 2.dp), horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Bottom) {
                    if (!m.mine) {
                        Box(Modifier.size(26.dp)) { if (lastInGroup) Avatar(person?.avatarUrl, person?.username, 26.dp) }
                        Spacer(Modifier.width(8.dp))
                    }
                    Column(horizontalAlignment = if (m.mine) Alignment.End else Alignment.Start) {
                        Text(
                            m.body, style = inter(14f, FontWeight.Normal, 19f), color = Color.White,
                            modifier = Modifier.widthIn(max = 270.dp).clip(shape)
                                .background(if (m.mine) Brush.linearGradient(listOf(Color(0xFFA796FF), Color(0xFF7A63F0))) else Brush.verticalGradient(listOf(Color(0xFF2C2C31), Color(0xFF232327))))
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                        if (m.mine && m.id == msgs.lastOrNull { it.mine }?.id) Text(if (m.read) "Seen" else "Sent", style = inter(11f), color = Muted, modifier = Modifier.padding(top = 3.dp, end = 4.dp))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(glass).border(1.dp, rim, CircleShape), contentAlignment = Alignment.Center) {
                Text("+", style = inter(20f, FontWeight.Light), color = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            Row(Modifier.weight(1f).heightIn(min = 36.dp).clip(RoundedCornerShape(22.dp)).background(glass).border(1.dp, rim, RoundedCornerShape(22.dp)).padding(start = 14.dp, end = 4.dp, top = 3.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).padding(vertical = 4.dp)) {
                    if (text.isEmpty()) Text("Message…", style = inter(14f), color = Muted)
                    BasicTextField(
                        value = text, onValueChange = { if (it.length <= 2000) text = it },
                        textStyle = inter(14f).copy(color = Color.White), cursorBrush = SolidColor(Accent), maxLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                val ready = text.isNotBlank() && !sending
                if (ready) Box(
                    Modifier.size(30.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFFA796FF), Color(0xFF7A63F0)))).clickable { send() },
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
