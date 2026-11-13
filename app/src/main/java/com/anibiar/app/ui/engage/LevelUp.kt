package com.anibiar.app.ui.engage

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anibiar.app.R
import com.anibiar.app.ui.theme.AniButton
import com.anibiar.app.ui.theme.Ani
import com.anibiar.app.ui.theme.inter
import kotlinx.coroutines.delay

class LevelUpRequest(val level: Int, val unlocked: String)

/** Full-screen celebration when the level goes up. Flat artwork from Figma, springs in, closes itself after a few seconds. */
@Composable
fun LevelUpScreen(req: LevelUpRequest, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val pop = remember { Animatable(0.3f) }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(req) {
        fade.animateTo(1f, tween(220))
        pop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
        delay(5500)
        onClose()
    }
    Box(Modifier.fillMaxSize().background(Color(0xE6000000)).pointerInput(Unit) { detectTapGestures { onClose() } }.graphicsLayer { alpha = fade.value }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Box(Modifier.size(220.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }, contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.art_levelup), null, Modifier.fillMaxSize())
                Text("${req.level}", style = inter(54f, FontWeight.ExtraBold), color = Color.White, modifier = Modifier.padding(top = 78.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text("Level ${req.level} reached!", style = inter(26f, FontWeight.ExtraBold), color = Color.White, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(req.unlocked, style = inter(14.5f, lineHeight = 21f), color = Ani.Muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            AniButton("Continue", onClose)
        }
    }
}
