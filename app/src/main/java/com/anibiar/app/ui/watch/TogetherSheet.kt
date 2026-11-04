package com.anibiar.app.ui.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anibiar.app.AniBiarApp
import com.anibiar.app.social.Room
import com.anibiar.app.social.UserProfile
import com.anibiar.app.social.WatchTogether
import com.anibiar.app.ui.profile.Avatar
import com.anibiar.app.ui.theme.Ani
import com.anibiar.app.ui.theme.inter
import kotlinx.coroutines.launch

/** Start a Watch Together room, invite friends, see who is in, leave. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TogetherSheet(
    room: Room?, together: WatchTogether?, isHost: Boolean, starting: Boolean,
    onStart: () -> Unit, onLeave: () -> Unit, onDismiss: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as AniBiarApp
    val social = app.social
    val scope = rememberCoroutineScope()
    val friendIds by social.myFriends.collectAsStateWithLifecycle()
    val members by (together?.members ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptySet<String>()) }).collectAsStateWithLifecycle()
    var friends by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    val invited = remember { mutableStateListOf<String>() }
    var failed by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(friendIds) { friends = social.profiles(friendIds) }
    LaunchedEffect(room?.id) { room?.let { r -> runCatching { invited.clear(); invited.addAll(social.invitedIds(r.id)) } } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Ani.Panel) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp).padding(bottom = 16.dp)) {
            Text("Watch together", style = inter(20f, FontWeight.Bold), color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text(
                if (room == null) "Start a room and invite friends. Play, pause and seeking stay in sync for everyone, and anyone can change the episode."
                else "${room.title} · everyone in the room sees the same moment.",
                style = inter(13.5f, lineHeight = 19f), color = Ani.Muted,
            )
            Spacer(Modifier.height(14.dp))
            if (room == null) {
                Box(
                    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp)).background(Ani.Lime).clickable(enabled = !starting, onClick = onStart),
                    contentAlignment = Alignment.Center,
                ) { Text(if (starting) "Starting…" else "Start a room", style = inter(15f, FontWeight.Bold), color = Color.Black) }
            } else {
                failed?.let { Text(it, style = inter(12.5f), color = Ani.Amber, modifier = Modifier.padding(bottom = 8.dp)) }
                Text("Friends", style = inter(13f, FontWeight.SemiBold), color = Ani.Soft)
                Spacer(Modifier.height(6.dp))
                if (friends.isEmpty()) {
                    Text("Add friends from their profile to invite them here.", style = inter(13.5f), color = Ani.Muted, modifier = Modifier.padding(vertical = 8.dp))
                } else LazyColumn(Modifier.fillMaxWidth().height((friends.size.coerceAtMost(5) * 60).dp)) {
                    items(friends, key = { it.uid }) { f ->
                        val here = f.uid in members
                        val sent = f.uid in invited
                        Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(f.avatarUrl, f.shownName, 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(f.shownName, style = inter(15f, FontWeight.SemiBold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (here) "Watching now" else "@${f.username}", style = inter(12.5f), color = if (here) Ani.Lime else Ani.Muted)
                            }
                            if (isHost && !here) {
                                Box(
                                    Modifier.clip(RoundedCornerShape(50)).background(if (sent) Ani.ButtonDark else Ani.Lavender)
                                        .clickable {
                                            scope.launch {
                                                runCatching { social.inviteToRoom(room, f.uid) }
                                                    .onSuccess { if (f.uid !in invited) invited.add(f.uid); failed = null }
                                                    .onFailure { failed = it.message ?: "Couldn't send the invite" }
                                            }
                                        }.padding(horizontal = 16.dp, vertical = 8.dp),
                                ) { Text(if (sent) "Invite again" else "Invite", style = inter(13f, FontWeight.SemiBold), color = if (sent) Color.White else Color.Black) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp)).background(Ani.ButtonDark).clickable(onClick = onLeave),
                    contentAlignment = Alignment.Center,
                ) { Text(if (isHost) "End room for everyone" else "Leave room", style = inter(14.5f, FontWeight.SemiBold), color = Color(0xFFFF6B6B)) }
            }
        }
    }
}
