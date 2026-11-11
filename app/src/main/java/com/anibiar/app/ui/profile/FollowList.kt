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
    onMessage: (String) -> Unit = {},
) {
    BackHandler(onBack = onBack)
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var query by rememberSaveable { mutableStateOf("") }
    val tabs = listOf("followers" to followers, "following" to following, "friends" to friends)
    val list = tabs.first { it.first == tab }.second.let { l ->
        val q = query.trim()
        if (q.isEmpty()) l else {
            val ranked = com.anibiar.app.nativecore.Native.rank(q, l.map { it.username + " " + (it.displayName ?: "") })
            if (ranked != null) ranked.map { l[it] } else l.filter { it.username.contains(q, ignoreCase = true) }
        }
    }

    Column(Modifier.fillMaxSize().background(Ani.Onyx).statusBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp)) {
            Box(Modifier.align(Alignment.CenterStart)) {
                com.anibiar.app.ui.theme.GlassBack(onBack)
            }
            Text("@$username", style = inter(17f, FontWeight.Bold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.Center).padding(horizontal = 56.dp))
        }
        Row(Modifier.fillMaxWidth()) {
            tabs.forEach { (key, l) ->
                val sel = key == tab
                Column(Modifier.weight(1f).clickable { tab = key }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${l.size} ${key.replaceFirstChar { it.uppercase() }}", style = inter(13.5f, FontWeight.SemiBold),
                        color = if (sel) Color.White else Color(0xFF9B98A0), maxLines = 1, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 13.dp),
                    )
                    Box(Modifier.fillMaxWidth().height(if (sel) 2.dp else 1.dp).background(if (sel) Color.White else Color(0x1AFFFFFF)))
                }
            }
        }
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth().height(40.dp)
                .clip(RoundedCornerShape(50)).background(com.anibiar.app.ui.theme.GlassFill).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AniIcons.Search, null, tint = Ani.Muted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(10.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) Text("Search", style = inter(15f), color = Ani.Muted)
                BasicTextField(
                    query, { query = it }, singleLine = true,
                    textStyle = inter(15f).copy(color = Color.White), cursorBrush = SolidColor(com.anibiar.app.ui.theme.Ani.Lavender),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 100.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
                val isMe = u.uid == myUid
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(com.anibiar.app.ui.theme.GlassFill).clickable { onOpenUser(u.uid) }.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(u.avatarUrl, u.shownName, 54.dp, deco = u.avatarDeco)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(u.shownName, style = inter(15f, FontWeight.Bold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("@${u.username}", style = inter(12.5f), color = Ani.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val sub = when { u.uid in myFriends -> "Friends"; theyFollow && !isMe -> "Follows you"; else -> null }
                        if (sub != null) Text(
                            sub, style = inter(11f, FontWeight.SemiBold), color = com.anibiar.app.ui.theme.Ani.Lavender,
                            modifier = Modifier.padding(top = 5.dp).clip(RoundedCornerShape(50)).background(com.anibiar.app.ui.theme.Ani.Lavender.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                    if (myUid != null && !isMe) {
                        Spacer(Modifier.size(8.dp))
                        val label = when { iFollow -> "Following"; theyFollow -> "Follow back"; else -> "Follow" }
                        Box(
                            Modifier.widthIn(min = 92.dp).height(34.dp).clip(RoundedCornerShape(50))
                                .background(if (iFollow) Color(0xFF232326) else com.anibiar.app.ui.theme.Ani.Lavender)
                                .clickable { onToggle(u.uid) }.padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(label, style = inter(13f, FontWeight.Bold), color = if (iFollow) Color.White else Ani.Onyx, maxLines = 1)
                        }
                        if (iFollow || theyFollow) {
                            Spacer(Modifier.size(6.dp))
                            Box(
                                Modifier.size(34.dp).clip(CircleShape).background(Color(0xFF232326)).clickable { onMessage(u.uid) },
                                contentAlignment = Alignment.Center,
                            ) { Icon(AniIcons.Mail, "Message", tint = Color.White, modifier = Modifier.size(17.dp)) }
                        }
                    }
                }
            }
        }
    }
}
