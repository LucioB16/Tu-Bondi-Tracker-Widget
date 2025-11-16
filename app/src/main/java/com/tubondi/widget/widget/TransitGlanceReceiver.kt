package com.tubondi.widget.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.tubondi.widget.work.RefreshWorker

/**
 * Receiver que conecta el widget con el SO y dispara refrescos.
 */
class TransitGlanceReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TransitAppWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshWorker.enqueueAll(context)
    }

    override fun onUpdate(context: Context, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetIds)
        RefreshWorker.enqueueAll(context)
    }
}