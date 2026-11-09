package com.anibiar.app.data

import android.content.Context
import android.os.Build
import com.anibiar.app.social.SupabaseConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File

/** One anonymous health line a day: app version, Android version, device model and problem counts. No account, no tokens. */
object Health {
    private fun p(c: Context) = c.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    fun enabled(c: Context) = p(c).getBoolean("health_reports", true)
    fun setEnabled(c: Context, on: Boolean) = p(c).edit().putBoolean("health_reports", on).apply()

    fun send(c: Context, crashed: Boolean) {
        if (!enabled(c)) return
        val prefs = p(c)
        val now = System.currentTimeMillis()
        if (!crashed && now - prefs.getLong("health_at", 0L) < 24 * 3600_000L) return
        runCatching {
            val log = runCatching { File(c.filesDir, "diag.log").readText() }.getOrDefault("")
            val v = c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: "?"
            val body = JSONObject()
                .put("app_version", v.take(32)).put("sdk", Build.VERSION.SDK_INT)
                .put("device", "${Build.MANUFACTURER} ${Build.MODEL}".take(80))
                .put("slow_frames", log.lines().count { "[frames]" in it })
                .put("stream_errors", log.lines().count { "[player]" in it || "[stream]" in it })
                .put("crashed", crashed)
            val req = Request.Builder().url("${SupabaseConfig.URL}/rest/v1/app_health")
                .header("apikey", SupabaseConfig.PUBLISHABLE_KEY).header("Prefer", "return=minimal")
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()
            Http.plain.newCall(req).execute().use { if (it.isSuccessful) prefs.edit().putLong("health_at", now).apply() }
        }
    }
}
