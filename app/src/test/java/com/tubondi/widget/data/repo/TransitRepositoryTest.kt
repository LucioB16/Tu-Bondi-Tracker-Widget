package com.tubondi.widget.data.repo

import com.google.common.truth.Truth.assertThat
import com.tubondi.widget.data.catalog.CatalogRepository
import com.tubondi.widget.data.remote.TuBondiHttpClient
import com.tubondi.widget.data.remote.dto.ArrivalDto
import com.tubondi.widget.data.remote.dto.ArrivalsResponseDto
import com.tubondi.widget.data.remote.dto.LineDto
import com.tubondi.widget.data.remote.dto.LinesRoutesResponseDto
import com.tubondi.widget.data.remote.dto.RouteSelectionResponseDto
import com.tubondi.widget.data.remote.dto.StopDto
import com.tubondi.widget.data.remote.dto.StopLineDto
import com.tubondi.widget.domain.model.Line
import com.tubondi.widget.domain.model.Stop
import com.tubondi.widget.domain.model.StopSelection
import com.tubondi.widget.domain.model.WidgetConfiguration
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class TransitRepositoryTest {
    private val client = mockk<TuBondiHttpClient>()
    private val catalogRepository = mockk<CatalogRepository> {
        every { resolveStop(any()) } answers { firstArg() }
    }
    private val repository = TransitRepository(client, Clock.systemUTC(), catalogRepository)

    @Test
    fun filtersArrivalsBySelectedLines() = runTest {
        val stop = Stop(
            code = "0001",
            name = "Colón y General Paz",
            lines = listOf(
                Line("10", "10", "#FF9500", clientId = 411),
                Line("20", "20", "#0099CB", clientId = 411)
            )
        )
        val config = WidgetConfiguration(
            appWidgetId = 7,
            selections = listOf(StopSelection(stop, selectedLines = listOf("411:10"))),
            refreshIntervalMinutes = 5,
            notificationsEnabled = false
        )
        coEvery { client.getLinesAndRoutes(any()) } returns LinesRoutesResponseDto(
            lineas = listOf(
                LineDto("10", "10", "#FF9500", cliente = 411),
                LineDto("20", "20", "#0099CB", cliente = 411),
                LineDto("10", "10", "#D93062", cliente = 408)
            )
        )
        // Contrato real: eta por demora/proximo, sin codigo_parada por arribo.
        coEvery { client.getArrivals("0001", any(), any(), any()) } returns ArrivalsResponseDto(
            arrivals = listOf(
                ArrivalDto(lineId = "10", lineName = "10", cliente = 411, color = "#FF9500", demora = "4min", distancia = 300.0, coche = "101"),
                ArrivalDto(lineId = "10", lineName = "10", cliente = 408, color = "#D93062", proximo = "2min", distancia = 200.0, coche = "102"),
                ArrivalDto(lineId = "20", lineName = "20", cliente = 411, color = "#0099CB", proximo = "6min", distancia = 200.0, coche = "103")
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
        assertThat(filtered.first().color).isEqualTo("#FF9500")
        assertThat(filtered.first().clientId).isEqualTo(411)
        assertThat(filtered.first().notification).isNull()
    }

    @Test
    fun arrivalColorsUseCompositeCatalogIdentityAndGoNeutralOnDisagreement() = runTest {
        val config = WidgetConfiguration(
            appWidgetId = 9,
            selections = listOf(StopSelection(Stop("0001", "Colón y General Paz")))
        )
        coEvery { client.getLinesAndRoutes(any()) } returns LinesRoutesResponseDto(
            lineas = listOf(
                LineDto("10", "10", "#FF9500", cliente = 411),
                LineDto("10", "10", "#D93062", cliente = 408)
            )
        )
        coEvery { client.getArrivals("0001", any(), any(), any()) } returns ArrivalsResponseDto(
            arrivals = listOf(
                ArrivalDto(lineId = "10", lineName = "10", cliente = 411, color = "#D93062", demora = "4min"),
                ArrivalDto(lineId = "10", lineName = "10", cliente = 408, color = "#D93062", demora = "5min")
            )
        )

        val arrivals = repository.fetchArrivalsForConfig(config).single().arrivals

        assertThat(arrivals.map { it.clientId }).containsExactly(411, 408).inOrder()
        assertThat(arrivals[0].color).isNull()
        assertThat(arrivals[1].color).isEqualTo("#D93062")
    }

    @Test
    fun emptyLineFilterIncludesAllLines() = runTest {
        val stop = Stop("0001", "Colón y General Paz", lines = listOf(Line("10", "10"), Line("20", "20")))
        val config = WidgetConfiguration(
            appWidgetId = 8,
            selections = listOf(StopSelection(stop, selectedLines = emptyList()))
        )
        coEvery { client.getArrivals("0001", any(), any(), any()) } returns ArrivalsResponseDto(
            arrivals = listOf(
                ArrivalDto(lineId = "10", lineName = "10", demora = "4min"),
                ArrivalDto(lineId = "20", lineName = "20", demora = "6min")
            )
        )

        val result = repository.fetchArrivalsForConfig(config)

        assertThat(result.single().arrivals.map { it.lineId }).containsExactly("10", "20")
    }

    @Test
    fun routeSelectionParsesLiveStringCoordinatesAndUsesCatalogLineColor() = runTest {
        coEvery { client.selectRouteTrace(17, 411, "cbaciudad") } returns RouteSelectionResponseDto(
            paradas = listOf(
                StopDto(
                    codigo = "PB01",
                    nombre = "42",
                    paradaNombre = "AV. SANTA ANA 5370",
                    lat = kotlinx.serialization.json.JsonPrimitive("-31.411503"),
                    lon = kotlinx.serialization.json.JsonPrimitive("-64.256727"),
                    lineId = "42",
                    clientId = 411,
                    color = "#fe3434"
                )
            )
        )

        val stop = repository.fetchStopsForRoute(
            routeId = 17,
            clientId = 411,
            conf = "cbaciudad",
            lineId = "42",
            lineName = "42",
            lineColor = "#ff9500"
        ).single()

        assertThat(stop.latitude).isWithin(0.000001).of(-31.411503)
        assertThat(stop.longitude).isWithin(0.000001).of(-64.256727)
        assertThat(stop.lines.single().selectionKey).isEqualTo("411:42")
        assertThat(stop.lines.single().color).isEqualTo("#FF9500")
    }

    @Test
    fun routeLineIdentityIsInheritedOnlyForMatchingLinesMissingClientId() = runTest {
        coEvery { client.selectRouteTrace(10, 1, "cbaciudad") } returns RouteSelectionResponseDto(
            paradas = listOf(
                StopDto(
                    codigo = "A911",
                    descripcion = "Av. Duarte Quirós 1717–1728",
                    lineas = listOf(
                        StopLineDto(lineId = "10", name = "10"),
                        StopLineDto(lineId = "10", name = "10", clientId = 408, operador = "Operador B")
                    )
                )
            )
        )

        val stop = repository.fetchStopsForRoute(
            routeId = 10,
            clientId = 1,
            conf = "cbaciudad",
            lineId = "10",
            lineName = "10",
            lineColor = "#0E627C"
        ).single()

        assertThat(stop.lines.map { it.selectionKey }).containsExactly("1:10", "408:10").inOrder()
        assertThat(stop.lines.map { it.name }).containsExactly("10", "10").inOrder()
        assertThat(stop.lines.first().color).isEqualTo("#0E627C")
        assertThat(stop.lines[1].clientId).isEqualTo(408)
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
        val repo = TransitRepository(client, fakeClock, catalogRepository)
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

    @Test
    fun realCycleBudgetCancelsSlowHttpAndRejectsPartialResults() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Set-Cookie", "PHPSESSID=c1-test; path=/")
                    .setBody("<html><script>operacion:'12345678901234567890';</script></html>")
            )
            server.enqueue(MockResponse().setResponseCode(200)) // registro de fp
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"lineas":[],"grupos":[],"clientes":[]}""")) // canonical line-color catalog
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"proximos_arribos":[]}""")
                    .setBodyDelay(12, TimeUnit.SECONDS)
            )
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

            val callCanceled = CountDownLatch(1)
            val cancellationObserved = AtomicBoolean(false)
            val okHttpClient = OkHttpClient.Builder()
                .eventListener(object : EventListener() {
                    override fun callFailed(call: Call, ioe: IOException) {
                        if (call.isCanceled()) {
                            cancellationObserved.set(true)
                            callCanceled.countDown()
                        }
                    }
                })
                .retryOnConnectionFailure(false)
                .readTimeout(40, TimeUnit.SECONDS)
                .build()
            val httpClient = TuBondiHttpClient(
                baseUrl = server.url("/").toString().trimEnd('/'),
                okHttpClient = okHttpClient
            )
            val repo = TransitRepository(httpClient, Clock.systemUTC(), catalogRepository)
            val config = WidgetConfiguration(
                appWidgetId = 7,
                selections = listOf(
                    StopSelection(Stop("0001", "A"), emptyList()),
                    StopSelection(Stop("0002", "B"), emptyList())
                ),
                refreshIntervalMinutes = 15,
                notificationsEnabled = false
            )
            val startedAt = System.nanoTime()

            val failure = runCatching { repo.fetchArrivalsForConfig(config) }.exceptionOrNull()
            val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)

            assertThat(TransitRepository.CYCLE_BUDGET_MS).isEqualTo(30_000L)
            assertThat(elapsedMs).isAtLeast(28_000L)
            assertThat(failure).isInstanceOf(IOException::class.java)
            assertThat(failure?.message).contains("budget exceeded")
            assertThat(callCanceled.await(2, TimeUnit.SECONDS)).isTrue()
            assertThat(cancellationObserved.get()).isTrue()
            assertThat(server.requestCount).isEqualTo(5)
            assertThat(httpClient.currentSessionId).isNull()
            repeat(5) { assertThat(server.takeRequest(1, TimeUnit.SECONDS)).isNotNull() }
            assertThat(server.takeRequest(100, TimeUnit.MILLISECONDS)).isNull()
        } finally {
            server.shutdown()
        }
    }
}
