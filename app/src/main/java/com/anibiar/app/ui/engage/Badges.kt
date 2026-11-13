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
class BadgesRequest(val isMe: Boolean, val name: String)

/** Full-screen things opened from inside the profile but drawn above the whole app (see MainActivity). */
object EngageOverlay {
    val highlight = MutableStateFlow<HlRequest?>(null)
    val badges = MutableStateFlow<BadgesRequest?>(null)
    val levelUp = MutableStateFlow<LevelUpRequest?>(null)
}

/** Level page: your level, XP progress, what unlocks next, and the weekly recap. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LevelScreen(req: BadgesRequest, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val (stats, eng) = if (req.isMe) rememberEngage() else (null to null)
    Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTapGestures { } }) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
                Icon(AniIcons.Close, "Close", tint = Color.White, modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp).clip(CircleShape).clickable(onClick = onClose).padding(10.dp).size(24.dp))
                Text("Level", style = inter(17f, FontWeight.Bold), color = Color.White)
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
            Spacer(Modifier.height(40.dp))
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
        Box(Modifier.fillMaxSize().background(Color(0xB3000000)))
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
