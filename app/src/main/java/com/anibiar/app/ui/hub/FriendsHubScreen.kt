package com.anibiar.app.ui.hub

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anibiar.app.AniBiarApp
import com.anibiar.app.data.displayTitle
import com.anibiar.app.data.posterUrl
import com.anibiar.app.social.FriendActivity
import com.anibiar.app.social.ListItem
import com.anibiar.app.social.SharedList
import com.anibiar.app.social.UserProfile
import com.anibiar.app.ui.home.PosterImage
import com.anibiar.app.ui.theme.Ani
import com.anibiar.app.ui.theme.GlassBack
import com.anibiar.app.ui.theme.inter
import kotlinx.coroutines.launch

private fun ago(at: Long): String {
    val m = (System.currentTimeMillis() - at) / 60_000
    return when { at <= 0 -> ""; m < 1 -> "just now"; m < 60 -> "${m}m ago"; m < 1440 -> "${m / 60}h ago"; else -> "${m / 1440}d ago" }
}

/** What friends are watching, plus lists you share with them and vote on together. */
@Composable
fun FriendsHubScreen(onBack: () -> Unit, onOpenAnime: (Int) -> Unit, onOpenUser: (String) -> Unit, backEnabled: Boolean = true) {
    val context = LocalContext.current
    val app = context.applicationContext as AniBiarApp
    val social = app.social
    val auth by social.authState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var openList by remember { mutableStateOf<SharedList?>(null) }
    BackHandler(enabled = backEnabled) { if (openList != null) openList = null else onBack() }

    Column(Modifier.fillMaxSize().background(Ani.Onyx).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassBack(onClick = { if (openList != null) openList = null else onBack() })
            Spacer(Modifier.width(12.dp))
            Text(openList?.name ?: "Friends", style = inter(20f, FontWeight.SemiBold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (auth == null) {
            Text("Log in to see what friends are watching and share lists.", style = inter(15f), color = Ani.Muted, modifier = Modifier.padding(24.dp))
            return@Column
        }
        val list = openList
        if (list != null) { ListDetail(list, onOpenAnime, onDeleted = { openList = null }); return@Column }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Activity", "Shared lists").forEachIndexed { i, t -> Pill(t, tab == i) { tab = i } }
        }
        Spacer(Modifier.height(8.dp))
        if (tab == 0) ActivityTab(onOpenAnime, onOpenUser) else ListsTab { openList = it }
    }
}

@Composable
private fun Pill(text: String, on: Boolean, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(if (on) Ani.Lavender else Color(0xFF1B1B1F)).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text, style = inter(14f, FontWeight.Medium), color = if (on) Color.Black else Color.White)
    }
}

@Composable
private fun ActivityTab(onOpenAnime: (Int) -> Unit, onOpenUser: (String) -> Unit) {
    val context = LocalContext.current
    val social = (context.applicationContext as AniBiarApp).social
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<FriendActivity>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var share by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        runCatching { items = social.friendActivity() }.onFailure { failed = true }
        runCatching { social.myId?.let { share = social.fetchProfile(it)?.shareActivity ?: true } }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable {
        share = !share
        scope.launch { runCatching { social.setShareActivity(share) }.onFailure { share = !share; Toast.makeText(context, "Couldn't change that", Toast.LENGTH_SHORT).show() } }
    }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Share what I watch with friends", style = inter(14.5f), color = Color.White)
            Text(if (share) "Friends can see your recent anime here" else "Hidden. Friends can't see your activity", style = inter(12.5f), color = Ani.Muted)
        }
        androidx.compose.material3.Switch(share, { share = it; scope.launch { runCatching { social.setShareActivity(it) } } }, colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Ani.Lavender, checkedThumbColor = Color.Black))
    }
    val list = items
    when {
        failed -> Text("Couldn't load. Check your connection.", style = inter(15f), color = Ani.Muted, modifier = Modifier.padding(24.dp))
        list == null -> Text("Loading…", style = inter(15f), color = Ani.Muted, modifier = Modifier.padding(24.dp))
        list.isEmpty() -> Text("Nothing yet. Add friends and their recent anime show up here.", style = inter(15f), color = Ani.Muted, modifier = Modifier.padding(24.dp))
        else -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp)) {
            items(list, key = { it.user.uid + it.mediaId }) { a ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1B1B1F)).clickable { onOpenAnime(a.mediaId) }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PosterImage(a.image, a.title, maxSide = 300, modifier = Modifier.width(48.dp).height(68.dp).clip(RoundedCornerShape(8.dp)).background(Ani.Item))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.user.shownName, style = inter(13f, FontWeight.SemiBold), color = Ani.Lavender, modifier = Modifier.clickable { onOpenUser(a.user.uid) })
                        Text(a.title, style = inter(15f, FontWeight.Medium, lineHeight = 20f), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${if (a.progress >= 0.9f) "Finished" else "Watching"} episode ${a.episode} · ${ago(a.at)}", style = inter(12.5f), color = Ani.Muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun ListsTab(onOpen: (SharedList) -> Unit) {
    val context = LocalContext.current
    val social = (context.applicationContext as AniBiarApp).social
    val scope = rememberCoroutineScope()
    var lists by remember { mutableStateOf<List<SharedList>?>(null) }
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    suspend fun load() { runCatching { lists = social.myLists() }.onFailure { lists = emptyList() } }
    LaunchedEffect(Unit) { load() }
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(14.dp)).background(Ani.Lavender).clickable { creating = true }.padding(14.dp), contentAlignment = Alignment.Center) {
        Text("New shared list", style = inter(15f, FontWeight.SemiBold), color = Color.Black)
    }
    val l = lists
    if (l == null) Text("Loading…", style = inter(15f), color = Ani.Muted, modifier = Modifier.padding(24.dp))
    else if (l.isEmpty()) Text("Make a list with friends, add anime and vote on what to watch next.", style = inter(15f), color = Ani.Muted, modifier = Modifier.padding(24.dp))
    else LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp)) {
        items(l, key = { it.id }) { s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1B1B1F)).clickable { onOpen(s) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(s.name, style = inter(16f, FontWeight.Medium), color = Color.White, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (s.mine) "Yours" else "Shared with you", style = inter(12.5f), color = Ani.Muted)
            }
        }
    }
    if (creating) AlertDialog(
        onDismissRequest = { creating = false }, containerColor = Ani.Panel,
        title = { Text("New shared list", style = inter(18f, FontWeight.SemiBold), color = Color.White) },
        text = {
            BasicTextField(name, { name = it.take(60) }, singleLine = true, textStyle = inter(15f).copy(color = Color.White), cursorBrush = SolidColor(Color.White),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF1C1C1E)).padding(14.dp),
                decorationBox = { inner -> if (name.isEmpty()) Text("List name", style = inter(15f), color = Ani.Muted); inner() })
        },
        confirmButton = {
            Text("Create", style = inter(15f, FontWeight.SemiBold), color = Color.White, modifier = Modifier.clickable {
                if (name.isBlank()) return@clickable
                val n = name; name = ""; creating = false
                scope.launch { runCatching { social.createList(n) }.onFailure { Toast.makeText(context, "Couldn't create the list", Toast.LENGTH_SHORT).show() }; load() }
            }.padding(10.dp))
        },
        dismissButton = { Text("Cancel", style = inter(15f), color = Ani.Muted, modifier = Modifier.clickable { creating = false }.padding(10.dp)) },
    )
}

@Composable
private fun ListDetail(list: SharedList, onOpenAnime: (Int) -> Unit, onDeleted: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as AniBiarApp
    val social = app.social
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<ListItem>?>(null) }
    var query by remember { mutableStateOf("") }
    var inviting by remember { mutableStateOf(false) }
    suspend fun load() { runCatching { items = social.listItems(list.id) }.onFailure { if (items == null) items = emptyList() } }
    LaunchedEffect(list.id) { load() }
    fun toast(t: String) = Toast.makeText(context, t, Toast.LENGTH_SHORT).show()

    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (list.mine) Pill("Invite friends", false) { inviting = true }
        Pill(if (list.mine) "Delete list" else "Leave list", false) {
            scope.launch { runCatching { if (list.mine) social.deleteList(list.id) else social.leaveList(list.id) }; onDeleted() }
        }
    }
    Spacer(Modifier.height(10.dp))
    BasicTextField(
        query, { query = it }, singleLine = true, textStyle = inter(15f).copy(color = Color.White), cursorBrush = SolidColor(Color.White),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF1C1C1E)).padding(horizontal = 16.dp, vertical = 11.dp),
        decorationBox = { inner -> if (query.isEmpty()) Text("Add an anime (search anything you've seen in Kiro)", style = inter(14f), color = Ani.Muted); inner() },
    )
    val found = remember(query) { if (query.length >= 2) app.catalog.search(query, 6) else emptyList() }
    found.forEach { m ->
        Text("+  ${m.displayTitle}", style = inter(14f), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().clickable {
                query = ""
                scope.launch { runCatching { social.addToList(list.id, m.id, m.displayTitle, m.posterUrl) }.onFailure { toast("Couldn't add") }; load() }
            }.padding(horizontal = 20.dp, vertical = 10.dp))
    }
    val l = items
    if (l == null) Text("Loading…", style = inter(15f), color = Ani.Muted, modifier = Modifier.padding(24.dp))
    else if (l.isEmpty()) Text("Empty. Search above to add the first anime.", style = inter(15f), color = Ani.Muted, modifier = Modifier.padding(24.dp))
    else LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp)) {
        items(l, key = { it.mediaId }) { it ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1B1B1F)).clickable { onOpenAnime(it.mediaId) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                PosterImage(it.image, it.title, maxSide = 300, modifier = Modifier.width(48.dp).height(68.dp).clip(RoundedCornerShape(8.dp)).background(Ani.Item))
                Spacer(Modifier.width(12.dp))
                Text(it.title, style = inter(15f, FontWeight.Medium, lineHeight = 20f), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Box(
                    Modifier.clip(RoundedCornerShape(16.dp)).background(if (it.iVoted) Ani.Lavender else Color(0x22FFFFFF)).clickable {
                        scope.launch { runCatching { social.vote(list.id, it.mediaId, !it.iVoted) }.onFailure { toast("Couldn't vote") }; load() }
                    }.padding(horizontal = 12.dp, vertical = 8.dp),
                ) { Text("👍 ${it.votes}", style = inter(13.5f, FontWeight.Medium), color = if (it.iVoted) Color.Black else Color.White) }
            }
        }
    }
    if (inviting) InviteDialog(list, onDone = { inviting = false })
}

@Composable
private fun InviteDialog(list: SharedList, onDone: () -> Unit) {
    val context = LocalContext.current
    val social = (context.applicationContext as AniBiarApp).social
    val scope = rememberCoroutineScope()
    var friends by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var members by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(Unit) {
        runCatching { friends = social.profiles(social.myFriendIds()); members = social.listMembers(list.id) }
    }
    AlertDialog(
        onDismissRequest = onDone, containerColor = Ani.Panel,
        title = { Text("Invite friends", style = inter(18f, FontWeight.SemiBold), color = Color.White) },
        text = {
            if (friends.isEmpty()) Text("Add friends first, then invite them here.", style = inter(14f), color = Ani.Soft)
            else Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                friends.forEach { f ->
                    val inList = f.uid in members
                    Row(Modifier.fillMaxWidth().clickable(enabled = !inList) {
                        scope.launch { runCatching { social.inviteToList(list.id, f.uid); members = members + f.uid }.onFailure { Toast.makeText(context, "Couldn't invite", Toast.LENGTH_SHORT).show() } }
                    }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(f.shownName, style = inter(15f), color = Color.White, modifier = Modifier.weight(1f))
                        Text(if (inList) "Added" else "Invite", style = inter(13f), color = if (inList) Ani.Muted else Ani.Lavender)
                    }
                }
            }
        },
        confirmButton = { Text("Done", style = inter(15f, FontWeight.SemiBold), color = Color.White, modifier = Modifier.clickable(onClick = onDone).padding(10.dp)) },
    )
}
