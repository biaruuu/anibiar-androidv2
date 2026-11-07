package com.anibiar.app.ui.auth

import com.anibiar.app.ui.theme.glass
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.anibiar.app.AniBiarApp
import com.anibiar.app.ui.splash.KiroLogo
import com.anibiar.app.ui.theme.Ani
import com.anibiar.app.ui.theme.AniIcons
import com.anibiar.app.ui.theme.BrandIcons
import com.anibiar.app.ui.theme.inter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Survives screen changes: when sending another link is allowed again (epoch ms). */
private object SendGate {
    var until by mutableStateOf(0L)
    /** Returns seconds left if [msg] is a rate-limit error (and starts the cooldown), else null. */
    fun fromError(msg: String?): Int? {
        val n = msg?.takeIf { it.startsWith("RATE_LIMIT:") }?.removePrefix("RATE_LIMIT:")?.toIntOrNull() ?: return null
        until = System.currentTimeMillis() + n * 1000L
        return n
    }
}

@Composable
private fun rememberCooldown(): Int {
    var left by remember { mutableStateOf(((SendGate.until - System.currentTimeMillis() + 999) / 1000).toInt().coerceAtLeast(0)) }
    LaunchedEffect(SendGate.until) {
        while (true) {
            left = ((SendGate.until - System.currentTimeMillis() + 999) / 1000).toInt().coerceAtLeast(0)
            if (left == 0) break
            delay(500)
        }
    }
    return left
}

/** Passwordless sign-in: enter an email, get a link, tap it and you're in. No form, no password. */
@Composable
fun AuthScreen(onClose: () -> Unit) {
    var sentTo by rememberSaveable { mutableStateOf<String?>(null) }
    var email by rememberSaveable { mutableStateOf("") }

    Box(
        Modifier.fillMaxSize().background(Ani.Onyx).background(
            Brush.radialGradient(
                listOf(Color(0x33B9A1DF), Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(540f, 500f),
                radius = 1100f,
            ),
        ),
    ) {
        AnimatedContent(
            targetState = sentTo,
            transitionSpec = {
                if (targetState != null) (slideInHorizontally { it / 5 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 5 } + fadeOut())
                else (slideInHorizontally { -it / 5 } + fadeIn()) togetherWith (slideOutHorizontally { it / 5 } + fadeOut())
            },
            label = "authStep",
        ) { sent ->
            if (sent == null) EmailStep(email, { email = it }, onClose) { sentTo = it }
            else SentStep(sent, onBack = { sentTo = null })
        }
    }
}

@Composable
private fun BackArrow(onClick: () -> Unit) {
    com.anibiar.app.ui.theme.GlassBack(onClick)
}

private fun validEmail(s: String) = android.util.Patterns.EMAIL_ADDRESS.matcher(s.trim()).matches()

@Composable
private fun EmailStep(email: String, onEmail: (String) -> Unit, onClose: () -> Unit, onSent: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as AniBiarApp
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val wait = rememberCooldown()
    val ok = validEmail(email) && wait == 0

    fun submit() {
        if (!ok || busy) return
        busy = true; error = null
        scope.launch {
            try { app.social.sendSignInLink(email.trim()); onSent(email.trim()) } catch (e: Exception) { if (SendGate.fromError(e.message) == null) error = e.message }
            busy = false
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        val logo = min(maxWidth * 0.52f, maxHeight * 0.26f).coerceAtLeast(96.dp)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 8.dp)) { BackArrow(onClose) }
            Spacer(Modifier.weight(1f).height(16.dp))
            KiroLogo(logo)
            Spacer(Modifier.height(6.dp))
            Text("Kiro", style = inter(28f, FontWeight.Bold), color = Color.White)
            Spacer(Modifier.weight(1f).height(24.dp))
            Column(
                Modifier.widthIn(max = 460.dp).fillMaxWidth().padding(horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Enter your email to log in or sign up", style = inter(14f, FontWeight.Medium), color = Color.White, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                EmailField(email, onEmail, ::submit)
                error?.let { Spacer(Modifier.height(8.dp)); Text(it, style = inter(13f, lineHeight = 18f), color = Color(0xFFFF8A80), textAlign = TextAlign.Center) }
                Spacer(Modifier.height(10.dp))
                val shape = RoundedCornerShape(50)
                Box(
                    Modifier.fillMaxWidth().height(44.dp).clip(shape)
                        .background(if (ok) Color.White else Ani.ButtonDark)
                        .clickable(enabled = ok && !busy, onClick = ::submit),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (busy) "Sending…" else if (wait > 0) "Try again in ${wait}s" else "Continue", style = inter(14f, FontWeight.Medium), color = if (ok) Color.Black else Ani.Muted, maxLines = 1)
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(1.dp).background(Ani.Border))
                    Text("  OR  ", style = inter(12f, FontWeight.Medium), color = Ani.Muted)
                    Box(Modifier.weight(1f).height(1.dp).background(Ani.Border))
                }
                Spacer(Modifier.height(14.dp))
                SocialButton("Continue with Google", BrandIcons.Google) { Toast.makeText(context, "Google sign-in is coming soon", Toast.LENGTH_SHORT).show() }
                Spacer(Modifier.height(8.dp))
                SocialButton("Continue with Discord", BrandIcons.Discord) { Toast.makeText(context, "Discord sign-in is coming soon", Toast.LENGTH_SHORT).show() }
                Spacer(Modifier.height(18.dp))
                Terms()
                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

@Composable
private fun SentStep(email: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as AniBiarApp
    val scope = rememberCoroutineScope()
    var cooldown by remember { mutableStateOf(45) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(cooldown) { if (cooldown > 0) { delay(1000); cooldown-- } }
    val gate = rememberCooldown()

    BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        val chibiH = min(maxHeight * 0.40f, 340.dp)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 8.dp)) { BackArrow(onBack) }
            // "Email sent" notice
            val shape = RoundedCornerShape(18.dp)
            Row(
                Modifier.widthIn(max = 460.dp).fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp).clip(shape)
                    .background(com.anibiar.app.ui.theme.GlassFill).border(1.dp, Ani.Border, shape).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AniIcons.CheckCircle, null, tint = Ani.Lime, modifier = Modifier.size(24.dp))
                Spacer(Modifier.size(12.dp))
                Column {
                    Text("Email sent!", style = inter(12.5f), color = Ani.Muted)
                    Text("Kindly check your email for the sign-in link.", style = inter(15f, FontWeight.SemiBold, lineHeight = 21f), color = Color.White)
                }
            }
            Spacer(Modifier.weight(1f).height(8.dp))
            ApngImage("reze.apng", Modifier.height(chibiH).fillMaxWidth(), "Reze chibi")
            Spacer(Modifier.weight(1f).height(12.dp))
            Column(
                Modifier.widthIn(max = 460.dp).fillMaxWidth().padding(horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "WE'VE SENT A SIGN IN LINK TO YOUR EMAIL",
                    style = inter(26f, FontWeight.ExtraBold, lineHeight = 30f), color = Color.White, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Text(email, style = inter(14f, FontWeight.Medium), color = Ani.Lavender, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Text("Check your spam folder if you can't find it.", style = inter(14f), color = Ani.Soft, textAlign = TextAlign.Center)
                note?.let { Spacer(Modifier.height(8.dp)); Text(it, style = inter(13f), color = Ani.Lavender, textAlign = TextAlign.Center) }
                Spacer(Modifier.height(18.dp))
                val pill = RoundedCornerShape(50)
                val canResend = cooldown == 0 && gate == 0 && !busy
                Box(
                    Modifier.fillMaxWidth().height(44.dp).glass(pill)
                        .clickable(enabled = canResend) {
                            busy = true; note = null
                            scope.launch {
                                note = try { app.social.sendSignInLink(email); cooldown = 45; "Sent again. It can take a minute." } catch (e: Exception) { if (SendGate.fromError(e.message) != null) null else e.message }
                                busy = false
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (gate > 0) "Try again in ${gate}s" else if (cooldown > 0) "Didn't receive it? Resend in ${cooldown}s" else if (busy) "Sending…" else "Didn't receive it? Resend Email",
                        style = inter(14f, FontWeight.Medium), color = if (canResend) Color.White else Ani.Muted, maxLines = 1,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.fillMaxWidth().height(44.dp).clip(pill).background(Color.White).clickable {
                        try {
                            context.startActivity(
                                Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        } catch (e: Exception) {
                            Toast.makeText(context, "No email app found", Toast.LENGTH_SHORT).show()
                        }
                    },
                    contentAlignment = Alignment.Center,
                ) { Text("Open Email", style = inter(14f, FontWeight.Medium), color = Color.Black, maxLines = 1) }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun EmailField(value: String, onValue: (String) -> Unit, onDone: () -> Unit) {
    val shape = RoundedCornerShape(50)
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(44.dp).glass(shape)
            .then(if (focused) Modifier.border(1.5.dp, Ani.Lavender, shape) else Modifier).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AniIcons.Mail, null, tint = Ani.Muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text("Enter your email", style = inter(14f), color = Ani.Muted, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = { onValue(it.replace(" ", "")) },
                singleLine = true,
                textStyle = inter(14f).copy(color = Color.White),
                cursorBrush = SolidColor(Ani.Lavender),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onDone() }),
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            )
        }
    }
}

@Composable
private fun SocialButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.fillMaxWidth().height(44.dp).clip(shape).border(1.dp, Ani.Border, shape).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Image(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(10.dp))
        Text(label, style = inter(14f, FontWeight.Medium), color = Color.White)
    }
}

@Composable
private fun Terms() {
    val context = LocalContext.current
    val link = SpanStyle(color = Ani.Lavender, fontWeight = FontWeight.Medium, textDecoration = TextDecoration.Underline)
    val text = buildAnnotatedString {
        append("By continuing, you agree to the ")
        pushStyle(link); append("Terms of Use"); pop()
        append(" and ")
        pushStyle(link); append("Privacy Policy"); pop()
        append(".")
    }
    Text(
        text, style = inter(11.5f, lineHeight = 16f), color = Ani.Muted, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().clickable { Toast.makeText(context, "Coming soon", Toast.LENGTH_SHORT).show() },
    )
}
