package com.tubondi.widget.data.remote

import com.tubondi.widget.data.remote.dto.ArrivalsResponseDto
import com.tubondi.widget.data.remote.dto.LinesRoutesResponseDto
import com.tubondi.widget.data.remote.dto.RouteSelectionResponseDto
import java.io.IOException
import java.net.CookieManager
import java.net.CookiePolicy
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.JavaNetCookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import okio.BufferedSink

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
 * 4. Los POST de catálogo/ruta reintentan una vez tras fallo de sesión/request.
 *    `proximos_arribos` no repite el POST de una parada durante el mismo ciclo;
 *    invalida solo su contexto y falla el ciclo. SerializationException no reintenta.
 * 5. Un contexto por `conf`, protegido durante toda la operación; las credenciales
 *    solo se publican después del 2xx de `cmd=vista` (body vacío permitido).
 *
 * Cookies y credenciales viven solo en memoria del proceso (RNF-4): nunca se
 * persisten ni se loguean (HttpLoggingInterceptor BASIC no registra cookies).
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
    private val baseClient: OkHttpClient = okHttpClient ?: OkHttpClient.Builder()
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
    // ponytail: one context per distinct conf until process death; add idle eviction only if custom-conf volume grows.
    private val sessions = ConcurrentHashMap<String, SessionState>()

    private fun sessionFor(conf: String): SessionState = sessions.computeIfAbsent(conf) {
        newSessionState()
    }

    private fun newSessionState(): SessionState {
        val cookieManager = CookieManager(null, CookiePolicy.ACCEPT_ALL)
        return SessionState(newSessionClient(cookieManager), cookieManager)
    }

    private fun newSessionClient(cookieManager: CookieManager): OkHttpClient =
        baseClient.newBuilder()
            .cookieJar(JavaNetCookieJar(cookieManager))
            .build()

    private data class SessionCredentials(
        val id: String,
        val fingerprint: String
    )

    private class SessionState(
        var client: OkHttpClient,
        var cookieManager: CookieManager
    ) {
        val mutex = Mutex()
        @Volatile var credentials: SessionCredentials? = null
    }

    suspend fun initializeSession(conf: String = defaultConf): String {
        val session = sessionFor(conf)
        return session.mutex.withLock {
            resetSession(session)
            try {
                initializeSessionWith(session, conf)
            } catch (ex: Exception) {
                resetSession(session)
                throw ex
            }
        }
    }

    suspend fun getLinesAndRoutes(conf: String? = null): LinesRoutesResponseDto =
        executeWithSession(conf) { session ->
            postForm(
                session = session,
                queryCommand = "lineasyrutas",
                conf = conf,
                params = emptyMap(),
                parse = { body -> json.decodeFromString(LinesRoutesResponseDto.serializer(), body) }
            )
        }

    suspend fun selectRouteTrace(rutaId: Int, clienteId: Int, conf: String? = null): RouteSelectionResponseDto =
        executeWithSession(conf) { session ->
            postForm(
                session = session,
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
    ): ArrivalsResponseDto = executeWithSession(conf, maxAttempts = 1) { session ->
        // RNF-1: como máximo un POST de arribos por parada y ciclo. Un 408,
        // 403, cuerpo vacío o error I/O falla el refresh completo; la próxima
        // invocación manual/periódica inicia sesión nueva, sin repetir esta parada.
        val serializer = MapSerializer(String.serializer(), Boolean.serializer())
        val onlyGpsPayload = onlyGps?.entries?.associate { it.key.toString() to it.value }.orEmpty()
        postForm(
            session = session,
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
    ): String = executeWithSession(conf) { session ->
        postForm(
            session = session,
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

    suspend fun getViewBounds(conf: String? = null): String = executeWithSession(conf) { session ->
        postForm(
            session = session,
            queryCommand = "vista",
            conf = conf,
            params = emptyMap(),
            parse = { it }
        )
    }

    val currentSessionId: String?
        get() = sessions[defaultConf]?.credentials?.id

    /**
     * Una operación completa (bootstrap, fp, POST, retry/reset) se serializa
     * por `conf`; otros `conf` usan cookies y sesiones independientes.
     */
    private suspend fun <T> executeWithSession(
        conf: String? = null,
        maxAttempts: Int = 2,
        block: suspend (SessionState) -> T
    ): T {
        require(maxAttempts > 0)
        val effectiveConf = conf ?: defaultConf
        val session = sessionFor(effectiveConf)
        return session.mutex.withLock {
            var attempt = 0
            var lastError: Throwable? = null
            while (attempt < maxAttempts) {
                try {
                    if (session.credentials == null) {
                        initializeSessionWith(session, effectiveConf)
                    }
                    return@withLock block(session)
                } catch (ex: CancellationException) {
                    resetSession(session)
                    throw ex
                } catch (ex: SerializationException) {
                    throw ex
                } catch (ex: IOException) {
                    currentCoroutineContext().ensureActive()
                    lastError = ex
                    resetSession(session)
                    attempt++
                }
            }
            throw lastError ?: IllegalStateException("Unknown error calling TuBondi")
        }
    }

    private fun resetSession(session: SessionState) {
        session.credentials = null
        val cookieManager = CookieManager(null, CookiePolicy.ACCEPT_ALL)
        session.cookieManager = cookieManager
        session.client = newSessionClient(cookieManager)
    }

    private suspend fun initializeSessionWith(session: SessionState, conf: String): String =
        withContext(Dispatchers.IO) {
            val url = webUrl.newBuilder().addQueryParameter("conf", conf).build()
            val request = Request.Builder().get().url(url).header("User-Agent", BROWSER_UA).build()
            val response = requestText(session, request)
            if (response.first !in 200..299) {
                throw IOException("Bootstrap failed ${response.first}")
            }
            val sessionId = extractSessionId(session.cookieManager)
                ?: throw IOException("PHPSESSID missing after bootstrap")
            if (OPERACION_REGEX.find(response.second) == null) {
                throw IOException("operacion not found in bootstrap HTML")
            }
            val fingerprint = newFingerprint()
            registerFingerprint(session, fingerprint)
            session.credentials = SessionCredentials(sessionId, fingerprint)
            sessionId
        }

    private suspend fun registerFingerprint(session: SessionState, fp: String) = withContext(Dispatchers.IO) {
        // El 200 vacío es correcto; 403/408 y cualquier otro status no-2xx
        // invalidan el handshake antes de enviar el primer comando de negocio.
        val url = cmdUrl.newBuilder().addQueryParameter("cmd", "vista").build()
        val request = Request.Builder()
            .url(url)
            .post(formBodyNoCharset(listOf("fp" to fp)))
            .header("User-Agent", BROWSER_UA)
            .build()
        val response = requestText(session, request)
        if (response.first !in 200..299) {
            throw IOException("Fingerprint registration failed ${response.first}")
        }
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
        session: SessionState,
        queryCommand: String? = null,
        bodyCommand: String? = null,
        conf: String? = null,
        params: Map<String, String>,
        parse: (String) -> T
    ): T = withContext(Dispatchers.IO) {
        val fingerprint = session.credentials?.fingerprint
            ?: throw IOException("Fingerprint missing for session")
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
            .header("X-Device-FlakerParam", fingerprint)
            .header("User-Agent", BROWSER_UA)
            .build()
        val response = requestText(session, request)
        when (response.first) {
            408 -> throw IOException("Session expired (408)")
            403 -> throw IOException("Session expired (403)")
        }
        if (response.first !in 200..299) {
            throw IOException("HTTP ${response.first}")
        }
        val payload = response.second
        if (payload.isNullOrEmpty()) {
            throw IOException("Empty body (posible 408 lógico del server)")
        }
        return@withContext parse(payload)
    }

    private suspend fun requestText(session: SessionState, request: Request): Pair<Int, String> =
        suspendCancellableCoroutine { continuation ->
            val call = session.client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val result = response.use { it.code to it.body?.string().orEmpty() }
                        if (continuation.isActive) continuation.resume(result)
                    } catch (e: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }
                }
            })
        }

    private fun newFingerprint(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun extractSessionId(cookieManager: CookieManager): String? {
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
