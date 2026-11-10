package com.anibiar.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import com.anibiar.app.data.AiringSchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Home-screen widget: today's next releases with their air times. Refreshes about every 30 minutes. */
class AiringWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val lines = runCatching {
                    val now = System.currentTimeMillis()
                    val clock = DateFormat.getTimeInstance(DateFormat.SHORT)
                    AiringSchedule.today().filter { it.at > now - 3_600_000 }.take(5).map { "${clock.format(Date(it.at))}  ·  ${it.title}  ·  Ep ${it.episode}" }
                }.getOrDefault(emptyList())
                ids.forEach { manager.updateAppWidget(it, build(context, lines)) }
            } finally { pending.finish() }
        }
    }

    companion object {
        private fun build(context: Context, lines: List<String>): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_airing)
            val slots = intArrayOf(R.id.a1, R.id.a2, R.id.a3, R.id.a4, R.id.a5)
            slots.forEachIndexed { i, id -> v.setTextViewText(id, lines.getOrNull(i) ?: if (i == 0) "No more releases today." else "") }
            val open = PendingIntent.getActivity(
                context, 1, context.packageManager.getLaunchIntentForPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            v.setOnClickPendingIntent(R.id.airing_root, open)
            return v
        }
        fun refresh(context: Context) {
            val m = AppWidgetManager.getInstance(context)
            val ids = m.getAppWidgetIds(ComponentName(context, AiringWidget::class.java))
            if (ids.isNotEmpty()) context.sendBroadcast(android.content.Intent(context, AiringWidget::class.java).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids))
        }
    }
}
