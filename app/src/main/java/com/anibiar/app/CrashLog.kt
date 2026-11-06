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

    /** Returns and clears the report saved by the previous crash, if any. */
    fun take(c: Context): String? = runCatching {
        val f = file(c)
        if (f.exists()) f.readText().also { f.delete() } else null
    }.getOrNull()
}
