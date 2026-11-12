package com.anibiar.app.ui.engage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.anibiar.app.AniBiarApp
import com.anibiar.app.data.PartyWorker
import com.anibiar.app.data.displayTitle
import com.anibiar.app.data.posterUrl
import com.anibiar.app.social.EventInfo
import com.anibiar.app.social.Fav
import com.anibiar.app.social.Party
import com.anibiar.app.social.QuizRow
import com.anibiar.app.social.UserList
import com.anibiar.app.ui.home.PosterImage
import com.anibiar.app.ui.profile.Avatar
import com.anibiar.app.ui.theme.Ani
import com.anibiar.app.ui.theme.inter
import kotlinx.coroutines.launch

private val CARD = RoundedCornerShape(16.dp)
private val DLG = RoundedCornerShape(22.dp)

@Composable
private fun Pill(text: String, on: Boolean = false, onClick: () -> Unit) {
    Text(text, style = inter(13.5f, FontWeight.SemiBold), color = if (on) Color.Black else Color.White,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Ani.Lavender else Ani.ButtonDark).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp))
}

private fun countdown(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return when { s >= 86400 -> "${s / 86400}d ${(s % 86400) / 3600}h"; s >= 3600 -> "${s / 3600}h ${(s % 3600) / 60}m"; else -> "${s / 60}m" }
}

// ------------------------------------------------------------------ seasonal event banner (Home)

@Composable
fun EventBanner(modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as AniBiarApp
    var ev by remember { mutableStateOf<EventInfo?>(null) }
    LaunchedEffect(Unit) { ev = runCatching { app.social.currentEvent() }.getOrNull() }
    val e = ev ?: return
    Box(modifier.fillMaxWidth().clip(CARD).background(Ani.Panel).border(1.dp, Ani.Border, CARD)) {
        if (!e.image.isNullOrBlank()) {
            PosterImage(e.image, e.title, Modifier.matchParentSize(), alpha = 0.45f, maxSide = 900)
        }
        Column(Modifier.padding(16.dp)) {
            Text("EVENT · ends in ${countdown(e.endsAt - System.currentTimeMillis())}", style = inter(11f, FontWeight.Bold), color = Ani.Lavender)
            Text(e.title, style = inter(19f, FontWeight.Bold), color = Color.White, modifier = Modifier.padding(top = 2.dp))
            if (e.body.isNotBlank()) Text(e.body, style = inter(13.5f), color = Color.White.copy(alpha = 0.85f), modifier = Modifier.padding(top = 4.dp))
        }
    }
}

// ------------------------------------------------------------------ custom / shared lists

/** Details: pick a list (or make a new one) to add this anime to. */
@Composable
fun AddToListDialog(animeId: Int, title: String, cover: String?, onClose: () -> Unit) {
    val app = LocalContext.current.applicationContext as AniBiarApp
    val scope = rememberCoroutineScope()
    val me = app.social.myId
    var lists by remember { mutableStateOf<List<UserList>?>(null) }
    var name by remember { mutableStateOf("") }
    var collab by remember { mutableStateOf(false) }
    var added by remember { mutableStateOf(setOf<String>()) }
    var msg by remember { mutableStateOf<String?>(null) }
    fun reload() { scope.launch { lists = me?.let { runCatching { app.social.listsOf(it) }.getOrDefault(emptyList()) } ?: emptyList() } }
    LaunchedEffect(Unit) { reload() }
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().clip(DLG).background(Ani.Panel).border(1.dp, Ani.Border, DLG).padding(18.dp)) {
            Text("Add to list", style = inter(18f, FontWeight.Bold), color = Color.White)
            Text(title, style = inter(13f), color = Ani.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
            if (me == null) { Text("Sign in to make lists.", style = inter(14f), color = Ani.Muted); return@Column }
            val l = lists
            if (l == null) Text("Loading…", style = inter(14f), color = Ani.Muted)
            else LazyColumn(Modifier.height(if (l.isEmpty()) 10.dp else 180.dp)) {
                items(l, key = { it.id }) { x ->
                    val done = x.id in added
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(x.title + if (x.collaborative) "  · shared" else "", style = inter(14.5f, FontWeight.SemiBold), color = Color.White, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (done) "Added" else "Add", style = inter(13f, FontWeight.Bold), color = if (done) Ani.Soft else Color.Black,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(if (done) Ani.ButtonDark else Ani.Lavender).clickable(enabled = !done) {
                                added = added + x.id
                                scope.launch { runCatching { app.social.addToUList(x.id, Fav(animeId, title, cover)) }.onFailure { added = added - x.id; msg = it.message } }
                            }.padding(horizontal = 16.dp, vertical = 7.dp))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            androidx.compose.foundation.text.BasicTextField(name, { name = it.take(60) }, singleLine = true,
                textStyle = inter(14.5f).copy(color = Color.White), cursorBrush = androidx.compose.ui.graphics.SolidColor(Ani.Lavender),
                decorationBox = { inner -> Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ani.Item).padding(12.dp)) { if (name.isEmpty()) Text("New list name", style = inter(14.5f), color = Ani.Muted); inner() } })
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Friends can add", collab) { collab = !collab }
                Pill("Create") {
                    if (name.isBlank()) return@Pill
                    val n = name; name = ""
                    scope.launch {
                        runCatching { val id = app.social.createUList(n, collab); app.social.addToUList(id, Fav(animeId, title, cover)); added = added + id }.onFailure { msg = it.message }
                        reload()
                    }
                }
            }
            msg?.let { Text(it, style = inter(12f), color = Color(0xFFFF6B6B), modifier = Modifier.padding(top = 8.dp)) }
        }
    }
}

/** Profile: the person's lists. Tap one to see its anime. */
@Composable
fun ListsSection(uid: String, isMe: Boolean, onOpenAnime: (Int) -> Unit, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as AniBiarApp
    var lists by remember(uid) { mutableStateOf<List<UserList>>(emptyList()) }
    var open by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(uid, open) { lists = runCatching { app.social.listsOf(uid) }.getOrDefault(emptyList()).filter { isMe || it.isPublic } }
    if (lists.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        Text("Lists", style = inter(16f, FontWeight.Bold), color = Color.White, modifier = Modifier.padding(bottom = 8.dp))
        lists.forEach { l ->
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(12.dp)).background(Ani.Panel).clickable { open = l.id }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(l.title, style = inter(14.5f, FontWeight.SemiBold), color = Color.White, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (l.collaborative) Text("shared", style = inter(11.5f), color = Ani.Lavender)
            }
        }
    }
    open?.let { ListDialog(it, onOpenAnime = { id -> open = null; onOpenAnime(id) }, onClose = { open = null }) }
}

@Composable
fun ListDialog(listId: String, onOpenAnime: (Int) -> Unit, onClose: () -> Unit) {
    val app = LocalContext.current.applicationContext as AniBiarApp
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<UserList?>(null) }
    var items by remember { mutableStateOf<List<Fav>>(emptyList()) }
    LaunchedEffect(listId) {
        list = runCatching { app.social.listById(listId) }.getOrNull()
        items = runCatching { app.social.uListItems(listId) }.getOrDefault(emptyList())
    }
    val mine = list?.ownerId == app.social.myId
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().clip(DLG).background(Ani.Panel).border(1.dp, Ani.Border, DLG).padding(18.dp)) {
            Text(list?.title ?: "List", style = inter(18f, FontWeight.Bold), color = Color.White)
            Text("${items.size} anime", style = inter(12.5f), color = Ani.Muted, modifier = Modifier.padding(bottom = 10.dp))
            if (items.isEmpty()) Text("Nothing here yet.", style = inter(14f), color = Ani.Muted, modifier = Modifier.padding(vertical = 20.dp))
            LazyColumn(Modifier.height(300.dp)) {
                items(items, key = { it.animeId }) { f ->
                    Row(Modifier.fillMaxWidth().clickable { onOpenAnime(f.animeId) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        PosterImage(f.cover, f.title, Modifier.width(44.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).background(Ani.Item), maxSide = 160)
                        Spacer(Modifier.width(12.dp))
                        Text(f.title, style = inter(14f, FontWeight.SemiBold), color = Color.White, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (mine) Text("Remove", style = inter(12f), color = Ani.Muted, modifier = Modifier.clickable {
                            items = items - f; scope.launch { runCatching { app.social.removeFromUList(listId, f.animeId) } }
                        }.padding(8.dp))
                    }
                }
            }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Share") {
                    val i = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(android.content.Intent.EXTRA_TEXT, "${list?.title ?: "My list"} on Kiro: kiro://list/$listId")
                    ctx.startActivity(android.content.Intent.createChooser(i, "Share list").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                if (mine) Pill("Delete list") { scope.launch { runCatching { app.social.deleteUList(listId) }; onClose() } }
            }
        }
    }
}

// ------------------------------------------------------------------ scheduled watch parties

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PartyCreateDialog(animeId: Int, title: String, cover: String?, onClose: () -> Unit) {
    val app = LocalContext.current.applicationContext as AniBiarApp
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var friends by remember { mutableStateOf<List<com.anibiar.app.social.UserProfile>?>(null) }
    var picked by remember { mutableStateOf(setOf<String>()) }
    var slot by remember { mutableStateOf(1) }
    var ep by remember { mutableStateOf(1) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { friends = runCatching { app.social.profiles(app.social.myFriendIds()) }.getOrDefault(emptyList()) }
    val slots = listOf("In 30 min" to 30, "In 1 hour" to 60, "In 3 hours" to 180, "Tonight 8 PM" to -1, "Tomorrow 8 PM" to -2)
    fun startMs(): Long {
        val (_, m) = slots[slot]
        if (m > 0) return System.currentTimeMillis() + m * 60_000L
        val c = java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, 20); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0) }
        if (m == -2 || c.timeInMillis < System.currentTimeMillis() + 5 * 60_000L) c.add(java.util.Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().clip(DLG).background(Ani.Panel).border(1.dp, Ani.Border, DLG).padding(18.dp)) {
            Text("Watch party", style = inter(18f, FontWeight.Bold), color = Color.White)
            Text(title, style = inter(13f), color = Ani.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                slots.forEachIndexed { i, (n, _) -> Pill(n, slot == i) { slot = i } }
            }
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Episode", style = inter(14f), color = Ani.Soft)
                Pill("−") { if (ep > 1) ep-- }
                Text("$ep", style = inter(16f, FontWeight.Bold), color = Color.White)
                Pill("+") { ep++ }
            }
            Text("Invite", style = inter(14f), color = Ani.Soft, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            val f = friends
            when {
                f == null -> Text("Loading…", style = inter(13.5f), color = Ani.Muted)
                f.isEmpty() -> Text("No friends yet.", style = inter(13.5f), color = Ani.Muted)
                else -> LazyColumn(Modifier.height(170.dp)) {
                    items(f, key = { it.uid }) { u ->
                        val on = u.uid in picked
                        Row(Modifier.fillMaxWidth().clickable { picked = if (on) picked - u.uid else picked + u.uid }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(u.avatarUrl, u.username, 36.dp, deco = u.avatarDeco)
                            Spacer(Modifier.width(10.dp))
                            Text(u.shownName, style = inter(14f, FontWeight.SemiBold), color = Color.White, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (on) "Invited" else "Invite", style = inter(12.5f, FontWeight.Bold), color = if (on) Color.Black else Color.White,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Ani.Lavender else Ani.ButtonDark).padding(horizontal = 12.dp, vertical = 5.dp))
                        }
                    }
                }
            }
            err?.let { Text(it, style = inter(12f), color = Color(0xFFFF6B6B), modifier = Modifier.padding(top = 6.dp)) }
            Spacer(Modifier.height(12.dp))
            Text(if (busy) "Scheduling…" else "Schedule", style = inter(14.5f, FontWeight.Bold), color = Color.Black,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Ani.Lavender).clickable(enabled = !busy) {
                    busy = true; err = null
                    val at = startMs()
                    scope.launch {
                        runCatching {
                            app.social.createParty(animeId, title, cover, ep, at, picked.toList())
                            picked.forEach { u -> runCatching { app.social.sendMessage(u, "[[anime:$animeId|${title.replace("|", " ")}|${cover ?: ""}]] Watch party: episode $ep at ${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(at))}. Join me!") } }
                            PartyWorker.schedule(ctx, animeId, title, ep, at)
                        }.onSuccess { onClose() }.onFailure { err = it.message; busy = false }
                    }
                }.padding(vertical = 12.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

/** Inbox: upcoming parties I host or am invited to. */
@Composable
fun PartiesRow(onOpenAnime: (Int) -> Unit, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as AniBiarApp
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var parties by remember { mutableStateOf<List<Party>>(emptyList()) }
    LaunchedEffect(Unit) {
        val me = app.social.myId
        parties = runCatching { app.social.myParties() }.getOrDefault(emptyList()).filter { it.hostId == me || me in it.invited }
        parties.forEach { if (it.startAt > System.currentTimeMillis()) PartyWorker.schedule(ctx, it.animeId, it.title, it.episode, it.startAt) }
    }
    if (parties.isEmpty()) return
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Watch parties", style = inter(15f, FontWeight.Bold), color = Color.White)
        parties.forEach { p ->
            val left = p.startAt - System.currentTimeMillis()
            Row(Modifier.fillMaxWidth().clip(CARD).background(Ani.Panel).border(1.dp, Ani.Border, CARD).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                PosterImage(p.cover, p.title, Modifier.width(48.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).background(Ani.Item), maxSide = 180)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.title, style = inter(14.5f, FontWeight.Bold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Ep ${p.episode} · host ${p.hostName}", style = inter(12f), color = Ani.Muted, maxLines = 1)
                    Text(if (left > 0) "Starts in ${countdown(left)}" else "Live now", style = inter(12.5f, FontWeight.SemiBold), color = Ani.Lavender)
                }
                Text(if (left > 0) "Open" else "Join", style = inter(13f, FontWeight.Bold), color = Color.Black,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Ani.Lavender).clickable { onOpenAnime(p.animeId) }.padding(horizontal = 16.dp, vertical = 7.dp))
                if (p.hostId == app.social.myId) Text("✕", style = inter(14f), color = Ani.Muted, modifier = Modifier.clickable {
                    parties = parties - p; scope.launch { runCatching { app.social.deleteParty(p.id) } }
                }.padding(10.dp))
            }
        }
    }
}

// ------------------------------------------------------------------ daily quiz

private data class QRound(val answer: String, val poster: String?, val options: List<String>, val ox: Float, val oy: Float)

@Composable
fun QuizCard(modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as AniBiarApp
    val day = remember { (System.currentTimeMillis() / 86_400_000L).toInt() }
    val prefs = remember { app.getSharedPreferences("engage", android.content.Context.MODE_PRIVATE) }
    var done by remember { mutableStateOf(prefs.getInt("quiz_$day", -1)) }
    var open by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth().clip(CARD).background(Ani.Panel).border(1.dp, Ani.Border, CARD).clickable { open = true }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("🧩", style = inter(26f))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Daily quiz", style = inter(16f, FontWeight.Bold), color = Color.White)
            Text(if (done >= 0) "Today: $done/5 · see the leaderboard" else "Guess 5 anime from a zoomed poster", style = inter(12.5f), color = Ani.Muted)
        }
        Text(if (done >= 0) "Board" else "Play", style = inter(13f, FontWeight.Bold), color = Color.Black, modifier = Modifier.clip(RoundedCornerShape(50)).background(Ani.Lavender).padding(horizontal = 16.dp, vertical = 7.dp))
    }
    if (open) QuizDialog(day, done, onDone = { s -> done = s; prefs.edit().putInt("quiz_$day", s).apply() }, onClose = { open = false })
}

@Composable
private fun QuizDialog(day: Int, doneScore: Int, onDone: (Int) -> Unit, onClose: () -> Unit) {
    val app = LocalContext.current.applicationContext as AniBiarApp
    val scope = rememberCoroutineScope()
    var rounds by remember { mutableStateOf<List<QRound>?>(null) }
    var i by remember { mutableStateOf(0) }
    var score by remember { mutableStateOf(0) }
    var picked by remember { mutableStateOf<String?>(null) }
    var finished by remember { mutableStateOf(doneScore >= 0) }
    var board by remember { mutableStateOf<List<QuizRow>?>(null) }
    var started by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        if (doneScore < 0) {
            val pool = runCatching { app.repository.fetchTrending() }.getOrDefault(emptyList()).filter { it.posterUrl != null }
            if (pool.size >= 8) {
                val rnd = java.util.Random(day.toLong() * 7919L)
                val shuffled = pool.shuffled(rnd)
                rounds = shuffled.take(5).map { m ->
                    val wrong = shuffled.drop(5).filter { it.id != m.id }.shuffled(rnd).take(3).map { it.displayTitle }
                    QRound(m.displayTitle, m.posterUrl, (wrong + m.displayTitle).shuffled(rnd), 0.2f + rnd.nextFloat() * 0.6f, 0.2f + rnd.nextFloat() * 0.5f)
                }
                started = System.currentTimeMillis()
            }
        }
    }
    LaunchedEffect(finished) {
        if (finished) {
            val me = app.social.myId
            board = runCatching { app.social.quizBoard(day, (app.social.myFriendIds() + listOfNotNull(me)).distinct()) }.getOrDefault(emptyList())
        }
    }
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().clip(DLG).background(Ani.Panel).border(1.dp, Ani.Border, DLG).padding(18.dp)) {
            val r = rounds
            if (!finished) {
                if (r == null) { Text("Loading today's quiz…", style = inter(14f), color = Ani.Muted, modifier = Modifier.padding(vertical = 30.dp)); return@Column }
                val q = r[i]
                Text("Question ${i + 1}/5  ·  score $score", style = inter(13f, FontWeight.SemiBold), color = Ani.Muted)
                Box(Modifier.padding(vertical = 12.dp).fillMaxWidth().aspectRatio(1.4f).clip(RoundedCornerShape(14.dp)).background(Ani.Item)) {
                    val zoom = if (picked == null) 3.2f else 1f
                    PosterImage(q.poster, null, Modifier.matchParentSize().graphicsLayer { scaleX = zoom; scaleY = zoom; transformOrigin = TransformOrigin(q.ox, q.oy) }, maxSide = 700)
                }
                q.options.forEach { o ->
                    val p = picked
                    val bg = when { p == null -> Ani.ButtonDark; o == q.answer -> Color(0xFF1F7A3E); o == p -> Color(0xFF8A2A2A); else -> Ani.ButtonDark }
                    Text(o, style = inter(14f, FontWeight.SemiBold), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(12.dp)).background(bg).clickable(enabled = p == null) {
                            picked = o; if (o == q.answer) score++
                            scope.launch {
                                kotlinx.coroutines.delay(1100)
                                if (i < 4) { i++; picked = null }
                                else {
                                    val ms = (System.currentTimeMillis() - started).toInt()
                                    onDone(score); finished = true
                                    runCatching { app.social.submitQuiz(day, score, ms) }
                                }
                            }
                        }.padding(12.dp))
                }
            } else {
                Text("Today's leaderboard", style = inter(18f, FontWeight.Bold), color = Color.White)
                Text("You scored ${if (doneScore >= 0) doneScore else score}/5. New quiz every day.", style = inter(12.5f), color = Ani.Muted, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
                val b = board
                if (b == null) Text("Loading…", style = inter(14f), color = Ani.Muted)
                else if (b.isEmpty()) Text("No scores yet from you or your friends.", style = inter(14f), color = Ani.Muted)
                else b.forEachIndexed { n, x ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${n + 1}", style = inter(14f, FontWeight.Bold), color = Ani.Lavender, modifier = Modifier.width(24.dp))
                        Avatar(x.avatar, x.name, 34.dp, deco = x.deco)
                        Spacer(Modifier.width(10.dp))
                        Text(x.name, style = inter(14f, FontWeight.SemiBold), color = Color.White, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${x.score}/5", style = inter(14f, FontWeight.Bold), color = Color.White)
                    }
                }
            }
        }
    }
}

/** A list opened from a kiro://list/<id> link. */
object ListLink { val pending = kotlinx.coroutines.flow.MutableStateFlow<String?>(null) }
