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
import kotlinx.serialization.json.put
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.draw.alpha
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
import com.anibiar.app.data.displayTitle
import com.anibiar.app.data.posterUrl
import com.anibiar.app.AniBiarApp
import com.anibiar.app.social.ImageProc
import com.anibiar.app.social.UserProfile
import com.anibiar.app.ui.theme.Ani
import com.anibiar.app.ui.theme.AniIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
    val pickBanner = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) scope.launch {
            busy = true
            try { val b = withContext(Dispatchers.Default) { ImageProc.banner(context, uri) }; social.setBanner(b.bytes, b.mime, b.ext) } catch (e: Exception) { toast(e.message ?: "Couldn't upload") }
            busy = false
        }
    }

    if (showStatus) StatusSheet(profile) { showStatus = false }
    Box(Modifier.fillMaxSize().background(Ani.Onyx)) {
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
            "look" -> LookPage(
                profile, onBack = { page = "main" },
                onSave = { d, e, th -> scope.launch { try { social.setLook(d, e, th) } catch (x: Exception) { toast(x.message ?: "Couldn't save") } }; page = "main" },
            )
            "highlights" -> HighlightsPage(onBack = { page = "main" })
            "bio" -> FieldPage(
                "Bio", "You can edit your bio anytime.", profile?.bio.orEmpty(), 200,
                filter = { true }, allowEmpty = true, multiline = true, placeholder = "My account is all about…", onCancel = { page = "main" },
            ) { social.setBio(it); page = "main" }
            else -> Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.align(Alignment.CenterStart).padding(start = 14.dp)) {
                        com.anibiar.app.ui.theme.GlassBack(onBack)
                    }
                    Text("Edit profile", style = inter(17f, FontWeight.Bold), color = Color.White)
                }
                Spacer(Modifier.height(12.dp))
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(104.dp).clickable { pickAvatar.launch("image/*") }, contentAlignment = Alignment.Center) {
                        Avatar(profile?.avatarUrl, profile?.shownName, 104.dp, Modifier.border(2.dp, Color.White, CircleShape), deco = profile?.avatarDeco)
                        Box(Modifier.size(104.dp).clip(CircleShape).background(Color(0x66000000)), contentAlignment = Alignment.Center) {
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
                        "Highlights",
                        "Collections like Best Isekai, with who can view them", onClick = { page = "highlights" },
                    )
                    FormRow(
                        "Avatar decoration & effect",
                        listOfNotNull(Looks.deco(profile?.avatarDeco)?.name, Looks.effects.firstOrNull { it.id == profile?.profileEffect }?.name).joinToString(" + ").ifBlank { "None" },
                        onClick = { page = "look" },
                    )
                    FormRow(
                        "Banner", "", onClick = { pickBanner.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                        trailing = {
                            Box(Modifier.width(72.dp).height(34.dp).clip(RoundedCornerShape(8.dp)).background(com.anibiar.app.ui.theme.GlassFill)) {
                                if (profile?.bannerUrl != null) BannerMedia(profile.bannerUrl, Modifier.fillMaxSize())
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
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(com.anibiar.app.ui.theme.GlassFill).border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(20.dp))) { content() }
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
                .clip(RoundedCornerShape(16.dp)).background(Color(0xFF151517)).border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = if (multiline) 14.dp else 0.dp),
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun LookPage(profile: UserProfile?, onBack: () -> Unit, onSave: (String?, String?, String?) -> Unit) {
    var theme by remember { mutableStateOf(profile?.profileTheme) }
    var deco by remember { mutableStateOf(profile?.avatarDeco) }
    var fx by remember { mutableStateOf(profile?.profileEffect) }
    val liveLevel = com.anibiar.app.ui.engage.rememberEngage().second?.level ?: 1
    val dev = (LocalContext.current.applicationContext as AniBiarApp).social.authState.collectAsState().value?.email.equals(com.anibiar.app.ui.admin.ADMIN_EMAIL, ignoreCase = true)
    val level = if (dev) 999 else maxOf(liveLevel, com.anibiar.app.data.EngageCalc.levelOf(profile?.xp ?: 0L))
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.align(Alignment.CenterStart).padding(start = 14.dp)) { com.anibiar.app.ui.theme.GlassBack(onBack) }
            Text("Decoration & effect", style = inter(17f, FontWeight.Bold), color = Color.White)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            // Live preview of the namecard
            val shape = RoundedCornerShape(18.dp)
            Box(Modifier.fillMaxWidth().clip(shape).background(com.anibiar.app.ui.theme.GlassFill).border(1.dp, Color(0x26FFFFFF), shape)) {
                if (profile?.bannerUrl != null) BannerMedia(profile.bannerUrl, Modifier.matchParentSize(), alpha = 0.5f)
                Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(Color(0x66151517), Color(0xE6151517), Color(0xFF151517)))))
                ThemeTint(theme, Modifier.matchParentSize())
                EffectLayer(fx, Modifier.matchParentSize())
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(profile?.avatarUrl, profile?.shownName, 64.dp, deco = deco)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(profile?.shownName.orEmpty(), style = inter(16.5f, FontWeight.Bold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("@${profile?.username.orEmpty()}", style = inter(12.5f), color = Ani.Muted, maxLines = 1)
                    }
                }
            }
            Text(if (dev) "Developer: everything is unlocked." else "Level $level. Decorations and effects unlock as you level up by watching.", style = inter(12.5f), color = Ani.Muted, modifier = Modifier.padding(start = 6.dp, top = 14.dp))
            Text("Avatar decoration", style = inter(13.5f, FontWeight.SemiBold), color = Ani.Muted, modifier = Modifier.padding(start = 6.dp, top = 22.dp, bottom = 10.dp))
            androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LookTile("None", deco == null, { deco = null }) { androidx.compose.material3.Icon(com.anibiar.app.ui.theme.AniIcons.Close, null, tint = Ani.Muted, modifier = Modifier.size(24.dp)) }
                Looks.decos.forEach { d -> LookTile(d.name, deco == d.id, { deco = d.id }, lockLevel = Looks.decoReq(d.id).takeIf { it > level }) { Avatar(null, profile?.shownName, 44.dp, deco = d.id) } }
            }
            Text("Namecard effect", style = inter(13.5f, FontWeight.SemiBold), color = Ani.Muted, modifier = Modifier.padding(start = 6.dp, top = 22.dp, bottom = 10.dp))
            androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LookTile("None", fx == null, { fx = null }) { androidx.compose.material3.Icon(com.anibiar.app.ui.theme.AniIcons.Close, null, tint = Ani.Muted, modifier = Modifier.size(24.dp)) }
                Looks.effects.forEach { e -> LookTile(e.name, fx == e.id, { fx = e.id }, lockLevel = Looks.fxReq(e.id).takeIf { it > level }) { Box(Modifier.size(58.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF151517))) { EffectLayer(e.id, Modifier.matchParentSize()) } } }
            }
            Text("Profile theme", style = inter(13.5f, FontWeight.SemiBold), color = Ani.Muted, modifier = Modifier.padding(start = 6.dp, top = 22.dp, bottom = 10.dp))
            androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LookTile("None", theme == null, { theme = null }) { androidx.compose.material3.Icon(com.anibiar.app.ui.theme.AniIcons.Close, null, tint = Ani.Muted, modifier = Modifier.size(24.dp)) }
                Looks.themes.forEach { th -> LookTile(th.name, theme == th.id, { theme = th.id }) { Box(Modifier.size(44.dp).clip(CircleShape).background(th.color)) } }
            }
            Spacer(Modifier.height(20.dp))
        }
        Text(
            "Save", style = inter(15f, FontWeight.Bold), color = Color.Black, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth().clip(RoundedCornerShape(50)).background(Color.White).clickable { onSave(deco, fx, theme) }.padding(vertical = 13.dp),
        )
    }
}

@Composable
private fun LookTile(label: String, selected: Boolean, onClick: () -> Unit, lockLevel: Int? = null, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.width(92.dp).clip(shape).background(if (selected) SolidColor(Color(0xFF2A2A2E)) else com.anibiar.app.ui.theme.GlassFill)
            .border(1.dp, if (selected) Color(0x99FFFFFF) else Color(0x1AFFFFFF), shape).clickable(enabled = lockLevel == null, onClick = onClick).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.height(66.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.then(if (lockLevel != null) Modifier.alpha(0.35f) else Modifier)) { content() }
            if (lockLevel != null) androidx.compose.material3.Icon(com.anibiar.app.ui.theme.EngageIcons.Lock, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(if (lockLevel != null) "Level $lockLevel" else label, style = inter(12f, FontWeight.Medium), color = if (lockLevel != null) Ani.Muted else Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private val AUDIENCES = listOf("everyone" to "Everyone", "friends" to "Friends only", "me" to "Only me")

/** Highlights: collections of anime (Best Isekai, Comfort shows...) each with its own audience. */
@Composable
private fun HighlightsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val social = (context.applicationContext as AniBiarApp).social
    val scope = rememberCoroutineScope()
    val uid = social.myId
    var list by remember { mutableStateOf<List<com.anibiar.app.social.Highlight>?>(null) }
    var editing by remember { mutableStateOf<com.anibiar.app.social.Highlight?>(null) }
    var creating by remember { mutableStateOf(false) }
    fun reload() { scope.launch { list = if (uid == null) emptyList() else runCatching { social.highlightsOf(uid) }.getOrDefault(emptyList()) } }
    LaunchedEffect(uid) { reload() }
    BackHandler(enabled = editing != null || creating) { editing = null; creating = false }
    if (editing != null || creating) {
        HighlightEditor(editing, onBack = { editing = null; creating = false }) { editing = null; creating = false; reload() }
        return
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.align(Alignment.CenterStart).padding(start = 14.dp)) { com.anibiar.app.ui.theme.GlassBack(onBack) }
            Text("Highlights", style = inter(17f, FontWeight.Bold), color = Color.White)
        }
        Text("Group anime into collections that show on your profile as round covers. Each one has its own audience.", style = inter(13f), color = Ani.Muted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        list?.forEach { h ->
            Row(Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Ani.Panel).clickable { editing = h }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).border(2.dp, Ani.Lavender, CircleShape).padding(3.dp).clip(CircleShape).background(Ani.Item)) { com.anibiar.app.ui.home.PosterImage(h.items.firstOrNull()?.cover, h.title, Modifier.fillMaxSize(), maxSide = 200) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(h.title, style = inter(15f, FontWeight.SemiBold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${h.items.size} anime · ${AUDIENCES.firstOrNull { it.first == h.visibility }?.second ?: "Everyone"}", style = inter(12f), color = Ani.Muted)
                }
            }
        }
        if (list == null) Text("Loading…", style = inter(13.5f), color = Ani.Muted, modifier = Modifier.padding(20.dp))
        Text("New highlight", style = inter(14.5f, FontWeight.Bold), color = Color.Black, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(50)).background(Color.White).clickable { creating = true }.padding(vertical = 13.dp))
    }
}

@Composable
private fun HighlightEditor(existing: com.anibiar.app.social.Highlight?, onBack: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val social = (context.applicationContext as AniBiarApp).social
    val scope = rememberCoroutineScope()
    val uid = social.myId
    val favs by remember(uid) { if (uid != null) social.favorites(uid) else kotlinx.coroutines.flow.flowOf(emptyList()) }.collectAsState(initial = emptyList())
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var vis by remember { mutableStateOf(existing?.visibility ?: "everyone") }
    val chosen = remember { androidx.compose.runtime.mutableStateListOf<com.anibiar.app.social.Fav>().apply { addAll(existing?.items.orEmpty()) } }
    var busy by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<com.anibiar.app.social.Fav>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    val repo = (context.applicationContext as AniBiarApp).repository
    LaunchedEffect(query) {
        if (query.isBlank()) { results = emptyList(); searching = false; return@LaunchedEffect }
        searching = true
        kotlinx.coroutines.delay(350)
        results = runCatching {
            val vars = kotlinx.serialization.json.buildJsonObject {
                put("page", 1); put("perPage", 24); put("search", query.trim())
                put("sort", kotlinx.serialization.json.JsonArray(listOf(kotlinx.serialization.json.JsonPrimitive("SEARCH_MATCH"))))
            }
            repo.search(vars).media.map { m -> com.anibiar.app.social.Fav(m.id, m.displayTitle, m.posterUrl) }
        }.getOrDefault(emptyList())
        searching = false
    }
    fun toast(m: String) = Toast.makeText(context, m, Toast.LENGTH_SHORT).show()
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.align(Alignment.CenterStart).padding(start = 14.dp)) { com.anibiar.app.ui.theme.GlassBack(onBack) }
            Text(if (existing == null) "New highlight" else "Edit highlight", style = inter(17f, FontWeight.Bold), color = Color.White)
            Text(if (busy) "Saving…" else "Save", style = inter(15f, FontWeight.Bold), color = Ani.Lavender, modifier = Modifier.align(Alignment.CenterEnd).clickable(enabled = !busy) {
                if (title.isBlank()) { toast("Give it a title"); return@clickable }
                if (chosen.isEmpty()) { toast("Add at least one anime"); return@clickable }
                busy = true
                scope.launch { try { social.saveHighlight(existing?.id, title, vis, chosen.toList()); onDone() } catch (x: Exception) { toast(x.message ?: "Couldn't save"); busy = false } }
            }.padding(horizontal = 18.dp, vertical = 10.dp))
        }
        Text("Title", style = inter(13f, FontWeight.SemiBold), color = Ani.Soft, modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 6.dp))
        androidx.compose.foundation.text.BasicTextField(title, { title = it.take(24) }, singleLine = true,
            textStyle = inter(16f, FontWeight.SemiBold).copy(color = Color.White), cursorBrush = androidx.compose.ui.graphics.SolidColor(Ani.Lavender),
            decorationBox = { inner -> Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Ani.Panel).padding(14.dp)) { if (title.isEmpty()) Text("Best Isekai", style = inter(16f), color = Ani.Muted); inner() } })
        Text("Who can view", style = inter(13f, FontWeight.SemiBold), color = Ani.Soft, modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 8.dp))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AUDIENCES.forEach { (id, label) ->
                val on = vis == id
                Text(label, style = inter(13.5f, FontWeight.SemiBold), color = if (on) Color.Black else Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Ani.Lavender else Ani.ButtonDark).clickable { vis = id }.padding(horizontal = 16.dp, vertical = 9.dp))
            }
        }
        Text("Anime in this highlight (${chosen.size})", style = inter(13f, FontWeight.SemiBold), color = Ani.Soft, modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 8.dp))
        if (chosen.isNotEmpty()) androidx.compose.foundation.lazy.LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(chosen.toList(), key = { it.animeId }) { f ->
                Box(Modifier.width(64.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(Ani.Item).clickable { chosen.removeAll { it.animeId == f.animeId } }) {
                    com.anibiar.app.ui.home.PosterImage(f.cover, f.title, Modifier.fillMaxSize(), maxSide = 240)
                    Icon(AniIcons.Close, "Remove", tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).clip(CircleShape).background(Color(0xB3000000)).padding(3.dp).size(14.dp))
                }
            }
        }
        Text("Add anime", style = inter(13f, FontWeight.SemiBold), color = Ani.Soft, modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 8.dp))
        androidx.compose.foundation.text.BasicTextField(query, { query = it.take(60) }, singleLine = true,
            textStyle = inter(15f).copy(color = Color.White), cursorBrush = androidx.compose.ui.graphics.SolidColor(Ani.Lavender),
            decorationBox = { inner -> Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Ani.Panel).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(AniIcons.Search, null, tint = Ani.Muted, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) { if (query.isEmpty()) Text("Search any anime", style = inter(15f), color = Ani.Muted); inner() }
            } })
        Text(if (query.isBlank()) "Your favorites" else if (searching) "Searching…" else "Results", style = inter(12f), color = Ani.Muted, modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 8.dp))
        val shown = if (query.isBlank()) favs else results
        if (!searching && shown.isEmpty()) Text(if (query.isBlank()) "Favorite some anime or search above." else "Nothing found.", style = inter(13f), color = Ani.Muted, modifier = Modifier.padding(horizontal = 20.dp))
        shown.chunked(3).forEach { row ->
            Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { f ->
                    val on = chosen.any { it.animeId == f.animeId }
                    Column(Modifier.weight(1f).clickable { if (on) chosen.removeAll { it.animeId == f.animeId } else if (chosen.size < 40) chosen.add(f) }) {
                        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).background(Ani.Item).border(if (on) 2.dp else 0.dp, Ani.Lavender, RoundedCornerShape(12.dp))) {
                            com.anibiar.app.ui.home.PosterImage(f.cover, f.title, Modifier.fillMaxSize(), alpha = if (on) 0.55f else 1f, maxSide = 360)
                            if (on) Icon(AniIcons.CheckCircle, null, tint = Ani.Lavender, modifier = Modifier.align(Alignment.Center).size(30.dp))
                        }
                        Text(f.title, style = inter(11.5f, FontWeight.Medium), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (existing != null) Text("Delete highlight", style = inter(14f, FontWeight.SemiBold), color = Color(0xFFFF6B6B), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(16.dp).padding(top = 20.dp).fillMaxWidth().clip(RoundedCornerShape(50)).border(1.dp, Color(0x55FF6B6B), RoundedCornerShape(50)).clickable {
                scope.launch { runCatching { social.deleteHighlight(existing.id) }; onDone() }
            }.padding(vertical = 13.dp))
        Spacer(Modifier.height(32.dp))
    }
}
