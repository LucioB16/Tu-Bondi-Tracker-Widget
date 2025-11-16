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
        val stop = Stop(code = "0001", name = "Test", lines = listOf(Line(1, "1")))
        val arrivals = listOf(
            StopArrivals(stop, listOf(Arrival(stopCode = "0001", lineName = "1", etaMinutes = 4, distanceMeters = 100, direction = null, vehicleId = "10", operator = null, color = null))),
            StopArrivals(stop, listOf(Arrival(stopCode = "0001", lineName = "2", etaMinutes = 9, distanceMeters = 200, direction = null, vehicleId = "11", operator = null, color = null)))
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
        val stop = Stop(code = "0002", name = "Otra", lines = listOf(Line(1, "1")))
        val arrivals = listOf(StopArrivals(stop, listOf(Arrival(stopCode = "0002", lineName = "1", etaMinutes = 2, distanceMeters = 50, direction = null, vehicleId = "15", operator = null, color = null))))

        notifier.silenceOneHour()
        notifier.dispatch(config, arrivals)
        val shadow = shadowOf(context.getSystemService(NotificationManager::class.java))
        assertThat(shadow.allNotifications).isEmpty()
    }
}