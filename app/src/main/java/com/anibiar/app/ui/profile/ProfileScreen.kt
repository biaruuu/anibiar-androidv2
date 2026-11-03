package com.anibiar.app.ui.profile

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.anibiar.app.AniBiarApp
import com.anibiar.app.social.ImageProc
import com.anibiar.app.social.UserProfile
import com.anibiar.app.ui.theme.Ani
import com.anibiar.app.ui.theme.AniIcons
import com.anibiar.app.ui.theme.inter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Your own profile (uid == null) or someone else's. Everything is live from Realtime Database. */
@Composable
fun ProfileScreen(
    uid: String?, onBack: (() -> Unit)?, onOpenUser: (String) -> Unit,
    onOpenInbox: (() -> Unit)? = null, onOpenSettings: (() -> Unit)? = null, onOpenAnime: (Int) -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as AniBiarApp
    val social = app.social
    val me by social.authState.collectAsStateWithLifecycle()
    val targetUid = uid ?: me?.uid ?: return
    val isMe = targetUid == me?.uid
    val scope = rememberCoroutineScope()

    val profile by remember(targetUid) { social.profile(targetUid) }.collectAsStateWithLifecycle(null)
    val followerIds by remember(targetUid) { social.ids(targetUid, "followers") }.collectAsStateWithLifecycle(emptySet())
    val followingIds by remember(targetUid) { social.ids(targetUid, "following") }.collectAsStateWithLifecycle(emptySet())
    val followers by produceState(emptyList<UserProfile>(), followerIds) { value = social.profiles(followerIds) }
    val following by produceState(emptyList<UserProfile>(), followingIds) { value = social.profiles(followingIds) }
    val friendIds by remember(targetUid) { social.friendIds(targetUid) }.collectAsStateWithLifecycle(emptySet())
    val friends by produceState(emptyList<UserProfile>(), friendIds) { value = social.profiles(friendIds) }
    val myFriends by social.myFriends.collectAsStateWithLifecycle()
    val incoming by social.incomingRequests.collectAsStateWithLifecycle()
    val outgoing by social.outgoingRequests.collectAsStateWithLifecycle()
    val unread by social.unread.collectAsStateWithLifecycle()
    var confirmUnfriend by remember { mutableStateOf(false) }
    val canSeeLists = isMe || when (profile?.listsVisibility) { "me" -> false; "friends" -> (me?.uid ?: "") in friendIds; else -> true }
    val myFollowing by social.myFollowing.collectAsStateWithLifecycle()
    val myFollowers by social.myFollowers.collectAsStateWithLifecycle()

    var listTab by rememberSaveable(targetUid) { mutableStateOf<String?>(null) }
    var editing by rememberSaveable(targetUid) { mutableStateOf(false) }
    var showStatus by remember { mutableStateOf(false) }
    var favPrivacy by remember { mutableStateOf(false) }
    val favs by remember(targetUid) { social.favorites(targetUid) }.collectAsStateWithLifecycle(emptyList())
    val canSeeFavs = isMe || when (profile?.favoritesVisibility) { "me" -> false; "friends" -> (me?.uid ?: "") in friendIds; else -> true }
    val favCols = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.let { if (it >= 900) 6 else if (it >= 600) 4 else 3 }
    var busy by remember { mutableStateOf(false) }

    fun toast(m: String) = Toast.makeText(context, m, Toast.LENGTH_SHORT).show()
    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val full = withContext(Dispatchers.Default) { ImageProc.avatar(context, uri) }
                social.setAvatar(full)
            } catch (e: Exception) { toast(e.message ?: "Couldn't upload") }
            busy = false
        }
    }
    val pickBanner = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val b = withContext(Dispatchers.Default) { ImageProc.banner(context, uri) }
                social.setBanner(b.bytes, b.gif)
            } catch (e: Exception) { toast(e.message ?: "Couldn't upload") }
            busy = false
        }
    }

    Box(Modifier.fillMaxSize().background(Ani.Onyx)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 90.dp),
        ) {
            item(key = "header") {
                Box(Modifier.fillMaxWidth().height(150.dp + WindowInsets.statusBars.asPaddingValues().calculateTopPadding())) {
                    Banner(profile?.bannerUrl, Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x66000000), Color.Transparent, Color(0xFF121214)))))
                    if (isMe) {
                        Chip("Edit banner", Modifier.align(Alignment.BottomEnd).padding(12.dp)) { pickBanner.launch("image/*") }
                    }
                }
                Column(Modifier.padding(horizontal = 16.dp)) {
                    // Instagram-style: avatar overlaps the banner, the counts sit to its right.
                    Row(Modifier.fillMaxWidth().height(60.dp)) {
                        Box(Modifier.offset(y = (-36).dp).requiredSize(88.dp)) {
                            Avatar(profile?.avatarUrl, profile?.shownName, 88.dp, Modifier.requiredSize(88.dp).border(4.dp, Ani.Onyx, CircleShape))
                            StatusDot(
                                profile?.status, 30.dp, Ani.Onyx,
                                Modifier.align(Alignment.BottomEnd).then(if (isMe) Modifier.clip(CircleShape).clickable { showStatus = true } else Modifier),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Row(Modifier.weight(1f).padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            val open = { t: String -> if (canSeeLists) listTab = t else toast("This list is private") }
                            Stat("Followers", followerIds.size) { open("followers") }
                            Stat("Following", followingIds.size) { open("following") }
                            Stat("Friends", friendIds.size) { open("friends") }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(profile?.shownName ?: "Loading…", style = inter(20f, FontWeight.Bold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    profile?.let { Text("@${it.username}", style = inter(13.5f), color = Ani.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    profile?.let { p ->
                        if (!p.statusText.isNullOrBlank() || !p.statusEmoji.isNullOrBlank()) {
                            Spacer(Modifier.height(8.dp))
                            StatusChip(p, isMe) { showStatus = true }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    val bio = profile?.bio.orEmpty()
                    if (bio.isNotBlank()) Text(bio, style = inter(14f, lineHeight = 20f), color = Ani.Soft)
                    Spacer(Modifier.height(14.dp))
                    if (isMe) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionButton("Edit profile", Modifier.weight(1f)) { editing = true }
                            ActionButton("Share profile", Modifier.weight(1f)) {
                                val slug = profile?.linkSlug
                                if (slug.isNullOrBlank()) toast("Set your profile link in Settings first")
                                else context.startActivity(
                                    android.content.Intent.createChooser(
                                        android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                                            .putExtra(android.content.Intent.EXTRA_TEXT, "Add me on Kiro: kiro://u/$slug"),
                                        "Share profile",
                                    ),
                                )
                            }
                        }
                    } else if (me != null) {
                        val iFollow = targetUid in myFollowing
                        val theyFollow = targetUid in myFollowers
                        val friendAction: Pair<String, () -> Unit>? = when {
                            targetUid in myFriends -> "Friends" to { confirmUnfriend = true }
                            targetUid in incoming -> "Accept request" to { scope.launch { runCatching { social.acceptFriend(targetUid) }.onFailure { toast(it.message ?: "Couldn't accept") } } }
                            targetUid in outgoing -> "Request sent" to { scope.launch { runCatching { social.removeFriend(targetUid) } } }
                            iFollow && theyFollow && profile?.friendRequests != "nobody" ->
                                "Add friend" to { scope.launch { runCatching { social.sendFriendRequest(targetUid) }.onFailure { toast(it.message ?: "Couldn't send") } } }
                            else -> null
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ProfileButton(
                                if (iFollow) "Following" else if (theyFollow) "Follow back" else "Follow", !iFollow, Modifier.weight(1f),
                            ) { scope.launch { toggle(social, targetUid, myFollowing) } }
                            friendAction?.let { (label, act) -> ProfileButton(label, label == "Accept request" || label == "Add friend", Modifier.weight(1f), act) }
                        }
                        if (targetUid in incoming) {
                            Spacer(Modifier.height(8.dp))
                            ProfileButton("Decline request", false, Modifier.fillMaxWidth()) { scope.launch { runCatching { social.removeFriend(targetUid) } } }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                }
            }
            item(key = "fav-tabs") {
                Box(Modifier.fillMaxWidth().height(50.dp)) {
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp).background(Ani.Border))
                    Box(Modifier.padding(start = 16.dp).width(64.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Icon(AniIcons.Bookmark, "Favorites", tint = Color.White, modifier = Modifier.size(26.dp))
                        Box(Modifier.align(Alignment.BottomCenter).width(34.dp).height(2.dp).background(Color.White))
                    }
                    if (isMe) Box(
                        Modifier.align(Alignment.CenterEnd).padding(end = 10.dp).size(44.dp).clip(CircleShape).clickable { favPrivacy = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(AniIcons.Lock, "Who can see my favorites", tint = if (profile?.favoritesVisibility == "everyone" || profile == null) Ani.Muted else Color.White, modifier = Modifier.size(22.dp))
                    }
                }
            }
            if (!canSeeFavs) item(key = "fav-locked")                }
            }
            if (!canSeeFavs) item(key = "fav-locked") {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(18.dp)).background(Ani.Panel).border(1.dp, Ani.Border, RoundedCornerShape(18.dp)).padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(AniIcons.Lock, null, tint = Ani.Muted, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(if (profile?.favoritesVisibility == "friends") "Favorites are friends-only" else "Favorites are private", style = inter(15f, FontWeight.SemiBold), color = Color.White)
                }
            } else if (favs.isEmpty()) item(key = "fav-empty") {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(18.dp)).background(Ani.Panel).border(1.dp, Ani.Border, RoundedCornerShape(18.dp)).padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(AniIcons.Bookmark, null, tint = Ani.Muted, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(if (isMe) "No favorites yet" else "Nothing here yet", style = inter(15f, FontWeight.SemiBold), color = Color.White)
                    if (isMe) Text("Tap the bookmark on any anime to save it here.", style = inter(13f, lineHeight = 18f), color = Ani.Muted, modifier = Modifier.padding(top = 4.dp))
                }
            } else items(favs.chunked(favCols), key = { row -> "fav-" + row.first().animeId }) { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    row.forEach { f ->
                        Box(Modifier.weight(1f).aspectRatio(3f / 4f).background(Ani.Item).clickable { onOpenAnime(f.animeId) }) {
                            if (f.cover != null) AsyncImage(f.cover, f.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))) {
                                Text(f.title, style = inter(11.5f, FontWeight.SemiBold, lineHeight = 14f), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 7.dp, end = 7.dp, top = 18.dp, bottom = 7.dp))
                            }
                        }
                    }
                    repeat(favCols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        listTab?.let { t ->
            FollowListScreen(
                username = profile?.username.orEmpty(), initialTab = t,
                followers = followers, following = following, friends = friends,
                myUid = me?.uid, myFollowing = myFollowing, myFollowers = myFollowers, myFriends = myFriends,
                onBack = { listTab = null }, onOpenUser = { onOpenUser(it) },
                onToggle = { scope.launch { toggle(social, it, myFollowing) } },
            )
        }
        if (editing) EditProfileScreen(profile) { editing = false }
        if (isMe && listTab == null && !editing && (onOpenInbox != null || onOpenSettings != null)) {
            Row(
                Modifier.align(Alignment.TopEnd).padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp, end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                onOpenInbox?.let { open ->
                    Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0x99000000)).clickable(onClick = open), contentAlignment = Alignment.Center) {
                        Icon(AniIcons.Bell, "Inbox", tint = Color.White, modifier = Modifier.size(21.dp))
                        if (unread > 0) Box(
                            Modifier.align(Alignment.TopEnd).padding(top = 3.dp, end = 3.dp).size(if (unread > 9) 18.dp else 16.dp).clip(CircleShape).background(Color(0xFFFF4D5E)),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (unread > 9) "9+" else "$unread", style = inter(9.5f, FontWeight.Bold), color = Color.White) }
                    }
                }
                onOpenSettings?.let { open ->
                    Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0x99000000)).clickable(onClick = open), contentAlignment = Alignment.Center) {
                        Icon(AniIcons.Settings, "Settings", tint = Color.White, modifier = Modifier.size(21.dp))
                    }
                }
            }
        }
        if (onBack != null && listTab == null && !editing) {
            Box(
                Modifier.padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp, start = 12.dp)
                    .size(38.dp).clip(CircleShape).background(Color(0x99000000)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(AniIcons.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(22.dp)) }
        }
        if (busy) {
            Text("Uploading…", style = inter(13f, FontWeight.Medium), color = Color.White,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 14.dp)
                    .clip(RoundedCornerShape(50)).background(Color(0xCC000000)).padding(horizontal = 14.dp, vertical = 6.dp))
        }
    }

    if (showStatus) StatusSheet(profile) { showStatus = false }
    if (favPrivacy) com.anibiar.app.ui.settings.ChoiceDialog(
        "Who can see my favorites", profile?.favoritesVisibility ?: "everyone",
        listOf("everyone" to "Everyone", "friends" to "Friends", "me" to "Only me"), onDismiss = { favPrivacy = false },
    ) { v -> scope.launch { runCatching { social.setFavoritesVisibility(v) }.onFailure { toast(it.message ?: "Couldn't save") } } }
    if (confirmUnfriend) {
        AlertDialog(
            onDismissRequest = { confirmUnfriend = false },
            containerColor = Ani.Panel,
            title = { Text("Remove friend?", style = inter(18f, FontWeight.SemiBold), color = Color.White) },
            text = { Text("${profile?.username.orEmpty()} will no longer be your friend. You'll still follow each other.", style = inter(14f, lineHeight = 20f), color = Ani.Soft) },
            confirmButton = {
                Text("Remove", style = inter(15f, FontWeight.SemiBold), color = Color(0xFFFF8A80),
                    modifier = Modifier.clickable { confirmUnfriend = false; scope.launch { runCatching { social.removeFriend(targetUid) } } }.padding(10.dp))
            },
            dismissButton = { Text("Cancel", style = inter(15f), color = Ani.Muted, modifier = Modifier.clickable { confirmUnfriend = false }.padding(10.dp)) },
        )
    }

    // First login: accounts start with a placeholder name; ask for a real one.
    val needsName = isMe && profile?.username?.matches(Regex("^user[0-9a-f]{8}$")) == true
    if (needsName) {
        var name by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }
        var saving by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = {},
            containerColor = Ani.Panel,
            title = { Text("Choose your username", style = inter(18f, FontWeight.SemiBold), color = Color.White) },
            text = {
                Column {
                    Text("This is how friends find you. 3-20 letters, numbers, _ or .", style = inter(13.5f, lineHeight = 19f), color = Ani.Muted)
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, Ani.Border, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                        if (name.isEmpty()) Text("username", style = inter(15f), color = Ani.Muted)
                        BasicTextField(name, { name = it.filter { c -> c.isLetterOrDigit() || c == '_' || c == '.' }.take(20) }, singleLine = true, textStyle = inter(15f).copy(color = Color.White), cursorBrush = SolidColor(Ani.Lavender), modifier = Modifier.fillMaxWidth())
                    }
                    err?.let { Spacer(Modifier.height(6.dp)); Text(it, style = inter(12.5f), color = Color(0xFFFF8A80)) }
                }
            },
            confirmButton = {
                Text(if (saving) "Saving…" else "Continue", style = inter(15f, FontWeight.SemiBold), color = if (name.length >= 3) Ani.Lavender else Ani.Muted,
                    modifier = Modifier.clickable(enabled = name.length >= 3 && !saving) {
                        saving = true; err = null
                        scope.launch { try { social.setUsername(name) } catch (e: Exception) { err = e.message }; saving = false }
                    }.padding(10.dp))
            },
        )
    }
}

private suspend fun toggle(social: com.anibiar.app.social.SocialRepository, uid: String, myFollowing: Set<String>) {
    runCatching { if (uid in myFollowing) social.unfollow(uid) else social.follow(uid) }
}

@Composable
private fun Banner(url: String?, modifier: Modifier) {
    if (url == null) {
        Box(modifier.background(Brush.linearGradient(listOf(Color(0xFF2A2140), Color(0xFF15151A), Color(0xFF1F2A2E)))))
    } else {
        AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = modifier)
    }
}

@Composable
fun Avatar(url: String?, name: String?, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(Ani.Item), contentAlignment = Alignment.Center) {
        if (url != null) {
            AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text((name?.firstOrNull() ?: '?').uppercase(), style = inter((size.value * 0.42f), FontWeight.Bold), color = Ani.Lavender)
        }
    }
}

@Composable
private fun Chip(label: String, modifier: Modifier, onClick: () -> Unit) {
    Text(label, style = inter(12.5f, FontWeight.Medium), color = Color.White,
        modifier = modifier.clip(RoundedCornerShape(50)).background(Color(0x99000000)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp))
}

@Composable
private fun OutlineChip(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(label, style = inter(13f, FontWeight.Medium), color = Ani.Soft,
        modifier = Modifier.clip(shape).border(1.dp, Ani.Border, shape).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp))
}

@Composable
private fun Stat(label: String, count: Int, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$count", style = inter(18f, FontWeight.Bold), color = Color.White)
        Text(label, style = inter(13f), color = Color.White)
    }
}

@Composable
private fun ProfileButton(label: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(36.dp).clip(RoundedCornerShape(10.dp)).background(if (primary) Color.White else Ani.ButtonDark).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = inter(13.5f, FontWeight.Medium), color = if (primary) Color.Black else Color.White, maxLines = 1) }
}

@Composable
private fun ActionButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(36.dp).clip(RoundedCornerShape(10.dp)).background(Ani.ButtonDark).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = inter(13.5f, FontWeight.Medium), color = Color.White) }
}

/** Follow / Follow back / Following / Friends, from the viewer's perspective. */
@Composable
internal fun FollowButton(uid: String, myFollowing: Set<String>, myFollowers: Set<String>, onClick: () -> Unit) {
    val iFollow = uid in myFollowing
    val theyFollow = uid in myFollowers
    val label = when {
        iFollow -> "Following"
        theyFollow -> "Follow back"
        else -> "Follow"
    }
    val filled = !iFollow
    val shape = RoundedCornerShape(50)
    Text(
        label, style = inter(13.5f, FontWeight.SemiBold), color = if (filled) Color(0xFF121214) else Color.White,
        modifier = Modifier.clip(shape).then(if (filled) Modifier.background(Ani.Lavender) else Modifier.border(1.dp, Ani.Border, shape))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
internal fun UserRow(
    user: UserProfile,
    myUid: String?,
    myFollowing: Set<String>,
    myFollowers: Set<String>,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(user.avatarUrl, user.shownName, 44.dp)
        Spacer(Modifier.width(12.dp))
        Text(user.shownName, style = inter(15f, FontWeight.Medium), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (myUid != null && user.uid != myUid) FollowButton(user.uid, myFollowing, myFollowers, onToggle)
    }
}


@Composable
private fun VerifyBanner(email: String) {
    val app = LocalContext.current.applicationContext as AniBiarApp
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    // Notice the tapped link by itself.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(5000)
            if (app.social.refreshVerification()) break
        }
    }
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(shape)
            .background(Color(0x1FFEA500)).border(1.dp, Color(0x66FEA500), shape).padding(14.dp),
    ) {
        Text("Verify your email", style = inter(15f, FontWeight.SemiBold), color = Ani.Amber)
        Spacer(Modifier.height(4.dp))
        Text(
            "We sent a link to $email. Check your inbox and spam folder, then tap it.",
            style = inter(13f, lineHeight = 18f), color = Ani.Soft,
        )
        msg?.let { Spacer(Modifier.height(6.dp)); Text(it, style = inter(12.5f), color = Ani.Lavender) }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlineChip(if (busy) "Sending…" else "Resend email") {
                if (!busy) scope.launch {
                    busy = true
                    msg = try { app.social.resendVerification(); "Sent. It can take a minute to arrive." } catch (e: Exception) { e.message }
                    busy = false
                }
            }
            OutlineChip("I've verified") {
                scope.launch { msg = if (app.social.refreshVerification()) "Verified!" else "Not verified yet." }
            }
        }
    }
}


/** Name card: banner, avatar ring, display name + @username, bio, stats and relationship chips. */
@Composable
internal fun UserCard(
    user: UserProfile,
    myUid: String?,
    myFollowing: Set<String>,
    myFollowers: Set<String>,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    myFriends: Set<String> = emptySet(),
) {
    val shape = RoundedCornerShape(20.dp)
    val isOther = myUid != null && user.uid != myUid
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clip(shape)
            .background(Ani.Panel).border(1.dp, Ani.Border, shape).clickable(onClick = onOpen),
    ) {
        Box(Modifier.fillMaxWidth().height(92.dp)) {
            if (user.bannerUrl != null) AsyncImage(user.bannerUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF2A2140), Color(0xFF1B1B20), Color(0xFF1F2A2E)))))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x66151517)))))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.requiredSize(72.dp).offset(y = (-34).dp)) {
                Avatar(user.avatarUrl, user.shownName, 72.dp, Modifier.requiredSize(72.dp).border(4.dp, Ani.Panel, CircleShape))
                StatusDot(user.status, 24.dp, Ani.Panel, Modifier.align(Alignment.BottomEnd))
            }
            Spacer(Modifier.weight(1f))
            if (isOther) Box(Modifier.padding(top = 10.dp)) { FollowButton(user.uid, myFollowing, myFollowers, onToggle) }
        }
        Column(Modifier.padding(horizontal = 14.dp).padding(bottom = 14.dp).offset(y = (-24).dp)) {
            Text(user.shownName, style = inter(18.5f, FontWeight.Bold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("@${user.username}", style = inter(13f), color = Ani.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (user.bio.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(user.bio, style = inter(13.5f, lineHeight = 19f), color = Ani.Soft, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                user.followers?.let { Text(buildAnnotatedString { withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) { append("$it") }; append(" followers") }, style = inter(12.5f), color = Ani.Muted) }
                user.following?.let { Text(buildAnnotatedString { withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) { append("$it") }; append(" following") }, style = inter(12.5f), color = Ani.Muted) }
            }
            val isFriend = user.uid in myFriends
            val followsYou = isOther && user.uid in myFollowers
            if (isFriend || followsYou) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isFriend) Text("Friends", style = inter(11.5f, FontWeight.SemiBold), color = Ani.Lime,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0x22AAFF01)).padding(horizontal = 10.dp, vertical = 4.dp))
                    if (followsYou) Text("Follows you", style = inter(11.5f, FontWeight.SemiBold), color = Ani.Soft,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Ani.ButtonDark).padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
        }
    }
}
