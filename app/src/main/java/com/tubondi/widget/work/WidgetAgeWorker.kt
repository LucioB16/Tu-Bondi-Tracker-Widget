package com.tubondi.widget.work

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tubondi.widget.widget.TransitAppWidget

/** Re-renders one widget periodically so its relative freshness label advances. */
class WidgetAgeWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val widgetId = inputData.getInt(KEY_WIDGET_ID, INVALID_WIDGET)
        if (widgetId == INVALID_WIDGET) return Result.success()
        return try {
            val manager = GlanceAppWidgetManager(applicationContext)
            val glanceId = manager.getGlanceIds(TransitAppWidget::class.java)
                .firstOrNull { manager.getAppWidgetId(it) == widgetId }
                ?: return Result.success()
            TransitAppWidget().update(applicationContext, glanceId)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_WIDGET_ID = "widget_id"
        const val INVALID_WIDGET = -1
    }
}
