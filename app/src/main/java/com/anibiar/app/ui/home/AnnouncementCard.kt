package com.anibiar.app.ui.home

import com.anibiar.app.ui.theme.liquidGlass
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anibiar.app.data.Announcement
import com.anibiar.app.ui.theme.Ani
import com.anibiar.app.ui.theme.inter

/** An announcement from the owner, shown on Home and at the top of the inbox. [onDismiss] is null where it can't be hidden. */
@Composable
fun AnnouncementCard(a: Announcement, modifier: Modifier = Modifier, onDismiss: (() -> Unit)? = null) {
    val accent = if (a.important) Color(0xFFFFC94D) else Ani.Lavender
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.fillMaxWidth().liquidGlass(shape).background(accent.copy(alpha = 0.14f)).border(1.dp, accent.copy(alpha = 0.40f), shape).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (a.important) "IMPORTANT" else "ANNOUNCEMENT", style = inter(11f, FontWeight.Bold), color = accent)
            if (a.draft) Text("PREVIEW · ONLY YOU SEE THIS", style = inter(10.5f, FontWeight.Bold), color = Color(0xFFFF8A80))
            Spacer(Modifier.weight(1f))
            if (onDismiss != null) Text("Dismiss", style = inter(12.5f), color = Ani.Muted, modifier = Modifier.clickable(onClick = onDismiss).padding(4.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(a.title, style = inter(16f, FontWeight.Bold), color = Color.White)
        if (a.body.isNotBlank()) Text(a.body, style = inter(14f, lineHeight = 20f), color = Ani.Soft, modifier = Modifier.padding(top = 4.dp))
    }
}
