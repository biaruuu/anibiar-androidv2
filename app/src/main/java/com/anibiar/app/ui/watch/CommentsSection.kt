package com.anibiar.app.ui.watch

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anibiar.app.AniBiarApp
import com.anibiar.app.social.Comment
import com.anibiar.app.ui.profile.Avatar
import com.anibiar.app.ui.theme.Ani
import com.anibiar.app.ui.theme.AniIcons
import com.anibiar.app.ui.theme.inter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val REACTIONS = listOf("👍", "❤️", "😂", "🔥", "😮", "😢")

private fun stamp(ms: Long): String {
    val now = Calendar.getInstance(); val then = Calendar.getInstance().apply { timeInMillis = ms }
    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ms))
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    if (sameDay) return "Today at $time"
    now.add(Calendar.DAY_OF_YEAR, -1)
    if (now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)) return "Yesterday at $time"
    return SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).format(Date(ms))
}

/**
 * Discord-style community chat for an anime. On the watch page [episode] is the episode being watched, so the tabs are
 * that episode, the anime's general chat, and a feed of every episode. On the details page [episode] is null.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CommentsSection(animeId: Int, episode: Int?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val social = (context.applicationContext as AniBiarApp).social
    val scope = rememberCoroutineScope()
    val auth by social.authState.collectAsState()
    val me = auth?.uid

    // Tabs: key = episode filter sent to the server (null = all episodes).
    val tabs: List<Pair<String, Int?>> = buildList {
        if (episode != null) add("Episode $episode" to episode)
        add("Anime" to 0)
        add("All episodes" to null)
    }
    var tab by remember(animeId, episode) { mutableStateOf(0) }
    val filter = tabs[tab.coerceIn(0, tabs.lastIndex)].second
    val postEpisode = filter ?: (episode ?: 0)
    val channel = if (filter == null) (if (episode != null) "Episode $episode" else "the anime chat") else if (filter == 0) "the anime chat" else "Episode $filter"

    var limit by remember(animeId, filter) { mutableStateOf(30) }
    var bump by remember { mutableStateOf(0) }
    var comments by remember(animeId, filter) { mutableStateOf<List<Comment>?>(null) }
    var loadError by remember(animeId, filter) { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<Comment?>(null) }
    var sending by remember { mutableStateOf(false) }

    // Live: refresh every few seconds while the section is on screen, and straight after posting.
    LaunchedEffect(animeId, filter, limit, bump) {
        while (true) {
            runCatching { social.fetchComments(animeId, filter, limit) }
                .onSuccess { comments = it; loadError = null }
                .onFailure { if (comments == null) loadError = it.message ?: "Couldn't load comments" }
            delay(4000)
        }
    }

    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Ani.Panel).border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(18.dp)).padding(bottom = 12.dp)) {
        Text("Community", style = inter(15.5f, FontWeight.Bold), color = Color.White, modifier = Modifier.padding(start = 14.dp, top = 14.dp, end = 14.dp))
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tabs.forEachIndexed { i, (label, _) ->
                val on = i == tab
                Text(label, style = inter(12.5f, FontWeight.SemiBold), color = if (on) Color.Black else Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) WebAccent else Ani.Item).clickable { tab = i }.padding(horizontal = 13.dp, vertical = 7.dp))
            }
        }

        val list = comments
        when {
            list == null && loadError != null -> Text(loadError!!, style = inter(13f), color = Ani.Muted, modifier = Modifier.padding(14.dp))
            list == null -> Text("Loading…", style = inter(13f), color = Ani.Muted, modifier = Modifier.padding(14.dp))
            list.isEmpty() -> Text("No messages yet. Start the conversation!", style = inter(13.5f), color = Ani.Muted, modifier = Modifier.padding(horizontal = 14.dp, vertical = 18.dp))
            else -> {
                if (list.size >= limit) Text("Load older messages", style = inter(12.5f, FontWeight.SemiBold), color = WebAccent,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp).clickable { limit += 30 })
                val ordered = list.reversed()
                ordered.forEachIndexed { i, c ->
                    val prev = ordered.getOrNull(i - 1)
                    val grouped = prev != null && prev.userId == c.userId && prev.episode == c.episode && c.replyTo == null && c.createdAt - prev.createdAt < 5 * 60_000
                    key(c.id) {
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            Row(Modifier.fillMaxWidth().clickable { menu = true }.padding(horizontal = 14.dp, vertical = if (grouped) 1.dp else 6.dp)) {
                                if (grouped) Spacer(Modifier.width(46.dp)) else Avatar(c.author?.avatarUrl, c.author?.shownName, 36.dp, Modifier.padding(end = 10.dp))
                                Column(Modifier.weight(1f)) {
                                    if (!grouped) {
                                        if (c.replyTo != null) Row(Modifier.padding(bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Box(Modifier.width(3.dp).height(14.dp).clip(CircleShape).background(Ani.Border))
                                            Text("  @${c.replyAuthor ?: "deleted"}  ${c.replyBody?.take(60) ?: "Original message was deleted"}", style = inter(11.5f), color = Ani.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(c.author?.shownName ?: "User", style = inter(14f, FontWeight.Bold), color = if (c.userId == me) WebAccent else Color.White)
                                            Text("  " + stamp(c.createdAt), style = inter(11f), color = Ani.Muted)
                                            if (filter == null && c.episode > 0) Text("  EP ${c.episode}", style = inter(10.5f, FontWeight.Bold), color = Ani.Lime)
                                        }
                                    }
                                    Text(c.body + if (c.edited) "  (edited)" else "", style = inter(14f, lineHeight = 19.5f), color = Color(0xFFE6E3EA))
                                    if (c.reactions.isNotEmpty()) FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        c.reactions.forEach { (emoji, users) ->
                                            val mine = me != null && me in users
                                            Text("$emoji ${users.size}", style = inter(12f, FontWeight.SemiBold), color = if (mine) WebAccent else Ani.Soft,
                                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (mine) WebAccent.copy(alpha = 0.16f) else Ani.Item)
                                                    .border(1.dp, if (mine) WebAccent.copy(alpha = 0.6f) else Color(0x1FFFFFFF), RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        if (me == null) { android.widget.Toast.makeText(context, "Log in to react", android.widget.Toast.LENGTH_SHORT).show(); return@clickable }
                                                        comments = comments?.map { x -> if (x.id != c.id) x else x.copy(reactions = x.reactions.toMutableMap().also { m ->
                                                            val l = (m[emoji] ?: emptyList()).let { if (mine) it - me else it + me }
                                                            if (l.isEmpty()) m.remove(emoji) else m[emoji] = l }) }
                                                        scope.launch { runCatching { social.toggleReaction(c.id, emoji, !mine) }; bump++ }
                                                    }.padding(horizontal = 8.dp, vertical = 3.dp))
                                        }
                                    }
                                }
                            }
                            DropdownMenu(menu, { menu = false }, modifier = Modifier.background(Color(0xF716161C)).border(1.dp, Color(0x38FFFFFF), RoundedCornerShape(12.dp))) {
                                Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    REACTIONS.forEach { e ->
                                        Text(e, style = inter(20f), modifier = Modifier.clip(CircleShape).clickable {
                                            menu = false
                                            if (me == null) { android.widget.Toast.makeText(context, "Log in to react", android.widget.Toast.LENGTH_SHORT).show(); return@clickable }
                                            val has = me in (c.reactions[e] ?: emptyList())
                                            scope.launch { runCatching { social.toggleReaction(c.id, e, !has) }; bump++ }
                                        }.padding(6.dp))
                                    }
                                }
                                DropdownMenuItem(text = { Text("Reply", style = inter(14.5f), color = Color.White) }, onClick = { menu = false; replyTo = c })
                                DropdownMenuItem(text = { Text("Copy text", style = inter(14.5f), color = Color.White) }, onClick = {
                                    menu = false
                                    (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("comment", c.body))
                                })
                                if (c.userId == me) DropdownMenuItem(text = { Text("Delete", style = inter(14.5f), color = Color(0xFFFF6B6B)) }, onClick = {
                                    menu = false
                                    comments = comments?.filter { it.id != c.id }
                                    scope.launch { runCatching { social.deleteComment(c.id) }; bump++ }
                                })
                            }
                        }
                    }
                }
            }
        }

        // Composer
        Spacer(Modifier.height(8.dp))
        if (me == null) {
            Text("Log in to join the conversation", style = inter(13.5f, FontWeight.SemiBold), color = Ani.Muted,
                modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Ani.Item).padding(14.dp))
        } else {
            replyTo?.let { r ->
                Row(Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)).background(Color(0xFF202023)).padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Replying to ${r.author?.shownName ?: "user"}", style = inter(12f), color = Ani.Muted, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(AniIcons.Close, "Cancel reply", tint = Ani.Muted, modifier = Modifier.size(16.dp).clickable { replyTo = null })
                }
            }
            Row(Modifier.padding(horizontal = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Ani.Item).padding(horizontal = 14.dp, vertical = 11.dp)) {
                    if (text.isEmpty()) Text("Message $channel", style = inter(14f), color = Ani.Muted)
                    BasicTextField(
                        value = text, onValueChange = { if (it.length <= 1000) text = it },
                        textStyle = inter(14f).copy(color = Color.White), cursorBrush = SolidColor(WebAccent), maxLines = 5,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.width(8.dp))
                val ready = text.isNotBlank() && !sending
                Box(Modifier.size(42.dp).clip(CircleShape).background(if (ready) WebAccent else Ani.Item)
                    .clickable(enabled = ready) {
                        val body = text; val r = replyTo; sending = true
                        scope.launch {
                            runCatching { social.postComment(animeId, postEpisode, body, r?.id) }
                                .onSuccess { text = ""; replyTo = null; bump++ }
                                .onFailure { android.widget.Toast.makeText(context, it.message ?: "Couldn't send", android.widget.Toast.LENGTH_SHORT).show() }
                            sending = false
                        }
                    }, contentAlignment = Alignment.Center) {
                    Icon(AniIcons.ArrowUp, "Send", tint = if (ready) Color.Black else Ani.Muted, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}
