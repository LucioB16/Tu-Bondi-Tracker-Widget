package com.tubondi.widget.data.remote

import java.io.IOException
import java.net.CookieManager
import java.net.CookiePolicy
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okio.BufferedSink
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.JavaNetCookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import com.tubondi.widget.data.remote.dto.ArrivalsResponseDto
import com.tubondi.widget.data.remote.dto.LinesRoutesResponseDto
import com.tubondi.widget.data.remote.dto.RouteSelectionResponseDto

/**
 * Cliente HTTP con el handshake anti-408 verificado (receta de Oma,
 * replicada y confirmada contra la API real el 2026-10-25; ver
 * docs/SDD_API_CONTRACT.md §7 y docs/evidence/handshake-verificacion.log):
 *
 * 1. GET bootstrap (UA de navegador) -> PHPSESSID + HTML con `operacion`
 *    (extraída como gate de frescura; NO se envía en los POST).
 * 2. fp aleatorio de 64 hex registrado con POST ?cmd=vista (form fp=, sin conf).
 * 3. Todo POST lleva X-Device-FlakerParam: <fp> + headers de browser y
 *    Content-Type SIN charset.
 * 4. 408/403/cuerpo vacío = sesión expirada -> re-sesión + 1 retry.
 *    SerializationException = contenido no parseable -> sin retry.
 *
 * El estado de sesión vive sólo en memoria del proceso (RNF-4): nunca
 * se persiste ni se loguea (HttpLoggingInterceptor BASIC no registra cookies).
 */
class TuBondiHttpClient(
    baseUrl: String = DEFAULT_BASE_URL,
    private val defaultConf: String = DEFAULT_CONF,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    },
    okHttpClient: OkHttpClient? = null
) {
    private val normalizedBase = if (baseUrl.endsWith('/')) baseUrl.dropLast(1) else baseUrl
    private val cookieManager = CookieManager(null, CookiePolicy.ACCEPT_ALL)
    private val client: OkHttpClient = okHttpClient ?: OkHttpClient.Builder()
        .cookieJar(JavaNetCookieJar(cookieManager))
        // El re-sesión+retry ante 408 lo maneja executeWithSession (con nueva
        // sesión); el retry transparente de OkHttp repetiría el POST sin
        // re-sesionar y ensuciaría el flujo.
        .retryOnConnectionFailure(false)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()
    private val webUrl = (normalizedBase + BOOTSTRAP_PATH).toHttpUrl()
    private val cmdUrl = (normalizedBase + CMD).toHttpUrl()
    private val secureRandom = SecureRandom()

    // Estado de sesión del ciclo (nunca persistido).
    private var sessionId: String? = null
    private var sessionOperacion: String? = null
    private var sessionFp: String? = null

    suspend fun initializeSession(conf: String = defaultConf): String = withContext(Dispatchers.IO) {
        val url = webUrl.newBuilder().addQueryParameter("conf", conf).build()
        val request = Request.Builder().get().url(url).header("User-Agent", BROWSER_UA).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Bootstrap failed ${response.code}")
            }
            val html = response.body?.string().orEmpty()
            sessionId = extractSessionId()
                ?: throw IllegalStateException("PHPSESSID missing after bootstrap")
            sessionOperacion = OPERACION_REGEX.find(html)?.groupValues?.get(1)
                ?: throw IOException("operacion not found in bootstrap HTML")
            val fp = newFingerprint()
            sessionFp = fp
            registerFingerprint(fp)
            return@withContext sessionId!!
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

    /**
     * Ciclo con re-sesión: 2 intentos como máximo. Una SerializationException
     * no se reintenta (la respuesta fue 200 pero el contenido no es el esperado;
     * una sesión nueva no lo arregla).
     */
    private suspend fun <T> executeWithSession(block: suspend () -> T): T {
        var attempt = 0
        var lastError: Throwable? = null
        while (attempt < 2) {
            try {
                if (sessionId.isNullOrEmpty()) initializeSession()
                return block()
            } catch (ex: SerializationException) {
                throw ex
            } catch (ex: IOException) {
                lastError = ex
                resetSession()
                attempt++
            }
        }
        throw lastError ?: IllegalStateException("Unknown error calling TuBondi")
    }

    private fun resetSession() {
        sessionId = null
        sessionOperacion = null
        sessionFp = null
    }

    private suspend fun registerFingerprint(fp: String) = withContext(Dispatchers.IO) {
        // Igual que el cosechador de Oma: UA + cookies, sin X-Device-FlakerParam
        // (todavía no está registrado) y respuesta descartada.
        val url = cmdUrl.newBuilder().addQueryParameter("cmd", "vista").build()
        val request = Request.Builder()
            .url(url)
            .post(formBodyNoCharset(listOf("fp" to fp)))
            .header("User-Agent", BROWSER_UA)
            .build()
        client.newCall(request).execute().use { /* respuesta descartada */ }
    }

    /**
     * POST de formulario con Content-Type SIN charset (la receta anti-408).
     * FormBody dura agrega "; charset=UTF-8", así que se envuelve su cuerpo
     * con el MediaType exacto verificado.
     */
    private fun formBodyNoCharset(params: List<Pair<String, String>>): RequestBody {
        val builder = FormBody.Builder()
        params.forEach { (k, v) -> builder.add(k, v) }
        val form = builder.build()
        return object : RequestBody() {
            override fun contentType() = "application/x-www-form-urlencoded".toMediaType()
            override fun contentLength(): Long = form.contentLength()
            override fun writeTo(sink: BufferedSink) = form.writeTo(sink)
        }
    }

    private suspend fun <T> postForm(
        queryCommand: String? = null,
        bodyCommand: String? = null,
        conf: String? = null,
        params: Map<String, String>,
        parse: (String) -> T
    ): T = withContext(Dispatchers.IO) {
        val formPairs = buildList {
            add("conf" to (conf ?: defaultConf))
            if (!bodyCommand.isNullOrEmpty()) add("cmd" to bodyCommand)
            params.forEach { (k, v) -> add(k to v) }
        }
        val target = cmdUrl.newBuilder().apply {
            if (!queryCommand.isNullOrEmpty()) {
                addQueryParameter("cmd", queryCommand)
            }
        }.build()
        val request = Request.Builder()
            .url(target)
            .post(formBodyNoCharset(formPairs))
            .header("Accept", "*/*")
            .header("Accept-Language", "es-419,es;q=0.9")
            .header("Origin", normalizedBase)
            .header("Referer", "$normalizedBase$BOOTSTRAP_PATH?conf=${conf ?: defaultConf}")
            .header("Sec-Fetch-Dest", "empty")
            .header("Sec-Fetch-Mode", "cors")
            .header("Sec-Fetch-Site", "same-origin")
            .header("X-Device-FlakerParam", sessionFp.orEmpty())
            .header("User-Agent", BROWSER_UA)
            .build()
        client.newCall(request).execute().use { response ->
            when (response.code) {
                408 -> throw IOException("Session expired (408)")
                403 -> throw IOException("Session expired (403)")
            }
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }
            val payload = response.body?.string()
            if (payload.isNullOrEmpty()) {
                throw IOException("Empty body (posible 408 lógico del server)")
            }
            return@withContext parse(payload)
        }
    }

    private fun newFingerprint(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun extractSessionId(): String? {
        return cookieManager.cookieStore.cookies
            .firstOrNull { it.name == "PHPSESSID" }
            ?.value
    }

    companion object {
        private const val BOOTSTRAP_PATH = "/usuario/urbano.php"
        private const val CMD = "/usuario/urbano2_cmd.php"
        const val DEFAULT_BASE_URL = "https://micronauta4.dnsalias.net"
        const val DEFAULT_CONF = "cbaciudad"
        private val OPERACION_REGEX = Regex("operacion:'(\\d+)'")
        private const val BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36 Edg/154.0.0.0"
    }
}
