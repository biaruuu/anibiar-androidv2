package com.anibiar.app.ui.profile

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.anibiar.app.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** How a decoration moves around the avatar. */
enum class DecoMotion { Bob, Spin, Pulse, Float, Shake }

class Deco(val id: String, val name: String, @DrawableRes val res: Int, val motion: DecoMotion)
class Fx(val id: String, val name: String)

/** The avatar decorations and namecard effects people can pick. Artwork for decorations lives in res/drawable-nodpi/deco_*.png. */
object Looks {
    val decos = listOf(
        Deco("cat", "Kitty", R.drawable.deco_cat, DecoMotion.Bob),
        Deco("bolt", "Thunder", R.drawable.deco_bolt, DecoMotion.Spin),
        Deco("burst", "Burst", R.drawable.deco_burst, DecoMotion.Pulse),
        Deco("butterfly", "Butterflies", R.drawable.deco_butterfly, DecoMotion.Float),
        Deco("notes", "Study buddy", R.drawable.deco_notes, DecoMotion.Bob),
        Deco("rage", "Furious", R.drawable.deco_rage, DecoMotion.Shake),
    )
    val effects = listOf(
        Fx("sparkle", "Sparkles"), Fx("sakura", "Sakura"), Fx("embers", "Embers"), Fx("snow", "Snowfall"), Fx("aura", "Aura"),
    )
    fun deco(id: String?) = decos.firstOrNull { it.id == id }
}

/** A decoration drawn 20% larger than the avatar it sits on, with a little motion. Does not clip, so it can overhang. */
@Composable
fun DecoOverlay(id: String?, size: Dp, modifier: Modifier = Modifier) {
    val d = Looks.deco(id) ?: return
    val t = rememberInfiniteTransition(label = "deco")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(16000, easing = LinearEasing)), label = "spin")
    val pulse by t.animateFloat(0.97f, 1.05f, infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val sway by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sway")
    val shake by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(220), RepeatMode.Reverse), label = "shake")
    Image(
        painterResource(d.res), null,
        modifier = modifier.requiredSize(size * 1.2f).graphicsLayer {
            when (d.motion) {
                DecoMotion.Bob -> translationY = sway * size.toPx() * 0.012f
                DecoMotion.Spin -> rotationZ = spin
                DecoMotion.Pulse -> { scaleX = pulse; scaleY = pulse; rotationZ = spin * 0.15f }
                DecoMotion.Float -> { translationY = sway * size.toPx() * 0.015f; rotationZ = sway * 4f }
                DecoMotion.Shake -> rotationZ = shake * 3f
            }
        },
    )
}

/** Animated particles drawn over a namecard or banner. Cheap: a few dozen shapes from a seeded random. */
@Composable
fun EffectLayer(id: String?, modifier: Modifier = Modifier) {
    if (id == null || Looks.effects.none { it.id == id }) return
    val t = rememberInfiniteTransition(label = "fx")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(12000, easing = LinearEasing)), label = "p")
    val pts = remember(id) { List(if (id == "aura") 1 else 28) { i -> java.util.Random(i * 7919L + id.hashCode()).let { r -> floatArrayOf(r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextFloat()) } } }
    Canvas(modifier) {
        val w = size.width; val h = size.height; val u = minOf(w, h).coerceAtLeast(1f)
        when (id) {
            "sparkle" -> pts.forEach { q ->
                val a = abs(sin((p * 3f + q[2]) * PI.toFloat())).let { it * it * it }
                val c = Color(0xFFFFF2C4).copy(alpha = a)
                val r = (0.018f + 0.03f * q[3]) * u
                val o = Offset(q[0] * w, q[1] * h)
                drawLine(c, Offset(o.x - r, o.y), Offset(o.x + r, o.y), r * 0.22f, StrokeCap.Round)
                drawLine(c, Offset(o.x, o.y - r), Offset(o.x, o.y + r), r * 0.22f, StrokeCap.Round)
                drawCircle(c, r * 0.22f, o)
            }
            "sakura" -> pts.forEach { q ->
                val k = 1 + (q[3] * 2).toInt()
                val y = (p * k + q[1]) % 1f
                val x = q[0] + sin((p * 2f * PI.toFloat() * k) + q[2] * 6f) * 0.03f
                val c = Offset(x * w, y * h)
                val s = (0.016f + 0.02f * q[2]) * u
                rotate(degrees = (p * 360f * k) + q[2] * 90f, pivot = c) {
                    drawOval(Color(0xFFFFB7D5).copy(alpha = 0.85f), Offset(c.x - s, c.y - s * 0.55f), Size(s * 2f, s * 1.1f))
                }
            }
            "embers" -> pts.forEach { q ->
                val k = 1 + (q[3] * 2).toInt()
                val f = (p * k + q[1]) % 1f
                val c = Offset((q[0] + sin(f * 6f + q[2] * 6f) * 0.02f) * w, h * (1f - f))
                val a = (1f - f) * (0.4f + 0.6f * q[2])
                drawCircle(Color(0xFFFF9A3C).copy(alpha = a), (0.008f + 0.012f * q[2]) * u, c)
            }
            "snow" -> pts.forEach { q ->
                val k = 1 + (q[3] * 2).toInt()
                val f = (p * k + q[1]) % 1f
                val c = Offset((q[0] + sin(f * 8f + q[2] * 6f) * 0.015f) * w, f * h)
                drawCircle(Color.White.copy(alpha = 0.35f + 0.5f * q[2]), (0.006f + 0.011f * q[2]) * u, c)
            }
            "aura" -> {
                val a = 0.16f + 0.12f * (0.5f + 0.5f * sin(p * 2f * PI.toFloat() * 3f))
                drawRect(Brush.radialGradient(listOf(Color(0xFF4BA3FF).copy(alpha = a), Color.Transparent), Offset(w * 0.8f, h), w * 0.9f))
                drawRect(Brush.radialGradient(listOf(Color(0xFFFFB454).copy(alpha = a * 0.8f), Color.Transparent), Offset(w * 0.05f, 0f), w * 0.6f))
            }
        }
    }
}
