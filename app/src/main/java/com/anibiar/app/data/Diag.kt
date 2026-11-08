package com.anibiar.app.data

import android.content.Context
import android.os.Build
import com.anibiar.app.nativecore.Native
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Rolling event log (last ~300 lines) for problems the user can't see: stream failures, caption errors, network trouble. */
object Diag {
    private val lock = Any()
    private var file: File? = null
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

    fun init(c: Context) { file = File(c.applicationContext.filesDir, "diag.log") }

    fun log(tag: String, msg: String) {
        val f = file ?: return
        synchronized(lock) {
            runCatching {
                f.appendText("${fmt.format(Date())} [$tag] ${msg.take(300).replace('\n', ' ')}\n")
                if (f.length() > 60_000) f.writeText(f.readLines().takeLast(300).joinToString("\n", postfix = "\n"))
            }
        }
    }

    fun clear() { synchronized(lock) { runCatching { file?.delete() } } }

    /** Everything worth attaching to a bug report. No account details or tokens. */
    fun report(c: Context): String = buildString {
        val v = runCatching { c.packageManager.getPackageInfo(c.packageName, 0).versionName }.getOrNull()
        appendLine("Kiro $v · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Rust core: ${Native.versionOrNull() ?: "not loaded"} · Data saver: ${AppPrefs.dataSaver(c)}")
        appendLine("---")
        appendLine(synchronized(lock) { runCatching { file?.takeIf { it.exists() }?.readText() }.getOrNull() ?: "(no events logged)" })
    }
}
