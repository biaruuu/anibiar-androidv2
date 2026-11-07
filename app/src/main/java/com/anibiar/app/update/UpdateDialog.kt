package com.anibiar.app.update

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.anibiar.app.ui.theme.Ani
import com.anibiar.app.ui.theme.inter
import kotlinx.coroutines.launch
import java.io.File

/** "New version" prompt: downloads in the app and opens the installer over the current app. */
@Composable
fun UpdateDialog(info: UpdateInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableFloatStateOf(0f) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var file by remember { mutableStateOf<File?>(null) }
    var needsPermission by remember { mutableStateOf(false) }

    fun go() {
        error = null
        val f = file
        if (f != null) { needsPermission = !Updater.install(context, f); return }
        busy = true
        scope.launch {
            runCatching { Updater.download(context, info) { progress = it } }
                .onSuccess { file = it; busy = false; needsPermission = !Updater.install(context, it) }
                .onFailure { busy = false; error = it.message ?: "Download failed" }
        }
    }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(com.anibiar.app.ui.theme.GlassFill).padding(22.dp)) {
            Text("Update available", style = inter(19f, FontWeight.Bold), color = Color.White)
            Spacer(Modifier.height(6.dp))
            Text(
                "Build ${info.build} is ready (${"%.0f".format(info.size / 1_000_000f)} MB). It installs over your current app, so you stay logged in and keep your data.",
                style = inter(14f, lineHeight = 20f), color = Ani.Muted,
            )
            if (info.entries.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("What's new", style = inter(13f, FontWeight.SemiBold), color = Ani.Soft)
                Spacer(Modifier.height(6.dp))
                Column(Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState())) { ChangeList(info.entries) }
            }
            if (needsPermission) {
                Spacer(Modifier.height(10.dp))
                Text("Allow Kiro to install updates in the settings screen that just opened, come back, then tap Install.", style = inter(13f, lineHeight = 18f), color = Ani.Amber)
            }
            error?.let { Spacer(Modifier.height(10.dp)); Text(it, style = inter(13f), color = Color(0xFFFF6B6B)) }
            if (busy) {
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(Ani.Item)) {
                    Box(Modifier.fillMaxWidth(progress.coerceIn(0.02f, 1f)).height(8.dp).clip(RoundedCornerShape(50)).background(Ani.Lime))
                }
                Spacer(Modifier.height(6.dp))
                Text("Downloading… ${(progress * 100).toInt()}%", style = inter(12.5f), color = Ani.Muted)
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (!busy) Text("Later", style = inter(14f, FontWeight.SemiBold), color = Ani.Muted, modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onDismiss).padding(horizontal = 14.dp, vertical = 10.dp))
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(if (busy) Ani.ButtonDark else Ani.Lime).clickable(enabled = !busy) { go() }.padding(horizontal = 22.dp, vertical = 11.dp),
                ) { Text(if (busy) "Downloading" else if (file != null) "Install" else "Update now", style = inter(14.5f, FontWeight.Bold), color = if (busy) Ani.Muted else Color.Black) }
            }
        }
    }
}
