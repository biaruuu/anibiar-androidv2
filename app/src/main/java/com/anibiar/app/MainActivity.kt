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
    var searchMode by rememberSaveable { mutableStateOf("anime") }
    val userStack = rememberSaveable(
        saver = androidx.compose.runtime.saveable.listSaver<SnapshotStateList<String>, String>(
            save = { it.toList() },
            restore = { mutableStateListOf<String>().apply { addAll(it) } },
        ),
    ) { mutableStateListOf<String>() }
    val context0 = androidx.compose.ui.platform.LocalContext.current
    val social = (context0.applicationContext as AniBiarApp).social
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
    BackHandler(enabled = settingsOpen) { settingsOpen = false }
    BackHandler(enabled = profileOpen) { profileOpen = false }
    BackHandler(enabled = inboxOpen) { inboxOpen = false }
    BackHandler(enabled = searchOpen) { searchOpen = false }
    BackHandler(enabled = userStack.isNotEmpty()) { userStack.removeAt(userStack.lastIndex) }
    BackHandler(enabled = stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) }
    var update by androidx.compose.runtime.remember { mutableStateOf<com.anibiar.app.update.UpdateInfo?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) { update = com.anibiar.app.update.Updater.check() }
    update?.let { u -> com.anibiar.app.update.UpdateDialog(u, onDismiss = { update = null }) }
    var watchId by rememberSaveable { mutableStateOf(0) }
    var watchEp by rememberSaveable { mutableStateOf(1) }
    var joinRoom by androidx.compose.runtime.remember { mutableStateOf<com.anibiar.app.social.Room?>(null) }
    BackHandler(enabled = watchId != 0) { watchId = 0; joinRoom = null }
    val toastCtx = androidx.compose.ui.platform.LocalContext.current
    val mainScope = androidx.compose.runtime.rememberCoroutineScope()
    var banner by androidx.compose.runtime.remember { mutableStateOf<Pair<com.anibiar.app.social.Room, String>?>(null) }
    fun joinRoomById(roomId: String) {
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
    // Invites arrive live over realtime; each one is checked against the database before it is shown.
    androidx.compose.runtime.LaunchedEffect(authState?.uid) {
        social.inviteStream().collect { b ->
            val rid = b.payload["room"]?.jsonPrimitive?.contentOrNull ?: return@collect
            val r = runCatching { social.fetchRoom(rid) }.getOrNull() ?: return@collect
            if (r.ended || joinRoom?.id == r.id || r.hostId == social.myId) return@collect
            val who = runCatching { social.profiles(listOf(r.hostId)).firstOrNull()?.shownName }.getOrNull() ?: "A friend"
            banner = r to who
        }
    }
    androidx.compose.runtime.LaunchedEffect(banner) { if (banner != null) { kotlinx.coroutines.delay(20_000); banner = null } }

    // Keep Home composed underneath so its scroll position survives opening a page.
    Box(Modifier.fillMaxSize().background(Ani.Onyx)) {
        HomeScreen(onOpen = { id -> stack.add(id) }, onSearch = { profileOpen = false; settingsOpen = false; searchOpen = true }, onWatch = { id -> watchEp = 1; watchId = id })

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

        if (settingsOpen) {
            Box(Modifier.fillMaxSize().background(Ani.Onyx)) {
                com.anibiar.app.ui.settings.SettingsScreen(
                    onLogin = { settingsOpen = false; profileOpen = true },
                    onOpenInbox = { inboxOpen = true },
                    onOpenProfile = { settingsOpen = false; profileOpen = true },
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
                Box(Modifier.fillMaxSize().background(Ani.Onyx)) {
                    com.anibiar.app.ui.profile.ProfileScreen(
                        uid = uid,
                        onBack = { if (userStack.isNotEmpty()) userStack.removeAt(userStack.lastIndex) },
                        onOpenUser = { userStack.add(it) }, onOpenAnime = { stack.add(it) },
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
                }
            }
        }

        if (watchId != 0) {
            androidx.compose.runtime.key(watchId, joinRoom?.id) {
                Box(Modifier.fillMaxSize().background(Ani.Onyx)) {
                    com.anibiar.app.ui.watch.WatchScreen(
                        id = watchId, startEp = watchEp, onBack = { watchId = 0; joinRoom = null }, joinRoom = joinRoom,
                        onRoomMoved = { r -> joinRoom = r; watchEp = r.episode; watchId = r.animeId },
                        onOpenDetails = { aid -> watchId = 0; if (stack.lastOrNull() != aid) stack.add(aid) },
                    )
                }
            }
        }

        // Live "join my room" invite
        banner?.let { (r, who) ->
            Row(
                Modifier.align(androidx.compose.ui.Alignment.TopCenter).statusBarsPadding().padding(12.dp).fillMaxWidth()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp)).background(Ani.Panel)
                    .border(1.dp, Ani.Lime.copy(alpha = 0.5f), androidx.compose.foundation.shape.RoundedCornerShape(18.dp)).padding(14.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                    androidx.compose.material3.Text("$who invited you to watch together", style = com.anibiar.app.ui.theme.inter(14f, androidx.compose.ui.text.font.FontWeight.SemiBold), color = androidx.compose.ui.graphics.Color.White, maxLines = 2)
                    androidx.compose.material3.Text("${r.title} · Episode ${r.episode}", style = com.anibiar.app.ui.theme.inter(12.5f), color = Ani.Muted, maxLines = 1)
                }
                androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
                androidx.compose.material3.Text("Dismiss", style = com.anibiar.app.ui.theme.inter(13f), color = Ani.Muted, modifier = Modifier.clickable { banner = null }.padding(8.dp))
                androidx.compose.material3.Text(
                    "Join", style = com.anibiar.app.ui.theme.inter(13.5f, androidx.compose.ui.text.font.FontWeight.Bold), color = androidx.compose.ui.graphics.Color.Black,
                    modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(Ani.Lime).clickable { joinRoomById(r.id) }.padding(horizontal = 18.dp, vertical = 9.dp),
                )
            }
        }

        // Floating glass tab bar, only on the Home level (a details page slides over it).
        androidx.compose.animation.AnimatedVisibility(
            visible = stack.isEmpty() && !profileOpen && !inboxOpen && userStack.isEmpty() && watchId == 0,
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
            enter = androidx.compose.animation.slideInVertically { it } + fadeIn(),
            exit = androidx.compose.animation.slideOutVertically { it } + fadeOut(),
        ) {
            val context = androidx.compose.ui.platform.LocalContext.current
            com.anibiar.app.ui.nav.BottomNavBar(
                selected = when {
                    profileOpen -> com.anibiar.app.ui.nav.NavTab.Profile
                    settingsOpen -> com.anibiar.app.ui.nav.NavTab.Settings
                    searchOpen -> com.anibiar.app.ui.nav.NavTab.Search
                    else -> com.anibiar.app.ui.nav.NavTab.Home
                },
                onSelect = { tab ->
                    if (tab == com.anibiar.app.ui.nav.NavTab.Home) { searchOpen = false; profileOpen = false; settingsOpen = false; inboxOpen = false; userStack.clear() }
                    else if (tab == com.anibiar.app.ui.nav.NavTab.Search) { profileOpen = false; settingsOpen = false; inboxOpen = false; userStack.clear(); searchOpen = true }
                    else if (tab == com.anibiar.app.ui.nav.NavTab.Profile) { searchOpen = false; settingsOpen = false; userStack.clear(); profileOpen = true }
                    else { searchOpen = false; profileOpen = false; inboxOpen = false; userStack.clear(); settingsOpen = true }
                },
                modifier = Modifier
                    .androidx_navPadding(),
            )
        }
    }
}

private fun Modifier.androidx_navPadding(): Modifier = this
    .navigationBarsPadding()
    .padding(bottom = 10.dp)
