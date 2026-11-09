package com.anibiar.app.ui.settings

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.anibiar.app.data.AppPrefs
import com.anibiar.app.data.Backup
import com.anibiar.app.data.Diag
import com.anibiar.app.ui.theme.Ani
import com.anibiar.app.ui.theme.inter

@Composable
internal fun SwitchRow(label: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = inter(15f), color = Color.White)
            if (sub != null) Text(sub, style = inter(12.5f, lineHeight = 17f), color = Ani.Muted)
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Ani.Lavender, checkedThumbColor = Color.Black))
    }
}

@Composable
internal fun ToolRow(label: String, value: String = "", onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = inter(15f), color = Color.White, modifier = Modifier.weight(1f))
        if (value.isNotEmpty()) Text(value, style = inter(14f), color = Ani.Muted)
    }
}

/** Data saver, adult filter, PIN lock, backup/restore and diagnostics. */
@Composable
fun AppToolRows(context: Context, toast: (String) -> Unit) {
    var saver by remember { mutableStateOf(AppPrefs.dataSaver(context)) }
    var diag by remember { mutableStateOf(false) }
    var restoreNote by remember { mutableStateOf<String?>(null) }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)!!.use { it.write(Backup.export(context).toByteArray()) }
            toast("Backup saved")
        }.onFailure { toast("Couldn't save the backup") }
    }
    val load = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val text = context.contentResolver.openInputStream(uri)!!.use { String(it.readBytes()) }
            Backup.restore(context, text)
        }.onSuccess { restoreNote = "Restored $it items." }.onFailure { toast(it.message ?: "Couldn't read that file") }
    }

    var alerts by remember { mutableStateOf(com.anibiar.app.data.NewEpisodeWorker.enabled(context)) }
    var health by remember { mutableStateOf(com.anibiar.app.data.Health.enabled(context)) }
    var cacheSize by remember { mutableStateOf("…") }
    var cacheTick by remember { mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(cacheTick) {
        cacheSize = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val bytes = context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            if (bytes >= 1_048_576) "${bytes / 1_048_576} MB" else "${bytes / 1024} KB"
        }
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { alerts = true; com.anibiar.app.data.NewEpisodeWorker.set(context, true) } else toast("Allow notifications to get alerts")
    }

    SwitchRow("New episode alerts", "Tells you when a show you watch or saved has a new episode", alerts) { on ->
        if (!on) { alerts = false; com.anibiar.app.data.NewEpisodeWorker.set(context, false) }
        else if (android.os.Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) ask.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        else { alerts = true; com.anibiar.app.data.NewEpisodeWorker.set(context, true) }
    }
    SettingsDivider()
    SwitchRow("Data saver", "Caps video at 480p on mobile data", saver) { saver = it; AppPrefs.setDataSaver(context, it) }
    SettingsDivider()
    SwitchRow("Anonymous health report", "Once a day: app version, device model and error counts. No account info", health) { health = it; com.anibiar.app.data.Health.setEnabled(context, it) }
    SettingsDivider()
    ToolRow("Clear image cache", cacheSize) {
        runCatching { coil.Coil.imageLoader(context).let { it.memoryCache?.clear(); it.diskCache?.clear() } }
        cacheTick++
        toast("Cache cleared")
    }
    SettingsDivider()
    ToolRow("Back up my data") { save.launch("kiro-backup.json") }
    SettingsDivider()
    ToolRow("Restore from backup") { load.launch(arrayOf("application/json", "text/plain", "*/*")) }
    SettingsDivider()
    ToolRow("Diagnostics") { diag = true }

    restoreNote?.let {
        AlertDialog(
            onDismissRequest = {}, containerColor = Ani.Panel,
            title = { Text("Backup restored", style = inter(18f, FontWeight.SemiBold), color = Color.White) },
            text = { Text("$it Kiro will close so everything loads cleanly. Open it again.", style = inter(14f, lineHeight = 20f), color = Ani.Soft) },
            confirmButton = { Text("Close Kiro", style = inter(15f, FontWeight.SemiBold), color = Color.White, modifier = Modifier.clickable { android.os.Process.killProcess(android.os.Process.myPid()) }.padding(10.dp)) },
        )
    }
    if (diag) {
        val text = remember { Diag.report(context) }
        AlertDialog(
            onDismissRequest = { diag = false }, containerColor = Ani.Panel,
            title = { Text("Diagnostics", style = inter(18f, FontWeight.SemiBold), color = Color.White) },
            text = { Text(text.takeLast(3500), style = inter(11f, lineHeight = 15f), color = Ani.Soft, modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) },
            confirmButton = {
                Text("Share report", style = inter(15f, FontWeight.SemiBold), color = Color.White, modifier = Modifier.clickable {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share report"))
                }.padding(10.dp))
            },
            dismissButton = {
                Row {
                    Text("Clear", style = inter(15f), color = Ani.Muted, modifier = Modifier.clickable { Diag.clear(); diag = false }.padding(10.dp))
                    Text("Close", style = inter(15f), color = Ani.Muted, modifier = Modifier.clickable { diag = false }.padding(10.dp))
                }
            },
        )
    }
}

@Composable
private fun SettingsDivider() { Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x14FFFFFF))) }
