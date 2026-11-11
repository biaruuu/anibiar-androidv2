package com.anibiar.app.ui.profile

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class Deco(val id: String, val name: String)
class Fx(val id: String, val name: String)
class Theme(val id: String, val name: String, val color: Color)

/**
 * The avatar decorations and namecard effects people can pick. Decorations are the animated WebP files in
 * assets/deco (`<id>.webp` animated, `<id>_s.webp` first frame); they carry their own motion, so none is added here.
 */
object Looks {
    val decos = listOf(
        Deco("cat", "Kitty"), Deco("bolt", "Thunder"), Deco("burst", "Burst"),
        Deco("butterfly", "Butterflies"), Deco("notes", "Study buddy"), Deco("rage", "Furious"),
    )
    val effects = listOf(
        Fx("storm", "Storm"), Fx("sparkle", "Sparkles"), Fx("sakura", "Sakura"), Fx("embers", "Embers"), Fx("snow", "Snowfall"), Fx("aura", "Aura"), Fx("rain", "Rain"), Fx("fireflies", "Fireflies"), Fx("hearts", "Hearts"),
    )
    val themes = listOf(
        Theme("ocean", "Ocean", Color(0xFF2B7FFF)), Theme("rose", "Rose", Color(0xFFFF4D8D)), Theme("forest", "Forest", Color(0xFF2FBF71)),
        Theme("sunset", "Sunset", Color(0xFFFF8A3D)), Theme("gold", "Gold", Color(0xFFFFC83D)), Theme("crimson", "Crimson", Color(0xFFE5383B)),
    )
    fun theme(id: String?) = themes.firstOrNull { it.id == id }
    fun deco(id: String?) = decos.firstOrNull { it.id == id }
}

/** Avatars at least this big play the decoration's animation; smaller ones (lists) show its first frame to stay light. */
private val AnimateFrom = 24.dp

/** A decoration drawn 20% larger than the avatar it sits on. Does not clip, so it can overhang. */
@Composable
fun DecoOverlay(id: String?, size: Dp, modifier: Modifier = Modifier) {
    val d = Looks.deco(id) ?: return
    val ctx = LocalContext.current
    val box = size * 1.2f
    val px = with(LocalDensity.current) { box.roundToPx() }
    val animated = size >= AnimateFrom
    val req = remember(d.id, px, animated) {
        ImageRequest.Builder(ctx).data("file:///android_asset/deco/${d.id}${if (animated) "" else "_s"}.webp").size(px).build()
    }
    AsyncImage(req, null, modifier = modifier.requiredSize(box), contentScale = ContentScale.Fit)
}

/** Animated particles drawn over a namecard or banner. Cheap: a few dozen shapes from a seeded random. */
@Composable
fun EffectLayer(id: String?, modifier: Modifier = Modifier) {
    if (id == null || Looks.effects.none { it.id == id }) return
    if (id == "storm") {
        AsyncImage(
            ImageRequest.Builder(LocalContext.current).data("file:///android_asset/deco/storm.webp").build(), null,
            modifier = modifier, contentScale = ContentScale.Crop, alignment = androidx.compose.ui.BiasAlignment(0f, -0.85f),
        )
        return
    }
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
            "rain" -> pts.forEach { q ->
                val k = 2 + (q[3] * 2).toInt()
                val f = (p * k * 2f + q[1]) % 1f
                val x = q[0] * w + f * w * 0.06f
                val y = f * h * 1.1f - h * 0.05f
                drawLine(Color(0xFFBFD8FF).copy(alpha = 0.25f + 0.35f * q[2]), Offset(x, y), Offset(x - 0.012f * u, y + 0.045f * u), 0.004f * u, StrokeCap.Round)
            }
            "fireflies" -> pts.forEach { q ->
                val a = (0.5f + 0.5f * sin((p * 4f + q[2]) * 2f * PI.toFloat())).let { it * it }
                val c = Offset((q[0] + sin((p + q[1]) * 2f * PI.toFloat()) * 0.04f) * w, (q[1] + sin((p * 2f + q[0]) * 2f * PI.toFloat()) * 0.03f) * h)
                val r = (0.008f + 0.012f * q[3]) * u
                drawCircle(Color(0xFFFFF59D).copy(alpha = a * 0.25f), r * 3f, c)
                drawCircle(Color(0xFFFFF59D).copy(alpha = a), r, c)
            }
            "hearts" -> pts.forEach { q ->
                val k = 1 + (q[3] * 2).toInt()
                val f = (p * k + q[1]) % 1f
                val c = Offset((q[0] + sin(f * 6f + q[2] * 6f) * 0.02f) * w, h * (1f - f))
                val r = (0.012f + 0.014f * q[2]) * u
                val col = Color(0xFFFF6B9D).copy(alpha = (1f - f) * 0.9f)
                drawCircle(col, r, Offset(c.x - r * 0.55f, c.y))
                drawCircle(col, r, Offset(c.x + r * 0.55f, c.y))
                val path = androidx.compose.ui.graphics.Path().apply { moveTo(c.x - r * 1.5f, c.y + r * 0.2f); lineTo(c.x, c.y + r * 2f); lineTo(c.x + r * 1.5f, c.y + r * 0.2f); close() }
                drawPath(path, col)
            }
            "aura" -> {
                val a = 0.16f + 0.12f * (0.5f + 0.5f * sin(p * 2f * PI.toFloat() * 3f))
                drawRect(Brush.radialGradient(listOf(Color(0xFF4BA3FF).copy(alpha = a), Color.Transparent), Offset(w * 0.8f, h), w * 0.9f))
                drawRect(Brush.radialGradient(listOf(Color(0xFFFFB454).copy(alpha = a * 0.8f), Color.Transparent), Offset(w * 0.05f, 0f), w * 0.6f))
            }
        }
    }
}


/** Soft colour wash from a profile theme, drawn over a banner or namecard. */
@Composable
fun ThemeTint(id: String?, modifier: Modifier = Modifier) {
    val t = Looks.theme(id) ?: return
    Box(modifier.background(Brush.verticalGradient(listOf(t.color.copy(alpha = 0.38f), t.color.copy(alpha = 0.10f), Color.Transparent))))
}


/** Banner media: still, GIF/animated WebP (Coil animates them) or a looping muted MP4 (live photos). */
@Composable
fun BannerMedia(url: String, modifier: Modifier = Modifier, alpha: Float = 1f) {
    if (!url.substringBefore('?').endsWith(".mp4", ignoreCase = true)) {
        AsyncImage(url, null, contentScale = ContentScale.Crop, alpha = alpha, modifier = modifier)
        return
    }
    val ctx = LocalContext.current
    val player = remember(url) {
        androidx.media3.exoplayer.ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(url))
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
            volume = 0f
            playWhenReady = true
            prepare()
        }
    }
    androidx.compose.runtime.DisposableEffect(player) { onDispose { player.release() } }
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { c ->
            androidx.media3.ui.PlayerView(c).apply {
                useController = false
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                this.player = player
            }
        },
        modifier = modifier.alpha(alpha),
    )
}
