package com.tubondi.widget.work

import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.tubondi.widget.domain.model.Arrival
import com.tubondi.widget.domain.model.Line
import com.tubondi.widget.domain.model.Stop
import com.tubondi.widget.domain.model.StopArrivals
import com.tubondi.widget.domain.model.WidgetConfiguration
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ArrivalNotifierTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var notifier: ArrivalNotifier

    @Before
    fun setUp() {
        context.getSharedPreferences("arrivals_notifier", android.content.Context.MODE_PRIVATE).edit().clear().apply()
        notifier = ArrivalNotifier(context)
    }

    @Test
    fun sendsNotificationOnlyForNearArrivalsAndDedups() {
        val config = WidgetConfiguration(appWidgetId = 99, selections = emptyList(), nearThresholdMinutes = 5)
        val stop = Stop(code = "0001", name = "Test", lines = listOf(Line("1", "1")))
        val arrivals = listOf(
            StopArrivals(stop, listOf(Arrival(stopCode = "0001", lineId = "1", lineName = "1", etaMinutes = 4, etaText = "4min", distanceKm = 0.1))),
            StopArrivals(stop, listOf(Arrival(stopCode = "0001", lineId = "2", lineName = "2", etaMinutes = 9, etaText = "9min", distanceKm = 0.2)))
        )

        notifier.dispatch(config, arrivals)
        val shadow = shadowOf(context.getSystemService(NotificationManager::class.java))
        assertThat(shadow.allNotifications).hasSize(1)

        notifier.dispatch(config, arrivals)
        assertThat(shadow.allNotifications).hasSize(1)
    }

    @Test
    fun silencePreventsNotifications() {
        val config = WidgetConfiguration(appWidgetId = 1, selections = emptyList(), nearThresholdMinutes = 5)
        val stop = Stop(code = "0002", name = "Otra", lines = listOf(Line("1", "1")))
        val arrivals = listOf(StopArrivals(stop, listOf(Arrival(stopCode = "0002", lineId = "1", lineName = "1", etaMinutes = 2, etaText = "2min", distanceKm = 0.05))))

        notifier.silenceOneHour()
        notifier.dispatch(config, arrivals)
        val shadow = shadowOf(context.getSystemService(NotificationManager::class.java))
        assertThat(shadow.allNotifications).isEmpty()
    }

    @Test
    fun blockedPermissionDoesNotBurnDedupWindow() {
        val config = WidgetConfiguration(appWidgetId = 2, selections = emptyList(), nearThresholdMinutes = 5)
        val stop = Stop(code = "0003", name = "Bloqueada", lines = listOf(Line("1", "1")))
        val arrivals = listOf(StopArrivals(stop, listOf(Arrival(stopCode = "0003", lineId = "1", lineName = "1", etaMinutes = 1, etaText = "1min", distanceKm = 0.05))))
        val shadow = shadowOf(context.getSystemService(NotificationManager::class.java))

        shadow.setNotificationsEnabled(false)
        notifier.dispatch(config, arrivals)
        assertThat(shadow.allNotifications).isEmpty()

        shadow.setNotificationsEnabled(true)
        notifier.dispatch(config, arrivals)
        assertThat(shadow.allNotifications).hasSize(1)
    }
}
