package com.anibiar.app.data

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "Remind me" for airing anime. Saved ids live in prefs; each upcoming episode of a saved anime gets an alarm that posts
 * a notification when it airs. Alarms are re-planned whenever the app opens or the schedule refreshes.
 */
class Reminders(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("reminders", Context.MODE_PRIVATE)
    private val _ids = MutableStateFlow(prefs.getStringSet("ids", emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet())
    val ids: StateFlow<Set<Int>> = _ids.asStateFlow()

    fun toggle(id: Int): Boolean {
        val on = id !in _ids.value
        _ids.value = if (on) _ids.value + id else _ids.value - id
        prefs.edit().putStringSet("ids", _ids.value.map { it.toString() }.toSet()).apply()
        return on
    }

    /** Sets an alarm for every upcoming episode of a reminded anime, and cancels the ones no longer wanted. */
    fun plan(schedule: List<LatestEpisode>) {
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()
        val planned = HashSet<String>(prefs.getStringSet("planned", emptySet()).orEmpty())
        val keep = HashSet<String>()
        for (e in schedule) {
            val key = "${e.media.id}:${e.episode}"
            val at = e.airingAt * 1000 + 60_000
            if (e.media.id !in _ids.value || at <= now) continue
            keep += key
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(e.media.id, e.episode, e.media.displayTitle, e.media.posterUrl))
        }
        for (old in planned - keep) {
            val (id, ep) = old.split(':').let { it[0].toInt() to it[1].toInt() }
            am.cancel(pending(id, ep, "", null))
        }
        prefs.edit().putStringSet("planned", keep).apply()
    }

    private fun pending(id: Int, ep: Int, title: String, image: String?): PendingIntent =
        PendingIntent.getBroadcast(
            app, id * 1000 + ep,
            Intent(app, ReminderReceiver::class.java).putExtra("id", id).putExtra("ep", ep).putExtra("title", title),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object { const val CHANNEL = "episodes" }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title").orEmpty().ifBlank { return }
        val ep = intent.getIntExtra("ep", 0)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(Reminders.CHANNEL, "New episodes", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            context, 0, context.packageManager.getLaunchIntentForPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, Reminders.CHANNEL)
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle(title)
            .setContentText("Episode $ep is out now")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(intent.getIntExtra("id", 0) * 1000 + ep, n) }
    }
}
