package com.anibiar.app.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/** Fires a notification when a scheduled watch party starts. */
class PartyWorker(private val ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val title = inputData.getString("title") ?: return Result.success()
        val ep = inputData.getInt("ep", 1)
        val id = inputData.getInt("id", 0)
        if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("parties", "Watch parties", NotificationManager.IMPORTANCE_HIGH))
        val open = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.let { PendingIntent.getActivity(ctx, id, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) }
        val n = NotificationCompat.Builder(ctx, "parties").setSmallIcon(ctx.applicationInfo.icon)
            .setContentTitle("Watch party is starting").setContentText("$title · episode $ep").setContentIntent(open).setAutoCancel(true).build()
        runCatching { NotificationManagerCompat.from(ctx).notify(900000 + id, n) }
        return Result.success()
    }

    companion object {
        fun schedule(c: Context, animeId: Int, title: String, ep: Int, startAt: Long) {
            val delay = startAt - System.currentTimeMillis()
            if (delay <= 0) return
            val req = OneTimeWorkRequestBuilder<PartyWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("title" to title, "ep" to ep, "id" to animeId)).build()
            WorkManager.getInstance(c.applicationContext).enqueueUniqueWork("party_${animeId}_$startAt", ExistingWorkPolicy.KEEP, req)
        }
    }
}
