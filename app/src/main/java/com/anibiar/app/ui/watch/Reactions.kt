package com.anibiar.app.ui.watch

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anibiar.app.social.WatchTogether

val REACTIONS = listOf("😂", "😭", "😱", "🔥", "❤️", "👏")

private class Floating(val id: Long, val emoji: String, val x: Float)

/** Emojis everyone in the room sends float up over the video. */
@Composable
fun ReactionOverlay(together: WatchTogether?, modifier: Modifier = Modifier) {
    val items = remember { mutableStateListOf<Floating>() }
    LaunchedEffect(together) {
        val t = together ?: return@LaunchedEffect
        var n = 0L
        t.reactions.collect { (_, e) ->
            if (items.size < 24) items.add(Floating(n++, e, 0.12f + (n * 37 % 76) / 100f))
        }
    }
    BoxWithConstraints(modifier) {
        val h = with(LocalDensity.current) { maxHeight.toPx() }
        items.toList().forEach { f ->
            key(f.id) {
                val a = remember { Animatable(0f) }
                LaunchedEffect(Unit) { a.animateTo(1f, tween(2300, easing = LinearEasing)); items.remove(f) }
                Text(
                    f.emoji, fontSize = 34.sp,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = maxWidth * f.x, bottom = 12.dp)
                        .graphicsLayerY(-(h * 0.75f) * a.value).alpha(1f - a.value * a.value),
                )
            }
        }
    }
}

private fun Modifier.graphicsLayerY(y: Float) = this.then(Modifier.graphicsLayer { translationY = y })

/** Quick-react row shown under the video. */
@Composable
fun ReactionBar(onReact: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Color(0xFF1A1A1C)).padding(horizontal = 6.dp, vertical = 2.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        REACTIONS.forEach { e -> Text(e, fontSize = 22.sp, modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onReact(e) }.padding(horizontal = 10.dp, vertical = 6.dp)) }
    }
}
