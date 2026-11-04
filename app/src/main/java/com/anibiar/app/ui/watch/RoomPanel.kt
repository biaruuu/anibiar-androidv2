package com.anibiar.app.ui.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anibiar.app.social.UserProfile
import com.anibiar.app.ui.profile.Avatar
import com.anibiar.app.ui.theme.Ani
import com.anibiar.app.ui.theme.AniIcons
import com.anibiar.app.ui.theme.inter

private val Speaking = Color(0xFF23A559)
private val Tile = Color(0xFF2B2B2D)
private val Danger = Color(0xFFED4245)

/** One person in the call: big avatar, green ring while talking, mic-off badge when muted. */
@Composable
private fun Tile(p: UserProfile?, uid: String, speaking: Boolean, muted: Boolean, host: Boolean, me: Boolean, modifier: Modifier) {
    BoxWithConstraints(
        modifier.clip(RoundedCornerShape(20.dp)).background(Tile)
            .then(if (speaking) Modifier.border(3.dp, Speaking, RoundedCornerShape(20.dp)) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        val d: Dp = (if (maxWidth < maxHeight) maxWidth else maxHeight) * 0.5f
        Avatar(p?.avatarUrl, p?.shownName ?: "?", d)
        Row(Modifier.align(Alignment.BottomStart).padding(10.dp).clip(RoundedCornerShape(50)).background(Color(0x99000000)).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text((p?.shownName ?: "…") + if (me) " (you)" else "", style = inter(12.5f, FontWeight.SemiBold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (host) Text("  HOST", style = inter(10f, FontWeight.Bold), color = Ani.Lime)
        }
        if (muted) {
            Box(Modifier.align(Alignment.BottomEnd).padding(10.dp).size(34.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xCC1E1F22)), contentAlignment = Alignment.Center) {
                Icon(AniIcons.MicOff, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun RoundButton(icon: ImageVector, bg: Color, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(54.dp).clip(CircleShape).background(bg).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(26.dp))
    }
}

/** The Discord-style call: participants on top, round controls under them. */
@Composable
fun RoomPanel(
    members: List<String>, profiles: Map<String, UserProfile>, hostId: String, myId: String,
    speaking: Set<String>, muted: Set<String>, micOk: Boolean, myMuted: Boolean,
    onMic: () -> Unit, onInvite: () -> Unit, onLeave: () -> Unit,
) {
    val ordered = members.sortedWith(compareBy({ it != hostId }, { it != myId }))
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cols = if (ordered.size <= 1) 1 else if (maxWidth >= 720.dp) 3 else 2
            val ratio = if (cols == 1) 16f / 9f else 1.1f
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ordered.chunked(cols).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { uid ->
                            Tile(
                                profiles[uid], uid, uid in speaking, uid in muted || (uid == myId && (myMuted || !micOk)), uid == hostId, uid == myId,
                                Modifier.weight(1f).aspectRatio(ratio),
                            )
                        }
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally)) {
            RoundButton(if (!micOk || myMuted) AniIcons.MicOff else AniIcons.Mic, if (!micOk || myMuted) Color.White else Color(0xFF3A3A3D), if (!micOk || myMuted) Color.Black else Color.White, onMic)
            RoundButton(AniIcons.PersonAdd, Color(0xFF3A3A3D), Color.White, onInvite)
            RoundButton(AniIcons.CallEnd, Danger, Color.White, onLeave)
        }
    }
}

/** Small avatars floating over the video in full view, so you still see who is there and who is talking. */
@Composable
fun AvatarStrip(members: List<String>, profiles: Map<String, UserProfile>, speaking: Set<String>, muted: Set<String>, myId: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        members.forEach { uid ->
            Box {
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(Color(0x66000000))
                        .then(if (uid in speaking) Modifier.border(3.dp, Speaking, CircleShape) else Modifier.border(1.dp, Color(0x55FFFFFF), CircleShape))
                        .padding(3.dp),
                ) { Avatar(profiles[uid]?.avatarUrl, profiles[uid]?.shownName ?: "?", 36.dp) }
                if (uid in muted) Box(Modifier.align(Alignment.BottomEnd).size(16.dp).clip(CircleShape).background(Danger), contentAlignment = Alignment.Center) {
                    Icon(AniIcons.MicOff, null, tint = Color.White, modifier = Modifier.size(10.dp))
                }
            }
        }
    }
}
