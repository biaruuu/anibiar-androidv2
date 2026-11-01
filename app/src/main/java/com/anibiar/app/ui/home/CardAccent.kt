package com.anibiar.app.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.anibiar.app.ui.theme.Ani
import com.anibiar.app.ui.theme.AniIcons

/** The anime's own cover colour (AniList `coverImage.color`), lifted so it stays readable on onyx. */
internal fun accentOf(hex: String?): Color {
    val base = hex?.let { runCatching { android.graphics.Color.parseColor(it) }.getOrNull() } ?: return Ani.Lavender
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(base, hsl)
    hsl[1] = hsl[1].coerceAtLeast(0.45f)
    hsl[2] = hsl[2].coerceIn(0.62f, 0.74f)
    return Color(ColorUtils.HSLToColor(hsl))
}

/** Touch-hold or mouse-hover state for a card, plus the modifier that wires it up. */
internal class CardHover {
    var pressed by mutableStateOf(false)
    val source = MutableInteractionSource()
}

@Composable
internal fun rememberCardHover() = remember { CardHover() }

@Composable
internal fun CardHover.active(): Boolean {
    val hovered by source.collectIsHoveredAsState()
    return pressed || hovered
}

/**
 * Touching a card lights it up ([onArm]) and it stays lit. A quick tap opens it; holding ~0.2s and releasing does
 * NOT open it, so the play / + buttons stay available. Scrolling that starts on the card cancels only the tap.
 */
internal fun Modifier.hoverCard(
    state: CardHover,
    onClick: () -> Unit,
    onArm: (() -> Unit)? = null,
): Modifier = this
    .hoverable(state.source)
    .pointerInput(onClick, onArm) {
        awaitEachGesture {
            awaitFirstDown()
            state.pressed = true
            onArm?.invoke() // the touched card lights up at once and stays lit (even if the finger starts scrolling)
            try {
                var released = false
                val quick = withTimeoutOrNull(HOLD_MS) {
                    val up = waitForUpOrCancellation()
                    released = true
                    up
                }
                when {
                    quick != null -> { quick.consume(); onClick() }
                    released -> Unit // cancelled by a scroll/drag
                    else -> {
                        // Held: with hover-arming the release only keeps the card lit; otherwise it is a normal tap.
                        val up = waitForUpOrCancellation()
                        if (onArm == null && up != null) { up.consume(); onClick() }
                    }
                }
            } finally {
                state.pressed = false
            }
        }
    }

private const val HOLD_MS = 200L

@Composable
internal fun accentColor(active: Boolean, accent: Color, normal: Color): State<Color> =
    animateColorAsState(if (active) accent else normal, tween(180), label = "accent")

/** Dim scrim + play glyph (and optional + / check button) that fades in on hover/press, like Miruro. */
@Composable
internal fun HoverOverlay(
    active: Boolean,
    saved: Boolean = false,
    showAdd: Boolean = false,
    accent: Color = Ani.Lavender,
    onToggleSaved: () -> Unit = {},
    playSize: Int = 44,
) {
    val a by animateFloatAsState(if (active) 1f else 0f, tween(180), label = "overlay")
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().alpha(a).background(Color.Black.copy(alpha = 0.32f)))
        Icon(
            AniIcons.Play, contentDescription = null, tint = Color.White,
            modifier = Modifier.align(Alignment.Center).size(playSize.dp).alpha(a),
        )
        if (showAdd && active) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1C1C1F))
                    .clickable(onClick = onToggleSaved),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (saved) AniIcons.Check else AniIcons.Add,
                    contentDescription = "Add to list",
                    tint = accent,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** Shared "which card is lit" state for a screen: one key at a time, plus the watchlist for the + button. */
internal class CardArm(
    val armed: String?,
    val arm: (String) -> Unit,
    val saved: Set<Int>,
    val toggle: (Int) -> Unit,
)

internal val LocalCardArm = androidx.compose.runtime.compositionLocalOf<CardArm?> { null }

/** Clears [onClear] only for a real tap on empty space — never for scrolling or dragging. */
internal fun Modifier.clearOnEmptyTap(onClear: () -> Unit): Modifier = pointerInput(Unit) {
    val slop = viewConfiguration.touchSlop
    awaitPointerEventScope {
        var downAt: androidx.compose.ui.geometry.Offset? = null
        while (true) {
            val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Final)
            for (c in event.changes) {
                if (c.pressed && !c.previousPressed) {
                    downAt = if (c.isConsumed) null else c.position
                } else if (!c.pressed && c.previousPressed) {
                    val start = downAt
                    if (start != null && (c.position - start).getDistance() < slop) onClear()
                    downAt = null
                }
            }
        }
    }
}
