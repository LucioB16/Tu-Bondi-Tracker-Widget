package com.tubondi.widget.data.remote

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verifica el handshake anti-408 (SA-3 de SDD_REQUIREMENTS): orden
 * bootstrap GET -> cmd=vista (registro fp) -> comando; headers anti-408;
 * 408/cuerpo vacío -> re-sesión + 1 retry; SerializationException sin retry.
 */
class TuBondiHttpClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun bootstrapPage(session: String) = MockResponse()
        .setResponseCode(200)
        .addHeader("Set-Cookie", "PHPSESSID=$session; path=/")
        // Formato real del HTML del backend: operacion:'NNN' (con dos puntos),
        // verificado con el grep del cosechador de Oma y la respuesta viva.
        .setBody("<html><script>operacion:'59608253602766063001';</script></html>")

    private fun linesJson() = MockResponse()
        .setResponseCode(200)
        .setBody("""{"lineas":[],"grupos":[],"clientes":[]}""")

    private fun RecordedRequest.formBodyText(): String = body.clone().readUtf8()

    private fun String.formValue(name: String): String? =
        split('&').firstOrNull { it.substringBefore('=') == name }?.substringAfter('=')

    private fun RecordedRequest.cookieSessionId(): String? = headers["Cookie"]
        ?.split(';')
        ?.map(String::trim)
        ?.firstOrNull { it.startsWith("PHPSESSID=") }
        ?.substringAfter('=')

    @Test
    fun handshakeOrderHeadersAndRetryOn408() = runTest {
        server.enqueue(bootstrapPage("first"))
        server.enqueue(MockResponse().setResponseCode(200)) // cmd=vista
        server.enqueue(MockResponse().setResponseCode(408)) // lineasyrutas -> re-sesión
        server.enqueue(bootstrapPage("second"))
        server.enqueue(MockResponse().setResponseCode(200)) // cmd=vista
        server.enqueue(linesJson())

        val client = TuBondiHttpClient(baseUrl = server.url("/").toString().trimEnd('/'))
        val result = client.getLinesAndRoutes()

        assertThat(result.lineas).isEmpty()
        assertThat(client.currentSessionId).isEqualTo("second")
        assertThat(server.requestCount).isEqualTo(6)

        // 1) bootstrap GET con UA de navegador y conf
        val r1 = server.takeRequest()
        assertThat(r1.method).isEqualTo("GET")
        assertThat(r1.requestUrl!!.queryParameter("conf")).isEqualTo("cbaciudad")
        assertThat(r1.headers["User-Agent"]).startsWith("Mozilla/5.0")
        // 2) registro de fp: cmd=vista, form fp=<64hex>, sin X-Device-FlakerParam
        val r2 = server.takeRequest()
        assertThat(r2.method).isEqualTo("POST")
        assertThat(r2.requestUrl!!.queryParameter("cmd")).isEqualTo("vista")
        val vistaBody = r2.body.readUtf8()
        assertThat(vistaBody).startsWith("fp=")
        assertTrue("fp debe ser 64 hex", vistaBody.removePrefix("fp=").length == 64)
        assertThat(r2.headers["X-Device-FlakerParam"]).isNull()
        // 3) comando con headers anti-408 completos
        val r3 = server.takeRequest()
        assertThat(r3.requestUrl!!.queryParameter("cmd")).isEqualTo("lineasyrutas")
        assertThat(r3.headers["X-Device-FlakerParam"]).isNotEmpty()
        assertThat(r3.headers["Content-Type"]).isEqualTo("application/x-www-form-urlencoded")
        assertThat(r3.headers["Origin"]).isNotEmpty()
        assertThat(r3.headers["Referer"]).isNotEmpty()
        assertThat(r3.headers["Sec-Fetch-Mode"]).isEqualTo("cors")
        assertThat(r3.headers["User-Agent"]).startsWith("Mozilla/5.0")
        assertThat(r3.body.readUtf8()).contains("conf=cbaciudad")
        // 4-5) re-sesión completa
        val r4 = server.takeRequest()
        assertThat(r4.method).isEqualTo("GET")
        assertThat(r4.headers["User-Agent"]).startsWith("Mozilla/5.0")
        val r5 = server.takeRequest()
        assertThat(r5.requestUrl!!.queryParameter("cmd")).isEqualTo("vista")
        // 6) reintento del comando
        val r6 = server.takeRequest()
        assertThat(r6.requestUrl!!.queryParameter("cmd")).isEqualTo("lineasyrutas")
    }

    @Test
    fun arrivalsDoNotRetryFailedFingerprintRegistrationWithinCycle() = runTest {
        for (status in listOf(403, 408)) {
            val conf = "arrivals$status"
            val start = server.requestCount
            server.enqueue(bootstrapPage("arrival-session-$status"))
            server.enqueue(MockResponse().setResponseCode(status))
            val client = TuBondiHttpClient(baseUrl = server.url("/").toString().trimEnd('/'))

            val failure = runCatching { client.getArrivals("0001", conf) }.exceptionOrNull()
            assertThat(failure).isInstanceOf(IOException::class.java)
            assertThat(server.requestCount).isEqualTo(start + 2)
            val bootstrap = server.takeRequest(1, TimeUnit.SECONDS)!!
            val registration = server.takeRequest(1, TimeUnit.SECONDS)!!
            assertThat(bootstrap.requestUrl!!.queryParameter("conf")).isEqualTo(conf)
            assertThat(registration.requestUrl!!.queryParameter("cmd")).isEqualTo("vista")
            assertThat(registration.cookieSessionId()).isEqualTo("arrival-session-$status")
            assertThat(registration.headers["X-Device-FlakerParam"]).isNull()
            assertThat(server.takeRequest(100, TimeUnit.MILLISECONDS)).isNull()
        }
    }

    @Test
    fun concurrentSameConfCallsWaitUntilFingerprintRegistration(): Unit = runBlocking {
        val bootstrapCount = AtomicInteger()
        val fingerprintCount = AtomicInteger()
        val commandCount = AtomicInteger()
        val bootstrapStarted = CountDownLatch(1)
        val fingerprintStarted = CountDownLatch(1)
        val releaseFingerprint = CountDownLatch(1)
        val unexpectedWhileBlocked = CountDownLatch(1)
        val registrationReturned = AtomicBoolean(false)
        val registeredFingerprint = AtomicReference<String?>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.method == "GET" -> {
                    if (bootstrapCount.incrementAndGet() > 1) unexpectedWhileBlocked.countDown()
                    bootstrapStarted.countDown()
                    bootstrapPage("same-conf-session")
                }
                request.requestUrl?.queryParameter("cmd") == "vista" -> {
                    fingerprintCount.incrementAndGet()
                    val body = request.formBodyText()
                    registeredFingerprint.set(body.removePrefix("fp="))
                    fingerprintStarted.countDown()
                    if (!releaseFingerprint.await(3, TimeUnit.SECONDS)) {
                        throw IOException("test did not release fingerprint response")
                    }
                    registrationReturned.set(true)
                    MockResponse().setResponseCode(200)
                }
                request.requestUrl?.queryParameter("cmd") == "lineasyrutas" -> {
                    commandCount.incrementAndGet()
                    if (!registrationReturned.get()) unexpectedWhileBlocked.countDown()
                    linesJson()
                }
                else -> MockResponse().setResponseCode(404)
            }
        }

        val client = TuBondiHttpClient(baseUrl = server.url("/").toString().trimEnd('/'))
        val first = async(Dispatchers.IO) { client.getLinesAndRoutes("cityA") }
        assertTrue(bootstrapStarted.await(2, TimeUnit.SECONDS))
        assertTrue(fingerprintStarted.await(2, TimeUnit.SECONDS))
        val second = async(start = CoroutineStart.UNDISPATCHED) { client.getLinesAndRoutes("cityA") }
        assertThat(unexpectedWhileBlocked.await(200, TimeUnit.MILLISECONDS)).isFalse()

        releaseFingerprint.countDown()
        assertThat(first.await().lineas).isEmpty()
        assertThat(second.await().lineas).isEmpty()
        assertThat(bootstrapCount.get()).isEqualTo(1)
        assertThat(fingerprintCount.get()).isEqualTo(1)
        assertThat(commandCount.get()).isEqualTo(2)
        assertThat(server.requestCount).isEqualTo(4)

        val requests = List(4) { server.takeRequest(1, TimeUnit.SECONDS)!! }
        val registered = requests.single { it.requestUrl!!.queryParameter("cmd") == "vista" }
        val commands = requests.filter { it.requestUrl!!.queryParameter("cmd") == "lineasyrutas" }
        assertThat(registered.cookieSessionId()).isEqualTo("same-conf-session")
        assertThat(registered.formBodyText()).isEqualTo("fp=${registeredFingerprint.get()}")
        assertThat(commands.map { it.cookieSessionId() }.toSet()).containsExactly("same-conf-session")
        assertThat(commands.map { it.headers["X-Device-FlakerParam"] }.toSet())
            .containsExactly(registeredFingerprint.get())
    }

    @Test
    fun emptyBodyTriggersResessionAndRetry() = runTest {
        server.enqueue(bootstrapPage("first"))
        server.enqueue(MockResponse().setResponseCode(200)) // cmd=vista
        server.enqueue(MockResponse().setResponseCode(200).setBody("")) // 408 lógico
        server.enqueue(bootstrapPage("second"))
        server.enqueue(MockResponse().setResponseCode(200)) // cmd=vista
        server.enqueue(linesJson())

        val client = TuBondiHttpClient(baseUrl = server.url("/").toString().trimEnd('/'))
        val result = client.getLinesAndRoutes()

        assertThat(result.lineas).isEmpty()
        assertThat(client.currentSessionId).isEqualTo("second")
        assertThat(server.requestCount).isEqualTo(6)
    }

    @Test
    fun concurrentDifferentConfsKeepCookiesAndFingerprintsIsolated(): Unit = runBlocking {
        val bothBootstrapsArrived = CountDownLatch(2)
        val bootstrapTimedOut = AtomicBoolean(false)
        val registeredFingerprints = ConcurrentHashMap<String, String>()
        val protocolMismatch = AtomicBoolean(false)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val command = request.requestUrl?.queryParameter("cmd")
                if (request.method == "GET") {
                    val conf = request.requestUrl?.queryParameter("conf") ?: return MockResponse().setResponseCode(400)
                    bothBootstrapsArrived.countDown()
                    if (!bothBootstrapsArrived.await(3, TimeUnit.SECONDS)) bootstrapTimedOut.set(true)
                    return bootstrapPage("session-$conf")
                }
                return when (command) {
                    "vista" -> {
                        val sessionId = request.cookieSessionId()
                        val fp = request.formBodyText().removePrefix("fp=")
                        if (sessionId == null || fp.length != 64) {
                            protocolMismatch.set(true)
                        } else {
                            registeredFingerprints[sessionId] = fp
                        }
                        MockResponse().setResponseCode(200) // respuesta vacía es válida
                    }
                    "lineasyrutas" -> {
                        val body = request.formBodyText()
                        val conf = body.formValue("conf")
                        val sessionId = request.cookieSessionId()
                        val fingerprint = request.headers["X-Device-FlakerParam"]
                        if (conf == null || sessionId != "session-$conf" ||
                            registeredFingerprints[sessionId] != fingerprint ||
                            !request.headers["Referer"].orEmpty().contains("conf=$conf")
                        ) {
                            protocolMismatch.set(true)
                        }
                        linesJson()
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val client = TuBondiHttpClient(baseUrl = server.url("/").toString().trimEnd('/'))
        val cityA = async(Dispatchers.IO) { client.getLinesAndRoutes("cityA") }
        val cityB = async(Dispatchers.IO) { client.getLinesAndRoutes("cityB") }
        assertThat(cityA.await().lineas).isEmpty()
        assertThat(cityB.await().lineas).isEmpty()

        assertThat(bootstrapTimedOut.get()).isFalse()
        assertThat(protocolMismatch.get()).isFalse()
        assertThat(server.requestCount).isEqualTo(6)
        val requests = List(6) { server.takeRequest(1, TimeUnit.SECONDS)!! }
        val bootstraps = requests.filter { it.method == "GET" }
        val registrations = requests.filter { it.requestUrl!!.queryParameter("cmd") == "vista" }
        val commands = requests.filter { it.requestUrl!!.queryParameter("cmd") == "lineasyrutas" }
        assertThat(bootstraps.map { it.requestUrl!!.queryParameter("conf") }.toSet())
            .containsExactly("cityA", "cityB")
        assertThat(registrations.map { it.cookieSessionId() }.toSet())
            .containsExactly("session-cityA", "session-cityB")
        assertThat(commands.map { it.formBodyText().formValue("conf") }.toSet())
            .containsExactly("cityA", "cityB")
        commands.forEach { request ->
            val conf = request.formBodyText().formValue("conf")!!
            val sessionId = request.cookieSessionId()
            assertThat(sessionId).isEqualTo("session-$conf")
            assertThat(request.headers["X-Device-FlakerParam"])
                .isEqualTo(registeredFingerprints[sessionId])
            assertThat(request.headers["Referer"]).contains("conf=$conf")
        }
    }

    @Test
    fun fingerprintRegistration403And408RetryCatalogBeforeSendingCommand() = runTest {
        for (status in listOf(403, 408)) {
            val conf = "registration$status"
            val start = server.requestCount
            server.enqueue(bootstrapPage("rejected-$status"))
            server.enqueue(MockResponse().setResponseCode(status))
            server.enqueue(bootstrapPage("accepted-$status"))
            server.enqueue(MockResponse().setResponseCode(200).setBody(""))
            server.enqueue(linesJson())
            val client = TuBondiHttpClient(baseUrl = server.url("/").toString().trimEnd('/'))

            assertThat(client.getLinesAndRoutes(conf).lineas).isEmpty()
            assertThat(server.requestCount).isEqualTo(start + 5)
            val requests = List(5) { server.takeRequest(1, TimeUnit.SECONDS)!! }
            assertThat(requests[0].method).isEqualTo("GET")
            assertThat(requests[0].requestUrl!!.queryParameter("conf")).isEqualTo(conf)
            assertThat(requests[1].requestUrl!!.queryParameter("cmd")).isEqualTo("vista")
            assertThat(requests[1].cookieSessionId()).isEqualTo("rejected-$status")
            assertThat(requests[1].formBodyText()).startsWith("fp=")
            assertThat(requests[2].method).isEqualTo("GET")
            assertThat(requests[2].requestUrl!!.queryParameter("conf")).isEqualTo(conf)
            assertThat(requests[2].getHeader("Cookie")).isNull()
            assertThat(requests[3].requestUrl!!.queryParameter("cmd")).isEqualTo("vista")
            assertThat(requests[3].cookieSessionId()).isEqualTo("accepted-$status")
            assertThat(requests[4].requestUrl!!.queryParameter("cmd")).isEqualTo("lineasyrutas")
            assertThat(requests[4].cookieSessionId()).isEqualTo("accepted-$status")
            assertThat(requests[4].headers["X-Device-FlakerParam"])
                .isEqualTo(requests[3].formBodyText().removePrefix("fp="))
        }
        assertThat(server.takeRequest(100, TimeUnit.MILLISECONDS)).isNull()
    }

    @Test
    fun catalog403ResessionsOnceWithFreshCookieAndFingerprint() = runTest {
        server.enqueue(bootstrapPage("old-session"))
        server.enqueue(MockResponse().setResponseCode(200)) // fp registration may be empty
        server.enqueue(MockResponse().setResponseCode(403))
        server.enqueue(bootstrapPage("new-session"))
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(linesJson())
        val conf = "city403"
        val client = TuBondiHttpClient(baseUrl = server.url("/").toString().trimEnd('/'))

        assertThat(client.getLinesAndRoutes(conf).lineas).isEmpty()
        assertThat(server.requestCount).isEqualTo(6)
        val requests = List(6) { server.takeRequest(1, TimeUnit.SECONDS)!! }
        assertThat(requests[0].requestUrl!!.queryParameter("conf")).isEqualTo(conf)
        assertThat(requests[1].cookieSessionId()).isEqualTo("old-session")
        assertThat(requests[2].cookieSessionId()).isEqualTo("old-session")
        assertThat(requests[3].requestUrl!!.queryParameter("conf")).isEqualTo(conf)
        assertThat(requests[3].getHeader("Cookie")).isNull()
        assertThat(requests[4].cookieSessionId()).isEqualTo("new-session")
        assertThat(requests[5].cookieSessionId()).isEqualTo("new-session")
        assertThat(requests[5].formBodyText().formValue("conf")).isEqualTo(conf)
        assertThat(requests[5].headers["Referer"]).contains("conf=$conf")
        assertThat(requests[5].headers["X-Device-FlakerParam"])
            .isEqualTo(requests[4].formBodyText().removePrefix("fp="))
    }

    @Test
    fun cancellationForOneConfDoesNotResetAnotherConfSession(): Unit = runBlocking {
        val slowPostStarted = CountDownLatch(1)
        val canceledCallObserved = CountDownLatch(1)
        val cancellationObserved = AtomicBoolean(false)
        val observingClient = OkHttpClient.Builder()
            .eventListener(object : EventListener() {
                override fun callFailed(call: Call, ioe: IOException) {
                    if (call.isCanceled()) {
                        cancellationObserved.set(true)
                        canceledCallObserved.countDown()
                    }
                }
            })
            .retryOnConnectionFailure(false)
            .build()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.method == "GET") {
                    val conf = request.requestUrl?.queryParameter("conf") ?: return MockResponse().setResponseCode(400)
                    return bootstrapPage("session-$conf")
                }
                return when (request.requestUrl?.queryParameter("cmd")) {
                    "vista" -> MockResponse().setResponseCode(200)
                    "lineasyrutas" -> {
                        val conf = request.formBodyText().formValue("conf")
                        if (conf == "slowCity") {
                            slowPostStarted.countDown()
                            MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
                        } else {
                            linesJson()
                        }
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val client = TuBondiHttpClient(
            baseUrl = server.url("/").toString().trimEnd('/'),
            okHttpClient = observingClient
        )
        val slow = async(Dispatchers.IO) { client.getLinesAndRoutes("slowCity") }
        assertTrue(slowPostStarted.await(3, TimeUnit.SECONDS))
        assertThat(client.getLinesAndRoutes("fastCity").lineas).isEmpty()
        slow.cancelAndJoin()
        assertTrue(canceledCallObserved.await(2, TimeUnit.SECONDS))
        assertThat(cancellationObserved.get()).isTrue()
        assertThat(client.getLinesAndRoutes("fastCity").lineas).isEmpty()

        assertThat(server.requestCount).isEqualTo(7)
        val requests = List(7) { server.takeRequest(1, TimeUnit.SECONDS)!! }
        assertThat(requests.count { it.method == "GET" }).isEqualTo(2)
        assertThat(requests.count { it.requestUrl!!.queryParameter("cmd") == "vista" }).isEqualTo(2)
        val fastCommands = requests.filter {
            it.requestUrl!!.queryParameter("cmd") == "lineasyrutas" &&
                it.formBodyText().formValue("conf") == "fastCity"
        }
        assertThat(fastCommands).hasSize(2)
        assertThat(fastCommands.map { it.cookieSessionId() }.toSet()).containsExactly("session-fastCity")
        assertThat(fastCommands.map { it.headers["X-Device-FlakerParam"] }.toSet()).hasSize(1)
    }

    @Test
    fun serializationErrorDoesNotRetry() = runTest {
        server.enqueue(bootstrapPage("s1"))
        server.enqueue(MockResponse().setResponseCode(200)) // cmd=vista
        server.enqueue(MockResponse().setResponseCode(200).setBody("no-json{"))

        val client = TuBondiHttpClient(baseUrl = server.url("/").toString().trimEnd('/'))
        var thrown: Throwable? = null
        try {
            client.getLinesAndRoutes()
        } catch (t: Throwable) {
            thrown = t
        }

        assertThat(thrown).isInstanceOf(SerializationException::class.java)
        assertThat(server.requestCount).isEqualTo(3)
    }

    @Test
    fun cancellationCancelsOkHttpCallAndDoesNotRetry() = kotlinx.coroutines.runBlocking {
        server.enqueue(bootstrapPage("first"))
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val canceledCall = CountDownLatch(1)
        val callFailure = AtomicReference<String?>()
        val callWasCanceled = AtomicBoolean(false)
        val http = OkHttpClient.Builder()
            .eventListener(object : EventListener() {
                override fun callFailed(call: Call, ioe: IOException) {
                    callFailure.set(ioe.toString())
                    callWasCanceled.set(call.isCanceled())
                    canceledCall.countDown()
                }
            })
            .retryOnConnectionFailure(false)
            .build()
        val client = TuBondiHttpClient(
            baseUrl = server.url("/").toString().trimEnd('/'),
            okHttpClient = http
        )

        val result = withTimeoutOrNull(1000) { client.getLinesAndRoutes() }

        assertThat(result).isNull()
        assertTrue("OkHttp debe cerrar la llamada al cancelarla; observado=${callFailure.get()}", canceledCall.await(2, TimeUnit.SECONDS))
        assertThat(callWasCanceled.get()).isTrue()
        assertThat(server.requestCount).isEqualTo(3)
        repeat(3) { assertThat(server.takeRequest(1, TimeUnit.SECONDS)).isNotNull() }
    }

    @Test
    fun cancellationDuringSessionRetryCancelsBootstrapAndDoesNotRetryAgain() = kotlinx.coroutines.runBlocking {
        server.enqueue(bootstrapPage("first"))
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(MockResponse().setResponseCode(408))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val canceledCall = CountDownLatch(1)
        val callFailure = AtomicReference<String?>()
        val callWasCanceled = AtomicBoolean(false)
        val http = OkHttpClient.Builder()
            .eventListener(object : EventListener() {
                override fun callFailed(call: Call, ioe: IOException) {
                    callFailure.set(ioe.toString())
                    callWasCanceled.set(call.isCanceled())
                    canceledCall.countDown()
                }
            })
            .retryOnConnectionFailure(false)
            .build()
        val client = TuBondiHttpClient(
            baseUrl = server.url("/").toString().trimEnd('/'),
            okHttpClient = http
        )

        val result = withTimeoutOrNull(1000) { client.getLinesAndRoutes() }

        assertThat(result).isNull()
        assertTrue("El bootstrap de re-sesión debe cerrar al cancelarlo; observado=${callFailure.get()}", canceledCall.await(2, TimeUnit.SECONDS))
        assertThat(callWasCanceled.get()).isTrue()
        assertThat(server.requestCount).isEqualTo(4)
        repeat(4) { assertThat(server.takeRequest(1, TimeUnit.SECONDS)).isNotNull() }
        assertThat(client.currentSessionId).isNull()
    }

    @Test
    fun rejectedStopRequestIsNotRetriedAndNextCycleStartsFreshSession() = runTest {
        server.enqueue(bootstrapPage("first"))
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(MockResponse().setResponseCode(408))
        server.enqueue(bootstrapPage("second"))
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"proximos_arribos":[]}"""))
        val client = TuBondiHttpClient(baseUrl = server.url("/").toString().trimEnd('/'))

        val failure = runCatching { client.getArrivals("0001") }.exceptionOrNull()

        assertThat(failure).isInstanceOf(IOException::class.java)
        assertThat(client.currentSessionId).isNull()
        assertThat(server.requestCount).isEqualTo(3)
        assertThat(server.takeRequest(1, TimeUnit.SECONDS)!!.method).isEqualTo("GET")
        assertThat(server.takeRequest(1, TimeUnit.SECONDS)!!.requestUrl!!.queryParameter("cmd")).isEqualTo("vista")
        val failedStopRequest = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertThat(failedStopRequest.body.readUtf8()).contains("cmd=proximos_arribos")
        assertThat(server.takeRequest(100, TimeUnit.MILLISECONDS)).isNull()

        assertThat(client.getArrivals("0001").arrivals).isEmpty()
        assertThat(client.currentSessionId).isEqualTo("second")
        assertThat(server.requestCount).isEqualTo(6)
        assertThat(server.takeRequest(1, TimeUnit.SECONDS)!!.method).isEqualTo("GET")
        assertThat(server.takeRequest(1, TimeUnit.SECONDS)!!.requestUrl!!.queryParameter("cmd")).isEqualTo("vista")
        val nextCycleStopRequest = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertThat(nextCycleStopRequest.body.readUtf8()).contains("cmd=proximos_arribos")
        assertThat(server.takeRequest(100, TimeUnit.MILLISECONDS)).isNull()
    }

    @Test
    fun forbiddenStopRequestIsNotRetriedWithinSameCycle() = runTest {
        server.enqueue(bootstrapPage("first"))
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(MockResponse().setResponseCode(403))
        val client = TuBondiHttpClient(baseUrl = server.url("/").toString().trimEnd('/'))

        val failure = runCatching { client.getArrivals("0001") }.exceptionOrNull()

        assertThat(failure).isInstanceOf(IOException::class.java)
        assertThat(client.currentSessionId).isNull()
        assertThat(server.requestCount).isEqualTo(3)
        assertThat(server.takeRequest(1, TimeUnit.SECONDS)!!.method).isEqualTo("GET")
        assertThat(server.takeRequest(1, TimeUnit.SECONDS)!!.requestUrl!!.queryParameter("cmd")).isEqualTo("vista")
        val forbiddenStopRequest = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertThat(forbiddenStopRequest.body.readUtf8()).contains("cmd=proximos_arribos")
        assertThat(server.takeRequest(100, TimeUnit.MILLISECONDS)).isNull()
    }

    @Test
    fun emptyStopResponseIsNotRetriedWithinSameCycle() = runTest {
        server.enqueue(bootstrapPage("first"))
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        val client = TuBondiHttpClient(baseUrl = server.url("/").toString().trimEnd('/'))

        val failure = runCatching { client.getArrivals("0001") }.exceptionOrNull()

        assertThat(failure).isInstanceOf(IOException::class.java)
        assertThat(client.currentSessionId).isNull()
        assertThat(server.requestCount).isEqualTo(3)
        assertThat(server.takeRequest(1, TimeUnit.SECONDS)!!.method).isEqualTo("GET")
        assertThat(server.takeRequest(1, TimeUnit.SECONDS)!!.requestUrl!!.queryParameter("cmd")).isEqualTo("vista")
        val emptyStopRequest = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertThat(emptyStopRequest.body.readUtf8()).contains("cmd=proximos_arribos")
        assertThat(server.takeRequest(100, TimeUnit.MILLISECONDS)).isNull()
    }
}
