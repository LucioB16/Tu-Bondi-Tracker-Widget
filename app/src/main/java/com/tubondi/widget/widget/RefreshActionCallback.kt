package com.tubondi.widget.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionCallback
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.tubondi.widget.work.RefreshWorker

/**
 * Acción que dispara un refresh manual cuando el usuario lo solicita.
 */
class RefreshActionCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        RefreshWorker.enqueueOneTime(context, appWidgetId)
    }
}