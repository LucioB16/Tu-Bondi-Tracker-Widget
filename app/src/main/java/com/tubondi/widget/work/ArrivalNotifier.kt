package com.tubondi.widget.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tubondi.widget.R
import com.tubondi.widget.domain.model.StopArrivals
import com.tubondi.widget.domain.model.WidgetConfiguration
import com.tubondi.widget.ui.config.ConfigActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gestiona el envío y la deduplicación de notificaciones.
 */
@Singleton
class ArrivalNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    init {
        ensureChannel()
    }

    fun dispatch(config: WidgetConfiguration, arrivals: List<StopArrivals>) {
        if (!config.notificationsEnabled || isMuted()) return
        val threshold = config.nearThresholdMinutes
        val now = System.currentTimeMillis()
        val recents = loadRecent()
        val manager = NotificationManagerCompat.from(context)
        arrivals.forEach { stopArrivals ->
            stopArrivals.arrivals
                .filter { it.etaMinutes <= threshold }
                .forEach { arrival ->
                    val key = "${config.appWidgetId}-${arrival.stopCode}-${arrival.lineName}"
                    val lastTime = recents[key] ?: 0L
                    if (now - lastTime < DEDUP_WINDOW_MS) return@forEach
                    recents[key] = now
                    val content = context.getString(
                        R.string.notification_body_template,
                        arrival.lineName,
                        arrival.etaMinutes,
                        stopArrivals.stop.name
                    )
                    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(context.getString(R.string.app_name))
                        .setContentText(content)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .setContentIntent(configIntent(config.appWidgetId))
                        .addAction(silenceAction())
                    manager.notify(key.hashCode(), builder.build())
                }
        }
        persistRecents(recents)
    }

    private fun configIntent(appWidgetId: Int): PendingIntent {
        val intent = Intent(context, ConfigActivity::class.java).apply {
            putExtra("appWidgetId", appWidgetId)
        }
        return PendingIntent.getActivity(
            context,
            appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun silenceAction(): NotificationCompat.Action {
        val intent = Intent(context, NotificationSilenceReceiver::class.java).apply {
            action = NotificationSilenceReceiver.ACTION_SILENCE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(
            0,
            context.getString(R.string.notification_silence_action),
            pendingIntent
        ).build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun isMuted(): Boolean {
        val until = prefs.getLong(KEY_MUTED_UNTIL, 0L)
        return System.currentTimeMillis() < until
    }

    private fun loadRecent(): MutableMap<String, Long> {
        val raw = prefs.getString(KEY_RECENTS, "") ?: ""
        val map = mutableMapOf<String, Long>()
        raw.split(';')
            .filter { it.contains(':') }
            .forEach {
                val parts = it.split(':')
                if (parts.size == 2) {
                    map[parts[0]] = parts[1].toLongOrNull() ?: 0L
                }
            }
        return map
    }

    private fun persistRecents(data: Map<String, Long>) {
        val sanitized = data
            .filterValues { System.currentTimeMillis() - it < DEDUP_WINDOW_MS }
            .entries
            .joinToString(";") { "${it.key}:${it.value}" }
        prefs.edit().putString(KEY_RECENTS, sanitized).apply()
    }

    fun silenceOneHour() {
        val until = System.currentTimeMillis() + ONE_HOUR_MS
        prefs.edit().putLong(KEY_MUTED_UNTIL, until).apply()
    }

    companion object {
        private const val PREFS = "arrivals_notifier"
        private const val KEY_RECENTS = "recent_keys"
        private const val KEY_MUTED_UNTIL = "mute_until"
        private const val CHANNEL_ID = "arrivals_alerts"
        private const val DEDUP_WINDOW_MS = 10 * 60 * 1000L
        private const val ONE_HOUR_MS = 60 * 60 * 1000L
    }
}