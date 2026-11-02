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
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    listOf(Color(0x33B9A1DF), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(540f, 260f),
                    radius = 900f,
                ),
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
            KiroLogo(112.dp)
            Spacer(Modifier.height(4.dp))
            Text("Kiro", style = inter(30f, FontWeight.Bold), color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text("Track anime, follow friends, watch together.", style = inter(14f), color = Ani.Muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            FormCard()
            Spacer(Modifier.height(18.dp))
            Terms()
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun validEmail(s: String) = android.util.Patterns.EMAIL_ADDRESS.matcher(s).matches()

@Composable
private fun GlassCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(Ani.Panel)
            .border(1.dp, Ani.Border, shape)
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
    var pending by rememberSaveable { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }

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
                if (signUp) {
                    if (app.social.signUp(e, username.trim(), password) == com.anibiar.app.social.SignUpResult.ConfirmEmail) pending = true
                } else app.social.signIn(e, password)
            } catch (ex: Exception) {
                error = ex.message
            } finally {
                busy = false
            }
        }
    }

    if (pending) {
        GlassCard {
            Text("Check your email", style = inter(20f, FontWeight.Bold), color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text(
                "We sent a confirmation link to ${email.trim()}. Tap it (check spam too), then log in here.",
                style = inter(14f, lineHeight = 20f), color = Ani.Soft,
            )
            (info ?: error)?.let { Spacer(Modifier.height(8.dp)); Text(it, style = inter(13f), color = if (error != null) Color(0xFFFF8A80) else Ani.Lavender) }
            Spacer(Modifier.height(16.dp))
            PrimaryButton(if (busy) "Checking…" else "I've confirmed, log in", enabled = !busy) {
                error = null; info = null; busy = true
                scope.launch {
                    try { app.social.signIn(email.trim(), password) } catch (ex: Exception) { error = ex.message }
                    busy = false
                }
            }
            Spacer(Modifier.height(10.dp))
            SocialButton("Resend email", null) {
                error = null
                scope.launch {
                    try { app.social.resendVerification(email.trim()); info = "Sent. It can take a minute to arrive." } catch (ex: Exception) { error = ex.message }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Use a different email", style = inter(13.5f, FontWeight.Medium), color = Ani.Muted,
                modifier = Modifier.align(Alignment.CenterHorizontally).clickable { pending = false; error = null; info = null }.padding(8.dp),
            )
        }
        return
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
            Text("We'll email you a link to verify your address.", style = inter(12.5f), color = Ani.Muted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
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
        Modifier.fillMaxWidth().height(48.dp).clip(shape).background(Ani.Item).border(1.dp, Ani.Border, shape),
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


@Composable
private fun Terms() {
    val context = LocalContext.current
    val soon = { Toast.makeText(context, "Coming soon", Toast.LENGTH_SHORT).show() }
    val text = androidx.compose.ui.text.buildAnnotatedString {
        append("By continuing, you agree to our ")
        pushStyle(androidx.compose.ui.text.SpanStyle(color = Ani.Soft, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline))
        append("Terms of Service")
        pop()
        append(" and ")
        pushStyle(androidx.compose.ui.text.SpanStyle(color = Ani.Soft, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline))
        append("Privacy Policy")
        pop()
        append(".")
    }
    Text(
        text, style = inter(12.5f, lineHeight = 18f), color = Ani.Muted, textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(max = 420.dp).padding(horizontal = 8.dp).clickable(onClick = soon),
    )
}
