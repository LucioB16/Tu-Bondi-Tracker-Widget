package com.tubondi.widget.data.repo

import com.google.common.truth.Truth.assertThat
import com.tubondi.widget.data.remote.TuBondiHttpClient
import com.tubondi.widget.data.remote.dto.ArrivalDto
import com.tubondi.widget.data.remote.dto.ArrivalsResponseDto
import com.tubondi.widget.data.remote.dto.StopDto
import com.tubondi.widget.domain.model.Line
import com.tubondi.widget.domain.model.Stop
import com.tubondi.widget.domain.model.StopSelection
import com.tubondi.widget.domain.model.WidgetConfiguration
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TransitRepositoryTest {
    private val client = mockk<TuBondiHttpClient>()
    private val repository = TransitRepository(client)

    @Test
    fun filtersArrivalsBySelectedLines() = runTest {
        val stop = Stop(
            code = "0001",
            name = "Colón y General Paz",
            lines = listOf(Line(10, "10"), Line(20, "20"))
        )
        val config = WidgetConfiguration(
            appWidgetId = 7,
            selections = listOf(StopSelection(stop, selectedLines = listOf(10))),
            refreshIntervalMinutes = 5,
            notificationsEnabled = false
        )
        coEvery { client.getArrivals("0001", any(), any(), any()) } returns ArrivalsResponseDto(
            arrivals = listOf(
                ArrivalDto(lineName = "10", stopCode = "0001", etaMinutes = 4, distanceMeters = 300, operator = null, color = null, direction = null, vehicleId = "101"),
                ArrivalDto(lineName = "20", stopCode = "0001", etaMinutes = 2, distanceMeters = 200, operator = null, color = null, direction = null, vehicleId = "102")
            ),
            parada = StopDto(codigo = "0001", descripcion = "Colón y General Paz", lineas = emptyList())
        )

        val result = repository.fetchArrivalsForConfig(config)

        assertThat(result).hasSize(1)
        assertThat(result.first().arrivals.map { it.lineName }).containsExactly("10")
    }
}