package com.anibiar.app.ui.engage

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.anibiar.app.AniBiarApp
import com.anibiar.app.data.Engage
import com.anibiar.app.data.StatsSummary
import com.anibiar.app.social.ActivityItem
import com.anibiar.app.social.EpComment
import com.anibiar.app.social.Reaction
import com.anibiar.app.ui.home.PosterImage
import com.anibiar.app.ui.profile.Avatar
import com.anibiar.app.ui.theme.Ani
import com.anibiar.app.ui.theme.inter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private class Rx(val id: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val tint: Color)
private val REACTS = listOf(
    Rx("fire", com.anibiar.app.ui.theme.EngageIcons.Flame, Color(0xFFFF9F0A)), Rx("laugh", com.anibiar.app.ui.theme.AniIcons.Sticker, Color(0xFFFFD60A)),
    Rx("sad", com.anibiar.app.ui.theme.EngageIcons.Drop, Color(0xFF64D2FF)), Rx("wow", com.anibiar.app.ui.theme.EngageIcons.Bolt, Color(0xFFBF5AF2)),
    Rx("love", com.anibiar.app.ui.theme.EngageIcons.Heart, Color(0xFFFF375F)), Rx("clap", com.anibiar.app.ui.theme.AniIcons.Star, Color(0xFF30D158)),
)

private fun agoText(at: Long): String {
    val s = (System.currentTimeMillis() - at) / 1000
    return when { s < 60 -> "now"; s < 3600 -> "${s / 60}m"; s < 86400 -> "${s / 3600}h"; else -> "${s / 86400}d" }
}

/** Watch page: quick reactions and a short comment thread for one episode. */
@Composable
fun EpisodeSocial(animeId: Int, episode: Int, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as AniBiarApp
    val social = app.social
    val scope = rememberCoroutineScope()
    var reactions by remember(animeId, episode) { mutableStateOf<List<Reaction>>(emptyList()) }
    var comments by remember(animeId, episode) { mutableStateOf<List<EpComment>>(emptyList()) }
    var open by remember(animeId, episode) { mutableStateOf(false) }
    var text by remember(animeId, episode) { mutableStateOf("") }
    var spoiler by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val myId = social.myId

    fun reload() = scope.launch {
        reactions = runCatching { social.reactions(animeId, episode) }.getOrDefault(reactions)
        comments = runCatching { social.comments(animeId, episode) }.getOrDefault(comments)
    }
    LaunchedEffect(animeId, episode) { reload() }

    val shape = RoundedCornerShape(16.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(Ani.Panel).border(1.dp, Ani.Border, shape).padding(14.dp)) {
        Text("Episode $episode reactions", style = inter(14f, FontWeight.Bold), color = Color.White)
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(REACTS) { x ->
                val r = reactions.firstOrNull { it.emoji == x.id }
                val mine = r?.mine == true
                Row(Modifier.clip(RoundedCornerShape(50)).background(if (mine) x.tint.copy(alpha = 0.22f) else Ani.ButtonDark)
                        .border(1.dp, if (mine) x.tint else Color.Transparent, RoundedCornerShape(50))
                        .clickable {
                            if (myId == null) { error = "Log in to react"; return@clickable }
                            scope.launch { runCatching { social.react(animeId, episode, if (mine) null else x.id) }.onFailure { error = it.message }; reload() }
                        }.padding(horizontal = 13.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(x.icon, null, tint = x.tint, modifier = Modifier.size(17.dp))
                    if ((r?.n ?: 0) > 0) { Spacer(Modifier.width(6.dp)); Text("${r?.n}", style = inter(13.5f, FontWeight.SemiBold), color = Color.White) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { open = !open }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Comments · ${comments.size}", style = inter(14f, FontWeight.Bold), color = Color.White, modifier = Modifier.weight(1f))
            Text(if (open) "Hide" else "Show", style = inter(13f, FontWeight.SemiBold), color = Ani.Lavender)
        }
        if (open) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Ani.Item).padding(horizontal = 12.dp, vertical = 10.dp)) {
                    if (text.isEmpty()) Text("Add a comment…", style = inter(14f), color = Ani.Muted)
                    BasicTextField(text, { text = it.take(280) }, textStyle = TextStyle(color = Color.White, fontSize = 14.sp), cursorBrush = SolidColor(Ani.Lavender), modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.width(8.dp))
                Text("Post", style = inter(13.5f, FontWeight.Bold), color = Color.Black,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (text.isBlank()) Ani.Muted else Ani.Lavender).clickable(enabled = text.isNotBlank()) {
                        val t = text; val sp = spoiler; text = ""
                        scope.launch { runCatching { social.addComment(animeId, episode, t, sp) }.onFailure { error = it.message }; reload() }
                    }.padding(horizontal = 16.dp, vertical = 10.dp))
            }
            Row(Modifier.padding(top = 8.dp).clip(RoundedCornerShape(8.dp)).clickable { spoiler = !spoiler }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(if (spoiler) com.anibiar.app.ui.theme.AniIcons.CheckCircle else com.anibiar.app.ui.theme.AniIcons.Info, null, tint = if (spoiler) Ani.Lavender else Ani.Muted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Contains spoilers", style = inter(12.5f), color = Ani.Soft)
            }
            error?.let { Text(it, style = inter(12f), color = Color(0xFFFF6B6B), modifier = Modifier.padding(top = 6.dp)) }
            Spacer(Modifier.height(10.dp))
            if (comments.isEmpty()) Text("No comments yet. Be the first!", style = inter(13f), color = Ani.Muted)
            comments.forEach { c ->
                var shown by remember(c.id) { mutableStateOf(!c.spoiler) }
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Avatar(c.avatar, c.name, 30.dp, deco = c.deco)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(c.name, style = inter(13f, FontWeight.Bold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("  ${agoText(c.at)}", style = inter(11.5f), color = Ani.Muted)
                        }
                        Text(c.body, style = inter(13.5f, lineHeight = 18f), color = Ani.Soft,
                            modifier = Modifier.then(if (shown) Modifier else Modifier.blur(8.dp)).clickable(enabled = !shown) { shown = true })
                        if (!shown) Text("Spoiler. Tap to reveal", style = inter(11.5f), color = Ani.Muted)
                    }
                    if (c.uid == myId) androidx.compose.material3.Icon(com.anibiar.app.ui.theme.AniIcons.Close, null, tint = Ani.Muted, modifier = Modifier.clickable { scope.launch { runCatching { social.deleteComment(c.id) }; reload() } }.padding(6.dp).size(16.dp))
                }
            }
        }
    }
}

/** Inbox: what the people I follow just watched. */
@Composable
fun FriendActivityRow(onOpenAnime: (Int) -> Unit, onOpenUser: (String) -> Unit, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as AniBiarApp
    var items by remember { mutableStateOf<List<ActivityItem>>(emptyList()) }
    LaunchedEffect(Unit) { items = runCatching { app.social.feed(20) }.getOrDefault(emptyList()).filter { it.uid != app.social.myId } }
    if (items.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        Text("Friends are watching", style = inter(14f, FontWeight.Bold), color = Ani.Muted, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp))
        LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { it.id }) { a ->
                Column(Modifier.width(112.dp)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp)).background(Ani.Item).clickable { a.animeId?.let(onOpenAnime) }) {
                        PosterImage(a.cover, a.title, Modifier.fillMaxWidth().height(168.dp), maxSide = 300)
                        Box(Modifier.align(Alignment.TopStart).padding(6.dp).clickable { onOpenUser(a.uid) }) { Avatar(a.avatar, a.name, 28.dp, deco = a.deco) }
                        a.episode?.let { Text("EP $it", style = inter(10.5f, FontWeight.Bold), color = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x99000000)).padding(horizontal = 6.dp, vertical = 2.dp)) }
                    }
                    Text(a.name, style = inter(12f, FontWeight.SemiBold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    Text("${a.title ?: ""} · ${agoText(a.at)}", style = inter(11f), color = Ani.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** "Send to a friend": picks a friend and sends a recommendation card as a chat message. */
@Composable
fun RecommendDialog(animeId: Int, title: String, cover: String?, onClose: () -> Unit) {
    val app = LocalContext.current.applicationContext as AniBiarApp
    val scope = rememberCoroutineScope()
    var friends by remember { mutableStateOf<List<com.anibiar.app.social.UserProfile>?>(null) }
    var sent by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(Unit) {
        friends = runCatching { app.social.profiles(app.social.myFriendIds()) }.getOrDefault(emptyList())
    }
    Dialog(onDismissRequest = onClose) {
        val shape = RoundedCornerShape(22.dp)
        Column(Modifier.fillMaxWidth().clip(shape).background(Ani.Panel).border(1.dp, Ani.Border, shape).padding(18.dp)) {
            Text("Send to a friend", style = inter(18f, FontWeight.Bold), color = Color.White)
            Text(title, style = inter(13f), color = Ani.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
            val f = friends
            when {
                f == null -> Text("Loading…", style = inter(14f), color = Ani.Muted, modifier = Modifier.padding(vertical = 24.dp))
                f.isEmpty() -> Text("No friends yet. Add friends from their profiles.", style = inter(14f), color = Ani.Muted, modifier = Modifier.padding(vertical = 24.dp))
                else -> androidx.compose.foundation.lazy.LazyColumn(Modifier.height(300.dp)) {
                    items(f, key = { it.uid }) { u ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(u.avatarUrl, u.username, 40.dp, deco = u.avatarDeco)
                            Spacer(Modifier.width(12.dp))
                            Text(u.shownName, style = inter(14.5f, FontWeight.SemiBold), color = Color.White, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val done = u.uid in sent
                            Text(if (done) "Sent" else "Send", style = inter(13f, FontWeight.Bold), color = if (done) Ani.Soft else Color.Black,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (done) Ani.ButtonDark else Ani.Lavender).clickable(enabled = !done) {
                                    sent = sent + u.uid
                                    scope.launch { runCatching { app.social.sendMessage(u.uid, "[[anime:$animeId|${title.replace("|", " ")}|${cover ?: ""}]] You should watch this!") }.onFailure { sent = sent - u.uid } }
                                }.padding(horizontal = 16.dp, vertical = 7.dp))
                        }
                    }
                }
            }
        }
    }
}

/** A recommendation message in a chat: poster, title and a button that opens the anime. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AnimeCardBubble(body: String, mine: Boolean, onOpen: (Int) -> Unit, onLong: () -> Unit) {
    val m = Regex("^\\[\\[anime:(\\d+)\\|([^|]*)\\|([^\\]]*)]]\\s*(.*)$", RegexOption.DOT_MATCHES_ALL).find(body) ?: return
    val id = m.groupValues[1].toInt(); val title = m.groupValues[2]; val cover = m.groupValues[3].ifBlank { null }; val note = m.groupValues[4]
    val shape = RoundedCornerShape(18.dp)
    Row(Modifier.width(260.dp).clip(shape).background(if (mine) Ani.Lavender.copy(alpha = 0.3f) else Ani.Item).border(1.dp, Ani.Border, shape)
        .combinedClickable(onClick = { onOpen(id) }, onLongClick = onLong).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        PosterImage(cover, title, Modifier.width(64.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(Ani.Panel), maxSide = 240)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(com.anibiar.app.ui.theme.AniIcons.Play, null, tint = Ani.Lavender, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text("Recommended", style = inter(11f, FontWeight.SemiBold), color = Ani.Lavender)
            }
            Text(title, style = inter(14.5f, FontWeight.Bold), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (note.isNotBlank()) Text(note, style = inter(12f), color = Ani.Soft, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Tap to open", style = inter(11.5f), color = Ani.Muted, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** Weekly recap picture, shareable. */
object RecapCard {
    suspend fun share(context: Context, name: String, s: StatsSummary, e: Engage) {
        val file = withContext(Dispatchers.Default) { render(context, name, s, e) } ?: return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TEXT, "My week on Kiro").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Share weekly recap"))
    }

    private fun render(context: Context, name: String, s: StatsSummary, e: Engage): File? = runCatching {
        val w = 1080; val h = 1350
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFF101114.toInt()
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.color = 0xFF0A84FF.toInt()
        c.drawRect(0f, 0f, w.toFloat(), h * 0.1f, p)
        val bold = androidx.core.content.res.ResourcesCompat.getFont(context, com.anibiar.app.R.font.inter_bold) ?: Typeface.DEFAULT_BOLD
        val reg = androidx.core.content.res.ResourcesCompat.getFont(context, com.anibiar.app.R.font.inter_regular) ?: Typeface.DEFAULT
        p.textAlign = Paint.Align.CENTER
        p.color = android.graphics.Color.WHITE; p.typeface = bold; p.textSize = 64f
        c.drawText("My week on Kiro", w / 2f, 170f, p)
        p.typeface = reg; p.textSize = 42f; p.color = 0xFFD1D1D6.toInt()
        c.drawText(name.take(28), w / 2f, 236f, p)
        val h2 = s.week / 3600; val m2 = s.week % 3600 / 60
        p.color = android.graphics.Color.WHITE; p.typeface = bold; p.textSize = 190f
        c.drawText(if (h2 > 0) "${h2}h ${m2}m" else "${m2}m", w / 2f, 560f, p)
        p.typeface = reg; p.textSize = 46f; p.color = 0xFFD1D1D6.toInt()
        c.drawText("watched this week", w / 2f, 630f, p)
        val rows = listOf("Streak" to "${s.streak} days", "Level" to "${e.level}", "Anime watched" to "${s.shows}", "Best day" to "${s.maxDay / 3600}h ${s.maxDay % 3600 / 60}m") +
            (s.genres.firstOrNull()?.let { listOf("Top genre" to it.first) } ?: emptyList())
        var y = 770f
        p.textAlign = Paint.Align.LEFT
        rows.forEach { (k, v) ->
            p.typeface = reg; p.textSize = 48f; p.color = 0xFFD1D1D6.toInt(); c.drawText(k, 140f, y, p)
            p.typeface = bold; p.color = android.graphics.Color.WHITE; p.textAlign = Paint.Align.RIGHT; c.drawText(v.take(18), w - 140f, y, p); p.textAlign = Paint.Align.LEFT
            y += 98f
        }
        p.textAlign = Paint.Align.CENTER; p.typeface = bold; p.textSize = 46f; p.color = android.graphics.Color.WHITE
        c.drawText("Kiro · AniBiar TV", w / 2f, 1280f, p)
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        File(dir, "recap.png").also { f -> f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }.getOrNull()
}
