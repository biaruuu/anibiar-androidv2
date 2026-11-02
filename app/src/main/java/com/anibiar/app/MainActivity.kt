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
    BackHandler(enabled = profileOpen) { profileOpen = false }
    BackHandler(enabled = searchOpen) { searchOpen = false }
    BackHandler(enabled = userStack.isNotEmpty()) { userStack.removeAt(userStack.lastIndex) }
    BackHandler(enabled = stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) }

    // Keep Home composed underneath so its scroll position survives opening a page.
    Box(Modifier.fillMaxSize().background(Ani.Onyx)) {
        HomeScreen(onOpen = { id -> stack.add(id) }, onSearch = { profileOpen = false; searchOpen = true })

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

        if (profileOpen) {
            Box(Modifier.fillMaxSize().background(Ani.Onyx)) {
                val a = authState
                if (a != null) {
                    com.anibiar.app.ui.profile.ProfileScreen(uid = null, onBack = null, onOpenUser = { userStack.add(it) })
                } else {
                    com.anibiar.app.ui.auth.AuthScreen(onClose = { profileOpen = false })
                }
            }
        }

        userStack.lastOrNull()?.let { uid ->
            androidx.compose.runtime.key(userStack.size, uid) {
                Box(Modifier.fillMaxSize().background(Ani.Onyx)) {
                    com.anibiar.app.ui.profile.ProfileScreen(
                        uid = uid,
                        onBack = { if (userStack.isNotEmpty()) userStack.removeAt(userStack.lastIndex) },
                        onOpenUser = { userStack.add(it) },
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
                        )
                    }
                }
            }
        }

        // Floating glass tab bar, only on the Home level (a details page slides over it).
        androidx.compose.animation.AnimatedVisibility(
            visible = stack.isEmpty(),
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
            enter = androidx.compose.animation.slideInVertically { it } + fadeIn(),
            exit = androidx.compose.animation.slideOutVertically { it } + fadeOut(),
        ) {
            val context = androidx.compose.ui.platform.LocalContext.current
            com.anibiar.app.ui.nav.BottomNavBar(
                selected = when {
                    profileOpen -> com.anibiar.app.ui.nav.NavTab.Profile
                    searchOpen -> com.anibiar.app.ui.nav.NavTab.Search
                    else -> com.anibiar.app.ui.nav.NavTab.Home
                },
                onSelect = { tab ->
                    if (tab == com.anibiar.app.ui.nav.NavTab.Home) { searchOpen = false; profileOpen = false; userStack.clear() }
                    else if (tab == com.anibiar.app.ui.nav.NavTab.Search) { profileOpen = false; userStack.clear(); searchOpen = true }
                    else if (tab == com.anibiar.app.ui.nav.NavTab.Profile) { searchOpen = false; userStack.clear(); profileOpen = true }
                    else {
                        android.widget.Toast.makeText(context, "${tab.label} is coming soon", android.widget.Toast.LENGTH_SHORT).show()
                    }
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
