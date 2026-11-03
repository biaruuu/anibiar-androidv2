package com.anibiar.app.ui.profile

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
fun ProfileScreen(uid: String?, onBack: (() -> Unit)?, onOpenUser: (String) -> Unit) {
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
    val friends = remember(following, followerIds) { following.filter { it.uid in followerIds } }
    val myFollowing by social.myFollowing.collectAsStateWithLifecycle()
    val myFollowers by social.myFollowers.collectAsStateWithLifecycle()

    var listTab by rememberSaveable(targetUid) { mutableStateOf<String?>(null) }
    var editBio by remember { mutableStateOf(false) }
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
                        Box(Modifier.offset(y = (-36).dp)) {
                            Avatar(profile?.avatarUrl, profile?.username, 88.dp, Modifier.requiredSize(88.dp).border(4.dp, Ani.Onyx, CircleShape))
                            if (isMe) {
                                Box(
                                    Modifier.align(Alignment.BottomEnd).size(28.dp).clip(CircleShape).background(Ani.Lavender)
                                        .border(2.dp, Ani.Onyx, CircleShape)
                                        .clickable { pickAvatar.launch("image/*") },
                                    contentAlignment = Alignment.Center,
                                ) { Icon(AniIcons.Add, null, tint = Color(0xFF121214), modifier = Modifier.size(18.dp)) }
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Row(Modifier.weight(1f).padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Stat("Followers", followerIds.size) { listTab = "followers" }
                            Stat("Following", followingIds.size) { listTab = "following" }
                            Stat("Friends", friends.size) { listTab = "friends" }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(profile?.username ?: "Loading…", style = inter(20f, FontWeight.Bold), color = Color.White)
                    profile?.createdAt?.takeIf { it > 0 }?.let {
                        Spacer(Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(AniIcons.Calendar, null, tint = Ani.Muted, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Member since ${SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(Date(it))}", style = inter(12.5f), color = Ani.Muted)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    val bio = profile?.bio.orEmpty()
                    if (bio.isNotBlank()) Text(bio, style = inter(14f, lineHeight = 20f), color = Ani.Soft)
                    else if (isMe) Text("Add a bio so people know who you are.", style = inter(14f), color = Ani.Muted)
                    Spacer(Modifier.height(14.dp))
                    if (isMe) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionButton("Edit bio", Modifier.weight(1f)) { editBio = true }
                            ActionButton("Log out", Modifier.weight(1f)) { social.signOut() }
                        }
                    } else if (me != null) {
                        FollowButton(targetUid, myFollowing, myFollowers) { scope.launch { toggle(social, targetUid, myFollowing) } }
                    }
                    Spacer(Modifier.height(14.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Ani.Border))
                }
            }
            if (isMe && me?.verified == false) item(key = "verify") {
                VerifyBanner(me?.email.orEmpty())
            }
        }
        listTab?.let { t ->
            FollowListScreen(
                username = profile?.username.orEmpty(), initialTab = t,
                followers = followers, following = following, friends = friends,
                myUid = me?.uid, myFollowing = myFollowing, myFollowers = myFollowers,
                onBack = { listTab = null }, onOpenUser = { onOpenUser(it) },
                onToggle = { scope.launch { toggle(social, it, myFollowing) } },
            )
        }
        if (onBack != null) {
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

    if (editBio) {
        var text by remember { mutableStateOf(profile?.bio.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editBio = false },
            containerColor = Ani.Panel,
            title = { Text("Edit bio", style = inter(18f, FontWeight.SemiBold), color = Color.White) },
            text = {
                Column {
                    Box(Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, Ani.Border, RoundedCornerShape(12.dp)).padding(12.dp)) {
                        BasicTextField(text, { if (it.length <= 200) text = it }, textStyle = inter(14.5f).copy(color = Color.White), cursorBrush = SolidColor(Ani.Lavender), modifier = Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("${text.length}/200", style = inter(12f), color = Ani.Muted)
                }
            },
            confirmButton = {
                Text("Save", style = inter(15f, FontWeight.SemiBold), color = Ani.Lavender,
                    modifier = Modifier.clickable { editBio = false; scope.launch { runCatching { social.setBio(text) }.onFailure { toast("Couldn't save") } } }.padding(10.dp))
            },
            dismissButton = { Text("Cancel", style = inter(15f), color = Ani.Muted, modifier = Modifier.clickable { editBio = false }.padding(10.dp)) },
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
        iFollow && theyFollow -> "Friends"
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
        Avatar(user.avatarUrl, user.username, 44.dp)
        Spacer(Modifier.width(12.dp))
        Text(user.username, style = inter(15f, FontWeight.Medium), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
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


/** Discord-style name card: banner, avatar, name, bio, join date and follow counts. */
@Composable
internal fun UserCard(
    user: UserProfile,
    myUid: String?,
    myFollowing: Set<String>,
    myFollowers: Set<String>,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clip(shape)
            .background(Ani.Panel).border(1.dp, Ani.Border, shape).clickable(onClick = onOpen),
    ) {
        Box(Modifier.fillMaxWidth().height(76.dp)) {
            if (user.bannerUrl != null) AsyncImage(user.bannerUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF2A2140), Color(0xFF1B1B20), Color(0xFF1F2A2E)))))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.Top) {
            Avatar(user.avatarUrl, user.username, 64.dp, Modifier.offset(y = (-26).dp).border(4.dp, Ani.Panel, CircleShape))
            Spacer(Modifier.weight(1f))
            if (myUid != null && user.uid != myUid) {
                Box(Modifier.padding(top = 10.dp)) { FollowButton(user.uid, myFollowing, myFollowers, onToggle) }
            }
        }
        Column(Modifier.padding(horizontal = 14.dp).padding(bottom = 14.dp).offset(y = (-18).dp)) {
            Text(user.username, style = inter(18f, FontWeight.Bold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (user.bio.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(user.bio, style = inter(13.5f, lineHeight = 19f), color = Ani.Soft, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                if (user.createdAt > 0) Text(
                    "Member since ${SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(Date(user.createdAt))}",
                    style = inter(12f), color = Ani.Muted,
                )
                user.followers?.let { Text("$it followers", style = inter(12f, FontWeight.Medium), color = Ani.Muted) }
                user.following?.let { Text("$it following", style = inter(12f, FontWeight.Medium), color = Ani.Muted) }
            }
            if (user.uid in myFollowing && user.uid in myFollowers) {
                Spacer(Modifier.height(8.dp))
                Text("Friends", style = inter(12f, FontWeight.SemiBold), color = Ani.Lime)
            }
        }
    }
}
