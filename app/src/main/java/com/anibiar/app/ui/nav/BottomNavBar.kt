package com.anibiar.app.ui.nav

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.anibiar.app.ui.theme.AniIcons
import com.anibiar.app.ui.theme.inter
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

enum class NavTab(val label: String, val icon: ImageVector) {
    Home("Home", com.anibiar.app.ui.theme.FeatherIcons.Home),
    Search("Search", com.anibiar.app.ui.theme.FeatherIcons.Search),
    Schedule("Schedule", com.anibiar.app.ui.theme.FeatherIcons.Calendar),
    Inbox("Inbox", com.anibiar.app.ui.theme.FeatherIcons.Message),
    Profile("Profile", com.anibiar.app.ui.theme.FeatherIcons.User),
    Settings("Settings", com.anibiar.app.ui.theme.FeatherIcons.Settings),
}

/** Flat full-width tab bar: near-black strip with a hairline on top, small icons and tiny labels (CornCastle style). */
@Composable
fun BottomNavBar(selected: NavTab, onSelect: (NavTab) -> Unit, modifier: Modifier = Modifier, inboxBadge: Int = 0) {
    val tabs = NavTab.entries
    val tick = com.anibiar.app.ui.theme.rememberTick()
    Column(modifier.fillMaxWidth().background(Color(0xFF08080C)).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEach { tab ->
                NavItem(tab, tab == selected, Modifier.weight(1f).clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { if (tab != selected) tick(); onSelect(tab) }, if (tab == NavTab.Inbox) inboxBadge else 0)
            }
        }
    }
}

@Composable
private fun NavItem(tab: NavTab, lit: Boolean, modifier: Modifier, badge: Int) {
    val tint by animateColorAsState(if (lit) Color.White else Color.White.copy(alpha = 0.45f), tween(140), label = "navTint")
    Column(
        modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box {
            Icon(tab.icon, contentDescription = tab.label, tint = tint, modifier = Modifier.size(19.dp))
            if (badge > 0) Box(
                Modifier.align(Alignment.TopEnd).offset(x = 7.dp, y = (-3).dp).clip(RoundedCornerShape(50)).background(Color(0xFFFF4D6A)).padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) { Text(if (badge > 99) "99+" else "$badge", style = inter(9f, FontWeight.Bold), color = Color.White, maxLines = 1) }
        }
        Spacer(Modifier.height(2.dp))
        Text(tab.label, style = inter(9f, if (lit) FontWeight.Bold else FontWeight.Medium), color = tint, maxLines = 1)
    }
}
