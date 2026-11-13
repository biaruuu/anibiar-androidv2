package com.anibiar.app.ui.settings

import com.anibiar.app.ui.theme.liquidGlass
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anibiar.app.AniBiarApp
import com.anibiar.app.social.UserProfile
import com.anibiar.app.ui.profile.Avatar
import com.anibiar.app.ui.theme.Ani
import com.anibiar.app.ui.theme.AniIcons
import com.anibiar.app.ui.theme.inter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed interface Dlg {
    data object Username : Dlg
    data object Link : Dlg
    data object Requests : Dlg
    data object Lists : Dlg
    data object Favs : Dlg
    data object Logout : Dlg
}

/** Account information, profile link, privacy and log out. One scrolling page, no sub-screens. */
@Composable
fun SettingsScreen(onAdmin: () -> Unit = {}, onLogin: () -> Unit, onOpenInbox: () -> Unit, onOpenProfile: () -> Unit, onBack: () -> Unit = {}) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    val context = LocalContext.current
    val app = context.applicationContext as AniBiarApp
    val social = app.social
    val scope = rememberCoroutineScope()
    val me by social.authState.collectAsStateWithLifecycle()
    val unread by social.unread.collectAsStateWithLifecycle()
    val profile by produceState<UserProfile?>(null, me?.uid) {
        val uid = me?.uid
        if (uid == null) value = null else social.profile(uid).collect { value = it }
    }
    var dlg by remember { mutableStateOf<Dlg?>(null) }
    var hlPrivacy by remember { mutableStateOf(false) }
    if (hlPrivacy) com.anibiar.app.ui.engage.HighlightPrivacySettings { hlPrivacy = false }
    fun toast(m: String) = Toast.makeText(context, m, Toast.LENGTH_SHORT).show()

    Column(
        Modifier.fillMaxSize().background(Ani.Onyx).statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp).padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 110.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            com.anibiar.app.ui.theme.GlassBack(onBack)
            Spacer(Modifier.width(12.dp))
            Text("Settings", style = inter(22f, FontWeight.ExtraBold), color = Color.White, modifier = Modifier.weight(1f))
            if (me != null) com.anibiar.app.ui.theme.GlassButton(onOpenInbox) {
                Icon(AniIcons.Bell, "Inbox", tint = Color.White, modifier = Modifier.size(18.dp))
                if (unread > 0) Box(
                    Modifier.align(Alignment.TopEnd).padding(top = 1.dp, end = 1.dp).size(if (unread > 9) 16.dp else 14.dp).clip(CircleShape).background(Color(0xFFFF4D5E)),
                    contentAlignment = Alignment.Center,
                ) { Text(if (unread > 9) "9+" else "$unread", style = inter(8.5f, FontWeight.Bold), color = Color.White) }
            }
        }

        if (me == null) {
            Card {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("You're not logged in", style = inter(17f, FontWeight.SemiBold), color = Color.White)
                    Spacer(Modifier.height(6.dp))
                    Text("Log in to manage your account, profile link and privacy.", style = inter(14f, lineHeight = 20f), color = Ani.Muted, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Box(
                        Modifier.widthIn(min = 160.dp).height(40.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).clickable(onClick = onLogin),
                        contentAlignment = Alignment.Center,
                    ) { Text("Log in", style = inter(14f, FontWeight.Medium), color = Color.Black) }
                }
            }
            Section("Appearance") { AppearanceRows(context) }
            Section("App") { AppToolRows(context, ::toast) }
            Section("About") { aboutRows(context, ::toast) }
            return@Column
        }

        // Account card
        Card {
            Row(Modifier.fillMaxWidth().clickable(onClick = onOpenProfile).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(profile?.avatarUrl, profile?.username, 56.dp, deco = profile?.avatarDeco)
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(profile?.username ?: "…", style = inter(18f, FontWeight.Bold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(me?.email.orEmpty(), style = inter(13f), color = Ani.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(AniIcons.ChevronRight, null, tint = Ani.Muted, modifier = Modifier.size(22.dp))
            }
        }

        Section("Account information") {
            InfoRow("Username", profile?.username ?: "…", onClick = { dlg = Dlg.Username })
            Divider()
            InfoRow("Email", me?.email.orEmpty())
            Divider()
            InfoRow("Email status", if (me?.verified == true) "Verified" else "Not verified")
            Divider()
            InfoRow("Member since", profile?.createdAt?.takeIf { it > 0 }?.let { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(it)) } ?: "…")
            Divider()
            InfoRow("Account ID", me?.uid?.take(8).orEmpty() + "…", onClick = {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("id", me?.uid.orEmpty()))
                toast("Account ID copied")
            })
        }

        Section("Profile link") {
            val slug = profile?.linkSlug
            InfoRow("Your link", if (slug.isNullOrBlank()) "Not set" else "kiro://u/$slug", onClick = { dlg = Dlg.Link })
            if (!slug.isNullOrBlank()) {
                Divider()
                InfoRow("Copy link", "", onClick = {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("link", "kiro://u/$slug"))
                    toast("Link copied")
                })
                Divider()
                InfoRow("Share link", "", onClick = {
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Add me on Kiro: kiro://u/$slug"), "Share profile",
                        ),
                    )
                })
            }
        }

        Section("Privacy") {
            InfoRow("Who can view my highlights", "Choose for each highlight", onClick = { hlPrivacy = true })
            Divider()
            InfoRow("Who can send me friend requests", if (profile?.friendRequests == "nobody") "Nobody" else "Mutual followers", onClick = { dlg = Dlg.Requests })
            Divider()
            InfoRow(
                "Who can see my followers & following",
                when (profile?.listsVisibility) { "me" -> "Only me"; "friends" -> "Friends"; else -> "Everyone" },
                onClick = { dlg = Dlg.Lists },
            )
            Divider()
            InfoRow(
                "Who can see my favorites",
                when (profile?.favoritesVisibility) { "me" -> "Only me"; "friends" -> "Friends"; else -> "Everyone" },
                onClick = { dlg = Dlg.Favs },
            )
        }

        if (me?.email.equals(com.anibiar.app.ui.admin.ADMIN_EMAIL, ignoreCase = true)) Section("Owner") {
            InfoRow("Admin panel", "Anime, streams, updates, stats", onClick = onAdmin)
        }

        Section("Notifications") {
            InfoRow("Inbox", if (unread > 0) "$unread new" else "", onClick = onOpenInbox)
        }

        Section("Appearance") { AppearanceRows(context) }
            Section("App") { AppToolRows(context, ::toast) }

        Section("About") { aboutRows(context, ::toast) }

        Spacer(Modifier.height(20.dp))
        Box(
            Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(16.dp)).background(Ani.ButtonDark).clickable { dlg = Dlg.Logout },
            contentAlignment = Alignment.Center,
        ) { Text("Log out", style = inter(15f, FontWeight.SemiBold), color = Color(0xFFFF8A80)) }
    }

    when (dlg) {
        Dlg.Username -> TextDialog("Change username", "3-20 letters, numbers, _ or .", profile?.username.orEmpty(), 20,
            filter = { c -> c.isLetterOrDigit() || c == '_' || c == '.' }, onDismiss = { dlg = null }) { v -> social.setUsername(v) }
        Dlg.Link -> TextDialog("Profile link", "kiro://u/ + your link. 3-30 letters, numbers, _ . or -. Leave empty to remove it.", profile?.linkSlug.orEmpty(), 30,
            filter = { c -> c.isLetterOrDigit() || c == '_' || c == '.' || c == '-' }, allowEmpty = true, prefix = "kiro://u/", onDismiss = { dlg = null }) { v -> social.setLinkSlug(v) }
        Dlg.Requests -> ChoiceDialog(
            "Friend requests", profile?.friendRequests ?: "mutuals",
            listOf("mutuals" to "Mutual followers", "nobody" to "Nobody"), onDismiss = { dlg = null },
        ) { v -> scope.launch { runCatching { social.setFriendRequests(v) }.onFailure { toast(it.message ?: "Couldn't save") } } }
        Dlg.Lists -> ChoiceDialog(
            "Followers & following list", profile?.listsVisibility ?: "everyone",
            listOf("everyone" to "Everyone", "friends" to "Friends", "me" to "Only me"), onDismiss = { dlg = null },
        ) { v -> scope.launch { runCatching { social.setListsVisibility(v) }.onFailure { toast(it.message ?: "Couldn't save") } } }
        Dlg.Favs -> ChoiceDialog(
            "Who can see my favorites", profile?.favoritesVisibility ?: "everyone",
            listOf("everyone" to "Everyone", "friends" to "Friends", "me" to "Only me"), onDismiss = { dlg = null },
        ) { v -> scope.launch { runCatching { social.setFavoritesVisibility(v) }.onFailure { toast(it.message ?: "Couldn't save") } } }
        Dlg.Logout -> AlertDialog(
            onDismissRequest = { dlg = null }, containerColor = Ani.Panel,
            title = { Text("Log out of Kiro?", style = inter(18f, FontWeight.SemiBold), color = Color.White) },
            text = { Text("You can log back in any time with your email.", style = inter(14f, lineHeight = 20f), color = Ani.Soft) },
            confirmButton = { Text("Log out", style = inter(15f, FontWeight.SemiBold), color = Color(0xFFFF8A80), modifier = Modifier.clickable { dlg = null; social.signOut() }.padding(10.dp)) },
            dismissButton = { Text("Cancel", style = inter(15f), color = Ani.Muted, modifier = Modifier.clickable { dlg = null }.padding(10.dp)) },
        )
        null -> {}
    }
}

@Composable
private fun aboutRows(context: Context, toast: (String) -> Unit) {
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
    InfoRow("Terms of Use", "", onClick = { toast("Coming soon") })
    Divider()
    InfoRow("Privacy Policy", "", onClick = { toast("Coming soon") })
    Divider()
    InfoRow("App version", version)
    Divider()
    InfoRow("Rust core", com.anibiar.app.nativecore.Native.versionOrNull() ?: "Not loaded")
    Divider()
    var found by remember { mutableStateOf<com.anibiar.app.update.UpdateInfo?>(null) }
    var checking by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    InfoRow(if (checking) "Checking…" else "Check for updates", "", onClick = {
        if (!checking) { checking = true; scope.launch { val u = com.anibiar.app.update.Updater.check(context); checking = false; if (u == null) toast("You're on the latest version") else found = u } }
    })
    found?.let { com.anibiar.app.update.UpdateDialog(it, onDismiss = { found = null }) }
    Divider()
    var showLog by remember { mutableStateOf(false) }
    InfoRow("What's new", "", onClick = { showLog = true })
    if (showLog) com.anibiar.app.update.WhatsNewDialog(com.anibiar.app.update.Changelog.bundled(context), "Changelog") { showLog = false }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(Modifier.fillMaxWidth().liquidGlass(shape)) { content() }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(title.uppercase(), style = inter(12f, FontWeight.SemiBold), color = Ani.Muted, modifier = Modifier.padding(top = 22.dp, bottom = 8.dp, start = 4.dp))
    Card { Column { content() } }
}

@Composable
private fun Divider() { Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x14FFFFFF))) }

@Composable
private fun InfoRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = inter(15f), color = Color.White, modifier = Modifier.weight(1f))
        if (value.isNotEmpty()) {
            Spacer(Modifier.size(12.dp))
            Text(value, style = inter(14f), color = Ani.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 190.dp))
        }
        if (onClick != null) { Spacer(Modifier.size(6.dp)); Icon(AniIcons.ChevronRight, null, tint = Ani.Muted, modifier = Modifier.size(20.dp)) }
    }
}

@Composable
internal fun ChoiceDialog(title: String, current: String, options: List<Pair<String, String>>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Ani.Panel,
        title = { Text(title, style = inter(18f, FontWeight.SemiBold), color = Color.White) },
        text = {
            Column {
                options.forEach { (key, label) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onPick(key); onDismiss() }.padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, style = inter(15f), color = Color.White, modifier = Modifier.weight(1f))
                        Box(
                            Modifier.size(22.dp).clip(CircleShape).border(2.dp, if (key == current) Color.White else Ani.Border, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { if (key == current) Box(Modifier.size(10.dp).clip(CircleShape).background(Color.White)) }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { Text("Cancel", style = inter(15f), color = Ani.Muted, modifier = Modifier.clickable(onClick = onDismiss).padding(10.dp)) },
    )
}

@Composable
private fun TextDialog(
    title: String, hint: String, initial: String, max: Int,
    filter: (Char) -> Boolean, allowEmpty: Boolean = false, prefix: String = "",
    onDismiss: () -> Unit, onSave: suspend (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf(initial) }
    var err by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val valid = (allowEmpty && text.isEmpty()) || text.length >= 3
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Ani.Panel,
        title = { Text(title, style = inter(18f, FontWeight.SemiBold), color = Color.White) },
        text = {
            Column {
                Text(hint, style = inter(13.5f, lineHeight = 19f), color = Ani.Muted)
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, Ani.Border, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (prefix.isNotEmpty()) Text(prefix, style = inter(14.5f), color = Ani.Muted)
                    BasicTextField(
                        text, { text = it.filter(filter).take(max) }, singleLine = true,
                        textStyle = inter(15f).copy(color = Color.White), cursorBrush = SolidColor(Ani.Lavender), modifier = Modifier.weight(1f),
                    )
                }
                err?.let { Spacer(Modifier.height(6.dp)); Text(it, style = inter(12.5f), color = Color(0xFFFF8A80)) }
            }
        },
        confirmButton = {
            Text(
                if (saving) "Saving…" else "Save", style = inter(15f, FontWeight.SemiBold), color = if (valid) Ani.Lavender else Ani.Muted,
                modifier = Modifier.clickable(enabled = valid && !saving) {
                    saving = true; err = null
                    scope.launch { try { onSave(text); onDismiss() } catch (e: Exception) { err = e.message; saving = false } }
                }.padding(10.dp),
            )
        },
        dismissButton = { Text("Cancel", style = inter(15f), color = Ani.Muted, modifier = Modifier.clickable(onClick = onDismiss).padding(10.dp)) },
    )
}


@Composable
private fun AppearanceRows(context: Context) {
    val tick = com.anibiar.app.ui.theme.rememberTick()
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Accent color", style = inter(15f), color = Color.White)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            com.anibiar.app.ui.theme.Appearance.accents.forEach { (name, c) ->
                val on = com.anibiar.app.ui.theme.Appearance.accent == c
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(c).border(if (on) 3.dp else 1.dp, if (on) Color.White else Color(0x33FFFFFF), CircleShape)
                        .clickable { tick(); com.anibiar.app.ui.theme.Appearance.setAccent(context, name) },
                )
            }
        }
    }
    Divider()
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Text size", style = inter(15f), color = Color.White)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.anibiar.app.ui.theme.Appearance.textSizes.forEach { (name, v) ->
                val on = com.anibiar.app.ui.theme.Appearance.textScale == v
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(if (on) Color.White else Ani.ButtonDark).clickable { tick(); com.anibiar.app.ui.theme.Appearance.setTextScale(context, v) }.padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Text(name, style = inter(13.5f, FontWeight.SemiBold), color = if (on) Color.Black else Color.White) }
            }
        }
    }
    Divider()
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Poster cards", style = inter(15f), color = Color.White)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.anibiar.app.ui.theme.Appearance.cardStyles.forEachIndexed { i, name ->
                val on = com.anibiar.app.ui.theme.Appearance.cardStyle == i
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(if (on) Color.White else Ani.ButtonDark).clickable { tick(); com.anibiar.app.ui.theme.Appearance.setCardStyle(context, i) }.padding(horizontal = 14.dp, vertical = 8.dp),
                ) { Text(name, style = inter(13f, FontWeight.SemiBold), color = if (on) Color.Black else Color.White) }
            }
        }
    }
    Divider()
    val amoled = com.anibiar.app.ui.theme.Appearance.amoled
    InfoRow("Pure black (AMOLED)", if (amoled) "On" else "Off", onClick = { tick(); com.anibiar.app.ui.theme.Appearance.setAmoled(context, !amoled) })
}
