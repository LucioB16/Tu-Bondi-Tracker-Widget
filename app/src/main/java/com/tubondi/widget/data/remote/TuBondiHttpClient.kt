package com.tubondi.widget.data.remote

import java.io.IOException
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import com.tubondi.widget.data.remote.dto.ArrivalsResponseDto
import com.tubondi.widget.data.remote.dto.LinesRoutesResponseDto
import com.tubondi.widget.data.remote.dto.RouteSelectionResponseDto

/**
 * Cliente HTTP minimalista que replica el contrato expuesto en C#.
 */
class TuBondiHttpClient(
    baseUrl: String = DEFAULT_BASE_URL,
    private val defaultConf: String = DEFAULT_CONF,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    },
    okHttpClient: OkHttpClient? = null
) {
    private val normalizedBase = if (baseUrl.endsWith('/')) baseUrl.dropLast(1) else baseUrl
    private val cookieManager = CookieManager(null, CookiePolicy.ACCEPT_ALL)
    private val client: OkHttpClient = okHttpClient ?: OkHttpClient.Builder()
        .cookieJar(JavaNetCookieJar(cookieManager))
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val webUrl = (normalizedBase + WEB_URBANO).toHttpUrl()
    private val cmdUrl = (normalizedBase + CMD).toHttpUrl()
    private var sessionId: String? = null

    suspend fun initializeSession(conf: String = defaultConf): String = withContext(Dispatchers.IO) {
        val url = webUrl.newBuilder().addQueryParameter("conf", conf).build()
        val request = Request.Builder().get().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Bootstrap failed ${response.code}")
            }
            sessionId = extractSessionId()
            return@withContext sessionId
                ?: throw IllegalStateException("PHPSESSID missing after bootstrap")
        }
    }

    suspend fun getLinesAndRoutes(conf: String? = null): LinesRoutesResponseDto =
        executeWithSession {
            postForm(
                queryCommand = "lineasyrutas",
                conf = conf,
                params = emptyMap(),
                parse = { body -> json.decodeFromString(LinesRoutesResponseDto.serializer(), body) }
            )
        }

    suspend fun selectRouteTrace(rutaId: Int, clienteId: Int, conf: String? = null): RouteSelectionResponseDto =
        executeWithSession {
            postForm(
                queryCommand = "seleccionatraza",
                conf = conf,
                params = mapOf(
                    "ruta" to rutaId.toString(),
                    "cliente_id" to clienteId.toString()
                ),
                parse = { body -> json.decodeFromString(RouteSelectionResponseDto.serializer(), body) }
            )
        }

    suspend fun getArrivals(
        stopCode: String,
        conf: String? = null,
        show80: Boolean = false,
        onlyGps: Map<Int, Boolean>? = null
    ): ArrivalsResponseDto = executeWithSession {
        val serializer = MapSerializer(String.serializer(), Boolean.serializer())
        val onlyGpsPayload = onlyGps?.entries?.associate { it.key.toString() to it.value }.orEmpty()
        postForm(
            bodyCommand = "proximos_arribos",
            conf = conf,
            params = mapOf(
                "codigo" to stopCode,
                "show80min" to show80.toString(),
                "onlygps_array" to json.encodeToString(serializer, onlyGpsPayload)
            ),
            parse = { body -> json.decodeFromString(ArrivalsResponseDto.serializer(), body) }
        )
    }

    suspend fun queryVehiclesByRoute(
        rutaId: Int,
        clienteId: Int,
        stopCode: String? = null,
        conf: String? = null
    ): String = executeWithSession {
        postForm(
            queryCommand = "consultacocheporruta",
            conf = conf,
            params = buildMap {
                put("ruta", rutaId.toString())
                put("coche", "0")
                put("cliente", clienteId.toString())
                if (!stopCode.isNullOrEmpty()) {
                    put("parada_seleccionada", stopCode)
                }
            },
            parse = { it }
        )
    }

    suspend fun getViewBounds(conf: String? = null): String = executeWithSession {
        postForm(
            queryCommand = "vista",
            conf = conf,
            params = emptyMap(),
            parse = { it }
        )
    }

    val currentSessionId: String?
        get() = sessionId

    private suspend fun <T> executeWithSession(block: suspend () -> T): T {
        var attempt = 0
        var lastError: Throwable? = null
        while (attempt < 2) {
            if (sessionId.isNullOrEmpty()) {
                initializeSession()
            }
            try {
                return block()
            } catch (ex: IOException) {
                lastError = ex
                sessionId = null
                attempt++
            } catch (ex: SerializationException) {
                throw ex
            }
        }
        throw lastError ?: IllegalStateException("Unknown error calling TuBondi")
    }

    private suspend fun <T> postForm(
        queryCommand: String? = null,
        bodyCommand: String? = null,
        conf: String? = null,
        params: Map<String, String>,
        parse: (String) -> T
    ): T = withContext(Dispatchers.IO) {
        val builder = FormBody.Builder()
            .add("conf", conf ?: defaultConf)
        if (!bodyCommand.isNullOrEmpty()) {
            builder.add("cmd", bodyCommand)
        }
        params.forEach { (k, v) -> builder.add(k, v) }
        val body = builder.build()
        val target = cmdUrl.newBuilder().apply {
            if (!queryCommand.isNullOrEmpty()) {
                addQueryParameter("cmd", queryCommand)
            }
        }.build()
        val request = Request.Builder()
            .url(target)
            .post(body)
            .header("Accept-Language", "es-419")
            .header("User-Agent", USER_AGENT)
            .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 403) {
                throw IOException("Session expired")
            }
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }
            val payload = response.body?.string() ?: throw IOException("Empty body")
            return@withContext parse(payload)
        }
    }

    private fun extractSessionId(): String? {
        return cookieManager.cookieStore.cookies
            .firstOrNull { it.name == "PHPSESSID" }
            ?.value
    }

    companion object {
        private const val WEB_URBANO = "/web/urbano/"
        private const val CMD = "/usuario/urbano2_cmd.php"
        const val DEFAULT_BASE_URL = "https://micronauta4.dnsalias.net"
        const val DEFAULT_CONF = "cbaciudad"
        private const val USER_AGENT = "TuBondiWidget/1.0 (Android)"
    }
}