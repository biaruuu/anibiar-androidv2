package com.anibiar.app.ui.engage

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import com.anibiar.app.ui.theme.AniIcons
import com.anibiar.app.ui.theme.EngageIcons
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.anibiar.app.AniBiarApp
import com.anibiar.app.data.Engage
import com.anibiar.app.data.EngageCalc
import com.anibiar.app.data.Media
import com.anibiar.app.data.displayTitle
import com.anibiar.app.data.posterUrl
import com.anibiar.app.data.StatsSummary
import com.anibiar.app.nativecore.Native
import com.anibiar.app.ui.home.PosterImage
import com.anibiar.app.ui.theme.Ani
import com.anibiar.app.ui.theme.inter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Badge id to icon, colour and name. The ids come from the Rust core (engage.rs). */
class BadgeDef(val icon: ImageVector, val tint: Color, val name: String)
val BADGES: Map<String, BadgeDef> = linkedMapOf(
    "first" to BadgeDef(AniIcons.Play, Color(0xFF64D2FF), "First episode"),
    "streak3" to BadgeDef(EngageIcons.Flame, Color(0xFFFF9F0A), "3-day streak"),
    "streak7" to BadgeDef(EngageIcons.Flame, Color(0xFFFF6B35), "7-day streak"),
    "streak30" to BadgeDef(EngageIcons.Flame, Color(0xFFFF375F), "30-day streak"),
    "shows10" to BadgeDef(EngageIcons.Book, Color(0xFF30D158), "10 anime"),
    "shows25" to BadgeDef(EngageIcons.Book, Color(0xFF32ADE6), "25 anime"),
    "shows100" to BadgeDef(EngageIcons.Mountain, Color(0xFFBF5AF2), "100 anime"),
    "hours24" to BadgeDef(AniIcons.Schedule, Color(0xFF64D2FF), "24 hours"),
    "hours100" to BadgeDef(EngageIcons.Trophy, Color(0xFFFFD60A), "100 hours"),
    "hours500" to BadgeDef(EngageIcons.Crown, Color(0xFFFFD60A), "500 hours"),
    "binge" to BadgeDef(EngageIcons.Bolt, Color(0xFFFF9F0A), "Binge day"),
    "marathon" to BadgeDef(EngageIcons.Medal, Color(0xFFFF453A), "Marathon day"),
    "lv10" to BadgeDef(AniIcons.Star, Color(0xFFFFD60A), "Level 10"),
    "lv25" to BadgeDef(EngageIcons.Diamond, Color(0xFF64D2FF), "Level 25"),
)

private fun fmtMin(sec: Long) = "${sec / 60}"

/** Stats and level, computed off the main thread (the maths runs in Rust). */
@Composable
fun rememberEngage(refresh: Int = 0): Pair<StatsSummary?, Engage?> {
    val app = LocalContext.current.applicationContext as AniBiarApp
    val v by produceState<Pair<StatsSummary?, Engage?>>(null to null, refresh) {
        value = withContext(Dispatchers.Default) {
            app.stats.flush()
            val s = app.stats.summary()
            s to s?.let { EngageCalc.compute(it) }
        }
    }
    return v
}

private fun goalMin(c: Context) = c.getSharedPreferences("engage", Context.MODE_PRIVATE).getInt("goal_min", 20)
private fun setGoal(c: Context, m: Int) = c.getSharedPreferences("engage", Context.MODE_PRIVATE).edit().putInt("goal_min", m).apply()


/** A circular progress ring with something centred in it. */
@Composable
private fun Ring(progress: Float, size: androidx.compose.ui.unit.Dp, color: Color, stroke: androidx.compose.ui.unit.Dp = 5.dp, content: @Composable () -> Unit) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.size(size)) {
            val w = stroke.toPx()
            val inset = w / 2
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - w, this.size.height - w)
            val tl = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(Color(0x22FFFFFF), 0f, 360f, false, tl, arcSize, style = androidx.compose.ui.graphics.drawscope.Stroke(w))
            if (progress > 0f) drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, tl, arcSize, style = androidx.compose.ui.graphics.drawscope.Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        content()
    }
}


/** Draws a thin progress ring just outside whatever it is applied to (profile avatars). */
fun Modifier.levelRing(progress: Float, color: Color, width: androidx.compose.ui.unit.Dp = 3.dp, gap: androidx.compose.ui.unit.Dp = 4.dp): Modifier = this.drawBehind {
    val w = width.toPx(); val g = gap.toPx()
    val r = size.minDimension / 2f + g + w / 2f
    val tl = androidx.compose.ui.geometry.Offset(size.width / 2f - r, size.height / 2f - r)
    val sz = androidx.compose.ui.geometry.Size(r * 2, r * 2)
    drawArc(Color(0x2EFFFFFF), 0f, 360f, false, tl, sz, style = androidx.compose.ui.graphics.drawscope.Stroke(w))
    if (progress > 0.01f) drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, tl, sz, style = androidx.compose.ui.graphics.drawscope.Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round))
}

/** Small round level number that sits on the corner of an avatar. */
@Composable
fun LevelBadge(level: Int, modifier: Modifier = Modifier) {
    Box(modifier.clip(CircleShape).background(Ani.Onyx).padding(2.dp).clip(CircleShape).background(Ani.Lavender).padding(horizontal = 7.dp, vertical = 3.dp), contentAlignment = Alignment.Center) {
        Text("$level", style = inter(11.5f, FontWeight.ExtraBold), color = Color.Black)
    }
}

/** Profile (own): the XP bar, what unlocks next, and the badge wall (earned ones lit, the rest locked). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LevelCard(modifier: Modifier = Modifier, onXp: (Long) -> Unit = {}) {
    val (s, e) = rememberEngage()
    LaunchedEffect(e?.xp) { e?.let { onXp(it.xp) } }
    if (s == null || e == null) return
    val shape = RoundedCornerShape(16.dp)
    val next = com.anibiar.app.ui.profile.Looks.nextUnlock(e.level)
    Column(modifier.fillMaxWidth().clip(shape).background(Ani.Panel).border(1.dp, Ani.Border, shape).padding(16.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Level ${e.level}", style = inter(18f, FontWeight.Bold), color = Color.White, modifier = Modifier.weight(1f))
            Text("${e.xp - e.floor} / ${e.next - e.floor} XP", style = inter(12.5f, FontWeight.SemiBold), color = Ani.Muted)
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(Ani.Chip)) {
            Box(Modifier.fillMaxWidth(e.progress.coerceAtLeast(0.02f)).height(8.dp).clip(RoundedCornerShape(50)).background(Ani.Lavender))
        }
        Text("XP comes only from watching. Every minute counts.", style = inter(11.5f), color = Ani.Muted, modifier = Modifier.padding(top = 8.dp))
        if (next != null) Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(EngageIcons.Lock, null, tint = Ani.Soft, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(5.dp))
            Text("${next.first} unlocks at Lv ${next.second}", style = inter(12f, FontWeight.Medium), color = Ani.Soft, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ani.ButtonDark).clickable { scope.launch { RecapCard.share(ctx, "My week", s, e) } }.padding(vertical = 11.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(AniIcons.Upload, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Share weekly recap", style = inter(13.5f, FontWeight.SemiBold), color = Color.White)
        }
        Spacer(Modifier.height(16.dp))
        Text("Badges  ${e.badges.size}/${BADGES.size}", style = inter(13f, FontWeight.SemiBold), color = Ani.Soft)
        Spacer(Modifier.height(10.dp))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BADGES.forEach { (id, b) ->
                val got = id in e.badges
                Column(Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(if (got) b.tint.copy(alpha = 0.18f) else Ani.Chip), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.Icon(if (got) b.icon else EngageIcons.Lock, null, tint = if (got) b.tint else Ani.Muted.copy(alpha = 0.6f), modifier = Modifier.size(if (got) 24.dp else 18.dp))
                    }
                    Text(b.name, style = inter(10.5f, FontWeight.Medium), color = if (got) Color.White else Ani.Muted, maxLines = 2, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

/** Instagram-style highlights: round covers with a caption. Edited in Edit profile. */
@Composable
fun Highlights(items: List<com.anibiar.app.social.Fav>, modifier: Modifier = Modifier, onOpen: (Int) -> Unit) {
    if (items.isEmpty()) return
    androidx.compose.foundation.lazy.LazyRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)) {
        items(items.take(5), key = { it.animeId }) { f ->
            Column(Modifier.width(76.dp).clickable { onOpen(f.animeId) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(72.dp).border(2.dp, Brush.sweepGradient(listOf(Ani.Lavender, Color(0xFFFF6B9A), Ani.Lavender)), CircleShape).padding(4.dp).clip(CircleShape).background(Ani.Item)) {
                    PosterImage(f.cover, f.title, Modifier.fillMaxSize(), maxSide = 220)
                }
                Text(f.note?.takeIf { it.isNotBlank() } ?: f.title, style = inter(11.5f, FontWeight.Medium), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}
