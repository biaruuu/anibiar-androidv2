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

private val FLAME = Color(0xFFFF9F0A)

/** Home: streak flame inside a ring that fills as you reach today's goal. */
@Composable
fun StreakGoalCard(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var goal by remember { mutableStateOf(goalMin(ctx)) }
    val (s, e) = rememberEngage()
    val streak = s?.streak ?: 0
    val todayMin = ((s?.today ?: 0L) / 60).toInt()
    val done = todayMin >= goal
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(Ani.Panel).border(1.dp, Ani.Border, shape).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ring(todayMin.toFloat() / goal, 60.dp, if (done) Color(0xFF30D158) else FLAME) {
                androidx.compose.material3.Icon(if (done) AniIcons.Check else EngageIcons.Flame, null, tint = if (done) Color(0xFF30D158) else if (streak > 0) FLAME else Ani.Muted, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(if (streak > 0) "$streak day streak" else "Start your streak", style = inter(16.5f, FontWeight.Bold), color = Color.White)
                Text(if (done) "Daily goal reached" else "$todayMin of $goal min today", style = inter(12.5f), color = if (done) Color(0xFF30D158) else Ani.Muted)
            }
            if (e != null) Row(Modifier.clip(RoundedCornerShape(50)).background(Ani.Chip).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(AniIcons.Star, null, tint = Color(0xFFFFD60A), modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text("Lv ${e.level}", style = inter(12.5f, FontWeight.Bold), color = Color.White)
            }
        }
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Daily goal", style = inter(12f), color = Ani.Muted)
            Spacer(Modifier.width(10.dp))
            listOf(10, 20, 30, 60).forEach { m ->
                val on = goal == m
                Text("${m}m", style = inter(12f, FontWeight.SemiBold), color = if (on) Color.Black else Ani.Soft,
                    modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(50)).background(if (on) Color.White else Ani.ButtonDark).clickable { goal = m; setGoal(ctx, m) }.padding(horizontal = 11.dp, vertical = 5.dp))
            }
        }
    }
}

/** Profile: XP ring, what unlocks next, stats and the full badge wall (earned ones lit, the rest locked). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LevelCard(modifier: Modifier = Modifier, onXp: (Long) -> Unit = {}) {
    val (s, e) = rememberEngage()
    LaunchedEffect(e?.xp) { e?.let { onXp(it.xp) } }
    if (s == null || e == null) return
    val shape = RoundedCornerShape(16.dp)
    val next = com.anibiar.app.ui.profile.Looks.nextUnlock(e.level)
    Column(modifier.fillMaxWidth().clip(shape).background(Ani.Panel).border(1.dp, Ani.Border, shape).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ring(e.progress, 72.dp, Ani.Lavender, 6.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("LV", style = inter(9.5f, FontWeight.Bold), color = Ani.Muted)
                    Text("${e.level}", style = inter(22f, FontWeight.ExtraBold), color = Color.White)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("${e.xp} XP", style = inter(18f, FontWeight.Bold), color = Color.White)
                Text("${e.next - e.xp} XP to level ${e.level + 1}", style = inter(12.5f), color = Ani.Muted)
                if (next != null) Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(EngageIcons.Lock, null, tint = Ani.Soft, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("${next.first} unlocks at Lv ${next.second}", style = inter(12f, FontWeight.Medium), color = Ani.Soft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf(
                Triple(AniIcons.Schedule, "${s.total / 3600}h ${s.total % 3600 / 60}m", "Watched"),
                Triple(EngageIcons.Flame, "${s.streak}d", "Streak"),
                Triple(EngageIcons.Trophy, "${s.bestStreak}d", "Best"),
                Triple(EngageIcons.Book, "${s.shows}", "Anime"),
            ).forEach { (ic, v, l) ->
                Column(Modifier.weight(1f)) {
                    androidx.compose.material3.Icon(ic, null, tint = Ani.Muted, modifier = Modifier.size(16.dp))
                    Text(v, style = inter(16f, FontWeight.Bold), color = Color.White, modifier = Modifier.padding(top = 4.dp))
                    Text(l, style = inter(11.5f), color = Ani.Muted)
                }
            }
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

private class Mood(val icon: ImageVector, val name: String, val genres: List<String>)
private val MOODS = listOf(
    Mood(EngageIcons.Bolt, "Hype", listOf("Action", "Adventure")), Mood(EngageIcons.Cup, "Chill", listOf("Slice of Life")),
    Mood(EngageIcons.Drop, "Emotional", listOf("Drama")), Mood(AniIcons.Sticker, "Funny", listOf("Comedy")),
    Mood(EngageIcons.Moon, "Dark", listOf("Psychological", "Horror", "Thriller")), Mood(EngageIcons.Heart, "Romantic", listOf("Romance")),
    Mood(EngageIcons.Eye, "Mind-bending", listOf("Mystery", "Sci-Fi", "Psychological")), Mood(EngageIcons.Sparkle, "Magical", listOf("Fantasy", "Supernatural")),
)

/** Home: "What should I watch?" opens the mood picker. */
@Composable
fun MoodCard(onOpen: (Int) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Row(modifier.fillMaxWidth().clip(shape).background(Ani.Panel).border(1.dp, Ani.Border, shape).clickable { open = true }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Icon(EngageIcons.Dice, null, tint = Ani.Lavender, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("What should I watch?", style = inter(16f, FontWeight.Bold), color = Color.White)
            Text("Pick a mood and get 3 picks", style = inter(12.5f), color = Ani.Muted)
        }
        Text("Pick", style = inter(13f, FontWeight.Bold), color = Color.Black, modifier = Modifier.clip(RoundedCornerShape(50)).background(Ani.Lavender).padding(horizontal = 16.dp, vertical = 7.dp))
    }
    if (open) MoodDialog(onOpen = { open = false; onOpen(it) }, onClose = { open = false })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoodDialog(onOpen: (Int) -> Unit, onClose: () -> Unit) {
    val app = LocalContext.current.applicationContext as AniBiarApp
    val scope = rememberCoroutineScope()
    var mood by remember { mutableStateOf<Mood?>(null) }
    var length by remember { mutableStateOf(0) } // 0 any, 1 short, 2 long
    var picks by remember { mutableStateOf<List<Media>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var empty by remember { mutableStateOf(false) }

    fun roll(m: Mood?) {
        if (busy) return
        busy = true; empty = false
        scope.launch {
            val found = runCatching {
                val seen = withContext(Dispatchers.Default) { app.history.load().map { it.mediaId }.toSet() }
                val taste = withContext(Dispatchers.Default) { app.stats.summary()?.genres.orEmpty().associate { it.first to it.second.toDouble() } }
                val genres = (m ?: MOODS.random()).genres
                val vars = buildJsonObject {
                    put("page", (1..3).random()); put("perPage", 30)
                    put("sort", JsonArray(listOf(JsonPrimitive(if ((0..1).random() == 0) "POPULARITY_DESC" else "SCORE_DESC"))))
                    put("genres", JsonArray(listOf(JsonPrimitive(genres.random()))))
                    put("adult", false)
                }
                val list = app.repository.search(vars).media.filter { it.id !in seen && (it.averageScore ?: 0) >= 60 }.filter {
                    val n = it.episodes ?: 0
                    when (length) { 1 -> n in 1..14; 2 -> n >= 24; else -> true }
                }
                withContext(Dispatchers.Default) {
                    val order = Native.reco(taste, list.map { (it.averageScore ?: 0) to it.genres.orEmpty() })
                    (if (order.size >= 3) order.take(10).map { list[it] } else list).shuffled().take(3)
                }
            }.getOrDefault(emptyList())
            picks = found; empty = found.isEmpty(); busy = false
        }
    }

    Dialog(onDismissRequest = onClose) {
        val shape = RoundedCornerShape(22.dp)
        Column(Modifier.fillMaxWidth().clip(shape).background(Ani.Panel).border(1.dp, Ani.Border, shape).padding(18.dp)) {
            Text("What are you in the mood for?", style = inter(18f, FontWeight.Bold), color = Color.White)
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MOODS.forEach { m ->
                    val on = mood == m
                    Row(Modifier.clip(RoundedCornerShape(50)).background(if (on) Ani.Lavender else Ani.ButtonDark).clickable { mood = m; roll(m) }.padding(horizontal = 13.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Icon(m.icon, null, tint = if (on) Color.Black else Color.White, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(m.name, style = inter(13.5f, FontWeight.SemiBold), color = if (on) Color.Black else Color.White)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Any length", "Short", "Long").forEachIndexed { i, label ->
                    val on = length == i
                    Text(label, style = inter(12.5f, FontWeight.SemiBold), color = if (on) Color.Black else Ani.Soft,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Color.White else Ani.Chip).clickable { length = i; if (mood != null) roll(mood) }.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            when {
                busy -> Text("Finding picks…", style = inter(14f), color = Ani.Muted, modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), textAlign = TextAlign.Center)
                empty -> Text("Nothing matched. Try another mood or length.", style = inter(14f), color = Ani.Muted, modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), textAlign = TextAlign.Center)
                picks.isNotEmpty() -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    picks.forEach { m ->
                        Column(Modifier.weight(1f).clickable { onOpen(m.id) }) {
                            PosterImage(m.posterUrl, m.displayTitle, Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).background(Ani.Item), maxSide = 360)
                            Spacer(Modifier.height(6.dp))
                            Text(m.displayTitle, style = inter(12.5f, FontWeight.Medium), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).clickable { mood = null; roll(null) }.padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(EngageIcons.Dice, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Surprise me", style = inter(14f, FontWeight.Bold), color = Color.Black)
            }
        }
    }
}

/** Up to five pinned favourites and the level chip, shown on a profile. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Showcase(
    profile: com.anibiar.app.social.UserProfile?, isMe: Boolean, favs: List<com.anibiar.app.social.Fav>,
    modifier: Modifier = Modifier, onOpen: (Int) -> Unit, onSave: (List<com.anibiar.app.social.Fav>) -> Unit,
) {
    val p = profile ?: return
    var edit by remember { mutableStateOf(false) }
    val level = EngageCalc.levelOf(p.xp)
    if (!isMe && p.showcase.isEmpty() && p.xp <= 0) return
    if (isMe && p.showcase.isEmpty() && favs.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Showcase", style = inter(16f, FontWeight.Bold), color = Color.White, modifier = Modifier.weight(1f))
            if (!isMe && p.xp > 0) Text("Lv $level", style = inter(12.5f, FontWeight.Bold), color = Ani.Lavender, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Ani.Lavender.copy(alpha = 0.16f)).padding(horizontal = 9.dp, vertical = 4.dp))
            if (isMe) Text(if (p.showcase.isEmpty()) "Pin favorites" else "Edit", style = inter(13f, FontWeight.SemiBold), color = Ani.Lavender, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { edit = true }.padding(horizontal = 8.dp, vertical = 4.dp))
        }
        if (p.showcase.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                p.showcase.take(5).forEach { f ->
                    Box(Modifier.weight(1f).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(Ani.Item).clickable { onOpen(f.animeId) }) {
                        PosterImage(f.cover, f.title, Modifier.fillMaxWidth().fillMaxHeight(), maxSide = 300)
                    }
                }
                repeat(5 - p.showcase.take(5).size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    if (edit) {
        var chosen by remember { mutableStateOf(p.showcase.map { it.animeId }) }
        Dialog(onDismissRequest = { edit = false }) {
            val shape = RoundedCornerShape(22.dp)
            Column(Modifier.fillMaxWidth().clip(shape).background(Ani.Panel).border(1.dp, Ani.Border, shape).padding(18.dp)) {
                Text("Pin up to 5 favorites", style = inter(18f, FontWeight.Bold), color = Color.White)
                Spacer(Modifier.height(12.dp))
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3), modifier = Modifier.height(340.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(favs.size, key = { favs[it].animeId }) { i ->
                        val f = favs[i]; val on = f.animeId in chosen
                        Box(Modifier.aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(Ani.Item).border(if (on) 3.dp else 0.dp, Ani.Lavender, RoundedCornerShape(10.dp))
                            .clickable { chosen = if (on) chosen - f.animeId else if (chosen.size < 5) chosen + f.animeId else chosen }) {
                            PosterImage(f.cover, f.title, Modifier.fillMaxWidth().fillMaxHeight(), maxSide = 260)
                            if (on) Text("${chosen.indexOf(f.animeId) + 1}", style = inter(12f, FontWeight.Bold), color = Color.Black, modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).clip(CircleShape).background(Ani.Lavender).padding(horizontal = 7.dp, vertical = 2.dp))
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("Save", style = inter(14.5f, FontWeight.Bold), color = Color.Black, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).clickable { onSave(chosen.mapNotNull { id -> favs.firstOrNull { it.animeId == id } }); edit = false }.padding(vertical = 12.dp))
            }
        }
    }
}
