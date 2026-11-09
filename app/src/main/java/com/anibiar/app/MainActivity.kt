package com.anibiar.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.lifecycleScope
import com.anibiar.app.AniBiarApp
import kotlinx.coroutines.launch
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.anibiar.app.ui.details.DetailsScreen
import com.anibiar.app.ui.home.HomeScreen
import com.anibiar.app.ui.theme.Ani
import com.anibiar.app.ui.theme.AniBiarTheme

class MainActivity : ComponentActivity() {
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (com.anibiar.app.data.Pip.canEnter && android.os.Build.VERSION.SDK_INT >= 26) {
            runCatching { enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().setAspectRatio(android.util.Rational(16, 9)).build()) }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        com.anibiar.app.data.Pip.active.value = isInPictureInPictureMode
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        handleLink(intent)
        setContent {
            AniBiarTheme {
                var splash by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(true) }
                Box(Modifier.fillMaxSize()) {
                    AppNavigation()
                    var crash by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(CrashLog.take(this@MainActivity)) }
                    crash?.let { report ->
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { crash = null },
                            title = { androidx.compose.material3.Text("Kiro closed unexpectedly") },
                            text = { androidx.compose.material3.Text(report.take(900), fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp)) },
                            confirmButton = { androidx.compose.material3.TextButton(onClick = {
                                val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                cm.setPrimaryClip(android.content.ClipData.newPlainText("crash", report)); crash = null
                            }) { androidx.compose.material3.Text("Copy report") } },
                            dismissButton = { androidx.compose.material3.TextButton(onClick = { crash = null }) { androidx.compose.material3.Text("Close") } },
                        )
                    }
                    if (splash) com.anibiar.app.ui.splash.SplashScreen(onFinished = { splash = false })
                }
            }
        }

    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLink(intent)
    }

    /** `kiro://auth#access_token=...` from the confirmation email: sign in and land on the profile. */
    private fun handleLink(intent: android.content.Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "kiro") return
        val app = application as AniBiarApp
        if (uri.host == "u") {
            val slug = uri.pathSegments.firstOrNull() ?: return
            lifecycleScope.launch {
                if (!app.social.openLink(slug)) android.widget.Toast.makeText(this@MainActivity, "Log in to open this profile", android.widget.Toast.LENGTH_LONG).show()
            }
            return
        }
        lifecycleScope.launch {
            val error = app.social.handleAuthLink(uri)
            android.widget.Toast.makeText(
                this@MainActivity,
                error ?: "Email confirmed. You're signed in!",
                android.widget.Toast.LENGTH_LONG,
            ).show()
        }
    }
}

/** A tiny back stack: empty = Home, otherwise the top entry is the anime whose Details page is open. */
@Composable
private fun AppNavigation() {
    val stack: SnapshotStateList<Int> = rememberSaveable(
        saver = listSaver<SnapshotStateList<Int>, Int>(save = { it.toList() }, restore = { saved -> mutableStateListOf<Int>().apply { addAll(saved) } }),
    ) { mutableStateListOf() }

    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var profileOpen by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var inboxOpen by rememberSaveable { mutableStateOf(false) }
    var inboxTab by rememberSaveable { mutableStateOf(false) }
    var chatUid by rememberSaveable { mutableStateOf<String?>(null) }
    var searchMode by rememberSaveable { mutableStateOf("anime") }
    val userStack = rememberSaveable(
        saver = androidx.compose.runtime.saveable.listSaver<SnapshotStateList<String>, String>(
            save = { it.toList() },
            restore = { mutableStateListOf<String>().apply { addAll(it) } },
        ),
    ) { mutableStateListOf<String>() }
    val context0 = androidx.compose.ui.platform.LocalContext.current
    val social = (context0.applicationContext as AniBiarApp).social
    val roomSession = (context0.applicationContext as AniBiarApp).roomSession
    val authState by social.authState.collectAsStateWithLifecycle()
    val linkLogins by social.linkLogins.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(linkLogins) {
        if (linkLogins > 0) { searchOpen = false; userStack.clear(); profileOpen = true }
    }
    // A profile opened from a kiro://u/<link> link.
    val pendingUser by social.pendingUser.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(pendingUser) {
        pendingUser?.let { searchOpen = false; settingsOpen = false; userStack.add(it); social.consumePendingUser() }
    }
    BackHandler(enabled = inboxTab && chatUid == null && userStack.isEmpty() && !inboxOpen) { inboxTab = false }
    BackHandler(enabled = settingsOpen) { settingsOpen = false }
    BackHandler(enabled = profileOpen) { profileOpen = false }
    BackHandler(enabled = inboxOpen) { inboxOpen = false }
    BackHandler(enabled = searchOpen) { searchOpen = false }
    BackHandler(enabled = userStack.isNotEmpty()) { userStack.removeAt(userStack.lastIndex) }
    BackHandler(enabled = stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) }
    var update by androidx.compose.runtime.remember { mutableStateOf<com.anibiar.app.update.UpdateInfo?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) { update = com.anibiar.app.update.Updater.check(context0) }
    // After an update: show what changed once.
    var whatsNew by androidx.compose.runtime.remember { mutableStateOf<List<com.anibiar.app.update.ChangeEntry>>(emptyList()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val all = com.anibiar.app.update.Changelog.bundled(context0)
        val seen = com.anibiar.app.update.Changelog.lastSeen(context0)
        val fresh = all.filter { it.id > seen }.take(4)
        if (fresh.isNotEmpty()) whatsNew = fresh
    }
    if (whatsNew.isNotEmpty()) com.anibiar.app.update.WhatsNewDialog(whatsNew, "What's new in this update") {
        com.anibiar.app.update.Changelog.markSeen(context0, whatsNew.maxOf { it.id }); whatsNew = emptyList()
    }
    update?.let { u -> com.anibiar.app.update.UpdateDialog(u, onDismiss = { update = null }) }
    var watchId by rememberSaveable { mutableStateOf(0) }
    var watchEp by rememberSaveable { mutableStateOf(1) }
    var joinRoom by androidx.compose.runtime.remember { mutableStateOf<com.anibiar.app.social.Room?>(null) }
    BackHandler(enabled = watchId != 0) { watchId = 0 }
    // The host moved the room to another anime: follow if the watch page is open.
    val liveRoom = roomSession.room
    var prevRoomAnime by androidx.compose.runtime.remember { mutableStateOf<Int?>(null) }
    androidx.compose.runtime.LaunchedEffect(liveRoom?.animeId) {
        val r = liveRoom; val prev = prevRoomAnime
        prevRoomAnime = r?.animeId
        if (r != null && prev != null && prev != r.animeId && watchId == prev) { joinRoom = r; watchEp = roomSession.together?.episode ?: r.episode; watchId = r.animeId }
    }
    val toastCtx = androidx.compose.ui.platform.LocalContext.current
    val mainScope = androidx.compose.runtime.rememberCoroutineScope()
    var banner by androidx.compose.runtime.remember { mutableStateOf<Pair<com.anibiar.app.social.Room, String>?>(null) }
    fun doJoin(roomId: String) {
        mainScope.launch {
            val r = runCatching { social.fetchRoom(roomId) }.getOrNull()
            if (r == null || r.ended) {
                android.widget.Toast.makeText(toastCtx, if (r == null) "Couldn't open that room" else "That room has ended", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                banner = null; inboxOpen = false; searchOpen = false; profileOpen = false; settingsOpen = false; userStack.clear(); stack.clear()
                joinRoom = r; watchEp = r.episode; watchId = r.animeId
            }
        }
    }
    // Joining a call needs the microphone allowed first (you can mute once you're in).
    var pendingRoom by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }
    val micAsk = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok ->
        val rid = pendingRoom; pendingRoom = null
        if (ok && rid != null) doJoin(rid) else if (rid != null) android.widget.Toast.makeText(toastCtx, "Allow the microphone to join the call. You can mute yourself once you're in.", android.widget.Toast.LENGTH_LONG).show()
    }
    fun joinRoomById(roomId: String) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(toastCtx, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) doJoin(roomId)
        else { pendingRoom = roomId; micAsk.launch(android.Manifest.permission.RECORD_AUDIO) }
    }
    var bannerTick by androidx.compose.runtime.remember { mutableStateOf(0) }
    // Invites arrive live over realtime; each one is checked against the database before it is shown.
    androidx.compose.runtime.LaunchedEffect(authState?.uid) {
        social.inviteStream().collect { b ->
            val rid = b.payload["room"]?.jsonPrimitive?.contentOrNull ?: return@collect
            val r = runCatching { social.fetchRoom(rid) }.getOrNull() ?: return@collect
            if (r.ended || roomSession.room?.id == r.id || r.hostId == social.myId) return@collect
            val who = runCatching { social.profiles(listOf(r.hostId)).firstOrNull()?.shownName }.getOrNull() ?: "A friend"
            banner = r to who; bannerTick++
        }
    }
    androidx.compose.runtime.LaunchedEffect(banner, bannerTick) { if (banner != null) { kotlinx.coroutines.delay(20_000); banner = null } }

    // Keep Home composed underneath so its scroll position survives opening a page.
    Box(Modifier.fillMaxSize().background(Ani.Onyx)) {
        HomeScreen(onOpen = { id -> stack.add(id) }, onSearch = { profileOpen = false; settingsOpen = false; inboxTab = false; searchOpen = true }, onWatch = { id -> watchEp = (context0.applicationContext as AniBiarApp).history.resumeEpisode(id); watchId = id }, onContinue = { id -> watchEp = (context0.applicationContext as AniBiarApp).history.resumeEpisode(id); watchId = id })

        if (searchOpen) {
            Box(Modifier.fillMaxSize().background(Ani.Onyx)) {
                com.anibiar.app.ui.search.SearchScreen(
                    onOpen = { id -> stack.add(id) },
                    onBack = { searchOpen = false },
                    mode = searchMode,
                    onModeChange = { searchMode = it },
                    onOpenUser = { userStack.add(it) },
                )
            }
        }

        if (inboxTab && authState != null) {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                com.anibiar.app.ui.inbox.InboxTab(
                    onBack = { inboxTab = false },
                    onOpenActivity = { inboxOpen = true },
                    onOpenChat = { chatUid = it },
                    onOpenUser = { userStack.add(it) },
                )
            }
        }
        if (settingsOpen) {
            Box(Modifier.fillMaxSize().background(Ani.Onyx)) {
                com.anibiar.app.ui.settings.SettingsScreen(
                    onLogin = { settingsOpen = false; profileOpen = true },
                    onOpenInbox = { inboxOpen = true },
                    onOpenProfile = { settingsOpen = false; profileOpen = true },
                    onBack = { settingsOpen = false },
                )
            }
        }

        if (profileOpen) {
            Box(Modifier.fillMaxSize().background(Ani.Onyx)) {
                val a = authState
                if (a != null) {
                    com.anibiar.app.ui.profile.ProfileScreen(
                        uid = null, onBack = { profileOpen = false }, onOpenUser = { userStack.add(it) }, onOpenAnime = { stack.add(it) },
                        onOpenInbox = { inboxOpen = true },
                        onOpenSettings = { profileOpen = false; settingsOpen = true },
                    )
                } else {
                    com.anibiar.app.ui.auth.AuthScreen(onClose = { profileOpen = false })
                }
            }
        }

        if (inboxOpen && authState != null) {
            Box(Modifier.fillMaxSize().background(Ani.Onyx)) {
                com.anibiar.app.ui.inbox.InboxScreen(onBack = { inboxOpen = false }, onOpenUser = { userStack.add(it) }, onJoinRoom = { joinRoomById(it) })
            }
        }

        userStack.lastOrNull()?.let { uid ->
            androidx.compose.runtime.key(userStack.size, uid) {
                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF0B0B0D))) {
                    com.anibiar.app.ui.profile.ProfileScreen(
                        uid = uid,
                        onBack = { if (userStack.isNotEmpty()) userStack.removeAt(userStack.lastIndex) },
                        onOpenUser = { userStack.add(it) }, onOpenAnime = { stack.add(it) },
                        onMessage = { chatUid = it },
                    )
                }
            }
        }

        chatUid?.let { cu ->
            androidx.compose.runtime.key(cu) {
                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF0B0B0D))) {
                    com.anibiar.app.ui.inbox.ChatScreen(
                        uid = cu, onBack = { chatUid = null },
                        onOpenUser = { chatUid = null; userStack.add(it) },
                    )
                }
            }
        }

        AnimatedContent(
            targetState = stack.lastOrNull(),
            transitionSpec = {
                if (targetState != null) {
                    (slideInHorizontally { it / 6 } + fadeIn()) togetherWith fadeOut()
                } else {
                    fadeIn() togetherWith (slideOutHorizontally { it / 6 } + fadeOut())
                }
            },
            label = "details",
        ) { id ->
            if (id != null) {
                // Opaque + swallow touches so Home underneath can't be scrolled through the page.
                Box(Modifier.fillMaxSize().background(Ani.Onyx)) {
                    androidx.compose.runtime.key(stack.size, id) {
                        DetailsScreen(
                            id = id,
                            onBack = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) },
                            onOpen = { next -> stack.add(next) },
                            onHome = { stack.clear() },
                            onWatch = { aid, n -> watchEp = n; watchId = aid },
                        )
                    }
                    com.anibiar.app.ui.theme.GlassBack(
                        onClick = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) },
                        modifier = Modifier.statusBarsPadding().padding(start = 12.dp, top = 8.dp),
                    )
                }
            }
        }

        if (watchId != 0) {
            androidx.compose.runtime.key(watchId, joinRoom?.id) {
                Box(Modifier.fillMaxSize().background(Ani.Onyx)) {
                    com.anibiar.app.ui.watch.WatchScreen(
                        id = watchId, startEp = watchEp, onBack = { watchId = 0 }, joinRoom = joinRoom,
                        onRoomMoved = { r -> joinRoom = r; watchEp = r.episode; watchId = r.animeId },
                        onOpenDetails = { aid -> watchId = 0; if (stack.lastOrNull() != aid) stack.add(aid) },
                        onWatch = { aid -> joinRoom = null; watchEp = (context0.applicationContext as AniBiarApp).history.resumeEpisode(aid); watchId = aid },
                    )
                }
            }
        }

        // In a call: a bar stays on screen so you can browse; tap it to return to the room.
        if (liveRoom != null && !(watchId != 0 && watchId == liveRoom.animeId)) {
            com.anibiar.app.ui.watch.CallBar(
                roomSession, social.myId,
                onOpen = { joinRoom = liveRoom; watchEp = roomSession.together?.episode ?: liveRoom.episode; searchOpen = false; profileOpen = false; settingsOpen = false; inboxOpen = false; userStack.clear(); stack.clear(); watchId = liveRoom.animeId },
                modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter).statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        // Live "join my room" invite
        banner?.let { (r, who) ->
            val edge = androidx.compose.foundation.shape.RoundedCornerShape(22.dp)
            Row(
                Modifier.align(androidx.compose.ui.Alignment.TopCenter).statusBarsPadding().padding(12.dp).fillMaxWidth()
                    .clip(edge).background(androidx.compose.ui.graphics.Color(0xFF1B1B1F)).border(1.dp, androidx.compose.ui.graphics.Color(0x40B7A9FF), edge).padding(12.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                if (r.cover != null) coil.compose.AsyncImage(
                    r.cover, null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.width(48.dp).height(66.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).background(androidx.compose.ui.graphics.Color(0xFF1A1A1C)),
                )
                androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
                androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                    androidx.compose.material3.Text("WATCH TOGETHER", style = com.anibiar.app.ui.theme.inter(10.5f, androidx.compose.ui.text.font.FontWeight.Bold), color = androidx.compose.ui.graphics.Color(0xFFB7A9FF))
                    androidx.compose.material3.Text("$who invited you", style = com.anibiar.app.ui.theme.inter(15f, androidx.compose.ui.text.font.FontWeight.Bold), color = androidx.compose.ui.graphics.Color.White, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    androidx.compose.material3.Text("${r.title} · Episode ${r.episode}", style = com.anibiar.app.ui.theme.inter(12.5f), color = Ani.Muted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.Text(
                            "Join", style = com.anibiar.app.ui.theme.inter(13.5f, androidx.compose.ui.text.font.FontWeight.Bold), color = androidx.compose.ui.graphics.Color.Black,
                            modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(androidx.compose.ui.graphics.Color(0xFFB7A9FF)).clickable { joinRoomById(r.id) }.padding(horizontal = 22.dp, vertical = 8.dp),
                        )
                        androidx.compose.material3.Text(
                            "Not now", style = com.anibiar.app.ui.theme.inter(13.5f, androidx.compose.ui.text.font.FontWeight.SemiBold), color = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(androidx.compose.ui.graphics.Color(0xFF232326)).clickable { banner = null }.padding(horizontal = 18.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }

        val dmUnread by social.dmUnread.collectAsStateWithLifecycle()
        val noticeUnread by social.unread.collectAsStateWithLifecycle()
        // Floating glass tab bar, only on the Home level (a details page slides over it).
        androidx.compose.animation.AnimatedVisibility(
            visible = stack.isEmpty() && !inboxOpen && !inboxTab && !profileOpen && !settingsOpen && userStack.isEmpty() && chatUid == null && watchId == 0 && !com.anibiar.app.ui.theme.NavState.hidden,
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
            enter = androidx.compose.animation.slideInVertically { it } + fadeIn(),
            exit = androidx.compose.animation.slideOutVertically { it } + fadeOut(),
        ) {
            val context = androidx.compose.ui.platform.LocalContext.current
            com.anibiar.app.ui.nav.BottomNavBar(
                selected = when {
                    profileOpen -> com.anibiar.app.ui.nav.NavTab.Profile
                    settingsOpen -> com.anibiar.app.ui.nav.NavTab.Settings
                    inboxTab -> com.anibiar.app.ui.nav.NavTab.Inbox
                    searchOpen -> com.anibiar.app.ui.nav.NavTab.Search
                    else -> com.anibiar.app.ui.nav.NavTab.Home
                },
                onSelect = { tab ->
                    inboxOpen = false; chatUid = null; userStack.clear()
                    searchOpen = false; profileOpen = false; settingsOpen = false; inboxTab = false
                    when (tab) {
                        com.anibiar.app.ui.nav.NavTab.Home -> {}
                        com.anibiar.app.ui.nav.NavTab.Search -> searchOpen = true
                        com.anibiar.app.ui.nav.NavTab.Inbox -> if (authState != null) inboxTab = true else profileOpen = true
                        com.anibiar.app.ui.nav.NavTab.Profile -> profileOpen = true
                        com.anibiar.app.ui.nav.NavTab.Settings -> settingsOpen = true
                    }
                },
                inboxBadge = dmUnread + noticeUnread,
                modifier = Modifier,
            )
        }
    }
}

private fun Modifier.androidx_navPadding(): Modifier = this
    .navigationBarsPadding()
    .padding(bottom = 10.dp)
