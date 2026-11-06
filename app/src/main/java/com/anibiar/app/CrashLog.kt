package com.anibiar.app

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Crash safety net. An error on a background thread (network, player loader, a stray coroutine) is logged and
 * ignored so it can't close the app; an error on the main thread is saved so the next launch can show what happened.
 */
object CrashLog {
    private fun file(c: Context) = File(c.filesDir, "last-crash.txt")

    fun install(app: Context) {
        val ctx = app.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            val text = buildString {
                append("Thread: ").append(t.name).append('\n')
                append(Log.getStackTraceString(e).take(6000))
            }
            runCatching { file(ctx).writeText(text) }
            Log.e("KiroCrash", text)
            val main = android.os.Looper.getMainLooper().thread
            if (t !== main && e !is OutOfMemoryError) return@setDefaultUncaughtExceptionHandler
            previous?.uncaughtException(t, e)
        }
    }

    /**
     * Returns and clears the report from the previous crash, if any. A Java error is saved by the handler above;
     * a native crash, out-of-memory kill or freeze never reaches it, so Android's own exit history is checked too.
     */
    fun take(c: Context): String? {
        val saved = runCatching { file(c).let { f -> if (f.exists()) f.readText().also { f.delete() } else null } }.getOrNull()
        if (saved != null) return saved
        if (android.os.Build.VERSION.SDK_INT < 30) return null
        return runCatching {
            val prefs = c.getSharedPreferences("crash_log", Context.MODE_PRIVATE)
            val am = c.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val last = prefs.getLong("seen", 0L)
            val info = am.getHistoricalProcessExitReasons(c.packageName, 0, 5).filter { it.timestamp > last }.maxByOrNull { it.timestamp }
                ?: return@runCatching null
            prefs.edit().putLong("seen", info.timestamp).apply()
            val reason = when (info.reason) {
                android.app.ApplicationExitInfo.REASON_CRASH -> "CRASH"
                android.app.ApplicationExitInfo.REASON_CRASH_NATIVE -> "NATIVE_CRASH"
                android.app.ApplicationExitInfo.REASON_ANR -> "NOT_RESPONDING"
                android.app.ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
                android.app.ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
                android.app.ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
                android.app.ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INIT_FAILURE"
                else -> return@runCatching null
            }
            "Exit reason: $reason\nStatus: ${info.status}\nDescription: ${info.description}\nMemory (PSS/RSS KB): ${info.pss}/${info.rss}\nTime: ${java.util.Date(info.timestamp)}"
        }.getOrNull()
    }
}
