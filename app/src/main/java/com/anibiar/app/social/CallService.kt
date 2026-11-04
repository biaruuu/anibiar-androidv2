package com.anibiar.app.social

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Keeps the microphone alive while you're in a watch-party call. Without a foreground service Android silences
 * the mic as soon as the app is minimised or the screen turns off, so friends stop hearing you.
 */
class CallService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("call", "Watch party call", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            this, 0, packageManager.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n: Notification = NotificationCompat.Builder(this, "call")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("In a watch party call")
            .setContentText("Tap to return to Kiro")
            .setOngoing(true).setContentIntent(open).setCategory(NotificationCompat.CATEGORY_CALL).build()
        runCatching {
            ServiceCompat.startForeground(
                this, 41, n,
                if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0,
            )
        }.onFailure { stopSelf() }
        return START_NOT_STICKY
    }

    companion object {
        fun start(context: Context) { runCatching { ContextCompat.startForegroundService(context, Intent(context, CallService::class.java)) } }
        fun stop(context: Context) { runCatching { context.stopService(Intent(context, CallService::class.java)) } }
    }
}
