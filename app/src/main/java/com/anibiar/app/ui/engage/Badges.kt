package com.anibiar.app.ui.engage

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anibiar.app.social.Highlight
import com.anibiar.app.ui.home.PosterImage
import com.anibiar.app.ui.profile.Avatar
import com.anibiar.app.ui.profile.Looks
import com.anibiar.app.ui.theme.Ani
import com.anibiar.app.ui.theme.AniIcons
import com.anibiar.app.ui.theme.EngageIcons
import com.anibiar.app.ui.theme.inter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

class HlRequest(val highlight: Highlight, val ownerName: String, val ownerAvatar: String?, val ownerDeco: String?, val onOpen: (Int) -> Unit)
class BadgesRequest(val ids: List<String>, val isMe: Boolean, val name: String)

/** Full-screen things opened from inside the profile but drawn above the whole app (see MainActivity). */
object EngageOverlay {
    val highlight = MutableStateFlow<HlRequest?>(null)
    val badges = MutableStateFlow<BadgesRequest?>(null)
}

// ------------------------------------------------------------------------------------------ badge art

enum class BadgeShape { Hex, Shield, Gem, Circle, Burst }

class BadgeDef(val shape: BadgeShape, val c1: Color, val c2: Color, val glyph: ImageVector, val name: String, val how: String)

/** Badge ids come from the Rust core (engage.rs). */
val BADGES: Map<String, BadgeDef> = linkedMapOf(
    "first" to BadgeDef(BadgeShape.Circle, Color(0xFF5AC8FA), Color(0xFF0A60FF), AniIcons.Play, "First episode", "Watch your first episode."),
    "streak3" to BadgeDef(BadgeShape.Hex, Color(0xFFFFB347), Color(0xFFFF5E3A), EngageIcons.Flame, "On a roll", "Watch 3 days in a row."),
    "streak7" to BadgeDef(BadgeShape.Shield, Color(0xFFFF8A3D), Color(0xFFD7263D), EngageIcons.Flame, "Week streak", "Watch 7 days in a row."),
    "streak30" to BadgeDef(BadgeShape.Burst, Color(0xFFFFD166), Color(0xFFE5174F), EngageIcons.Flame, "Unstoppable", "Watch 30 days in a row."),
    "shows10" to BadgeDef(BadgeShape.Hex, Color(0xFF5EE6A8), Color(0xFF0F8F7A), EngageIcons.Book, "Getting hooked", "Watch 10 different anime."),
    "shows25" to BadgeDef(BadgeShape.Shield, Color(0xFF4FD1E8), Color(0xFF2456D6), EngageIcons.Book, "Collector", "Watch 25 different anime."),
    "shows100" to BadgeDef(BadgeShape.Gem, Color(0xFFC77DFF), Color(0xFF5A189A), EngageIcons.Mountain, "Legend", "Watch 100 different anime."),
    "hours24" to BadgeDef(BadgeShape.Circle, Color(0xFF7AE7C7), Color(0xFF3A5BD9), AniIcons.Schedule, "A full day", "Watch 24 hours in total."),
    "hours100" to BadgeDef(BadgeShape.Shield, Color(0xFFFFE066), Color(0xFFE08A00), EngageIcons.Trophy, "Centurion", "Watch 100 hours in total."),
    "hours500" to BadgeDef(BadgeShape.Burst, Color(0xFFFFE9A0), Color(0xFFFF9500), EngageIcons.Crown, "Royalty", "Watch 500 hours in total."),
    "binge" to BadgeDef(BadgeShape.Hex, Color(0xFFFFEE58), Color(0xFFF57C00), EngageIcons.Bolt, "Binge day", "Watch 4 hours in one day."),
    "marathon" to BadgeDef(BadgeShape.Gem, Color(0xFFFF6B6B), Color(0xFF7B2FF7), EngageIcons.Medal, "Marathoner", "Watch 8 hours in one day."),
    "lv10" to BadgeDef(BadgeShape.Circle, Color(0xFFFFE066), Color(0xFFD98100), AniIcons.Star, "Level 10", "Reach level 10."),
    "lv25" to BadgeDef(BadgeShape.Gem, Color(0xFF6FE7FF), Color(0xFF8E3DFF), EngageIcons.Diamond, "Level 25", "Reach level 25."),
)

private fun shapePath(shape: BadgeShape, w: Float, inset: Float): Path {
    val k = w - 2 * inset
    fun x(u: Float) = inset + u * k
    fun y(v: Float) = inset + v * k
    return Path().apply {
        when (shape) {
            BadgeShape.Hex -> { moveTo(x(.5f), y(0f)); lineTo(x(.94f), y(.25f)); lineTo(x(.94f), y(.75f)); lineTo(x(.5f), y(1f)); lineTo(x(.06f), y(.75f)); lineTo(x(.06f), y(.25f)); close() }
            BadgeShape.Shield -> {
                moveTo(x(.5f), y(0f)); lineTo(x(.92f), y(.14f)); lineTo(x(.92f), y(.5f))
                cubicTo(x(.92f), y(.78f), x(.7f), y(.92f), x(.5f), y(1f))
                cubicTo(x(.3f), y(.92f), x(.08f), y(.78f), x(.08f), y(.5f)); lineTo(x(.08f), y(.14f)); close()
            }
            BadgeShape.Gem -> { moveTo(x(.28f), y(.04f)); lineTo(x(.72f), y(.04f)); lineTo(x(.98f), y(.38f)); lineTo(x(.5f), y(.98f)); lineTo(x(.02f), y(.38f)); close() }
            BadgeShape.Circle -> addOval(androidx.compose.ui.geometry.Rect(inset, inset, inset + k, inset + k))
            BadgeShape.Burst -> {
                val c = w / 2f
                for (n in 0 until 16) {
                    val r = if (n % 2 == 0) k / 2f else k / 2f * 0.80f
                    val a = Math.PI * 2 * n / 16 - Math.PI / 2
                    val px = c + (r * cos(a)).toFloat(); val py = c + (r * sin(a)).toFloat()
                    if (n == 0) moveTo(px, py) else lineTo(px, py)
                }
                close()
            }
        }
    }
}

private val LOCKED_A = Color(0xFF3A3A40)
private val LOCKED_B = Color(0xFF26262B)

/** One badge drawn as vector art: gradient body, lighter rim, soft gloss and a white glyph. */
@Composable
fun BadgeArt(id: String, size: Dp, modifier: Modifier = Modifier, locked: Boolean = false, special: Boolean = false) {
    val d = BADGES[id] ?: return
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "badge")
    val sweep by t.animateFloat(-0.4f, 1.4f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(2200, easing = androidx.compose.animation.core.LinearEasing)), label = "sweep")
    val glow by t.animateFloat(0.25f, 0.75f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1400), androidx.compose.animation.core.RepeatMode.Reverse), label = "glow")
    val ctx = LocalContext.current
    val res = remember(id) { ctx.resources.getIdentifier("badge_$id", "drawable", ctx.packageName) }
    if (res == 0) { BadgeArtVector(id, size, modifier, locked, special); return }
    val gray = remember { androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) }) }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        if (special) Canvas(Modifier.fillMaxSize().scale(1.25f)) {
            drawCircle(Brush.radialGradient(listOf(d.c1.copy(alpha = glow * 0.8f), Color.Transparent), center, this.size.minDimension * 0.5f), this.size.minDimension * 0.5f, center)
        }
        androidx.compose.foundation.Image(
            painterResource(res), null,
            modifier = Modifier.fillMaxSize()
                .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen; alpha = if (locked) 0.38f else 1f }
                .drawWithContent {
                    drawContent()
                    if (special) {
                        val w = this.size.minDimension; val x = sweep * w
                        drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.65f), Color.Transparent), Offset(x - w * 0.25f, 0f), Offset(x + w * 0.05f, w * 0.5f)), blendMode = androidx.compose.ui.graphics.BlendMode.SrcAtop)
                    }
                },
            colorFilter = if (locked) gray else null)
    }
}

@Composable
private fun BadgeArtVector(id: String, size: Dp, modifier: Modifier, locked: Boolean, special: Boolean) {
    val d = BADGES[id] ?: return
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "badgeV")
    val sweep by t.animateFloat(-0.4f, 1.4f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(2200, easing = androidx.compose.animation.core.LinearEasing)), label = "sweepV")
    val glow by t.animateFloat(0.25f, 0.75f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1400), androidx.compose.animation.core.RepeatMode.Reverse), label = "glowV")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.minDimension
            val a = if (locked) LOCKED_A else d.c1
            val b = if (locked) LOCKED_B else d.c2
            val outer = shapePath(d.shape, w, w * 0.02f)
            val inner = shapePath(d.shape, w, w * 0.09f)
            if (special) drawCircle(Brush.radialGradient(listOf(d.c1.copy(alpha = glow), Color.Transparent), center, w * 0.62f), w * 0.62f, center)
            drawPath(outer, Brush.verticalGradient(listOf(Color.White.copy(alpha = if (locked) 0.18f else 0.75f), b), 0f, w))
            drawPath(inner, Brush.linearGradient(listOf(a, b), Offset(w * 0.15f, 0f), Offset(w * 0.85f, w)))
            clipPath(inner) {
                drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (locked) 0.08f else 0.38f), Color.Transparent), 0f, w * 0.55f))
            }
            if (special) clipPath(inner) {
                val x = sweep * w
                drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.7f), Color.Transparent), Offset(x - w * 0.25f, 0f), Offset(x + w * 0.05f, w * 0.5f)))
            }
            drawPath(inner, Color.Black.copy(alpha = 0.18f), style = Stroke(w * 0.012f))
        }
        Icon(d.glyph, null, tint = Color.White.copy(alpha = if (locked) 0.32f else 1f), modifier = Modifier.size(size * 0.44f))
    }
}


/** Dark pill with the person's badges, shown next to the username like Discord. */
@Composable
fun BadgePill(ids: List<String>, isMe: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shown = ids.filter { it in BADGES }
    val all = shown.size == BADGES.size
    if (shown.isEmpty() && !isMe) return
    Row(modifier.clip(RoundedCornerShape(10.dp)).background(Color(0x99101014)).border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        if (shown.isEmpty()) {
            Icon(EngageIcons.Lock, null, tint = Ani.Soft, modifier = Modifier.size(15.dp))
            Text("Badges", style = inter(12f, FontWeight.SemiBold), color = Ani.Soft)
        } else shown.take(4).forEach { BadgeArt(it, 22.dp, special = all) }
        if (shown.size > 4) Text("+${shown.size - 4}", style = inter(11.5f, FontWeight.Bold), color = Ani.Soft)
    }
}

@Composable
private fun BadgeTile(id: String, locked: Boolean, special: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.size(82.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF131316)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        BadgeArt(id, 56.dp, locked = locked, special = special)
    }
}

/** Badges page: your level and XP on top, then earned badges and the ones left to unlock. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BadgesScreen(req: BadgesRequest, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val (stats, eng) = if (req.isMe) rememberEngage() else (null to null)
    val isDev = req.isMe && (LocalContext.current.applicationContext as com.anibiar.app.AniBiarApp).social.authState.collectAsState().value?.email.equals(com.anibiar.app.ui.admin.ADMIN_EMAIL, ignoreCase = true)
    val earned = if (isDev) BADGES.keys.toList() else if (req.isMe && eng != null) eng.badges else req.ids
    val complete = BADGES.keys.all { it in earned }
    var info by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTapGestures { } }) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
                Icon(AniIcons.Close, "Close", tint = Color.White, modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp).clip(CircleShape).clickable(onClick = onClose).padding(10.dp).size(24.dp))
                Text(if (req.isMe) "Your badges" else "${req.name}'s badges", style = inter(17f, FontWeight.Bold), color = Color.White)
            }
            if (eng != null && stats != null) {
                val next = Looks.nextUnlock(eng.level)
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF131316)).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("Level ${eng.level}", style = inter(20f, FontWeight.ExtraBold), color = Color.White, modifier = Modifier.weight(1f))
                        Text("${eng.xp - eng.floor} / ${eng.next - eng.floor} XP", style = inter(12.5f, FontWeight.SemiBold), color = Ani.Muted)
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(Ani.Chip)) {
                        Box(Modifier.fillMaxWidth(eng.progress.coerceAtLeast(0.02f)).height(8.dp).clip(RoundedCornerShape(50)).background(Ani.Lavender))
                    }
                    Text("XP comes only from watching. Every minute counts.", style = inter(11.5f), color = Ani.Muted, modifier = Modifier.padding(top = 8.dp))
                    if (next != null) Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(EngageIcons.Lock, null, tint = Ani.Soft, modifier = Modifier.size(13.dp)); Spacer(Modifier.width(5.dp))
                        Text("${next.first} unlocks at Lv ${next.second}", style = inter(12f, FontWeight.Medium), color = Ani.Soft)
                    }
                    Row(Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ani.ButtonDark).clickable { scope.launch { RecapCard.share(ctx, "My week", stats, eng) } }.padding(vertical = 11.dp),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(AniIcons.Upload, null, tint = Color.White, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp))
                        Text("Share weekly recap", style = inter(13.5f, FontWeight.SemiBold), color = Color.White)
                    }
                }
            }
            if (complete) CompleteBanner(isDev)
            Text(if (req.isMe) "Your badges" else "Badges", style = inter(18f, FontWeight.Bold), color = Color.White, modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 12.dp))
            val mine = BADGES.keys.filter { it in earned }
            if (mine.isEmpty()) Text("No badges yet.", style = inter(14f), color = Ani.Muted, modifier = Modifier.padding(horizontal = 20.dp))
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                mine.forEach { BadgeTile(it, false, special = complete) { info = it } }
            }
            if (req.isMe) {
                Text("Available to unlock", style = inter(18f, FontWeight.Bold), color = Color.White, modifier = Modifier.padding(start = 20.dp, top = 28.dp, bottom = 12.dp))
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BADGES.keys.filter { it !in earned }.forEach { BadgeTile(it, true) { info = it } }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
        info?.let { id ->
            val d = BADGES[id]
            if (d != null) androidx.compose.ui.window.Dialog(onDismissRequest = { info = null }) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color(0xFF17171B)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    BadgeArt(id, 96.dp, locked = id !in earned, special = complete)
                    Text(d.name, style = inter(19f, FontWeight.Bold), color = Color.White, modifier = Modifier.padding(top = 14.dp))
                    Text(d.how, style = inter(13.5f), color = Ani.Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                    Text(if (id in earned) "Earned" else "Locked", style = inter(12f, FontWeight.Bold), color = if (id in earned) Color(0xFF30D158) else Ani.Soft, modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
    }
}

/** Shown once every badge is earned (or for the developer): a slowly shifting gradient banner. */
@Composable
private fun CompleteBanner(dev: Boolean) {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "complete")
    val shift by t.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(3600, easing = androidx.compose.animation.core.LinearEasing), androidx.compose.animation.core.RepeatMode.Reverse), label = "s")
    val shape = RoundedCornerShape(16.dp)
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(shape)
        .background(Brush.linearGradient(listOf(Color(0xFFFFD166), Color(0xFFFF5E7E), Color(0xFF8E3DFF), Color(0xFF3AA8FF)), Offset(-300f + 900f * shift, 0f), Offset(500f + 900f * shift, 300f)))
        .padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(EngageIcons.Crown, null, tint = Color.White, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(if (dev) "Developer: every badge" else "Collection complete", style = inter(16f, FontWeight.ExtraBold), color = Color.White)
            Text(if (dev) "All badges are unlocked on this account." else "You unlocked every badge.", style = inter(12.5f), color = Color(0xE6FFFFFF))
        }
    }
}

// ------------------------------------------------------------------------------------------ highlight viewer


/** Full-screen story-style viewer. Tap the sides to move, hold to pause. */
@Composable
fun HighlightViewer(req: HlRequest, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val h = req.highlight
    var i by remember { mutableStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var p by remember { mutableStateOf(0f) }
    val last = h.items.lastIndex
    LaunchedEffect(i) {
        p = 0f
        var t0 = System.nanoTime()
        while (p < 1f) {
            androidx.compose.runtime.withFrameNanos { now ->
                val dt = (now - t0) / 1_000_000f; t0 = now
                if (!paused) p += dt / 5000f
            }
        }
        if (i < last) i++ else onClose()
    }
    val f = h.items[i.coerceIn(0, last)]
    val cover = f.cover
    Box(Modifier.fillMaxSize().background(Color(0xFF08080A)).pointerInput(i) {
        detectTapGestures(
            onPress = { paused = true; tryAwaitRelease(); paused = false },
            onTap = { o -> if (o.x < size.width / 3f) { if (i > 0) i-- else p = 0f } else if (i < last) i++ else onClose() },
        )
    }) {
        PosterImage(cover, null, Modifier.fillMaxSize().blur(36.dp).alpha(0.6f), maxSide = 300)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xCC000000), Color(0x33000000), Color(0x99000000), Color(0xF0000000)))))
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                h.items.forEachIndexed { n, _ ->
                    Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(50)).background(Color(0x40FFFFFF))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(if (n < i) 1f else if (n == i) p.coerceIn(0f, 1f) else 0f).background(Color.White))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(req.ownerAvatar, req.ownerName, 40.dp, deco = req.ownerDeco)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(req.ownerName.ifBlank { "Highlight" }, style = inter(14.5f, FontWeight.Bold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${h.title}  ·  ${i + 1} of ${h.items.size}", style = inter(12f), color = Color(0xCCFFFFFF), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(AniIcons.Close, "Close", tint = Color.White, modifier = Modifier.clip(CircleShape).clickable(onClick = onClose).padding(8.dp).size(24.dp))
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth(0.76f).aspectRatio(2f / 3f).shadow(28.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(Ani.Item)) {
                PosterImage(cover, f.title, Modifier.fillMaxSize(), maxSide = 1200)
            }
            Spacer(Modifier.height(18.dp))
            Text(f.title, style = inter(20f, FontWeight.ExtraBold), color = Color.White, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(14.dp))
            Text("View details", style = inter(14.5f, FontWeight.Bold), color = Color.Black,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White).clickable { onClose(); req.onOpen(f.animeId) }.padding(horizontal = 30.dp, vertical = 12.dp))
            Spacer(Modifier.weight(1f))
        }
    }
}
