package com.anibiar.app.ui.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anibiar.app.social.RoomSession
import com.anibiar.app.ui.theme.Ani
import com.anibiar.app.ui.theme.AniIcons
import com.anibiar.app.ui.theme.inter

/** Discord-style "you're in a call" bar: stays on screen while you browse; tap to go back to the room. */
@Composable
fun CallBar(session: RoomSession, myId: String?, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val room = session.room ?: return
    val together = session.together
    val members by (together?.members ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptySet<String>()) }).collectAsStateWithLifecycle()
    val muted by (session.voice?.muted ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) }).collectAsStateWithLifecycle()
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF1B1B1F))
            .border(1.dp, com.anibiar.app.ui.theme.Ani.Lavender.copy(alpha = 0.4f), RoundedCornerShape(16.dp)).clickable(onClick = onOpen).padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(Color(0xFF23A559)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("In call · ${members.size} here", style = inter(13f, FontWeight.Bold), color = Color.White)
            Text(room.title.ifBlank { "Watch together" } + " · Tap to open", style = inter(11.5f), color = Color(0xFF9B98A0), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(38.dp).clip(CircleShape).background(if (muted) Color.White else Color(0x33FFFFFF)).clickable { session.voice?.setMuted(!muted) }, contentAlignment = Alignment.Center) {
            Icon(if (muted) AniIcons.MicOff else AniIcons.Mic, null, tint = if (muted) Color.Black else Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFFED4245)).clickable { session.leave(room.hostId == myId) }, contentAlignment = Alignment.Center) {
            Icon(AniIcons.CallEnd, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}
