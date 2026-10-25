package com.tubondi.widget.data.repo

import com.tubondi.widget.data.remote.TuBondiHttpClient
import com.tubondi.widget.data.remote.dto.ArrivalDto
import com.tubondi.widget.data.remote.dto.ArrivalsResponseDto
import com.tubondi.widget.data.remote.dto.LineDto
import com.tubondi.widget.data.remote.dto.LinesRoutesResponseDto
import com.tubondi.widget.data.remote.dto.RouteSelectionResponseDto
import com.tubondi.widget.data.remote.dto.StopDto
import com.tubondi.widget.data.remote.dto.StopLineDto
import com.tubondi.widget.domain.EtaParser
import com.tubondi.widget.domain.model.Arrival
import com.tubondi.widget.domain.model.Line
import com.tubondi.widget.domain.model.Stop
import com.tubondi.widget.domain.model.StopArrivals
import com.tubondi.widget.domain.model.StopSelection
import com.tubondi.widget.domain.model.WidgetConfiguration
import java.time.Clock
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
    private val clock: Clock
) {

    suspend fun fetchLines(conf: String): List<Line> {
        val dto = client.getLinesAndRoutes(conf)
        return dto.lineas.map { it.toDomainLine() }
    }

    suspend fun fetchLinesPayload(conf: String) = client.getLinesAndRoutes(conf)

    suspend fun fetchStopsForRoute(routeId: Int, clientId: Int, conf: String): List<Stop> {
        val dto: RouteSelectionResponseDto = client.selectRouteTrace(routeId, clientId, conf)
        return dto.paradas.map { it.toDomainStop() }
    }

    suspend fun fetchArrivalsForConfig(config: WidgetConfiguration): List<StopArrivals> {
        val results = mutableListOf<StopArrivals>()
        // Presupuesto de ciclo (paridad Oma: todas las paradas dentro de ~30s).
        // Chequeo cooperativo antes de cada parada: lo que no llega queda con
        // el dato anterior (el worker retiene el último estado válido).
        val deadline = clock.millis() + CYCLE_BUDGET_MS
        for (selection in config.selections) {
            if (clock.millis() >= deadline) break
            val response = client.getArrivals(selection.stop.code, config.conf)
            val arrivals = filterArrivals(response, selection)
            val stop = response.parada?.toDomainStop() ?: selection.stop
            results += StopArrivals(
                stop = stop,
                arrivals = arrivals,
                backendMessage = response.err ?: response.notificacion?.mensaje
            )
        }
        return results
    }

    private fun filterArrivals(response: ArrivalsResponseDto, selection: StopSelection): List<Arrival> {
        val allowed = selection.selectedLines.toSet()
        return response.arrivals
            .filter { allowed.isEmpty() || it.lineId in allowed }
            .map { it.toDomain(selection.stop.code) }
    }

    private fun LineDto.toDomainLine(): Line = Line(
        id = id,
        name = name,
        color = color,
        operator = null
    )

    private fun StopLineDto.toDomainLine(): Line = Line(
        id = lineId,
        name = name,
        color = color,
        operator = operador
    )

    private fun StopDto.toDomainStop(): Stop = Stop(
        code = codigo,
        name = descripcion.ifBlank { paradaNombre },
        latitude = lat,
        longitude = lon,
        lines = lineas.map { it.toDomainLine() }
    )

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
            notification = notificacion
        )
    }

    companion object {
        /** Presupuesto de ciclo (paridad Oma: todas las paradas en ~30s). */
        const val CYCLE_BUDGET_MS = 30_000L
    }
}
