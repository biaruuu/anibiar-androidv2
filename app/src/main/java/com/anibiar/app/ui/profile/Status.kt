package com.anibiar.app.ui.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anibiar.app.AniBiarApp
import com.anibiar.app.social.UserProfile
import com.anibiar.app.ui.theme.Ani
import com.anibiar.app.ui.theme.AniIcons
import com.anibiar.app.ui.theme.inter
import kotlinx.coroutines.launch

internal fun statusColor(s: String?): Color = when (s) {
    "idle" -> Color(0xFFF0B232)
    "dnd" -> Color(0xFFF23F43)
    "invisible" -> Color(0xFF80848E)
    else -> Color(0xFF23A559)
}

internal fun statusLabel(s: String?): String = when (s) {
    "idle" -> "Idle"; "dnd" -> "Do Not Disturb"; "invisible" -> "Invisible"; else -> "Online"
}

/** Discord-style presence dot: green online, crescent idle, red bar for DND, hollow ring for invisible. */
@Composable
internal fun StatusDot(status: String?, size: Dp, ring: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val c = Offset(r, r)
        drawCircle(ring, r, c)
        val ir = r * 0.68f
        val col = statusColor(status)
        when (status) {
            "invisible" -> { drawCircle(col, ir, c); drawCircle(ring, ir * 0.5f, c) }
            "idle" -> { drawCircle(col, ir, c); drawCircle(ring, ir * 0.62f, Offset(c.x - ir * 0.42f, c.y - ir * 0.42f)) }
            "dnd" -> {
                drawCircle(col, ir, c)
                drawRoundRect(ring, Offset(c.x - ir * 0.58f, c.y - ir * 0.17f), Size(ir * 1.16f, ir * 0.34f), androidx.compose.ui.geometry.CornerRadius(ir * 0.17f))
            }
            else -> drawCircle(col, ir, c)
        }
    }
}

/** Custom-status pill under the name. On your own profile an empty one reads "Set a status". */
@Composable
internal fun StatusChip(p: UserProfile, isMe: Boolean, onClick: () -> Unit) {
    val has = !p.statusText.isNullOrBlank() || !p.statusEmoji.isNullOrBlank()
    if (!has && !isMe) return
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.clip(shape).background(Ani.Item).border(1.dp, Ani.Border, shape)
            .then(if (isMe) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (has) {
            if (!p.statusEmoji.isNullOrBlank()) { Text(p.statusEmoji, style = inter(14f)); Spacer(Modifier.width(6.dp)) }
            Text(p.statusText.orEmpty().ifBlank { statusLabel(p.status) }, style = inter(13f, FontWeight.Medium), color = Ani.Soft, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        } else {
            StatusDot(p.status, 12.dp, Ani.Item)
            Spacer(Modifier.width(6.dp))
            Text("Set a status", style = inter(13f, FontWeight.Medium), color = Ani.Muted)
        }
    }
}

private val quickEmoji = listOf("💤", "🎮", "📺", "🍿", "🎧", "💻", "🔥", "❤️", "😴", "📚")

/** "Change Online Status" bottom sheet: pick a presence and set a custom status. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatusSheet(profile: UserProfile?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val social = (context.applicationContext as AniBiarApp).social
    val scope = rememberCoroutineScope()
    var cur by remember { mutableStateOf(profile?.status ?: "online") }
    var emoji by remember { mutableStateOf(profile?.statusEmoji.orEmpty()) }
    var text by remember { mutableStateOf(profile?.statusText.orEmpty()) }
    var err by remember { mutableStateOf<String?>(null) }
    fun fail(e: Throwable) { err = e.message ?: "Couldn't save" }

    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Ani.Panel, contentColor = Color.White,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            Text("Change Online Status", style = inter(19f, FontWeight.SemiBold), color = Color.White, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(18.dp))
            Text("Online Status", style = inter(13.5f), color = Ani.Muted, modifier = Modifier.padding(start = 2.dp, bottom = 8.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Ani.Item)) {
                listOf("online", "idle", "dnd", "invisible").forEachIndexed { i, k ->
                    if (i > 0) Box(Modifier.padding(start = 56.dp).fillMaxWidth().height(1.dp).background(Ani.Border))
                    Row(
                        Modifier.fillMaxWidth().clickable { cur = k; scope.launch { runCatching { social.setStatus(k) }.onFailure(::fail) } }.padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusDot(k, 24.dp, Ani.Item)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(statusLabel(k), style = inter(16f), color = Color.White)
                            if (k == "dnd") Text("You won't get notification sounds", style = inter(12f), color = Ani.Muted)
                            if (k == "invisible") Text("You'll appear offline to everyone", style = inter(12f), color = Ani.Muted)
                        }
                        Box(
                            Modifier.size(24.dp).clip(CircleShape).border(2.dp, if (k == cur) Ani.Lavender else Ani.Border, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { if (k == cur) Box(Modifier.size(12.dp).clip(CircleShape).background(Ani.Lavender)) }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("Custom Status", style = inter(13.5f), color = Ani.Muted, modifier = Modifier.padding(start = 2.dp, bottom = 8.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Ani.Item).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(CircleShape).background(Ani.ButtonDark), contentAlignment = Alignment.Center) {
                        if (emoji.isNotEmpty()) Text(emoji, style = inter(19f)) else Icon(AniIcons.Star, null, tint = Ani.Muted, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f)) {
                        if (text.isEmpty()) Text("What's happening?", style = inter(15.5f), color = Ani.Muted)
                        BasicTextField(text, { text = it.take(60); err = null }, singleLine = true, textStyle = inter(15.5f).copy(color = Color.White), cursorBrush = SolidColor(Ani.Lavender), modifier = Modifier.fillMaxWidth())
                    }
                    if (text.isNotEmpty() || emoji.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier.size(28.dp).clip(CircleShape).clickable {
                                text = ""; emoji = ""
                                scope.launch { runCatching { social.setCustomStatus(null, null) }.onFailure(::fail) }
                            },
                            contentAlignment = Alignment.Center,
                        ) { Icon(AniIcons.Close, "Clear status", tint = Ani.Muted, modifier = Modifier.size(20.dp)) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().horizontalScrollCompat(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                    quickEmoji.forEach { e ->
                        Box(
                            Modifier.size(38.dp).clip(CircleShape).background(if (e == emoji) Ani.Lavender.copy(alpha = 0.25f) else Ani.ButtonDark)
                                .clickable { emoji = if (e == emoji) "" else e },
                            contentAlignment = Alignment.Center,
                        ) { Text(e, style = inter(18f)) }
                    }
                }
            }
            err?.let { Spacer(Modifier.height(8.dp)); Text(it, style = inter(13f), color = Color(0xFFFF8A80)) }
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp)).background(com.anibiar.app.ui.theme.Ani.Lavender).clickable {
                    scope.launch {
                        runCatching { social.setCustomStatus(emoji.ifBlank { null }, text.ifBlank { null }) }.onSuccess { onDismiss() }.onFailure(::fail)
                    }
                },
                contentAlignment = Alignment.Center,
            ) { Text("Save status", style = inter(15.5f, FontWeight.SemiBold), color = Color.White) }
        }
    }
}

@Composable
private fun Modifier.horizontalScrollCompat(): Modifier = this.then(Modifier.horizontalScroll(rememberScrollState()))
