package com.tubondi.widget.data.remote

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

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

    @Test
    fun retriesOnceWhenCookieExpires() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).addHeader("Set-Cookie", "PHPSESSID=first; path=/"))
        server.enqueue(MockResponse().setResponseCode(403))
        server.enqueue(MockResponse().setResponseCode(200).addHeader("Set-Cookie", "PHPSESSID=second; path=/"))
        server.enqueue(MockResponse().setBody("""{"lineas":[],"rutas":[],"clientes":[]}""").setResponseCode(200))

        val client = TuBondiHttpClient(baseUrl = server.url("/").toString().trimEnd('/'))
        val result = client.getLinesAndRoutes()

        assertThat(result.lineas).isEmpty()
        assertThat(client.currentSessionId).isEqualTo("second")
        assertThat(server.requestCount).isEqualTo(4)
    }
}