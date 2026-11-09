package com.anibiar.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews

/** Home-screen widget: the last three things you were watching, plus the next episode of a followed show to air. */
class ContinueWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, build(context)) }
    }

    companion object {
        fun refresh(context: Context) {
            val m = AppWidgetManager.getInstance(context)
            val ids = m.getAppWidgetIds(ComponentName(context, ContinueWidget::class.java))
            if (ids.isNotEmpty()) ids.forEach { m.updateAppWidget(it, build(context)) }
        }

        private fun build(context: Context): RemoteViews {
            val app = context.applicationContext as AniBiarApp
            val v = RemoteViews(context.packageName, R.layout.widget_continue)
            val items = runCatching { app.history.load() }.getOrDefault(emptyList()).take(3)
            val slots = intArrayOf(R.id.w1, R.id.w2, R.id.w3)
            slots.forEachIndexed { i, id ->
                val h = items.getOrNull(i)
                v.setTextViewText(id, if (h != null) "${h.title}  ·  Ep ${h.episode}" else if (i == 0) "Nothing yet. Start watching in Kiro." else "")
            }
            val open = PendingIntent.getActivity(
                context, 0, context.packageManager.getLaunchIntentForPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            v.setOnClickPendingIntent(R.id.widget_root, open)
            return v
        }
    }
}
