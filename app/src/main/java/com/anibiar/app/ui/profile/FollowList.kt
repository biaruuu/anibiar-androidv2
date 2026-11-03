package com.anibiar.app.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anibiar.app.social.UserProfile
import com.anibiar.app.ui.theme.Ani
import com.anibiar.app.ui.theme.AniIcons
import com.anibiar.app.ui.theme.inter

/** Full-screen followers / following / friends list, laid out like Instagram's. */
@Composable
internal fun FollowListScreen(
    username: String,
    initialTab: String,
    followers: List<UserProfile>,
    following: List<UserProfile>,
    friends: List<UserProfile>,
    myUid: String?,
    myFollowing: Set<String>,
    myFollowers: Set<String>,
    myFriends: Set<String>,
    onBack: () -> Unit,
    onOpenUser: (String) -> Unit,
    onToggle: (String) -> Unit,
) {
    BackHandler(onBack = onBack)
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var query by rememberSaveable { mutableStateOf("") }
    val tabs = listOf("followers" to followers, "following" to following, "friends" to friends)
    val list = tabs.first { it.first == tab }.second.let { l ->
        val q = query.trim()
        if (q.isEmpty()) l else l.filter { it.username.contains(q, ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize().background(Ani.Onyx).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                AniIcons.ArrowBack, "Back", tint = Color.White,
                modifier = Modifier.padding(start = 8.dp).size(44.dp).clip(CircleShape).clickable(onClick = onBack).padding(10.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(username, style = inter(20f, FontWeight.Bold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // Tabs: white underline under the selected one, hairline under the row.
        Row(Modifier.fillMaxWidth()) {
            tabs.forEach { (key, l) ->
                val sel = key == tab
                Column(
                    Modifier.weight(1f).clickable { tab = key },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "${l.size} $key", style = inter(14f, FontWeight.SemiBold),
                        color = if (sel) Color.White else Ani.Muted, maxLines = 1, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 13.dp),
                    )
                    Box(Modifier.fillMaxWidth().height(if (sel) 2.dp else 1.dp).background(if (sel) Color.White else Ani.Border))
                }
            }
        }
        // Search
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth().height(40.dp)
                .clip(RoundedCornerShape(10.dp)).background(Ani.Item).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AniIcons.Search, null, tint = Ani.Muted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(10.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) Text("Search", style = inter(15f), color = Ani.Muted)
                BasicTextField(
                    query, { query = it }, singleLine = true,
                    textStyle = inter(15f).copy(color = Color.White), cursorBrush = SolidColor(Ani.Lavender),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 100.dp),
        ) {
            if (list.isEmpty()) item {
                Text(
                    when {
                        query.isNotBlank() -> "No results for \"${query.trim()}\"."
                        tab == "following" -> "Not following anyone yet."
                        tab == "friends" -> "No friends yet. Follow back to become friends."
                        else -> "No followers yet."
                    },
                    style = inter(14f), color = Ani.Muted, modifier = Modifier.padding(16.dp),
                )
            }
            items(list, key = { "$tab-${it.uid}" }) { u ->
                val iFollow = u.uid in myFollowing
                val theyFollow = u.uid in myFollowers
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenUser(u.uid) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(u.avatarUrl, u.username, 52.dp)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(u.username, style = inter(15f, FontWeight.SemiBold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val sub = when { u.uid in myFriends -> "Friends"; theyFollow -> "Follows you"; else -> null }
                        if (sub != null) Text(sub, style = inter(13f), color = Ani.Muted, maxLines = 1)
                    }
                    if (myUid != null && u.uid != myUid) {
                        Spacer(Modifier.size(8.dp))
                        val label = when { iFollow -> "Following"; theyFollow -> "Follow back"; else -> "Follow" }
                        Box(
                            Modifier.widthIn(min = 104.dp).height(34.dp).clip(RoundedCornerShape(10.dp))
                                .background(if (iFollow) Ani.ButtonDark else Color.White)
                                .clickable { onToggle(u.uid) }.padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(label, style = inter(13.5f, FontWeight.SemiBold), color = if (iFollow) Color.White else Color.Black, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
