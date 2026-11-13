package com.anibiar.app.ui.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anibiar.app.ui.theme.Ani
import com.anibiar.app.ui.theme.Appearance
import com.anibiar.app.ui.theme.inter
import com.anibiar.app.ui.theme.rememberTick

/** Shown once on first launch: pick how Kiro looks. Everything here can be changed later in Settings. */
@Composable
fun Onboarding(onDone: () -> Unit) {
    val context = LocalContext.current
    val tick = rememberTick()
    Box(Modifier.fillMaxSize().background(Ani.Onyx).statusBarsPadding().navigationBarsPadding(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 460.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.anibiar.app.R.drawable.art_hero), null, Modifier.size(190.dp))
            Spacer(Modifier.height(8.dp))
            Text("Make Kiro yours", style = inter(26f, FontWeight.ExtraBold), color = Color.White, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text("Pick a color and text size. You can change these any time in Settings.", style = inter(14.5f, lineHeight = 21f), color = Ani.Muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(26.dp))
            Text("Accent color", style = inter(14f, FontWeight.SemiBold), color = Color.White)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Appearance.accents.forEach { (name, c) ->
                    val on = Appearance.accent == c
                    Box(Modifier.size(38.dp).clip(CircleShape).background(c).border(if (on) 3.dp else 1.dp, if (on) Color.White else Color(0x33FFFFFF), CircleShape).clickable { tick(); Appearance.setAccent(context, name) })
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("Text size", style = inter(14f, FontWeight.SemiBold), color = Color.White)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Appearance.textSizes.forEach { (name, v) ->
                    val on = Appearance.textScale == v
                    Box(Modifier.clip(RoundedCornerShape(50)).background(if (on) Color.White else Ani.ButtonDark).clickable { tick(); Appearance.setTextScale(context, v) }.padding(horizontal = 16.dp, vertical = 9.dp)) {
                        Text(name, style = inter(13.5f, FontWeight.SemiBold), color = if (on) Color.Black else Color.White)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Ani.Panel).clickable { tick(); Appearance.setAmoled(context, !Appearance.amoled) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Pure black (AMOLED)", style = inter(15f), color = Color.White, modifier = Modifier.weight(1f))
                Text(if (Appearance.amoled) "On" else "Off", style = inter(14f), color = Ani.Muted)
            }
            Spacer(Modifier.height(30.dp))
            Box(Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(50)).background(Appearance.accent).clickable { Appearance.setOnboarded(context); onDone() }, contentAlignment = Alignment.Center) {
                Text("Get started", style = inter(16f, FontWeight.Bold), color = Color.Black)
            }
        }
    }
}
