package com.anibiar.app.data

import android.content.Context
import com.anibiar.app.nativecore.Native

/** Remembers how fast the connection was recently so playback can start on a fitting quality instead of guessing. */
object NetEstimate {
    private fun p(c: Context) = c.applicationContext.getSharedPreferences("net_prefs", Context.MODE_PRIVATE)
    private fun key(c: Context) = if (runCatching { (c.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).isActiveNetworkMetered }.getOrDefault(false)) "mobile" else "wifi"
    private var lastAt = 0L

    fun record(c: Context, bps: Long) {
        val now = System.currentTimeMillis()
        if (bps <= 0 || now - lastAt < 4_000) return
        lastAt = now
        val k = key(c)
        val list = p(c).getString(k, "").orEmpty().split(',').filter { it.isNotBlank() }.takeLast(23) + bps.toString()
        p(c).edit().putString(k, list.joinToString(",")).apply()
    }

    /** Starting estimate in bits per second; 6 Mbps when there's no history yet. */
    fun initial(c: Context): Long {
        val s = p(c).getString(key(c), "").orEmpty().split(',').mapNotNull { it.toDoubleOrNull() }
        return (Native.bandwidth(s) ?: 6_000_000.0).toLong().coerceIn(500_000L, 40_000_000L)
    }
}
