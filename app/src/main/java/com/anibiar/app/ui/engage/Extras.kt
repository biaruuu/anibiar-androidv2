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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.anibiar.app.AniBiarApp
import com.anibiar.app.social.EventInfo
import com.anibiar.app.social.Fav
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

/** A list opened from a kiro://list/<id> link. */
object ListLink { val pending = kotlinx.coroutines.flow.MutableStateFlow<String?>(null) }
