package com.tubondi.widget.data.repo

import com.tubondi.widget.data.remote.TuBondiHttpClient
import com.tubondi.widget.data.remote.dto.ArrivalDto
import com.tubondi.widget.data.remote.dto.ArrivalsResponseDto
import com.tubondi.widget.data.remote.dto.LineDto
import com.tubondi.widget.data.remote.dto.LinesRoutesResponseDto
import com.tubondi.widget.data.remote.dto.RouteSelectionResponseDto
import com.tubondi.widget.data.remote.dto.StopDto
import com.tubondi.widget.data.remote.dto.StopLineDto
import com.tubondi.widget.data.catalog.CatalogRepository
import com.tubondi.widget.domain.EtaParser
import com.tubondi.widget.domain.LineColorResolver
import com.tubondi.widget.domain.LineKey
import com.tubondi.widget.domain.keyOrNull
import com.tubondi.widget.domain.model.Arrival
import com.tubondi.widget.domain.model.Line
import com.tubondi.widget.domain.model.Stop
import com.tubondi.widget.domain.model.StopArrivals
import com.tubondi.widget.domain.model.StopSelection
import com.tubondi.widget.domain.model.WidgetConfiguration
import java.io.IOException
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fuente de datos que combina respuestas de red con reglas de dominio.
 * El filtrado de líneas usa el line_id REAL del arribo (campo `linea`);
 * vacío = todas las líneas.
 */
@Singleton
class TransitRepository @Inject constructor(
    private val client: TuBondiHttpClient,
    private val clock: Clock,
    private val catalogRepository: CatalogRepository
) {

    @Volatile private var lineColorsByConf: Map<String, Pair<Long, Map<LineKey, String?>>> = emptyMap()

    suspend fun fetchLines(conf: String): List<Line> {
        val dto = client.getLinesAndRoutes(conf)
        val lines = dto.lineas.map { it.toDomainLine() }
        cacheLineColors(conf, lines)
        return lines
    }

    suspend fun fetchLinesPayload(conf: String): LinesRoutesResponseDto = client.getLinesAndRoutes(conf).also { payload ->
        cacheLineColors(conf, payload.lineas.map { it.toDomainLine() })
    }

    suspend fun fetchStopsForRoute(
        routeId: Int,
        clientId: Int,
        conf: String,
        lineId: String? = null,
        lineName: String? = null,
        lineColor: String? = null
    ): List<Stop> {
        val dto: RouteSelectionResponseDto = client.selectRouteTrace(routeId, clientId, conf)
        val routeLine = lineId?.takeIf { it.isNotBlank() }?.let {
            Line(
                id = it,
                name = lineName.orEmpty(),
                color = LineColorResolver.normalize(lineColor),
                clientId = clientId
            )
        }
        return dto.paradas.map { catalogRepository.resolveStop(it.toDomainStop(routeLine)) }
    }

    suspend fun fetchArrivalsForConfig(config: WidgetConfiguration): List<StopArrivals> =
        withTimeoutOrNull(CYCLE_BUDGET_MS) { fetchCompleteCycle(config) }
            ?: throw IOException("Refresh cycle budget exceeded")

    private suspend fun fetchCompleteCycle(config: WidgetConfiguration): List<StopArrivals> {
        val results = mutableListOf<StopArrivals>()
        val lineColors = trustedLineColors(config.conf)
        // Presupuesto de ciclo (paridad Oma: todas las paradas dentro de ~30s).
        // Si no se completa el ciclo, el worker conserva el estado anterior.
        val deadline = clock.millis() + CYCLE_BUDGET_MS
        for (selection in config.selections) {
            if (clock.millis() >= deadline) throw IOException("Refresh cycle budget exceeded")
            val response = client.getArrivals(selection.stop.code, config.conf)
            val arrivals = filterArrivals(response, selection, lineColors)
            val selectedStop = catalogRepository.resolveStop(selection.stop)
            val responseStop = response.parada?.toDomainStop() ?: selectedStop
            val stop = catalogRepository.resolveStop(
                responseStop.copy(
                    hood = responseStop.hood ?: selectedStop.hood,
                    customName = selectedStop.customName
                )
            )
            results += StopArrivals(
                stop = stop,
                arrivals = arrivals,
                backendMessage = response.err ?: response.notificacion?.mensaje
            )
        }
        if (clock.millis() >= deadline) throw IOException("Refresh cycle budget exceeded")
        return results
    }

    private fun filterArrivals(
        response: ArrivalsResponseDto,
        selection: StopSelection,
        lineColors: Map<LineKey, String?>
    ): List<Arrival> {
        val allowed = selection.selectedLines.mapNotNull(::parseLineKey).toSet()
        val filterIsTrusted = selection.selectedLines.isEmpty() || allowed.size == selection.selectedLines.size
        val canonicalByStop = selection.stop.lines.mapNotNull { line -> line.keyOrNull()?.let { it to line.color } }.toMap()
        return response.arrivals
            .filter { arrival ->
                val key = arrival.lineId?.let { id -> arrival.cliente?.let { LineKey(it, id) } }
                !filterIsTrusted || selection.selectedLines.isEmpty() || (key != null && key in allowed)
            }
            .map { dto ->
                val arrival = dto.toDomain(selection.stop.code)
                val key = arrival.clientId?.let { LineKey(it, arrival.lineId) }
                val canonical = key?.let {
                    if (lineColors.containsKey(it)) lineColors[it] else canonicalByStop[it]
                }
                arrival.copy(color = LineColorResolver.forArrival(canonical, arrival.color))
            }
    }

    private fun LineDto.toDomainLine(): Line = Line(
        id = id,
        name = name,
        color = LineColorResolver.normalize(color),
        operator = null,
        clientId = cliente
    )

    private fun StopLineDto.toDomainLine(): Line = Line(
        id = lineId,
        name = name,
        color = null,
        operator = operador,
        clientId = clientId
    )

    private fun StopDto.toDomainStop(routeLine: Line? = null): Stop {
        val observedLineId = lineId
        val observedLine = if (observedLineId != null && clientId != null) {
            Line(
                id = observedLineId,
                name = routeLine?.name.orEmpty(),
                clientId = clientId
            )
        } else null
        val canonicalRouteLine = routeLine?.takeIf {
            observedLine == null || it.selectionKey == observedLine.selectionKey
        }
        val stopLines = (listOfNotNull(canonicalRouteLine) + lineas.mapNotNull { dto ->
            val id = dto.lineId.ifBlank { dto.selectedRouteLineId.orEmpty() }
            // Some selection responses omit `cliente` from `lineas[]` while
            // repeating the selected route line there. Reuse the already
            // verified route identity only for that matching line ID; never
            // infer a client for another line from its visible number.
            val client = dto.clientId
                ?: clientId
                ?: routeLine?.clientId?.takeIf { id == routeLine.id }
            id.takeIf { it.isNotBlank() }?.let {
                Line(
                    id = it,
                    name = dto.name.ifBlank { routeLine?.takeIf { route -> route.id == id }?.name.orEmpty() },
                    operator = dto.operador,
                    clientId = client
                )
            }
        } + listOfNotNull(observedLine))
            .distinctBy { it.selectionKey }
        return Stop(
        code = codigo,
        name = descripcion.ifBlank { nombre.ifBlank { paradaNombre } },
        latitude = lat?.jsonPrimitive?.content?.toDoubleOrNull(),
        longitude = lon?.jsonPrimitive?.content?.toDoubleOrNull(),
        lines = stopLines
    )
    }

    private fun ArrivalDto.toDomain(stopCode: String): Arrival {
        val etaText = EtaParser.etaText(demora, proximo).orEmpty()
        return Arrival(
            stopCode = stopCode,
            lineId = lineId.orEmpty(),
            lineName = lineName,
            etaText = etaText,
            etaMinutes = EtaParser.parse(etaText),
            distanceKm = distancia,
            direction = sentido,
            routeDescription = routeDescription,
            vehicleId = coche,
            operator = operator,
            color = color,
            clientId = cliente,
            notification = notificacion
        )
    }

    private suspend fun trustedLineColors(conf: String): Map<LineKey, String?> {
        val now = clock.millis()
        val cached = lineColorsByConf[conf]
        if (cached != null && now - cached.first < COLOR_CACHE_MILLIS) return cached.second
        return try {
            val colors = client.getLinesAndRoutes(conf).lineas
                .map { it.toDomainLine() }
                .let(LineColorResolver::canonicalColors)
            lineColorsByConf = lineColorsByConf + (conf to (now to colors))
            colors
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            cached?.second.orEmpty()
        }
    }

    private fun cacheLineColors(conf: String, lines: List<Line>) {
        lineColorsByConf = lineColorsByConf + (conf to (clock.millis() to LineColorResolver.canonicalColors(lines)))
    }

    private fun parseLineKey(value: String): LineKey? {
        val separator = value.indexOf(':')
        if (separator <= 0 || separator == value.lastIndex) return null
        return value.substring(0, separator).toIntOrNull()?.let { clientId ->
            LineKey(clientId, value.substring(separator + 1))
        }
    }

    companion object {
        /** Presupuesto de ciclo (paridad Oma: todas las paradas en ~30s). */
        const val CYCLE_BUDGET_MS = 30_000L
        private const val COLOR_CACHE_MILLIS = 6 * 60 * 60 * 1000L
    }
}
