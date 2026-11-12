package com.anibiar.app.ui.auth

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anibiar.app.ui.theme.Ani
import com.anibiar.app.ui.theme.AniIcons
import com.anibiar.app.ui.theme.inter
import kotlin.math.PI
import kotlin.math.sin

private const val PREFS = "app_prefs"
fun isGuest(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("guest", false)
fun setGuest(c: Context, on: Boolean) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("guest", on).apply()

/** A four-point star with concave sides, drawn as vector path. */
private fun star(cx: Float, cy: Float, r: Float): Path = Path().apply {
    val k = r * 0.22f
    moveTo(cx, cy - r)
    quadraticBezierTo(cx + k, cy - k, cx + r, cy)
    quadraticBezierTo(cx + k, cy + k, cx, cy + r)
    quadraticBezierTo(cx - k, cy + k, cx - r, cy)
    quadraticBezierTo(cx - k, cy - k, cx, cy - r)
    close()
}

/**
 * First screen after onboarding: log in or carry on as a guest. Pure vector art (twinkling stars), no photos.
 * [onGuest] is called when the person picks guest; sign-in is handled by the email-link screen.
 */
@Composable
fun WelcomeGate(onGuest: () -> Unit) {
    var signIn by rememberSaveable { mutableStateOf(false) }
    if (signIn) { AuthScreen(onClose = { signIn = false }); return }
    val t = rememberInfiniteTransition(label = "twinkle")
    val phase by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart), label = "p")
    val accent = Ani.Lavender
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF10131F), Color(0xFF1A1030), Color(0xFF0A0A0C))))) {
        Canvas(Modifier.fillMaxSize()) {
            val rnd = java.util.Random(7)
            repeat(26) { i ->
                val x = rnd.nextFloat() * size.width; val y = rnd.nextFloat() * size.height * 0.78f
                val r = (6f + rnd.nextFloat() * 14f) * (size.width / 1080f * 2.2f)
                val a = 0.25f + 0.55f * (0.5f + 0.5f * sin(phase + i * 1.7f))
                drawPath(star(x, y, r), (if (i % 3 == 0) accent else Color(0xFFFFD60A)).copy(alpha = a))
            }
            // soft glow behind the emblem
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.28f), Color.Transparent), Offset(size.width / 2, size.height * 0.3f), size.width * 0.6f), size.width * 0.6f, Offset(size.width / 2, size.height * 0.3f))
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(104.dp).clip(CircleShape).background(accent).border(4.dp, Color(0x33FFFFFF), CircleShape), contentAlignment = Alignment.Center) {
                Icon(AniIcons.Play, null, tint = Color.White, modifier = Modifier.size(52.dp))
            }
            Spacer(Modifier.weight(1.1f))
            Text("Welcome to Kiro", style = inter(30f, FontWeight.ExtraBold), color = Color.White, textAlign = TextAlign.Center)
            Text("Log in to sync your list, history and friends across devices. Or just look around as a guest.", style = inter(15f, lineHeight = 21f), color = Ani.Soft, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp, bottom = 28.dp).widthIn(max = 380.dp))
            Column(Modifier.widthIn(max = 460.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Log in or create account", style = inter(16f, FontWeight.Bold), color = Color.Black, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Color.White).clickable { signIn = true }.padding(vertical = 16.dp))
                Text("Continue as guest", style = inter(16f, FontWeight.SemiBold), color = Color.White, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(50)).clickable(onClick = onGuest).padding(vertical = 16.dp))
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}
