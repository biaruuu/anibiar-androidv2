package com.anibiar.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
        Modifier.size(size).clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xB0404048), Color(0xB0202024), Color(0xB0303036))))
            .then(if (selected) Modifier.background(Color(0x40FFFFFF)) else Modifier)
            .border(1.dp, Brush.linearGradient(listOf(Color(0x99FFFFFF), Color(0x1AFFFFFF), Color(0x66FFFFFF))), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center, content = content,
    )
}

/** Set by a screen that covers the whole page (edit profile, follower lists) so the tab bar steps aside. */
object NavState {
    var hidden by androidx.compose.runtime.mutableStateOf(false)
}

/** The shared glass card fill used by the Inbox, Profile and Settings surfaces. */
val GlassFill: Brush = Brush.linearGradient(listOf(Color(0xFF26262B), Color(0xFF18181B), Color(0xFF212125)))

@Composable
fun GlassMenu(expanded: Boolean, onDismiss: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    androidx.compose.material3.DropdownMenu(
        expanded = expanded, onDismissRequest = onDismiss,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp), containerColor = Color(0xF2202024),
        tonalElevation = 0.dp, shadowElevation = 14.dp, content = content,
    )
}

@Composable
fun GlassMenuItem(label: String, icon: String, color: Color = Color.White, onClick: () -> Unit) {
    androidx.compose.material3.DropdownMenuItem(
        text = { androidx.compose.material3.Text(label, color = color, style = inter(15f, androidx.compose.ui.text.font.FontWeight.Medium)) },
        leadingIcon = { androidx.compose.material3.Text(icon, fontSize = androidx.compose.ui.unit.TextUnit(16f, androidx.compose.ui.unit.TextUnitType.Sp)) },
        onClick = onClick,
    )
}

/** The round glass "<" back button used on every full page. */
@Composable
fun GlassBack(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier) {
        GlassButton(onClick = onClick, size = 36.dp) {
            androidx.compose.foundation.Canvas(Modifier.size(20.dp)) {
                val w = size.width; val h = size.height
                val path = androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.62f, h * 0.2f); lineTo(w * 0.3f, h * 0.5f); lineTo(w * 0.62f, h * 0.8f) }
                drawPath(path, Color.White, style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.11f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
        }
    }
}
