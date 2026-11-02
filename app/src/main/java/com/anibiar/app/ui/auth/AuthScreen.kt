package com.anibiar.app.ui.auth

import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.anibiar.app.AniBiarApp
import com.anibiar.app.data.backdropUrl
import com.anibiar.app.ui.splash.KiroLogo
import com.anibiar.app.ui.theme.Ani
import com.anibiar.app.ui.theme.BrandIcons
import com.anibiar.app.ui.theme.inter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Sign-in / sign-up / verify-email, over a slowly drifting anime backdrop with the animated Kiro mark. */
@Composable
fun AuthScreen(onClose: () -> Unit) {
    val app = LocalContext.current.applicationContext as AniBiarApp
    val auth by app.social.authState.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(Ani.Onyx)) {
        Backdrop(app)
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xB3121214), Color(0x99121214), Color(0xF2121214))),
            ),
        )
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    "Skip", style = inter(14f, FontWeight.Medium), color = Ani.Soft,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClose).padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            KiroLogo(124.dp)
            Spacer(Modifier.height(6.dp))
            Text("Kiro", style = inter(30f, FontWeight.Bold), color = Color.White)
            Spacer(Modifier.height(22.dp))
            val a = auth
            if (a != null && !a.verified) VerifyCard(a.email, onClose) else FormCard()
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Backdrop(app: AniBiarApp) {
    val urls by produceState(emptyList<String>()) {
        val cached = app.repository.cachedTrending()
        value = cached.mapNotNull { it.backdropUrl }.take(6)
        runCatching { app.repository.fetchTrending() }.getOrNull()?.let { fresh ->
            val u = fresh.mapNotNull { it.bannerImage ?: it.backdropUrl }.take(6)
            if (u.isNotEmpty()) value = u
        }
    }
    var index by remember { mutableStateOf(0) }
    LaunchedEffect(urls) {
        while (urls.size > 1) {
            delay(5500)
            index = (index + 1) % urls.size
        }
    }
    val drift = rememberInfiniteTransition(label = "drift")
    val zoom by drift.animateFloat(1.05f, 1.22f, infiniteRepeatable(tween(11000, easing = LinearEasing), RepeatMode.Reverse), label = "zoom")
    val pan by drift.animateFloat(-18f, 18f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse), label = "pan")
    if (urls.isNotEmpty()) {
        Crossfade(urls[index.coerceIn(0, urls.lastIndex)], animationSpec = tween(1400), label = "bg") { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = pan },
            )
        }
    }
}

private fun validEmail(s: String) = android.util.Patterns.EMAIL_ADDRESS.matcher(s).matches()

@Composable
private fun GlassCard(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xD9121214))
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.02f))))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.08f))), shape)
            .padding(20.dp),
    ) { content() }
}

@Composable
private fun FormCard() {
    val context = LocalContext.current
    val app = context.applicationContext as AniBiarApp
    val scope = rememberCoroutineScope()
    var signUp by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        error = null
        val e = email.trim()
        when {
            !validEmail(e) -> { error = "Enter a valid email address"; return }
            signUp && !Regex("^[A-Za-z0-9_.]{3,20}$").matches(username.trim()) -> { error = "Username: 3-20 letters, numbers, _ or ."; return }
            password.length < 6 -> { error = "Password must be at least 6 characters"; return }
        }
        busy = true
        scope.launch {
            try {
                if (signUp) app.social.signUp(e, username.trim(), password) else app.social.signIn(e, password)
            } catch (ex: Exception) {
                error = ex.message
            } finally {
                busy = false
            }
        }
    }

    GlassCard {
        // Log in / Sign up switch
        val shape = RoundedCornerShape(50)
        Row(Modifier.fillMaxWidth().height(40.dp).clip(shape).border(1.dp, Ani.Border, shape)) {
            listOf("Log in" to false, "Sign up" to true).forEach { (label, isSignUp) ->
                val on = signUp == isSignUp
                Box(
                    Modifier.weight(1f).fillMaxSize().clip(shape)
                        .background(if (on) Ani.Lavender else Color.Transparent)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { signUp = isSignUp; error = null },
                    contentAlignment = Alignment.Center,
                ) { Text(label, style = inter(14f, FontWeight.SemiBold), color = if (on) Color(0xFF121214) else Ani.Soft) }
            }
        }
        Spacer(Modifier.height(16.dp))
        AuthField(email, { email = it }, "Email", KeyboardType.Email)
        if (signUp) {
            Spacer(Modifier.height(10.dp))
            AuthField(username, { username = it.filter { c -> c != ' ' } }, "Username", KeyboardType.Text)
        }
        Spacer(Modifier.height(10.dp))
        AuthField(
            password, { password = it }, "Password", KeyboardType.Password,
            password = !show,
            trailing = { Text(if (show) "Hide" else "Show", style = inter(13f, FontWeight.Medium), color = Ani.Muted, modifier = Modifier.clickable { show = !show }.padding(8.dp)) },
        )
        error?.let { Spacer(Modifier.height(10.dp)); Text(it, style = inter(13f, lineHeight = 18f), color = Color(0xFFFF8A80)) }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(if (busy) "Please wait…" else if (signUp) "Create account" else "Log in", enabled = !busy, onClick = ::submit)
        if (signUp) {
            Spacer(Modifier.height(8.dp))
            Text("We'll email you a verification link.", style = inter(12.5f), color = Ani.Muted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(1.dp).background(Ani.Border))
            Text("  or  ", style = inter(12.5f), color = Ani.Muted)
            Box(Modifier.weight(1f).height(1.dp).background(Ani.Border))
        }
        Spacer(Modifier.height(14.dp))
        SocialButton("Continue with Google", BrandIcons.Google) {
            Toast.makeText(context, "Google sign-in is coming soon", Toast.LENGTH_SHORT).show()
        }
        Spacer(Modifier.height(10.dp))
        SocialButton("Continue with Discord", BrandIcons.Discord) {
            Toast.makeText(context, "Discord sign-in is coming soon", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun VerifyCard(email: String, onClose: () -> Unit) {
    val app = LocalContext.current.applicationContext as AniBiarApp
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    // Poll quietly so the screen moves on by itself once the link is tapped.
    LaunchedEffect(Unit) {
        while (true) {
            delay(4000)
            if (app.social.refreshVerification()) break
        }
    }
    GlassCard {
        Text("Verify your email", style = inter(20f, FontWeight.Bold), color = Color.White)
        Spacer(Modifier.height(8.dp))
        Text("We sent a verification link to $email. Tap it, then come back here.", style = inter(14f, lineHeight = 20f), color = Ani.Soft)
        msg?.let { Spacer(Modifier.height(8.dp)); Text(it, style = inter(13f), color = Ani.Lavender) }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(if (busy) "Checking…" else "I've verified", enabled = !busy) {
            busy = true
            scope.launch {
                val ok = app.social.refreshVerification()
                msg = if (ok) null else "Not verified yet. Check your inbox (and spam)."
                busy = false
            }
        }
        Spacer(Modifier.height(10.dp))
        SocialButton("Resend email", null) {
            scope.launch {
                msg = try { app.social.resendVerification(); "Verification email sent" } catch (e: Exception) { e.message }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Use a different account", style = inter(13.5f, FontWeight.Medium), color = Ani.Muted,
            modifier = Modifier.align(Alignment.CenterHorizontally).clickable { app.social.signOut() }.padding(8.dp),
        )
    }
}

@Composable
private fun AuthField(
    value: String,
    onValue: (String) -> Unit,
    hint: String,
    type: KeyboardType,
    password: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(shape).border(1.dp, Ani.Border, shape).background(Color(0x66000000)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(hint, style = inter(15f), color = Ani.Muted)
            BasicTextField(
                value = value,
                onValueChange = onValue,
                singleLine = true,
                textStyle = inter(15f).copy(color = Color.White),
                cursorBrush = SolidColor(Ani.Lavender),
                keyboardOptions = KeyboardOptions(keyboardType = type),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        trailing?.invoke()
    }
}

@Composable
private fun PrimaryButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.fillMaxWidth().height(48.dp).clip(shape)
            .background(if (enabled) Ani.Lavender else Ani.Lavender.copy(alpha = 0.5f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = inter(15f, FontWeight.SemiBold), color = Color(0xFF121214)) }
}

@Composable
private fun SocialButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(shape).border(1.dp, Ani.Border, shape).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Image(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(10.dp))
        }
        Text(label, style = inter(14.5f, FontWeight.Medium), color = Color.White)
    }
}
