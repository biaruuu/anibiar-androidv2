package com.anibiar.app.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anibiar.app.R

/** Design tokens from the Figma "Kiro Design System" page. Flat colors only, no gradients. */
object Dim {
    val s4 = 4.dp; val s8 = 8.dp; val s12 = 12.dp; val s16 = 16.dp; val s24 = 24.dp; val s32 = 32.dp
    val r8 = RoundedCornerShape(8.dp); val r12 = RoundedCornerShape(12.dp); val r16 = RoundedCornerShape(16.dp)
    val r22 = RoundedCornerShape(22.dp); val pill = RoundedCornerShape(50)
}

/** Flat artwork drawn in Figma, shared by empty and error screens. */
object Art {
    val Favorites = R.drawable.art_empty_favorites
    val Friends = R.drawable.art_empty_friends
    val History = R.drawable.art_empty_history
    val Lists = R.drawable.art_empty_lists
    val Search = R.drawable.art_empty_search
    val Offline = R.drawable.art_offline
    val Error = R.drawable.art_error
    val NotFound = R.drawable.art_notfound
}

/** Small spring "press" on anything tappable. */
fun Modifier.pressScale(to: Float = 0.96f): Modifier = composed {
    var down by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (down) to else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "press")
    this.scale(s).pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            down = true
            waitForUpOrCancellation()
            down = false
        }
    }
}

/** Illustrated empty or error state with an optional action. */
@Composable
fun ArtState(art: Int, title: String, hint: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Column(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(art), null, Modifier.size(width = 220.dp, height = 165.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, style = inter(17f, FontWeight.SemiBold), color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(hint, style = inter(14f, lineHeight = 20f), color = Ani.Muted, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(18.dp))
            AniButton(action, onAction)
        }
    }
}

/** Primary pill button: white fill, or accent fill when [accent] is true. */
@Composable
fun AniButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, accent: Boolean = false, secondary: Boolean = false) {
    val bg = if (secondary) Ani.ButtonDark else if (accent) Ani.Lavender else Color.White
    val fg = if (secondary || accent) Color.White else Color.Black
    Box(modifier.pressScale().clip(Dim.pill).background(bg).clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 11.dp), contentAlignment = Alignment.Center) {
        Text(label, style = inter(14f, FontWeight.SemiBold), color = fg)
    }
}

/** Flat chip. */
@Composable
fun AniChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.pressScale(0.94f).clip(Dim.pill).background(if (selected) Color.White else Ani.Chip).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp)) {
        Text(label, style = inter(13f, FontWeight.SemiBold), color = if (selected) Color.Black else Color.White)
    }
}

/** Flat card surface. */
@Composable
fun AniCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val m = modifier.clip(Dim.r16).background(Ani.Panel)
    Box(if (onClick != null) m.pressScale(0.98f).clickable(onClick = onClick) else m) { content() }
}

/** Flat cover templates for highlights (drawn in Figma). */
val COVER_TEMPLATES = listOf("sakura", "waves", "peaks", "stars", "city", "stripes")
fun coverRes(tpl: String?): Int? = when (tpl) {
    "sakura" -> R.drawable.art_cover_sakura
    "waves" -> R.drawable.art_cover_waves
    "peaks" -> R.drawable.art_cover_peaks
    "stars" -> R.drawable.art_cover_stars
    "city" -> R.drawable.art_cover_city
    "stripes" -> R.drawable.art_cover_stripes
    else -> null
}
