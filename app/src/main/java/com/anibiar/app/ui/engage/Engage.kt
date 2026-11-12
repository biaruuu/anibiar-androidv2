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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
    drawArc(Color(0x66FFFFFF), 0f, 360f, false, tl, sz, style = androidx.compose.ui.graphics.drawscope.Stroke(w))
    if (progress > 0.01f) drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, tl, sz, style = androidx.compose.ui.graphics.drawscope.Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round))
}

/** Small round level number that sits on the corner of an avatar. */
@Composable
fun LevelBadge(level: Int, modifier: Modifier = Modifier) {
    Box(modifier.clip(CircleShape).background(Ani.Onyx).padding(2.dp).clip(CircleShape).background(Ani.Lavender).padding(horizontal = 7.dp, vertical = 3.dp), contentAlignment = Alignment.Center) {
        Text("$level", style = inter(11.5f, FontWeight.ExtraBold), color = Color.Black)
    }
}

/** Collections of anime on a profile: round covers with a title. Tap one to play it full screen like a story. */
@Composable
fun Highlights(
    items: List<com.anibiar.app.social.Highlight>, isMe: Boolean, owner: com.anibiar.app.social.UserProfile?,
    modifier: Modifier = Modifier, onOpen: (Int) -> Unit,
) {
    if (items.isEmpty()) return
    var privacy by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Highlights", style = inter(15f, FontWeight.Bold), color = Color.White, modifier = Modifier.weight(1f))
            if (isMe) Row(Modifier.clip(RoundedCornerShape(50)).background(Ani.Chip).clickable { privacy = true }.padding(horizontal = 11.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(EngageIcons.Lock, null, tint = Ani.Soft, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(5.dp))
                Text("Who can view", style = inter(12f, FontWeight.SemiBold), color = Ani.Soft)
            }
        }
        Spacer(Modifier.height(8.dp))
        androidx.compose.foundation.lazy.LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)) {
            items(items, key = { it.id }) { h ->
                Column(Modifier.width(76.dp).clickable(enabled = h.items.isNotEmpty()) {
                    EngageOverlay.highlight.value = HlRequest(h, owner?.shownName.orEmpty(), owner?.avatarUrl, owner?.avatarDeco, onOpen)
                }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(72.dp).border(2.dp, Ani.Lavender, CircleShape).padding(4.dp).clip(CircleShape).background(Ani.Item)) {
                        PosterImage(h.items.firstOrNull()?.cover, h.title, Modifier.fillMaxSize(), maxSide = 360)
                    }
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (h.visibility != "everyone") { androidx.compose.material3.Icon(EngageIcons.Lock, null, tint = Ani.Muted, modifier = Modifier.size(11.dp)); Spacer(Modifier.width(3.dp)) }
                        Text(h.title, style = inter(11.5f, FontWeight.Medium), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
    if (privacy) HighlightPrivacyDialog(items) { privacy = false }
}

private val AUD = listOf("everyone" to "Everyone", "friends" to "Friends", "me" to "Only me")

/** Change who can view each highlight without leaving the profile. */
@Composable
private fun HighlightPrivacyDialog(items: List<com.anibiar.app.social.Highlight>, onClose: () -> Unit) {
    val app = LocalContext.current.applicationContext as AniBiarApp
    val scope = rememberCoroutineScope()
    val vis = remember { androidx.compose.runtime.mutableStateMapOf<String, String>().apply { items.forEach { put(it.id, it.visibility) } } }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        val shape = RoundedCornerShape(22.dp)
        Column(Modifier.fillMaxWidth().clip(shape).background(Ani.Panel).border(1.dp, Ani.Border, shape).padding(18.dp)) {
            Text("Who can view your highlights", style = inter(17f, FontWeight.Bold), color = Color.White)
            Text("Friends only and Only me are enforced by the server, not just the app.", style = inter(12f), color = Ani.Muted, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
            items.forEach { h ->
                Text(h.title, style = inter(14f, FontWeight.SemiBold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 6.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AUD.forEach { (id, label) ->
                        val on = vis[h.id] == id
                        Text(label, style = inter(12.5f, FontWeight.SemiBold), color = if (on) Color.Black else Color.White,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Ani.Lavender else Ani.ButtonDark).clickable {
                                vis[h.id] = id
                                scope.launch { runCatching { app.social.saveHighlight(h.id, h.title, id, h.items) } }
                            }.padding(horizontal = 14.dp, vertical = 7.dp))
                    }
                }
            }
            Text("Done", style = inter(14f, FontWeight.Bold), color = Color.Black, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Color.White).clickable(onClick = onClose).padding(vertical = 12.dp))
        }
    }
}
