package com.tubondi.widget.widget

import com.google.common.truth.Truth.assertThat
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

    private fun stopGroup(code: String, vararg eta: Int) = StopArrivals(
        Stop(code, "Parada de prueba $code"),
        eta.map { value -> Arrival(stopCode = code, lineName = "Línea", etaMinutes = value, etaText = "${value}min") }
    )
}
