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
import java.time.Clock
import java.time.ZoneId
import java.time.ZoneOffset
import java.io.IOException

class TransitRepositoryTest {
    private val client = mockk<TuBondiHttpClient>()
    private val repository = TransitRepository(client, Clock.systemUTC())

    @Test
    fun filtersArrivalsBySelectedLines() = runTest {
        val stop = Stop(
            code = "0001",
            name = "Colón y General Paz",
            lines = listOf(Line("10", "10"), Line("20", "20"))
        )
        val config = WidgetConfiguration(
            appWidgetId = 7,
            selections = listOf(StopSelection(stop, selectedLines = listOf("10"))),
            refreshIntervalMinutes = 5,
            notificationsEnabled = false
        )
        // Contrato real: eta por demora/proximo, sin codigo_parada por arribo.
        coEvery { client.getArrivals("0001", any(), any(), any()) } returns ArrivalsResponseDto(
            arrivals = listOf(
                ArrivalDto(lineId = "10", lineName = "10", demora = "4min", distancia = 300.0, coche = "101"),
                ArrivalDto(lineId = "20", lineName = "20", proximo = "2min", distancia = 200.0, coche = "102")
            ),
            parada = StopDto(codigo = "0001", descripcion = "Colón y General Paz", lineas = emptyList())
        )

        val result = repository.fetchArrivalsForConfig(config)

        assertThat(result).hasSize(1)
        val filtered = result.first().arrivals
        assertThat(filtered.map { it.lineName }).containsExactly("10")
        assertThat(filtered.first().etaMinutes).isEqualTo(4)
        assertThat(filtered.first().etaText).isEqualTo("4min")
        assertThat(filtered.first().stopCode).isEqualTo("0001")
        assertThat(filtered.first().notification).isNull()
    }

    @Test
    fun cycleBudgetSkipsRemainingStops() = runTest {
        // Reloj simulado: tras la primera consulta salta 31s (> budget de 30s).
        var advanced = false
        val fakeClock = object : Clock() {
            override fun millis(): Long = if (advanced) 31_000L else 0L
            override fun getZone(): ZoneId = ZoneOffset.UTC
            override fun withZone(zone: ZoneId?): Clock = this
            override fun instant(): java.time.Instant = java.time.Instant.ofEpochMilli(millis())
        }
        val repo = TransitRepository(client, fakeClock)
        val config = WidgetConfiguration(
            appWidgetId = 7,
            selections = listOf(
                StopSelection(Stop("0001", "A"), emptyList()),
                StopSelection(Stop("0002", "B"), emptyList())
            ),
            refreshIntervalMinutes = 5,
            notificationsEnabled = false
        )
        coEvery { client.getArrivals("0001", any(), any(), any()) } answers {
            advanced = true
            ArrivalsResponseDto()
        }

        val failure = runCatching { repo.fetchArrivalsForConfig(config) }.exceptionOrNull()
        assertThat(failure).isInstanceOf(IOException::class.java)
    }
}
