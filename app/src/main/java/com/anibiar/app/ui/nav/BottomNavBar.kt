package com.anibiar.app.ui.nav

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
    Home("Home", AniIcons.Home),
    Search("Search", AniIcons.Search),
    Inbox("Inbox", AniIcons.Chat),
    Profile("Profile", AniIcons.Person),
    Settings("Settings", AniIcons.Settings),
}

/**
 * Compact floating liquid-glass pill. A glass lens rests on the selected tab; press and slide a finger
 * across the bar and the lens follows it, magnifying the icons it passes over. Lift to pick that tab.
 */
@Composable
fun BottomNavBar(selected: NavTab, onSelect: (NavTab) -> Unit, modifier: Modifier = Modifier, inboxBadge: Int = 0) {
    val tabs = NavTab.entries
    val shape = RoundedCornerShape(30.dp)
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val pad = with(density) { 5.dp.toPx() }
    var widthPx by remember { mutableStateOf(0f) }
    var down by remember { mutableStateOf(false) }
    val lens = remember { Animatable(0f) }
    val itemW = if (widthPx > 0f) (widthPx - 2 * pad) / tabs.size else 0f
    fun centerOf(i: Int) = pad + itemW * (i + 0.5f)

    LaunchedEffect(selected, itemW, down) {
        if (!down && itemW > 0f) lens.animateTo(centerOf(selected.ordinal), spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium))
    }

    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(start = 28.dp, end = 28.dp, bottom = 8.dp, top = 4.dp), contentAlignment = Alignment.BottomCenter) {
        Box(
            Modifier.widthIn(max = 360.dp).fillMaxWidth().height(52.dp)
                .shadow(14.dp, shape, ambientColor = Color.Black, spotColor = Color.Black).clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xCC2E2E34), Color(0xE61A1A1E))))
                .background(Brush.verticalGradient(listOf(Color(0x22FFFFFF), Color.Transparent)))
                .border(1.dp, Brush.verticalGradient(listOf(Color(0x66FFFFFF), Color(0x14FFFFFF), Color(0x33FFFFFF))), shape)
                .onSizeChanged { widthPx = it.width.toFloat() }
                .pointerInput(itemW) {
                    if (itemW <= 0f) return@pointerInput
                    awaitEachGesture {
                        val first = awaitFirstDown()
                        down = true
                        var x = first.position.x
                        scope.launch { lens.snapTo(x.coerceIn(centerOf(0), centerOf(tabs.size - 1))) }
                        do {
                            val ev = awaitPointerEvent()
                            val c = ev.changes.firstOrNull() ?: break
                            x = c.position.x
                            scope.launch { lens.snapTo(x.coerceIn(centerOf(0), centerOf(tabs.size - 1))) }
                            c.consume()
                        } while (ev.changes.any { it.pressed })
                        down = false
                        onSelect(tabs[((x - pad) / itemW).toInt().coerceIn(0, tabs.size - 1)])
                    }
                },
        ) {
            val lensShape = RoundedCornerShape(26.dp)
            val lensW = with(density) { (itemW * 1.02f).toDp() }
            Box(
                Modifier.offset { IntOffset((lens.value - itemW * 0.51f).roundToInt(), 0) }.width(lensW).fillMaxHeight().padding(vertical = 4.dp)
                    .graphicsLayer { val k = if (down) 1.16f else 1f; scaleX = k; scaleY = k }
                    .clip(lensShape)
                    .background(Brush.verticalGradient(if (down) listOf(Color(0x59FFFFFF), Color(0x26FFFFFF)) else listOf(Color(0x40FFFFFF), Color(0x1FFFFFFF))))
                    .border(1.dp, Brush.verticalGradient(listOf(Color(if (down) 0xCCFFFFFF else 0x66FFFFFF), Color(0x22FFFFFF))), lensShape),
            )
            Row(Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                tabs.forEachIndexed { i, tab ->
                    val mag = if (down && itemW > 0f) 1f + 0.42f * (1f - (abs(lens.value - centerOf(i)) / (itemW * 1.1f)).coerceIn(0f, 1f)) else 1f
                    NavItem(tab, tab == selected || (down && mag > 1.2f), Modifier.weight(1f), if (tab == NavTab.Inbox) inboxBadge else 0, mag)
                }
            }
        }
    }
}

@Composable
private fun NavItem(tab: NavTab, lit: Boolean, modifier: Modifier, badge: Int, mag: Float) {
    val tint by animateColorAsState(if (lit) Color.White else Color(0xFFC4C4CC), tween(140), label = "navTint")
    Column(
        modifier.fillMaxHeight().graphicsLayer { scaleX = mag; scaleY = mag },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box {
            Icon(tab.icon, contentDescription = tab.label, tint = tint, modifier = Modifier.size(21.dp))
            if (badge > 0) Box(
                Modifier.align(Alignment.TopEnd).offset(x = 7.dp, y = (-3).dp).clip(RoundedCornerShape(50)).background(Color(0xFFFF4D6A)).padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) { Text(if (badge > 99) "99+" else "$badge", style = inter(9f, FontWeight.Bold), color = Color.White, maxLines = 1) }
        }
        Spacer(Modifier.height(1.dp))
        Text(tab.label, style = inter(9.5f, if (lit) FontWeight.Bold else FontWeight.Medium), color = tint, maxLines = 1)
    }
}
