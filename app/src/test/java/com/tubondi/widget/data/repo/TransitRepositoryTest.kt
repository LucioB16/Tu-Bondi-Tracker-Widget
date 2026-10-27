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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Test
import java.io.IOException
import java.net.CookieManager
import java.net.CookiePolicy
import java.time.Clock
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

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
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"proximos_arribos":[]}""")
                    .setBodyDelay(12, TimeUnit.SECONDS)
            )
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

            val callCanceled = CountDownLatch(1)
            val cancellationObserved = AtomicBoolean(false)
            val cookieManager = CookieManager(null, CookiePolicy.ACCEPT_ALL)
            val okHttpClient = OkHttpClient.Builder()
                .cookieJar(JavaNetCookieJar(cookieManager))
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
                okHttpClient = okHttpClient,
                cookieManager = cookieManager
            )
            val repo = TransitRepository(httpClient, Clock.systemUTC())
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
            assertThat(server.requestCount).isEqualTo(4)
            assertThat(httpClient.currentSessionId).isNull()
            repeat(4) { assertThat(server.takeRequest(1, TimeUnit.SECONDS)).isNotNull() }
            assertThat(server.takeRequest(100, TimeUnit.MILLISECONDS)).isNull()
        } finally {
            server.shutdown()
        }
    }
}
