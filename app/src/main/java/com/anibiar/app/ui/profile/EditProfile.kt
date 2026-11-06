package com.anibiar.app.ui.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anibiar.app.AniBiarApp
import com.anibiar.app.social.ImageProc
import com.anibiar.app.social.UserProfile
import com.anibiar.app.ui.theme.Ani
import com.anibiar.app.ui.theme.AniIcons
import com.anibiar.app.ui.theme.inter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Edit profile: photo, name, username, profile link, bio and banner. Each field opens its own full page. */
@Composable
internal fun EditProfileScreen(profile: UserProfile?, onBack: () -> Unit) {
    val context = LocalContext.current
    val social = (context.applicationContext as AniBiarApp).social
    val scope = rememberCoroutineScope()
    var page by rememberSaveable { mutableStateOf("main") }
    var busy by remember { mutableStateOf(false) }
    var showStatus by remember { mutableStateOf(false) }
    BackHandler { if (page != "main") page = "main" else onBack() }
    fun toast(m: String) = Toast.makeText(context, m, Toast.LENGTH_SHORT).show()

    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) scope.launch {
            busy = true
            try { social.setAvatar(withContext(Dispatchers.Default) { ImageProc.avatar(context, uri) }) } catch (e: Exception) { toast(e.message ?: "Couldn't upload") }
            busy = false
        }
    }
    val pickBanner = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) scope.launch {
            busy = true
            try { val b = withContext(Dispatchers.Default) { ImageProc.banner(context, uri) }; social.setBanner(b.bytes, b.gif) } catch (e: Exception) { toast(e.message ?: "Couldn't upload") }
            busy = false
        }
    }

    if (showStatus) StatusSheet(profile) { showStatus = false }
    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B0D))) {
        when (page) {
            "name" -> FieldPage(
                "Name", "Your name can only be changed once every 7 days.", profile?.displayName.orEmpty(), 30,
                filter = { true }, allowEmpty = true, onCancel = { page = "main" },
            ) { social.setDisplayName(it); page = "main" }
            "username" -> FieldPage(
                "Username", "You can change your username once every 30 days. Usernames can contain only letters, numbers, underscores, and periods.",
                profile?.username.orEmpty(), 20, filter = { it.isLetterOrDigit() || it == '_' || it == '.' }, minLen = 3, previewPrefix = "@",
                onCancel = { page = "main" },
            ) { social.setUsername(it); page = "main" }
            "link" -> FieldPage(
                "Profile link", "This is the link people can use to open your profile. Use letters, numbers, underscores, periods or dashes. Leave it empty to remove it.",
                profile?.linkSlug.orEmpty(), 30, filter = { it.isLetterOrDigit() || it == '_' || it == '.' || it == '-' }, allowEmpty = true, minLen = 3,
                previewPrefix = "kiro://u/", onCancel = { page = "main" },
            ) { social.setLinkSlug(it); page = "main" }
            "bio" -> FieldPage(
                "Bio", "You can edit your bio anytime.", profile?.bio.orEmpty(), 200,
                filter = { true }, allowEmpty = true, multiline = true, placeholder = "My account is all about…", onCancel = { page = "main" },
            ) { social.setBio(it); page = "main" }
            else -> Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.align(Alignment.CenterStart).padding(start = 14.dp)) {
                        com.anibiar.app.ui.theme.GlassButton(onBack) { Icon(AniIcons.ChevronLeft, "Back", tint = Color.White, modifier = Modifier.size(20.dp)) }
                    }
                    Text("Edit profile", style = inter(17f, FontWeight.Bold), color = Color.White)
                }
                Spacer(Modifier.height(12.dp))
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(104.dp).clip(CircleShape).border(2.dp, Color.White, CircleShape).clickable { pickAvatar.launch("image/*") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Avatar(profile?.avatarUrl, profile?.shownName, 104.dp)
                        Box(Modifier.fillMaxSize().background(Color(0x66000000)), contentAlignment = Alignment.Center) {
                            Icon(AniIcons.Photo, "Change photo", tint = Color.White, modifier = Modifier.size(38.dp))
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        if (busy) "Uploading…" else "Change photo", style = inter(16f, FontWeight.SemiBold), color = Ani.Lavender,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = !busy) { pickAvatar.launch("image/*") }.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.height(26.dp))
                FormCard {
                    FormRow("Name", profile?.shownName.orEmpty(), onClick = { page = "name" })
                    FormRow("Username", profile?.username.orEmpty(), onClick = { page = "username" })
                    val slug = profile?.linkSlug
                    FormRow(
                        "", if (slug.isNullOrBlank()) "Add a profile link" else "kiro://u/$slug", muted = slug.isNullOrBlank(),
                        trailing = if (slug.isNullOrBlank()) null else ({
                            Icon(
                                AniIcons.Copy, "Copy link", tint = Color.White,
                                modifier = Modifier.size(34.dp).clip(CircleShape).clickable {
                                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("link", "kiro://u/$slug"))
                                    toast("Link copied")
                                }.padding(7.dp),
                            )
                        }),
                        onClick = { page = "link" },
                    )
                }
                Text("Basic info", style = inter(13.5f, FontWeight.SemiBold), color = Ani.Muted, modifier = Modifier.padding(start = 32.dp, top = 26.dp, bottom = 10.dp))
                FormCard {
                    FormRow(
                        "Bio", profile?.bio?.takeIf { it.isNotBlank() } ?: "Write a short description about who you are or what your account is about",
                        muted = profile?.bio.isNullOrBlank(), maxLines = 4, onClick = { page = "bio" },
                    )
                    FormRow(
                        "Status",
                        listOfNotNull(profile?.statusEmoji?.takeIf { it.isNotBlank() }, profile?.statusText?.takeIf { it.isNotBlank() }).joinToString(" ").ifBlank { com.anibiar.app.ui.profile.statusLabel(profile?.status) },
                        onClick = { showStatus = true },
                        trailing = { com.anibiar.app.ui.profile.StatusDot(profile?.status, 22.dp, Ani.Item) },
                    )
                    FormRow(
                        "Banner", "", onClick = { pickBanner.launch("image/*") },
                        trailing = {
                            Box(Modifier.width(72.dp).height(34.dp).clip(RoundedCornerShape(8.dp)).background(Ani.Panel)) {
                                if (profile?.bannerUrl != null) AsyncImage(profile.bannerUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                else Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF2A2140), Color(0xFF1F2A2E)))))
                            }
                        },
                    )
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun FormCard(content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFF151517)).border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(20.dp))) { content() }
}

@Composable
private fun FormRow(
    label: String, value: String, muted: Boolean = false, maxLines: Int = 1,
    trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = inter(15.5f), color = Color(0xFFE4E1E8), modifier = Modifier.width(96.dp))
        Text(
            value, style = inter(15.5f, if (muted) FontWeight.Normal else FontWeight.SemiBold, lineHeight = 22f),
            color = if (muted) Ani.Muted else Color.White, maxLines = maxLines, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        if (trailing != null) { trailing(); Spacer(Modifier.width(4.dp)) }
        Icon(AniIcons.ChevronRight, null, tint = Ani.Muted, modifier = Modifier.size(22.dp))
    }
}

/** A full page for one field: Cancel / Save on top, a big title, a hint, then the input. */
@Composable
private fun FieldPage(
    title: String, hint: String, initial: String, max: Int,
    filter: (Char) -> Boolean, minLen: Int = 1, allowEmpty: Boolean = false, multiline: Boolean = false,
    placeholder: String = "", previewPrefix: String? = null,
    onCancel: () -> Unit, onSave: suspend (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var text by rememberSaveable(title) { mutableStateOf(initial) }
    var err by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val trimmed = text.trim()
    val valid = (allowEmpty && trimmed.isEmpty()) || trimmed.length >= minLen
    val canSave = valid && trimmed != initial.trim() && !saving

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Cancel", style = inter(16.5f), color = Color.White, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onCancel).padding(vertical = 8.dp, horizontal = 2.dp))
            Spacer(Modifier.weight(1f))
            Text(
                if (saving) "Saving…" else "Save", style = inter(16.5f, FontWeight.SemiBold), color = if (canSave) Ani.Lavender else Ani.Lavender.copy(alpha = 0.35f),
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = canSave) {
                    saving = true; err = null
                    scope.launch { try { onSave(trimmed) } catch (e: Exception) { err = e.message; saving = false } }
                }.padding(vertical = 8.dp, horizontal = 2.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = inter(30f, FontWeight.Bold), color = Color.White)
        Spacer(Modifier.height(10.dp))
        Text(hint, style = inter(15.5f, lineHeight = 23f), color = Color(0xFFD7D3DA))
        Spacer(Modifier.height(22.dp))
        Row(
            Modifier.fillMaxWidth().then(if (multiline) Modifier.height(150.dp) else Modifier.height(52.dp))
                .clip(RoundedCornerShape(12.dp)).background(Color(0xFF151517)).border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = if (multiline) 14.dp else 0.dp),
            verticalAlignment = if (multiline) Alignment.Top else Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = if (multiline) Alignment.TopStart else Alignment.CenterStart) {
                if (text.isEmpty() && placeholder.isNotEmpty()) Text(placeholder, style = inter(16f), color = Ani.Muted)
                BasicTextField(
                    text, { text = it.filter(filter).take(max); err = null }, singleLine = !multiline,
                    textStyle = inter(16.5f, lineHeight = 23f).copy(color = Color.White), cursorBrush = SolidColor(Ani.Lavender),
                    keyboardOptions = KeyboardOptions(capitalization = if (multiline) KeyboardCapitalization.Sentences else KeyboardCapitalization.None),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            if (!multiline && text.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(24.dp).clip(CircleShape).background(Color(0xFF6B6870)).clickable { text = ""; err = null },
                    contentAlignment = Alignment.Center,
                ) { Icon(AniIcons.Close, "Clear", tint = Color(0xFF2A2A2E), modifier = Modifier.size(15.dp)) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
            if (previewPrefix != null && !multiline) Text(
                androidx.compose.ui.text.buildAnnotatedString {
                    pushStyle(androidx.compose.ui.text.SpanStyle(color = Ani.Muted)); append(previewPrefix); pop()
                    pushStyle(androidx.compose.ui.text.SpanStyle(color = Color.White, fontWeight = FontWeight.SemiBold)); append(trimmed); pop()
                },
                style = inter(14.5f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            ) else Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text("${text.length}/$max", style = inter(14.5f), color = Ani.Muted)
        }
        err?.let { Spacer(Modifier.height(10.dp)); Text(it, style = inter(14f, lineHeight = 20f), color = Color(0xFFFF8A80)) }
    }
}
