package com.tubondi.widget.widget

import com.google.common.truth.Truth.assertThat
import com.tubondi.widget.domain.model.Stop
import com.tubondi.widget.domain.model.StopArrivals
import org.junit.Test

/**
 * SA-8: ante un refresh fallido el estado retiene el último dato válido
 * (arribos + lastUpdated) y marca degradado en lugar de borrarlos.
 */
class WidgetUiStateTest {
    @Test
    fun errorStateRetainsPreviousArrivals() {
        val previous = WidgetUiState(
            arrivals = listOf(StopArrivals(Stop("0001", "A"), emptyList())),
            lastUpdatedEpochMillis = 123L
        )

        val state = WidgetUiState.errorState(previous, "Session expired (408)", highContrast = true)

        assertThat(state.arrivals).isEqualTo(previous.arrivals)
        assertThat(state.lastUpdatedEpochMillis).isEqualTo(123L)
        assertThat(state.errorMessage).isEqualTo("Session expired (408)")
        assertThat(state.degraded).isTrue()
        assertThat(state.highContrast).isTrue()
    }
}
