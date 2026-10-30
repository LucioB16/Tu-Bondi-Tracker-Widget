package com.tubondi.widget.domain

import com.google.common.truth.Truth.assertThat
import com.tubondi.widget.domain.model.Arrival
import org.junit.Test

class ArrivalFreshnessTest {
    @Test
    fun usesConfiguredRefreshIntervalAsFreshnessBoundary() {
        val success = 1_000_000L
        assertThat(ArrivalFreshness.isStale(success, success + 14 * ArrivalFreshness.MINUTE_MILLIS)).isFalse()
        assertThat(ArrivalFreshness.isStale(success, success + 15 * ArrivalFreshness.MINUTE_MILLIS)).isTrue()
        assertThat(ArrivalFreshness.isStale(success, success + 59 * ArrivalFreshness.MINUTE_MILLIS, 60)).isFalse()
        assertThat(ArrivalFreshness.isStale(success, success + 60 * ArrivalFreshness.MINUTE_MILLIS, 60)).isTrue()
        assertThat(ArrivalFreshness.isStale(success, success + 15 * ArrivalFreshness.MINUTE_MILLIS, 1)).isTrue()
    }

    @Test
    fun failedRefreshDoesNotChangeFreshnessOfLastSuccessfulData() {
        val lastSuccess = 10_000L
        val failedAt = lastSuccess + 4 * ArrivalFreshness.MINUTE_MILLIS
        val state = com.tubondi.widget.widget.WidgetUiState.errorState(
            previous = com.tubondi.widget.widget.WidgetUiState(
                lastUpdatedEpochMillis = lastSuccess,
                refreshIntervalMinutes = 60
            ),
            errorMessage = "Sin conexión",
            highContrast = false
        )

        assertThat(ArrivalFreshness.isStale(state.lastUpdatedEpochMillis, failedAt, state.refreshIntervalMinutes)).isFalse()
        assertThat(state.degraded).isTrue()
        assertThat(state.refreshIntervalMinutes).isEqualTo(60)
    }

    @Test
    fun ageUsesLastSuccessAndClampsClockSkew() {
        val lastSuccess = 20_000L
        assertThat(ArrivalFreshness.ageLabel(lastSuccess, lastSuccess + 2 * ArrivalFreshness.MINUTE_MILLIS))
            .isEqualTo("hace 2 min")
        assertThat(ArrivalFreshness.ageMillis(lastSuccess, lastSuccess - 100L)).isEqualTo(0L)
        assertThat(ArrivalFreshness.ageLabel(0L, lastSuccess)).isNull()
    }

    @Test
    fun etaCountsDownWhileFreshAndBecomesExplicitWhenEstimateHasPassed() {
        val success = 100_000L
        val arrival = Arrival(etaMinutes = 8, etaText = "8min")

        assertThat(ArrivalFreshness.etaLabel(arrival, success, success + 3 * ArrivalFreshness.MINUTE_MILLIS))
            .isEqualTo("5 min")
        assertThat(ArrivalFreshness.etaLabel(arrival, success, success + 9 * ArrivalFreshness.MINUTE_MILLIS))
            .isEqualTo("Estimación vencida")
        assertThat(ArrivalFreshness.etaLabel(arrival, success, success + 15 * ArrivalFreshness.MINUTE_MILLIS))
            .isEqualTo("Sin horario estimado")
    }

    @Test
    fun failedRequestDoesNotExtendTheLastSuccessTimestampOrEtaWindow() {
        val success = 100_000L
        val arrival = Arrival(etaMinutes = 70, etaText = "70min")
        val failedState = com.tubondi.widget.widget.WidgetUiState.errorState(
            previous = com.tubondi.widget.widget.WidgetUiState(
                arrivals = listOf(com.tubondi.widget.domain.model.StopArrivals(
                    stop = com.tubondi.widget.domain.model.Stop("internal", "Parada"),
                    arrivals = listOf(arrival)
                )),
                lastUpdatedEpochMillis = success,
                refreshIntervalMinutes = 60
            ),
            errorMessage = "Sin conexión",
            highContrast = false
        )

        assertThat(failedState.lastUpdatedEpochMillis).isEqualTo(success)
        assertThat(
            ArrivalFreshness.etaLabel(
                arrival,
                failedState.lastUpdatedEpochMillis,
                success + 59 * ArrivalFreshness.MINUTE_MILLIS,
                failedState.refreshIntervalMinutes
            )
        ).isEqualTo("11 min")
        assertThat(
            ArrivalFreshness.etaLabel(
                arrival,
                failedState.lastUpdatedEpochMillis,
                success + 60 * ArrivalFreshness.MINUTE_MILLIS,
                failedState.refreshIntervalMinutes
            )
        ).isEqualTo("Sin horario estimado")
    }
}
