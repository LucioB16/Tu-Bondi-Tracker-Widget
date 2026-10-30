package com.tubondi.widget.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.tubondi.widget.work.RefreshWorker

/**
 * Acción que dispara un refresh manual cuando el usuario lo solicita.
 */
class RefreshActionCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        // Let the worker publish one final Glance render. An intermediate render
        // can race the result and leave a stale progress label in the launcher.
        RefreshWorker.enqueueOneTime(context, appWidgetId)
    }
}
