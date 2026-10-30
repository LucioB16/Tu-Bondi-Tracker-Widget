package com.tubondi.widget.widget

import com.google.common.truth.Truth.assertThat
import androidx.compose.ui.unit.dp
import com.tubondi.widget.domain.ArrivalFreshness
import com.tubondi.widget.domain.model.Arrival
import com.tubondi.widget.domain.model.Stop
import com.tubondi.widget.domain.model.StopArrivals
import org.junit.Test

/**
 * SA-8: ante un refresh fallido el estado retiene el último dato válido
 * (arribos + lastUpdated) y marca degradado en lugar de borrarlos.
 */
class WidgetUiStateTest {
    @Test
    fun widgetRowsAdaptToSystemFontScale() {
        assertThat(widgetArrivalRowLimit(compact = true, medium = false, fontScale = 1.5f)).isEqualTo(1)
        assertThat(widgetArrivalRowLimit(compact = false, medium = true, fontScale = 1.3f)).isEqualTo(1)
        assertThat(widgetArrivalRowLimit(compact = false, medium = false, fontScale = 1.3f)).isEqualTo(2)
        assertThat(widgetArrivalRowLimit(compact = false, medium = false, fontScale = 1.0f)).isEqualTo(4)
        assertThat(widgetArrivalRowLimit(compact = false, medium = false, fontScale = 1.0f, hasNotices = true)).isEqualTo(3)
    }

    @Test
    fun tallWidgetUsesExpandedLayoutWhenPhoneLauncherLimitsItsWidth() {
        assertThat(isCompactWidget(258.dp, 161.dp)).isFalse()
        assertThat(isMediumWidget(258.dp, 161.dp)).isTrue()
        assertThat(isCompactWidget(258.dp, 250.dp)).isFalse()
        assertThat(isMediumWidget(258.dp, 250.dp)).isFalse()
        assertThat(isCompactWidget(180.dp, 240.dp)).isTrue()
    }

    @Test
    fun compactAgeKeepsLastUpdatedLineShortButDynamic() {
        assertThat(compactAgeLabel(1_000L, 1_000L)).isEqualTo("hace <1 min")
        assertThat(compactAgeLabel(1_000L, 61_000L)).isEqualTo("hace 1 min")
        assertThat(compactAgeLabel(1_000L, 3_601_000L)).isEqualTo("hace 1 h")
        assertThat(compactAgeLabel(0L, 3_601_000L)).isEqualTo("Sin datos")
    }

    @Test
    fun widgetPriorityCoversSoonestStopsBeforeAddingMoreRowsFromOneStop() {
        val state = WidgetUiState(
            arrivals = listOf(
                stopGroup("1", 9, 20),
                stopGroup("2", 5),
                stopGroup("3", 2)
            )
        )

        val medium = state.prioritizedArrivals(maxRows = 2)
        assertThat(medium.map { it.stop.code }).containsExactly("3", "2").inOrder()
        assertThat(medium.flatMap { it.arrivals }.map { it.etaMinutes }).containsExactly(2, 5).inOrder()

        val wide = state.prioritizedArrivals(maxRows = 4)
        assertThat(wide.sumOf { it.arrivals.size }).isEqualTo(4)
        assertThat(wide.first { it.stop.code == "1" }.arrivals.map { it.etaMinutes })
            .containsExactly(9, 20).inOrder()
    }

    @Test
    fun errorStateRetainsPreviousArrivals() {
        val previous = WidgetUiState(
            arrivals = listOf(
                StopArrivals(
                    Stop("0001", "A"),
                    listOf(
                        Arrival(
                            stopCode = "0001",
                            lineId = "10",
                            lineName = "10",
                            etaMinutes = 4,
                            etaText = "4min"
                        )
                    )
                ),
                StopArrivals(
                    Stop("0002", "B"),
                    listOf(
                        Arrival(
                            stopCode = "0002",
                            lineId = "20",
                            lineName = "20",
                            etaMinutes = 8,
                            etaText = "8min"
                        )
                    )
                )
            ),
            lastUpdatedEpochMillis = 123L
        )

        val state = WidgetUiState.errorState(previous, "Session expired (408)", highContrast = true)

        assertThat(state.arrivals).isEqualTo(previous.arrivals)
        assertThat(state.lastUpdatedEpochMillis).isEqualTo(123L)
        assertThat(state.errorMessage).isEqualTo("Session expired (408)")
        assertThat(state.degraded).isTrue()
        assertThat(state.highContrast).isTrue()
    }

    @Test
    fun staleWideWidgetKeepsStopAndLineDetailsButHidesExpiredArrivalTime() {
        val lastSuccess = 1_000_000L
        val now = lastSuccess + 16 * ArrivalFreshness.MINUTE_MILLIS
        val state = WidgetUiState(
            arrivals = listOf(
                StopArrivals(
                    Stop("A911", "Av. Duarte Quirós 1717–1728"),
                    listOf(
                        Arrival(
                            stopCode = "A911",
                            lineId = "42",
                            lineName = "42",
                            routeDescription = "Parque Sarmiento",
                            etaMinutes = 4,
                            etaText = "4 min"
                        )
                    )
                )
            ),
            lastUpdatedEpochMillis = lastSuccess,
            refreshIntervalMinutes = 15
        )

        val displayed = state.prioritizedArrivals(maxRows = 4, maxStops = 2)
        val arrival = displayed.single().arrivals.single()

        assertThat(displayed.single().stop.name).isEqualTo("Av. Duarte Quirós 1717–1728")
        assertThat(arrival.lineName).isEqualTo("42")
        assertThat(arrival.routeDescription).isEqualTo("Parque Sarmiento")
        assertThat(ArrivalFreshness.etaLabel(arrival, lastSuccess, now, 15))
            .isEqualTo("Sin horario estimado")
    }

    private fun stopGroup(code: String, vararg eta: Int) = StopArrivals(
        Stop(code, "Parada de prueba $code"),
        eta.map { value -> Arrival(stopCode = code, lineName = "Línea", etaMinutes = value, etaText = "${value}min") }
    )
}
