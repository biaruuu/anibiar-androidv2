package com.anibiar.app.ui.inbox

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anibiar.app.AniBiarApp
import com.anibiar.app.social.Notice
import com.anibiar.app.ui.profile.Avatar
import com.anibiar.app.ui.theme.Ani
import com.anibiar.app.ui.theme.AniIcons
import com.anibiar.app.ui.theme.inter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Follows, friend requests and accepted requests, newest first. */
@Composable
fun InboxScreen(onBack: () -> Unit, onOpenUser: (String) -> Unit, onJoinRoom: (String) -> Unit = {}) {
    BackHandler(onBack = onBack)
    val app = LocalContext.current.applicationContext as AniBiarApp
    val social = app.social
    val scope = rememberCoroutineScope()
    val notices by remember { social.notices() }.collectAsStateWithLifecycle(null)
    val myFollowing by social.myFollowing.collectAsStateWithLifecycle()
    val incoming by social.incomingRequests.collectAsStateWithLifecycle()
    val annTick by com.anibiar.app.social.LiveSync.tick.collectAsStateWithLifecycle()
    val anns by androidx.compose.runtime.produceState(emptyList<com.anibiar.app.data.Announcement>(), annTick) { value = com.anibiar.app.data.Announcements.load(app) }

    // Let the unread dots show for a moment, then clear them.
    LaunchedEffect(Unit) { delay(1800); social.markAllRead() }

    Column(Modifier.fillMaxSize().background(Ani.Onyx).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(12.dp))
            com.anibiar.app.ui.theme.GlassBack(onBack)
            Spacer(Modifier.width(8.dp))
            Text("Inbox", style = inter(20f, FontWeight.Bold), color = Color.White)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ani.Border))
        val list = notices
        if (list == null) {
            Text("Loading…", style = inter(14f), color = Ani.Muted, modifier = Modifier.padding(16.dp))
        } else if (list.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(top = 16.dp)) {
                anns.forEach { com.anibiar.app.ui.home.AnnouncementCard(it, Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
                com.anibiar.app.ui.theme.ArtState(com.anibiar.app.ui.theme.Art.Friends, "Nothing here yet", "New followers and friend requests show up here.")
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp),
            ) {
                items(anns, key = { "ann-${it.id}" }) { a -> com.anibiar.app.ui.home.AnnouncementCard(a, Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
                items(list, key = { it.id }) { n ->
                    NoticeRow(
                        n, iFollow = n.actorId in myFollowing, pending = n.actorId in incoming,
                        onOpen = { onOpenUser(n.actorId) },
                        onAccept = { scope.launch { runCatching { social.acceptFriend(n.actorId) } } },
                        onDecline = { scope.launch { runCatching { social.removeFriend(n.actorId) } } },
                        onFollowBack = { scope.launch { runCatching { social.follow(n.actorId) } } },
                        onJoin = { n.ref?.let(onJoinRoom) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NoticeRow(
    n: Notice, iFollow: Boolean, pending: Boolean,
    onOpen: () -> Unit, onAccept: () -> Unit, onDecline: () -> Unit, onFollowBack: () -> Unit, onJoin: () -> Unit,
) {
    val name = n.actor?.username ?: "Someone"
    val what = when (n.type) {
        "follow" -> " started following you."
        "friend_request" -> " wants to be your friend."
        "watch_invite" -> " invited you to watch together."
        else -> " accepted your friend request."
    }
    Row(
        Modifier.fillMaxWidth().background(if (n.read) Color.Transparent else Ani.Lavender.copy(alpha = 0.07f))
            .clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(n.actor?.avatarUrl, name, 48.dp, deco = n.actor?.avatarDeco)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                buildAnnotatedString {
                    pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Color.White)); append(name); pop()
                    append(what)
                    pushStyle(SpanStyle(color = Ani.Muted)); append("  ${ago(n.createdAt)}"); pop()
                },
                style = inter(14.5f, lineHeight = 20f), color = Ani.Soft, maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
            if (n.type == "friend_request" && pending) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    Pill("Accept", primary = true, onClick = onAccept)
                    Pill("Decline", primary = false, onClick = onDecline)
                }
            }
        }
        if (n.type == "watch_invite" && n.ref != null) {
            Spacer(Modifier.width(8.dp))
            Pill("Join", primary = true, onClick = onJoin)
        }
        if (n.type == "follow" && !iFollow) {
            Spacer(Modifier.width(8.dp))
            Pill("Follow back", primary = true, onClick = onFollowBack)
        }
    }
}

@Composable
private fun Pill(label: String, primary: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(32.dp).clip(RoundedCornerShape(14.dp)).background(if (primary) Color.White else Ani.ButtonDark)
            .clickable(onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = inter(13f, FontWeight.SemiBold), color = if (primary) Color.Black else Color.White, maxLines = 1) }
}

private fun ago(t: Long): String {
    if (t <= 0) return ""
    val s = ((System.currentTimeMillis() - t) / 1000).coerceAtLeast(0)
    return when {
        s < 60 -> "now"
        s < 3600 -> "${s / 60}m"
        s < 86400 -> "${s / 3600}h"
        s < 604800 -> "${s / 86400}d"
        else -> "${s / 604800}w"
    }
}
