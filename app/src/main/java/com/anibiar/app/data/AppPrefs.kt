package com.anibiar.app.data

import android.content.Context
import android.net.ConnectivityManager
import java.security.MessageDigest
import java.security.SecureRandom

/** Device-level switches: data saver, adult filter access and the app PIN. */
object AppPrefs {
    private fun p(c: Context) = c.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    fun dataSaver(c: Context) = p(c).getBoolean("data_saver", false)
    fun setDataSaver(c: Context, on: Boolean) = p(c).edit().putBoolean("data_saver", on).apply()

    /** True when Data saver is on and the current connection is metered (mobile data, hotspot). */
    fun saveData(c: Context): Boolean = dataSaver(c) &&
        runCatching { (c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).isActiveNetworkMetered }.getOrDefault(true)

    fun autoDownload(c: Context) = p(c).getBoolean("auto_dl", false)
    fun setAutoDownload(c: Context, on: Boolean) = p(c).edit().putBoolean("auto_dl", on).apply()
    fun deleteWatched(c: Context) = p(c).getBoolean("del_watched", false)
    fun setDeleteWatched(c: Context, on: Boolean) = p(c).edit().putBoolean("del_watched", on).apply()
    fun onWifi(c: Context): Boolean = runCatching { !(c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).isActiveNetworkMetered }.getOrDefault(false)

    fun allowAdult(c: Context) = p(c).getBoolean("allow_adult", false)
    fun setAllowAdult(c: Context, on: Boolean) = p(c).edit().putBoolean("allow_adult", on).apply()

    // ---- PIN ----
    fun hasPin(c: Context) = p(c).getString("pin", null) != null
    private fun hash(pin: String, salt: String) = MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray()).joinToString("") { "%02x".format(it) }
    fun setPin(c: Context, pin: String?) {
        if (pin == null) { p(c).edit().remove("pin").remove("salt").apply(); return }
        val salt = ByteArray(8).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        p(c).edit().putString("salt", salt).putString("pin", hash(pin, salt)).apply()
    }
    fun checkPin(c: Context, pin: String): Boolean = p(c).getString("pin", null) == hash(pin, p(c).getString("salt", "").orEmpty())

    fun markBackgrounded(c: Context) = p(c).edit().putLong("bg_at", System.currentTimeMillis()).apply()
    /** The app asks for the PIN again after it was in the background for over 30 s. */
    fun needsUnlock(c: Context) = hasPin(c) && System.currentTimeMillis() - p(c).getLong("bg_at", 0L) > 30_000
}
