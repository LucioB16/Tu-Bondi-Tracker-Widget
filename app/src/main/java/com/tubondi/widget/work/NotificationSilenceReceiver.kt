package com.tubondi.widget.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Recibe la acción para silenciar notificaciones por una hora.
 */
@AndroidEntryPoint
class NotificationSilenceReceiver : BroadcastReceiver() {

    @Inject lateinit var notifier: ArrivalNotifier

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_SILENCE) {
            notifier.silenceOneHour()
        }
    }

    companion object {
        const val ACTION_SILENCE = "com.tubondi.widget.action.SILENCE_ONE_HOUR"
    }
}