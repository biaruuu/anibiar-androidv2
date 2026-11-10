package com.anibiar.app.ui.theme

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch

/** The look the person picked: accent colour and pure-black (AMOLED) backgrounds. Stored on the device. */
object Appearance {
    val accents = listOf(
        "Lavender" to Color(0xFFB9A1DF), "Lime" to Color(0xFFAAFF01), "Rose" to Color(0xFFFF8FB1),
        "Sky" to Color(0xFF7CC4FF), "Amber" to Color(0xFFFFC94D), "Mint" to Color(0xFF6FE39A),
    )
    var accent by mutableStateOf(accents[0].second); private set
    var amoled by mutableStateOf(false); private set
    /** Multiplies every text size: 0.9 small, 1.0 default, 1.15 large. */
    var textScale by mutableStateOf(1f); private set
    /** How poster rows look: 0 poster only, 1 poster and title, 2 large cards with a score badge. */
    var cardStyle by mutableStateOf(1); private set
    /** Search results as a poster grid (false) or a detailed list (true). */
    var listView by mutableStateOf(false); private set
    val cardStyles = listOf("Poster only", "With titles", "Large")
    val textSizes = listOf("Small" to 0.9f, "Default" to 1f, "Large" to 1.15f)
    private const val PREFS = "appearance"

    fun load(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        accents.firstOrNull { it.first == p.getString("accent", null) }?.let { accent = it.second }
        amoled = p.getBoolean("amoled", false)
        textScale = p.getFloat("text", 1f)
        cardStyle = p.getInt("cards", 1).coerceIn(0, 2)
        listView = p.getBoolean("list", false)
    }
    fun setListView(context: Context, v: Boolean) {
        listView = v
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("list", v).apply()
    }
    fun setCardStyle(context: Context, v: Int) {
        cardStyle = v
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("cards", v).apply()
    }
    fun setTextScale(context: Context, v: Float) {
        textScale = v
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putFloat("text", v).apply()
    }
    fun onboarded(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("onboarded", false)
    fun setOnboarded(context: Context) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("onboarded", true).apply() }
    fun setAccent(context: Context, name: String) {
        val c = accents.firstOrNull { it.first == name } ?: return
        accent = c.second
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("accent", name).apply()
    }
    fun setAmoled(context: Context, on: Boolean) {
        amoled = on
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("amoled", on).apply()
    }
}

/** A light tap, used on selections and toggles. Follows the system "touch feedback" setting. */
@Composable
fun rememberTick(): () -> Unit {
    val h = LocalHapticFeedback.current
    return remember(h) { { h.performHapticFeedback(HapticFeedbackType.TextHandleMove) } }
}

/** A soft light sweep over a placeholder shape while content loads. */
fun Modifier.shimmer(): Modifier = composed {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart), label = "x")
    drawWithContent {
        drawContent()
        val w = size.width.coerceAtLeast(1f)
        val start = x * w
        drawRect(
            Brush.linearGradient(listOf(Color.Transparent, Color(0x1FFFFFFF), Color.Transparent), Offset(start, 0f), Offset(start + w * 0.6f, size.height * 0.3f)),
        )
    }
}

@Composable
fun SkeletonBox(modifier: Modifier = Modifier, radius: Int = 12) {
    Box(modifier.clip(RoundedCornerShape(radius.dp)).background(Color(0xFF1B1B1F)).shimmer())
}

/** Home while it loads: banner, then rows of poster cards. */
@Composable
fun HomeSkeleton() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState(), enabled = false)) {
        SkeletonBox(Modifier.fillMaxWidth().aspectRatio(0.9f), radius = 0)
        Spacer(Modifier.height(18.dp))
        repeat(2) {
            SkeletonBox(Modifier.padding(start = 16.dp).width(140.dp).height(18.dp), 8)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(3) { SkeletonBox(Modifier.weight(1f).aspectRatio(0.68f)) }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

/** A grid of poster placeholders; the column count follows the screen width. */
@Composable
fun GridSkeleton(modifier: Modifier = Modifier, rows: Int = 3) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cols = when { maxWidth >= 900.dp -> 6; maxWidth >= 600.dp -> 4; else -> 3 }
        Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(rows) { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { repeat(cols) { SkeletonBox(Modifier.weight(1f).aspectRatio(0.68f)) } } }
        }
    }
}

/** The details page while it loads: a tall header then text lines. */
@Composable
fun DetailsSkeleton() {
    Column(Modifier.fillMaxSize()) {
        SkeletonBox(Modifier.fillMaxWidth().aspectRatio(1.1f), radius = 0)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SkeletonBox(Modifier.fillMaxWidth(0.7f).height(26.dp), 8)
            SkeletonBox(Modifier.fillMaxWidth(0.4f).height(14.dp), 8)
            Spacer(Modifier.height(6.dp))
            repeat(4) { SkeletonBox(Modifier.fillMaxWidth().height(12.dp), 6) }
            Spacer(Modifier.height(8.dp))
            SkeletonBox(Modifier.fillMaxWidth().height(48.dp), 24)
        }
    }
}

/** A friendly empty or error screen with one clear action. */
@Composable
fun EmptyState(icon: ImageVector, title: String, hint: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Column(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(Appearance.accent.copy(alpha = 0.12f)).border(1.dp, Appearance.accent.copy(alpha = 0.35f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Appearance.accent, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = inter(17f, FontWeight.SemiBold), color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(hint, style = inter(14f, lineHeight = 20f), color = Ani.Muted, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(18.dp))
            Box(Modifier.clip(RoundedCornerShape(50)).background(Color.White).clickable(onClick = onAction).padding(horizontal = 22.dp, vertical = 11.dp)) {
                Text(action, style = inter(14f, FontWeight.SemiBold), color = Color.Black)
            }
        }
    }
}


/** A circular score: the arc fills to [score] out of 100 and takes the rating colour. */
@Composable
fun ScoreRing(score: Int, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 54.dp) {
    val color = when { score >= 75 -> Color(0xFF6FE39A); score >= 60 -> Color(0xFFFFC94D); else -> Color(0xFFFF8A80) }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val w = this.size.minDimension * 0.11f
            drawArc(Color(0x26FFFFFF), 0f, 360f, false, topLeft = Offset(w / 2, w / 2), size = androidx.compose.ui.geometry.Size(this.size.width - w, this.size.height - w), style = androidx.compose.ui.graphics.drawscope.Stroke(w))
            drawArc(color, -90f, 360f * score.coerceIn(0, 100) / 100f, false, topLeft = Offset(w / 2, w / 2), size = androidx.compose.ui.geometry.Size(this.size.width - w, this.size.height - w), style = androidx.compose.ui.graphics.drawscope.Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        Text("$score", style = inter(15f, FontWeight.Bold), color = Color.White)
    }
}


/** A springy pop plus a small burst of sparks when [on] turns true (favorite, like). Nothing happens on the first draw. */
fun Modifier.favPop(on: Boolean): Modifier = composed {
    val anim = remember { androidx.compose.animation.core.Animatable(1f) }
    val spark = remember { androidx.compose.animation.core.Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    androidx.compose.runtime.LaunchedEffect(on) {
        if (first) { first = false; return@LaunchedEffect }
        if (on) {
            spark.snapTo(0f)
            kotlinx.coroutines.coroutineScope {
                launch { spark.animateTo(1f, tween(520)) }
                anim.snapTo(0.6f)
                anim.animateTo(1f, androidx.compose.animation.core.spring(0.35f, 380f))
            }
        } else { anim.snapTo(0.8f); anim.animateTo(1f, androidx.compose.animation.core.spring(0.6f, 500f)) }
    }
    this
        .graphicsLayer { scaleX = anim.value; scaleY = anim.value }
        .drawWithContent {
            drawContent()
            val p = spark.value
            if (p < 1f) {
                val c = Offset(size.width / 2, size.height / 2)
                val r = size.minDimension * (0.55f + 0.9f * p)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0).toFloat()
                    drawCircle(Color(0xFFFFC94D).copy(alpha = (1f - p)), size.minDimension * 0.07f * (1f - p * 0.5f), Offset(c.x + kotlin.math.cos(a) * r, c.y + kotlin.math.sin(a) * r))
                }
            }
        }
}

/** Fades and lifts an item in once, delayed by its [index], so a row of cards eases in one after another. */
fun Modifier.staggerIn(index: Int, enabled: Boolean = true): Modifier = composed {
    if (!enabled || index > 7) return@composed this
    val a = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) { kotlinx.coroutines.delay(index * 45L); a.animateTo(1f, tween(380)) }
    this.graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 24.dp.toPx() }
}
