package com.anibiar.app.ui.search

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.KeyboardActions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anibiar.app.AniBiarApp
import com.anibiar.app.social.UserProfile
import com.anibiar.app.ui.profile.UserCard
import com.anibiar.app.ui.theme.Ani
import com.anibiar.app.ui.theme.AniIcons
import com.anibiar.app.ui.theme.inter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** "Anime ▾ / Users ▾" switch shown beside the Search heading. */
@Composable
internal fun ModeDropdown(mode: String, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val turn by animateFloatAsState(if (open) 180f else 0f, tween(250), label = "modeTurn")
    Box {
        Row(
            Modifier.height(40.dp).clip(shape).border(1.dp, Ani.Border, shape).clickable { open = true }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (mode == "users") "Users" else "Anime", style = inter(14f, FontWeight.Medium), color = Color.White)
            Spacer(Modifier.width(6.dp))
            Icon(AniIcons.ArrowDown, null, tint = Ani.Muted, modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = turn })
        }
        DropdownMenu(open, { open = false }, modifier = Modifier.background(Ani.Panel)) {
            listOf("anime" to "Anime", "users" to "Users").forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label, style = inter(15f), color = if (value == mode) Ani.Lavender else Color.White) },
                    onClick = { onChange(value); open = false },
                )
            }
        }
    }
}

@Composable
internal fun UserSearchScreen(onBack: () -> Unit, onModeChange: (String) -> Unit, onOpenUser: (String) -> Unit) {
    val app = LocalContext.current.applicationContext as AniBiarApp
    val social = app.social
    val me by social.authState.collectAsStateWithLifecycle()
    val myFollowing by social.myFollowing.collectAsStateWithLifecycle()
    val myFollowers by social.myFollowers.collectAsStateWithLifecycle()
    val myFriends by social.myFriends.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(query, me?.uid) {
        error = null
        if (query.isBlank()) { results = emptyList(); loading = false; return@LaunchedEffect }
        if (me == null) return@LaunchedEffect
        loading = true
        delay(350)
        try { results = social.search(query) } catch (e: Exception) { error = "Couldn't search. Check your connection." }
        loading = false
    }

    val shape = RoundedCornerShape(12.dp)
    LazyColumn(
        Modifier.fillMaxSize().background(Ani.Onyx),
        contentPadding = PaddingValues(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp,
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 90.dp,
        ),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    AniIcons.ArrowBack, "Back", tint = Ani.Soft,
                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(50)).clickable(onClick = onBack).padding(4.dp),
                )
                Spacer(Modifier.width(12.dp))
                Row(Modifier.weight(1f).height(40.dp).clip(shape).border(1.dp, Ani.Border, shape), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) Text("Search users by username…", style = inter(14f), color = Ani.Muted)
                        BasicTextField(
                            query, { query = it.trimStart() }, singleLine = true,
                            textStyle = inter(14f).copy(color = Color.White), cursorBrush = SolidColor(Ani.Lavender),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = {}),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) { Icon(AniIcons.Search, null, tint = Ani.Muted, modifier = Modifier.size(20.dp)) }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Search", style = inter(24f, FontWeight.Bold), color = Color.White)
                    Spacer(Modifier.height(2.dp))
                    Text("Find people on Kiro by their username.", style = inter(13f, lineHeight = 18f), color = Ani.Muted)
                }
                ModeDropdown("users", onModeChange)
            }
            Spacer(Modifier.height(16.dp))
        }
        when {
            me == null -> item { Hint("Log in from the Profile tab to find and follow people.") }
            query.isBlank() -> item { Hint("Type a username to start.") }
            error != null -> item { Hint(error.orEmpty()) }
            loading && results.isEmpty() -> item { Hint("Searching…") }
            results.isEmpty() -> item { Hint("No users found.") }
            else -> items(results, key = { it.uid }) { u ->
                UserCard(
                    u, me?.uid, myFollowing, myFollowers,
                    onOpen = { onOpenUser(u.uid) },
                    onToggle = { scope.launch { runCatching { if (u.uid in myFollowing) social.unfollow(u.uid) else social.follow(u.uid) } } },
                    myFriends = myFriends,
                )
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = inter(15f), color = Ani.Muted, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 32.dp))
}
