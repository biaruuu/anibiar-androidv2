package com.anibiar.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The glass look shared by the profile buttons, the tab bar and the video player controls. */
@Composable
fun GlassButton(onClick: () -> Unit, size: Dp = 36.dp, selected: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    val hi = if (selected) 0.30f else 0.14f
    Box(
        Modifier.size(size).clip(CircleShape).background(Color(if (selected) 0x59FFFFFF else 0x73000000))
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = hi), Color.White.copy(alpha = 0.02f), Color.White.copy(alpha = 0.06f))))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = if (selected) 0.55f else 0.38f), Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.22f))), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center, content = content,
    )
}

/** Set by a screen that covers the whole page (edit profile, follower lists) so the tab bar steps aside. */
object NavState {
    var hidden by androidx.compose.runtime.mutableStateOf(false)
}
