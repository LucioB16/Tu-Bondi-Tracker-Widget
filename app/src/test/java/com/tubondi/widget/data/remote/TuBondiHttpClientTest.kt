package com.tubondi.widget.data.remote

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
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
        val cookieManager = CookieManager(null, CookiePolicy.ACCEPT_ALL)
        val http = OkHttpClient.Builder()
            .cookieJar(JavaNetCookieJar(cookieManager))
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
            okHttpClient = http,
            cookieManager = cookieManager
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
        val cookieManager = CookieManager(null, CookiePolicy.ACCEPT_ALL)
        val http = OkHttpClient.Builder()
            .cookieJar(JavaNetCookieJar(cookieManager))
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
            okHttpClient = http,
            cookieManager = cookieManager
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
