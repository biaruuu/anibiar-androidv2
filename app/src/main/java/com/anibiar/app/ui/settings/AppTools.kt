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
    var adult by remember { mutableStateOf(AppPrefs.allowAdult(context)) }
    var hasPin by remember { mutableStateOf(AppPrefs.hasPin(context)) }
    var pinDlg by remember { mutableStateOf<String?>(null) } // "set" | "remove"
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

    SwitchRow("Data saver", "Caps video at 480p on mobile data", saver) { saver = it; AppPrefs.setDataSaver(context, it) }
    SettingsDivider()
    SwitchRow("Adult content filter in search", "Off hides the adult option from Search filters", adult) {
        if (it && hasPin) toast("Adult filter can only be turned on while no PIN is set") else { adult = it; AppPrefs.setAllowAdult(context, it) }
    }
    SettingsDivider()
    ToolRow("App PIN lock", if (hasPin) "On" else "Off") { pinDlg = if (hasPin) "remove" else "set" }
    SettingsDivider()
    ToolRow("Back up my data") { save.launch("kiro-backup.json") }
    SettingsDivider()
    ToolRow("Restore from backup") { load.launch(arrayOf("application/json", "text/plain", "*/*")) }
    SettingsDivider()
    ToolRow("Diagnostics") { diag = true }

    if (pinDlg != null) PinDialog(pinDlg == "set", context, onDone = { ok ->
        if (ok) { hasPin = AppPrefs.hasPin(context); if (hasPin) { adult = false; AppPrefs.setAllowAdult(context, false) } }
        pinDlg = null
    })
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
private fun PinDialog(setting: Boolean, context: Context, onDone: (Boolean) -> Unit) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { onDone(false) }, containerColor = Ani.Panel,
        title = { Text(if (setting) "Set a PIN" else "Turn off PIN", style = inter(18f, FontWeight.SemiBold), color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (setting) "4 to 6 digits. Kiro asks for it after being in the background for 30 seconds. If you forget it, the only way back in is clearing the app's data." else "Enter your current PIN.",
                    style = inter(13.5f, lineHeight = 19f), color = Ani.Soft,
                )
                PinField(a, if (setting) "New PIN" else "PIN") { a = it.filter(Char::isDigit).take(6) }
                if (setting) PinField(b, "Repeat PIN") { b = it.filter(Char::isDigit).take(6) }
                err?.let { Text(it, style = inter(13f), color = Color(0xFFFF8A80)) }
            }
        },
        confirmButton = {
            Text("OK", style = inter(15f, FontWeight.SemiBold), color = Color.White, modifier = Modifier.clickable {
                if (setting) {
                    if (a.length < 4) err = "Use at least 4 digits" else if (a != b) err = "The PINs don't match" else { AppPrefs.setPin(context, a); onDone(true) }
                } else if (AppPrefs.checkPin(context, a)) { AppPrefs.setPin(context, null); onDone(true) } else err = "Wrong PIN"
            }.padding(10.dp))
        },
        dismissButton = { Text("Cancel", style = inter(15f), color = Ani.Muted, modifier = Modifier.clickable { onDone(false) }.padding(10.dp)) },
    )
}

@Composable
private fun PinField(v: String, label: String, onChange: (String) -> Unit) {
    TextField(
        v, onChange, singleLine = true, placeholder = { Text(label) },
        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF1C1C1E)),
    )
}

@Composable
private fun SettingsDivider() { Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x14FFFFFF))) }

/** Full-screen PIN prompt shown over the app until the right PIN is typed. */
@Composable
fun PinLockScreen(onUnlock: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Color(0xFF0B0B0D)).statusBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Enter PIN", style = inter(22f, FontWeight.Bold), color = Color.White)
        Spacer(Modifier.height(6.dp))
        Text(if (wrong) "Wrong PIN, try again" else "Kiro is locked", style = inter(14f), color = if (wrong) Color(0xFFFF8A80) else Ani.Muted)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(6) { i -> Box(Modifier.size(14.dp).clip(RoundedCornerShape(7.dp)).background(if (i < pin.length) Color.White else Color(0x33FFFFFF))) }
        }
        Spacer(Modifier.height(28.dp))
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(vertical = 7.dp)) {
                row.forEach { k ->
                    Box(
                        Modifier.size(72.dp).clip(RoundedCornerShape(36.dp)).background(if (k.isEmpty()) Color.Transparent else Color(0xFF1B1B1F)).clickable(enabled = k.isNotEmpty()) {
                            wrong = false
                            if (k == "⌫") pin = pin.dropLast(1) else if (pin.length < 6) pin += k
                            if (pin.length >= 4 && AppPrefs.checkPin(context, pin)) onUnlock()
                            else if (pin.length == 6) { pin = ""; wrong = true }
                        },
                        contentAlignment = Alignment.Center,
                    ) { Text(k, style = inter(24f, FontWeight.Medium), color = Color.White) }
                }
            }
        }
    }
}
