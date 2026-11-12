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

/** Badge id to emoji and name. The ids come from the Rust core (engage.rs). */
val BADGES: Map<String, Pair<String, String>> = linkedMapOf(
    "first" to ("🎬" to "First episode"),
    "streak3" to ("🔥" to "3-day streak"), "streak7" to ("🔥" to "7-day streak"), "streak30" to ("🌋" to "30-day streak"),
    "shows10" to ("📚" to "10 anime"), "shows25" to ("📚" to "25 anime"), "shows100" to ("🏛" to "100 anime"),
    "hours24" to ("⏱" to "24 hours"), "hours100" to ("🏆" to "100 hours"), "hours500" to ("👑" to "500 hours"),
    "binge" to ("🍿" to "Binge day"), "marathon" to ("🏃" to "Marathon day"),
    "lv10" to ("⭐" to "Level 10"), "lv25" to ("💎" to "Level 25"),
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

/** Home: streak, today's progress toward the daily goal and the level chip. */
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
            Text(if (streak > 0) "🔥" else "✨", style = inter(26f))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(if (streak > 0) "$streak-day streak" else "Start your streak", style = inter(16f, FontWeight.Bold), color = Color.White)
                Text(if (done) "Goal done today. Nice!" else "$todayMin / $goal min watched today", style = inter(12.5f), color = if (done) Ani.Lavender else Ani.Muted)
            }
            if (e != null) Text("Lv ${e.level}", style = inter(12.5f, FontWeight.Bold), color = Ani.Lavender, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Ani.Lavender.copy(alpha = 0.16f)).padding(horizontal = 9.dp, vertical = 4.dp))
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Ani.Chip)) {
            Box(Modifier.fillMaxWidth((todayMin.toFloat() / goal).coerceIn(0f, 1f)).fillMaxHeight().background(Ani.Lavender))
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Daily goal", style = inter(11.5f), color = Ani.Muted)
            Spacer(Modifier.width(8.dp))
            listOf(10, 20, 30, 60).forEach { m ->
                val on = goal == m
                Text("${m}m", style = inter(11.5f, FontWeight.SemiBold), color = if (on) Color.Black else Ani.Soft,
                    modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(50)).background(if (on) Ani.Lavender else Ani.ButtonDark).clickable { goal = m; setGoal(ctx, m) }.padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
    }
}

/** Profile: level ring, XP bar, stats and every badge earned (own profile). */
@Composable
fun LevelCard(modifier: Modifier = Modifier, onXp: (Long) -> Unit = {}) {
    val (s, e) = rememberEngage()
    LaunchedEffect(e?.xp) { e?.let { onXp(it.xp) } }
    if (s == null || e == null) return
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(Ani.Panel).border(1.dp, Ani.Border, shape).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(54.dp).clip(CircleShape).background(Ani.Lavender.copy(alpha = 0.16f)).border(2.dp, Ani.Lavender, CircleShape), contentAlignment = Alignment.Center) {
                Text("${e.level}", style = inter(20f, FontWeight.ExtraBold), color = Ani.Lavender)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Level ${e.level}", style = inter(17f, FontWeight.Bold), color = Color.White)
                Text("${e.xp} XP · ${e.next - e.xp} to level ${e.level + 1}", style = inter(12.5f), color = Ani.Muted)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Ani.Chip)) {
                    Box(Modifier.fillMaxWidth(e.progress).fillMaxHeight().background(Ani.Lavender))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf("${s.total / 3600}h ${s.total % 3600 / 60}m" to "Watched", "${s.streak}d" to "Streak", "${s.bestStreak}d" to "Best", "${s.shows}" to "Anime").forEach { (v, l) ->
                Column(Modifier.weight(1f)) { Text(v, style = inter(16f, FontWeight.Bold), color = Color.White); Text(l, style = inter(11.5f), color = Ani.Muted) }
            }
        }
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        val myName = (ctx.applicationContext as AniBiarApp).social.let { sr -> "My week" }
        Spacer(Modifier.height(12.dp))
        Text("Share weekly recap", style = inter(13.5f, FontWeight.Bold), color = Color.Black, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).clickable { scope.launch { RecapCard.share(ctx, myName, s, e) } }.padding(vertical = 10.dp))
        if (e.badges.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text("Badges · ${e.badges.size}/${BADGES.size}", style = inter(13f, FontWeight.SemiBold), color = Ani.Soft)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BADGES.filterKeys { it in e.badges }.forEach { (_, v) ->
                    Text("${v.first} ${v.second}", style = inter(12.5f, FontWeight.Medium), color = Color.White, maxLines = 1, softWrap = false,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Ani.Chip).padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
        }
    }
}

private class Mood(val emoji: String, val name: String, val genres: List<String>)
private val MOODS = listOf(
    Mood("⚡", "Hype", listOf("Action", "Adventure")), Mood("☕", "Chill", listOf("Slice of Life")),
    Mood("😭", "Emotional", listOf("Drama")), Mood("😂", "Funny", listOf("Comedy")),
    Mood("🌑", "Dark", listOf("Psychological", "Horror", "Thriller")), Mood("💕", "Romantic", listOf("Romance")),
    Mood("🧠", "Mind-bending", listOf("Mystery", "Sci-Fi", "Psychological")), Mood("✨", "Magical", listOf("Fantasy", "Supernatural")),
)

/** Home: "What should I watch?" opens the mood picker. */
@Composable
fun MoodCard(onOpen: (Int) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Row(modifier.fillMaxWidth().clip(shape).background(Ani.Panel).border(1.dp, Ani.Border, shape).clickable { open = true }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("🎲", style = inter(26f))
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
                    Text("${m.emoji} ${m.name}", style = inter(13.5f, FontWeight.SemiBold), color = if (on) Color.Black else Color.White,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Ani.Lavender else Ani.ButtonDark).clickable { mood = m; roll(m) }.padding(horizontal = 14.dp, vertical = 8.dp))
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
            Text("🎲 Surprise me", style = inter(14f, FontWeight.Bold), color = Color.Black, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).clickable { mood = null; roll(null) }.padding(vertical = 12.dp))
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
