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
        // Feedback inmediato: "Actualizando…" reteniendo los arribos previos
        // (el worker sobrescribe al terminar, con éxito o con error).
        val previous = WidgetStateStorage.readState(context, glanceId)
        WidgetStateStorage.writeState(context, glanceId, previous.copy(errorMessage = "Actualizando…"))
        TransitAppWidget().update(context, glanceId)
        RefreshWorker.enqueueOneTime(context, appWidgetId)
    }
}