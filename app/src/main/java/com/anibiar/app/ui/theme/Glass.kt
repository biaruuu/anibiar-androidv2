package com.anibiar.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
        Modifier.size(size).clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xE61E1E20), Color(0xE61E1E20))))
            .then(if (selected) Modifier.background(Color(0x40FFFFFF)) else Modifier)
            .border(1.dp, Brush.linearGradient(listOf(Color(0x1FFFFFFF), Color(0x1FFFFFFF))), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center, content = content,
    )
}

/** Set by a screen that covers the whole page (edit profile, follower lists) so the tab bar steps aside. */
object NavState {
    var hidden by androidx.compose.runtime.mutableStateOf(false)
}

/** The shared glass card fill used by the Inbox, Profile and Settings surfaces. */
val GlassFill: Brush = Brush.linearGradient(listOf(Color(0xFF1C1C1E), Color(0xFF1C1C1E)))

@Composable
fun GlassMenu(expanded: Boolean, onDismiss: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    androidx.compose.material3.DropdownMenu(
        expanded = expanded, onDismissRequest = onDismiss,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp), containerColor = Color(0xF20C0C0F),
        tonalElevation = 0.dp, shadowElevation = 18.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Brush.linearGradient(listOf(Color(0x1FFFFFFF), Color(0x1FFFFFFF)))),
        content = content,
    )
}

/** A menu row with a real vector icon. Pressing it springs in and lights up like a drop of liquid glass. */
@Composable
fun GlassMenuItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color = Color.White, onClick: () -> Unit) {
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.93f else 1f, androidx.compose.animation.core.spring(0.45f, 600f), label = "menuScale")
    val glow by androidx.compose.animation.core.animateFloatAsState(if (pressed) 1f else 0f, androidx.compose.animation.core.tween(110), label = "menuGlow")
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(15.dp)
    androidx.compose.foundation.layout.Row(
        Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.24f * glow), Color.White.copy(alpha = 0.07f * glow))))
            .border(1.dp, Color.White.copy(alpha = 0.30f * glow), shape)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp)
            .androidx_widthIn(150.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        androidx.compose.foundation.layout.Spacer(Modifier.width(13.dp))
        androidx.compose.material3.Text(label, color = color, style = inter(15f, androidx.compose.ui.text.font.FontWeight.Medium))
    }
}

private fun Modifier.androidx_widthIn(min: androidx.compose.ui.unit.Dp) = this.then(Modifier.widthIn(min = min))

@Composable
fun ChevronIcon(modifier: Modifier = Modifier.size(20.dp)) {
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width; val h = size.height
        val path = androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.62f, h * 0.2f); lineTo(w * 0.3f, h * 0.5f); lineTo(w * 0.62f, h * 0.8f) }
        drawPath(path, Color.White, style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.11f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

/** Two or more icons share one glass pill, like the star and dots in the chat header. */
@Composable
fun GlassPill(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
    androidx.compose.foundation.layout.Row(
        modifier.height(36.dp).clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xE61E1E20), Color(0xE61E1E20))))
            .border(1.dp, Brush.linearGradient(listOf(Color(0x1FFFFFFF), Color(0x1FFFFFFF))), shape)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically, content = content,
    )
}

/** One tappable icon inside a [GlassPill]. */
@Composable
fun PillIcon(onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.size(width = 40.dp, height = 36.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center, content = content)
}

val GlassBtnBrush: Brush = Brush.linearGradient(listOf(Color(0xE61E1E20), Color(0xE61E1E20)))
val GlassRimBrush: Brush = Brush.linearGradient(listOf(Color(0x1FFFFFFF), Color(0x1FFFFFFF)))

/** iMessage glass: clip, translucent sheen fill and a bright rim. Use on any button, pill or chip. */
fun Modifier.glass(shape: androidx.compose.ui.graphics.Shape): Modifier =
    this.clip(shape).background(GlassBtnBrush).border(1.dp, GlassRimBrush, shape)

/** The round glass "<" back button used on every full page. */
@Composable
fun GlassBack(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier) {
        GlassButton(onClick = onClick, size = 36.dp) { ChevronIcon(Modifier.size(20.dp)) }
    }
}
