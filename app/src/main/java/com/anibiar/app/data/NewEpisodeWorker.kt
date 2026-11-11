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
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.anibiar.app.AniBiarApp
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Checks every half hour whether a show you watch or saved has a new episode out, and says so in a notification. */
class NewEpisodeWorker(private val ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (!enabled(ctx)) return Result.success()
        val app = ctx.applicationContext as AniBiarApp
        val ids = (app.history.load().take(30).map { it.mediaId } + app.watchlist.ids.value).distinct().take(50)
        if (ids.isEmpty()) return Result.success()
        val query = "query(\$ids:[Int]){Page(perPage:50){media(id_in:\$ids,type:ANIME){id status title{romaji english} nextAiringEpisode{episode} episodes}}}"
        val body = JSONObject().put("query", query).put("variables", JSONObject().put("ids", org.json.JSONArray(ids))).toString()
        val text = runCatching {
            Http.client.newCall(Request.Builder().url("https://graphql.anilist.co").post(body.toRequestBody("application/json".toMediaType())).build()).execute().use { if (it.isSuccessful) it.body?.string() else null }
        }.getOrNull() ?: return Result.retry()
        val media = runCatching { JSONObject(text).getJSONObject("data").getJSONObject("Page").getJSONArray("media") }.getOrNull() ?: return Result.retry()
        val seen = ctx.getSharedPreferences("ep_alerts", Context.MODE_PRIVATE)
        val edit = seen.edit()
        for (i in 0 until media.length()) {
            val m = media.getJSONObject(i)
            if (m.optString("status") != "RELEASING") continue
            val id = m.getInt("id")
            val next = m.optJSONObject("nextAiringEpisode")?.optInt("episode", 0) ?: 0
            if (next <= 1) continue
            val aired = next - 1
            val before = seen.getInt("n$id", -1)
            edit.putInt("n$id", aired)
            if (before in 0 until aired) {
                val t = m.getJSONObject("title")
                notify(id, t.optString("english").ifBlank { t.optString("romaji") }, aired)
            }
        }
        edit.apply()
        return Result.success()
    }

    private fun notify(id: Int, title: String, ep: Int) {
        if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "New episodes", NotificationManager.IMPORTANCE_DEFAULT))
        val open = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.let { PendingIntent.getActivity(ctx, id, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) }
        val n = NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(ctx.applicationInfo.icon)
            .setContentTitle(title).setContentText("Episode $ep is out. Tap to watch.").setContentIntent(open).setAutoCancel(true).build()
        runCatching { NotificationManagerCompat.from(ctx).notify(id, n) }
    }

    companion object {
        private const val CHANNEL = "episodes"
        private const val WORK = "new_episode_alerts"
        fun enabled(c: Context) = c.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).getBoolean("ep_alerts", false)
        fun set(c: Context, on: Boolean) {
            c.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit().putBoolean("ep_alerts", on).apply()
            apply(c)
        }
        fun apply(c: Context) {
            val wm = WorkManager.getInstance(c.applicationContext)
            if (!enabled(c)) { wm.cancelUniqueWork(WORK); return }
            val req = PeriodicWorkRequestBuilder<NewEpisodeWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
