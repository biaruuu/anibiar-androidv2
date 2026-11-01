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
import androidx.compose.foundation.layout.widthIn
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
    Profile("Profile", AniIcons.Person),
    Settings("Settings", AniIcons.Settings),
}

/** Floating glass pill: Home / Search / Profile / Settings. The selected tab sits in a lighter glass capsule. */
@Composable
fun BottomNavBar(selected: NavTab, onSelect: (NavTab) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .widthIn(max = 380.dp)
                .height(48.dp)
                .shadow(14.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(shape)
                .background(Color(0xA32B2B2E))
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.02f), Color.White.copy(alpha = 0.06f))))
                .border(
                    1.dp,
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.38f), Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.22f))),
                    shape,
                )
                .padding(3.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavTab.entries.forEach { tab ->
                NavItem(tab, tab == selected, { onSelect(tab) }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun NavItem(tab: NavTab, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val itemShape = RoundedCornerShape(50)
    val tint by animateColorAsState(if (selected) Color.White else Color.White.copy(alpha = 0.72f), tween(200), label = "navTint")
    val fill by animateColorAsState(if (selected) Color.White.copy(alpha = 0.20f) else Color.Transparent, tween(220), label = "navFill")
    val edge by animateColorAsState(if (selected) Color.White.copy(alpha = 0.22f) else Color.Transparent, tween(220), label = "navEdge")
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(itemShape)
            .background(fill)
            .border(1.dp, edge, itemShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(tab.icon, contentDescription = tab.label, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(1.dp))
            Text(tab.label, style = inter(10f, FontWeight.SemiBold), color = tint, maxLines = 1)
        }
    }
}
