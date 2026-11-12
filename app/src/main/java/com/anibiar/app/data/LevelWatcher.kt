package com.anibiar.app.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.anibiar.app.AniBiarApp
import com.anibiar.app.ui.profile.Looks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Tracks the level while the person watches and announces "Level N reached!" once per level. */
object LevelWatcher {
    suspend fun check(app: AniBiarApp) {
        val e = withContext(Dispatchers.Default) {
            app.stats.flush()
            app.stats.summary()?.let { EngageCalc.compute(it) }
        } ?: return
        val prefs = app.getSharedPreferences("engage", Context.MODE_PRIVATE)
        val last = prefs.getInt("last_level", 0)
        if (e.level > last) prefs.edit().putInt("last_level", e.level).apply()
        runCatching {
            val dev = app.social.authState.value?.email.equals(com.anibiar.app.ui.admin.ADMIN_EMAIL, ignoreCase = true)
            app.social.setXp(e.xp, if (dev) com.anibiar.app.ui.engage.BADGES.keys.toList() else e.badges)
        }
        if (last in 1 until e.level) announce(app, e.level)
    }

    private suspend fun announce(ctx: Context, level: Int) {
        val unlock = Looks.unlockedAt(level)
        val text = if (unlock.isNotEmpty()) "Unlocked: ${unlock.joinToString(", ")}" else "Keep watching to reach the next one."
        withContext(Dispatchers.Main) { Toast.makeText(ctx, "Level $level reached!", Toast.LENGTH_LONG).show() }
        if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("levels", "Level ups", NotificationManager.IMPORTANCE_DEFAULT))
        val open = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.let { PendingIntent.getActivity(ctx, 7001, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) }
        val n = NotificationCompat.Builder(ctx, "levels").setSmallIcon(ctx.applicationInfo.icon)
            .setContentTitle("Level $level reached!").setContentText(text).setContentIntent(open).setAutoCancel(true).build()
        runCatching { NotificationManagerCompat.from(ctx).notify(7000 + level, n) }
    }
}
