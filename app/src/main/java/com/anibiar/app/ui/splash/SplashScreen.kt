package com.anibiar.app.ui.splash

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.anibiar.app.R
import com.anibiar.app.ui.theme.Ani
import com.anibiar.app.ui.theme.inter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Logo layers are cut from the 1024px artwork; each keeps its position (x, y) on that canvas. */
private class Layer(val image: ImageBitmap, val x: Int, val y: Int)

private class LogoLayers(
    val legs: Layer,
    val eyeL: Layer, val eyeR: Layer,
    val slitL: Layer, val slitR: Layer,
    val earsL: List<Layer>, val earsR: List<Layer>,
)

private val EAR_L = intArrayOf(R.drawable.kiro_ear_l_00, R.drawable.kiro_ear_l_01, R.drawable.kiro_ear_l_02, R.drawable.kiro_ear_l_03, R.drawable.kiro_ear_l_04, R.drawable.kiro_ear_l_05, R.drawable.kiro_ear_l_06, R.drawable.kiro_ear_l_07, R.drawable.kiro_ear_l_08, R.drawable.kiro_ear_l_09, R.drawable.kiro_ear_l_10, R.drawable.kiro_ear_l_11)
private val EAR_R = intArrayOf(R.drawable.kiro_ear_r_00, R.drawable.kiro_ear_r_01, R.drawable.kiro_ear_r_02, R.drawable.kiro_ear_r_03, R.drawable.kiro_ear_r_04, R.drawable.kiro_ear_r_05, R.drawable.kiro_ear_r_06, R.drawable.kiro_ear_r_07, R.drawable.kiro_ear_r_08, R.drawable.kiro_ear_r_09, R.drawable.kiro_ear_r_10, R.drawable.kiro_ear_r_11)

private fun load(context: android.content.Context, id: Int, x: Int, y: Int): Layer {
    val options = BitmapFactory.Options().apply { inScaled = false }
    val bmp = BitmapFactory.decodeResource(context.resources, id, options)
    return Layer(bmp.asImageBitmap(), x, y)
}

private fun loadLayers(context: android.content.Context) = LogoLayers(
    legs = load(context, R.drawable.kiro_legs, 70, 375),
    eyeL = load(context, R.drawable.kiro_eye_l, 328, 581),
    eyeR = load(context, R.drawable.kiro_eye_r, 577, 581),
    slitL = load(context, R.drawable.kiro_slit_l, 328, 581),
    slitR = load(context, R.drawable.kiro_slit_r, 577, 581),
    earsL = EAR_L.map { load(context, it, 190, 140) },
    earsR = EAR_R.map { load(context, it, 504, 140) },
)

private const val TOTAL_MS = 2800f

/** 0 → 1 → 0 pulse starting at [start]ms: rises over [up]ms, falls over [down]ms. */
private fun pulse(ms: Float, start: Float, up: Float, down: Float): Float = when {
    ms < start || ms > start + up + down -> 0f
    ms < start + up -> ease((ms - start) / up)
    else -> ease(1f - (ms - start - up) / down)
}

private fun ease(x: Float): Float = x * x * (3f - 2f * x)

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val layers by produceState<LogoLayers?>(null) {
        value = withContext(Dispatchers.Default) { runCatching { loadLayers(context) }.getOrNull() }
    }
    val clock = remember { Animatable(0f) }
    LaunchedEffect(layers) {
        if (layers != null) {
            clock.animateTo(TOTAL_MS, tween(TOTAL_MS.toInt(), easing = LinearEasing))
            onFinished()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Ani.Onyx)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        contentAlignment = Alignment.Center,
    ) {
        val ms = clock.value
        val intro = ease((ms / 520f).coerceIn(0f, 1f))
        val outro = (1f - ((ms - 2450f) / 350f).coerceIn(0f, 1f))
        val breathe = 1f + 0.012f * kotlin.math.sin(ms / 420f)
        val glow = (0.10f + 0.10f * pulse(ms, 900f, 500f, 700f)) * intro

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            modifier = Modifier.graphicsLayer { alpha = outro }) {
            val l = layers
            Canvas(
                Modifier
                    .size(250.dp)
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                        val s = (0.84f + 0.16f * intro) * breathe
                        scaleX = s; scaleY = s
                        alpha = intro
                    }
                    .drawBehind {
                        drawCircle(
                            Brush.radialGradient(listOf(Color(0xFFB9A1DF).copy(alpha = glow), Color.Transparent)),
                            radius = size.minDimension * 0.75f,
                        )
                    },
            ) {
                if (l != null) drawLogo(l, ms)
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "Kiro",
                style = inter(30f, FontWeight.Bold),
                color = Color.White.copy(alpha = ease(((ms - 650f) / 500f).coerceIn(0f, 1f))),
            )
        }
    }
}

private fun DrawScope.drawLogo(l: LogoLayers, ms: Float) {
    val u = size.width / 1024f

    fun DrawScope.put(layer: Layer, dx: Float = 0f) {
        drawImage(
            image = layer.image,
            dstOffset = IntOffset(((layer.x + dx) * u).toInt(), (layer.y * u).toInt()),
            dstSize = IntSize((layer.image.width * u).toInt() + 1, (layer.image.height * u).toInt() + 1),
            filterQuality = FilterQuality.High,
        )
    }

    // Ears: a quick flick outward and back, left then right, then a softer double twitch.
    val flickL = maxOf(pulse(ms, 600f, 220f, 320f), 0.7f * pulse(ms, 1650f, 180f, 260f))
    val flickR = maxOf(pulse(ms, 760f, 220f, 320f), 0.7f * pulse(ms, 1650f, 180f, 260f))
    put(l.earsL[(flickL * (l.earsL.size - 1)).toInt().coerceIn(0, l.earsL.size - 1)])
    put(l.earsR[(flickR * (l.earsR.size - 1)).toInt().coerceIn(0, l.earsR.size - 1)])
    put(l.legs)

    // Shimmer: a lavender glint sweeping across the white artwork (only where it is already drawn).
    val sweep = ((ms - 1000f) / 900f).coerceIn(0f, 1f)
    if (sweep > 0f && sweep < 1f) {
        val band = size.width * 0.55f
        val x0 = -band + (size.width + band * 1.6f) * ease(sweep)
        drawRect(
            brush = Brush.linearGradient(
                colorStops = arrayOf(0f to Color.Transparent, 0.5f to Color(0xFFB9A1DF), 1f to Color.Transparent),
                start = Offset(x0, 0f),
                end = Offset(x0 + band, band * 0.45f),
            ),
            blendMode = BlendMode.SrcAtop,
        )
    }

    // Eyes: blink (squash vertically) and the slit pupil glancing left then right.
    val blink = maxOf(pulse(ms, 1100f, 70f, 110f), pulse(ms, 1950f, 70f, 110f))
    val sy = 1f - 0.92f * blink
    val look = when {
        ms < 1250f -> 0f
        ms < 1450f -> -ease((ms - 1250f) / 200f)
        ms < 1650f -> -1f + 2f * ease((ms - 1450f) / 200f)
        ms < 1850f -> 1f - ease((ms - 1650f) / 200f)
        else -> 0f
    }
    for ((eye, slit) in listOf(l.eyeL to l.slitL, l.eyeR to l.slitR)) {
        val cx = (eye.x + eye.image.width / 2f) * u
        val cy = (eye.y + eye.image.height / 2f) * u
        scale(1f, sy, pivot = Offset(cx, cy)) {
            put(eye)
            put(slit, dx = look * 8f)
        }
    }
}


/** The animated Kiro mark on a loop (ears flick, eyes blink and glance, lavender shimmer) for use on other screens. */
@Composable
fun KiroLogo(size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val layers by produceState<LogoLayers?>(null) {
        value = withContext(Dispatchers.Default) { runCatching { loadLayers(context) }.getOrNull() }
    }
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "kiroLoop")
    val ms by t.animateFloat(
        initialValue = 400f,
        targetValue = 3400f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "kiroMs",
    )
    Canvas(modifier.size(size).graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        layers?.let { drawLogo(it, ms) }
    }
}
