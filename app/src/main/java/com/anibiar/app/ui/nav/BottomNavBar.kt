package com.anibiar.app.ui.nav

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anibiar.app.ui.theme.Ani
import com.anibiar.app.ui.theme.AniIcons
import com.anibiar.app.ui.theme.inter

enum class NavTab(val label: String, val icon: ImageVector) {
    Home("Home", AniIcons.Home),
    Search("Search", AniIcons.Search),
    Inbox("Inbox", AniIcons.Mail),
    Profile("Profile", AniIcons.Person),
    Settings("Settings", AniIcons.Settings),
}

/** Flat gray tab bar, icon over label: white when selected, light gray otherwise, so it reads on a dark page. */
@Composable
fun BottomNavBar(selected: NavTab, onSelect: (NavTab) -> Unit, modifier: Modifier = Modifier, inboxBadge: Int = 0) {
    Column(modifier.fillMaxWidth().background(Color(0xFF1C1C1E)).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x1AFFFFFF)))
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            NavTab.entries.forEach { tab -> NavItem(tab, tab == selected, { onSelect(tab) }, Modifier.weight(1f), if (tab == NavTab.Inbox) inboxBadge else 0) }
        }
    }
}

@Composable
private fun NavItem(tab: NavTab, selected: Boolean, onClick: () -> Unit, modifier: Modifier, badge: Int = 0) {
    val tint by animateColorAsState(if (selected) Color.White else Color(0xFFA0A0A8), tween(160), label = "navTint")
    Column(
        modifier.fillMaxHeight().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box {
            Icon(tab.icon, contentDescription = tab.label, tint = tint, modifier = Modifier.size(25.dp))
            if (badge > 0) Box(
                Modifier.align(Alignment.TopEnd).padding(start = 16.dp).clip(RoundedCornerShape(50)).background(Color(0xFFFF4D6A)).padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) { Text(if (badge > 99) "99+" else "$badge", style = inter(9.5f, FontWeight.Bold), color = Color.White, maxLines = 1) }
        }
        Spacer(Modifier.height(2.dp))
        Text(tab.label, style = inter(10.5f, if (selected) FontWeight.Bold else FontWeight.Medium), color = tint, maxLines = 1)
    }
}
