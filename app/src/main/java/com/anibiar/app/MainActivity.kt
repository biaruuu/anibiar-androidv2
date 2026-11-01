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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
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
}

/** A tiny back stack: empty = Home, otherwise the top entry is the anime whose Details page is open. */
@Composable
private fun AppNavigation() {
    val stack: SnapshotStateList<Int> = rememberSaveable(
        saver = listSaver<SnapshotStateList<Int>, Int>(save = { it.toList() }, restore = { saved -> mutableStateListOf<Int>().apply { addAll(saved) } }),
    ) { mutableStateListOf() }

    BackHandler(enabled = stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) }

    // Keep Home composed underneath so its scroll position survives opening a page.
    Box(Modifier.fillMaxSize().background(Ani.Onyx)) {
        HomeScreen(onOpen = { id -> stack.add(id) })

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
                selected = com.anibiar.app.ui.nav.NavTab.Home,
                onSelect = { tab ->
                    if (tab != com.anibiar.app.ui.nav.NavTab.Home) {
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
    .padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
