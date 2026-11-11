package com.anibiar.app.ui.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
    onStart: () -> Unit, onLeave: () -> Unit, onDismiss: () -> Unit, onPickAnime: () -> Unit = {},
    audio: String = "sub", dubAvailable: Boolean = false, onAudio: (String) -> Unit = {},
    servers: List<String> = emptyList(), server: String? = null, onServer: (String) -> Unit = {},
) {
    val app = LocalContext.current.applicationContext as AniBiarApp
    val social = app.social
    val scope = rememberCoroutineScope()
    val friendIds by social.myFriends.collectAsStateWithLifecycle()
    val members by (together?.members ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptySet<String>()) }).collectAsStateWithLifecycle()
    var friends by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    val invited = remember { mutableStateListOf<String>() }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var failed by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(friendIds) { friends = social.profiles(friendIds) }
    LaunchedEffect(room?.id) { room?.let { r -> runCatching { invited.clear(); invited.addAll(social.invitedIds(r.id)) } } }

    FullScreenPanel(if (room == null) "Watch together" else "Room", onDismiss) {
        Column(Modifier.fillMaxWidth().weight(1f)) {
                        Text(
                if (room == null) "Start a room and invite friends. Play, pause and seeking stay in sync for everyone, and anyone can change the episode."
                else "${room.title} · everyone sees the same moment, and you're on a voice call. Only the host changes the episode or anime. Use headphones to avoid echo.",
                style = inter(13.5f, lineHeight = 19f), color = Ani.Muted,
            )
            Spacer(Modifier.height(14.dp))
            if (room == null) {
                Box(
                    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp)).background(Ani.Lime).clickable(enabled = !starting, onClick = onStart),
                    contentAlignment = Alignment.Center,
                ) { Text(if (starting) "Starting…" else "Start a room", style = inter(15f, FontWeight.Bold), color = Color.Black) }
            } else {
                failed?.let { Text(it, style = inter(12.5f), color = Ani.Amber, modifier = Modifier.padding(bottom = 8.dp)) }
                Text("Friends", style = inter(13f, FontWeight.SemiBold), color = Ani.Soft)
                Spacer(Modifier.height(6.dp))
                if (friends.isEmpty()) {
                    Text("Add friends from their profile to invite them here.", style = inter(13.5f), color = Ani.Muted, modifier = Modifier.padding(vertical = 8.dp))
                } else LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    items(friends, key = { it.uid }) { f ->
                        val here = f.uid in members
                        val sent = f.uid in invited
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF1B1B1F)).border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(f.avatarUrl, f.shownName, 40.dp, deco = f.avatarDeco)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(f.shownName, style = inter(15f, FontWeight.SemiBold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (here) "Watching now" else "@${f.username}", style = inter(12.5f), color = if (here) Ani.Lime else Ani.Muted)
                            }
                            if (isHost && !here) {
                                Box(
                                    Modifier.clip(RoundedCornerShape(50)).background(if (sent) Color(0xFF232326) else com.anibiar.app.ui.theme.Ani.Lavender)
                                        .clickable {
                                            scope.launch {
                                                runCatching { social.inviteToRoom(room, f.uid) }
                                                    .onSuccess { if (f.uid !in invited) invited.add(f.uid); failed = null; android.widget.Toast.makeText(ctx, "Invite sent to ${f.shownName}", android.widget.Toast.LENGTH_SHORT).show() }
                                                    .onFailure { failed = it.message ?: "Couldn't send the invite" }
                                            }
                                        }.padding(horizontal = 16.dp, vertical = 8.dp),
                                ) { Text(if (sent) "Invite again" else "Invite", style = inter(13f, FontWeight.SemiBold), color = if (sent) Color.White else Color.Black) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Playback", style = inter(13f, FontWeight.SemiBold), color = Ani.Soft)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    listOf("sub" to "Sub", "dub" to "Dub").forEach { (k, label) ->
                        val on = audio == k
                        val can = isHost && (k == "sub" || dubAvailable)
                        Text(label, style = inter(13.5f, FontWeight.SemiBold), color = if (on) Color.Black else if (can) Color.White else Ani.Muted,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) com.anibiar.app.ui.theme.Ani.Lavender else Ani.ButtonDark).clickable(enabled = can && !on) { onAudio(k) }.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
                if (servers.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        servers.forEach { s ->
                            Text(s, style = inter(13.5f, FontWeight.SemiBold), color = if (s == server) Color.Black else Color.White,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (s == server) com.anibiar.app.ui.theme.Ani.Lavender else Ani.ButtonDark).clickable { onServer(s) }.padding(horizontal = 16.dp, vertical = 8.dp))
                        }
                    }
                    Text("Your server only affects your own video quality.", style = inter(11.5f), color = Ani.Muted, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp)).background(com.anibiar.app.ui.theme.Ani.Lavender).clickable(onClick = onPickAnime),
                    contentAlignment = Alignment.Center,
                ) { Text(if (isHost) "Change anime" else "Request an anime", style = inter(14.5f, FontWeight.Bold), color = Color.Black) }
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1B1B1F)).border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(14.dp)).clickable(onClick = onLeave),
                    contentAlignment = Alignment.Center,
                ) { Text(if (isHost) "End room for everyone" else "Leave room", style = inter(14.5f, FontWeight.SemiBold), color = Color(0xFFFF6B6B)) }
            }
        }
    }
}
