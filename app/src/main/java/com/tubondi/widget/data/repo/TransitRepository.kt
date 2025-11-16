package com.tubondi.widget.data.repo

import com.tubondi.widget.data.remote.TuBondiHttpClient
import com.tubondi.widget.data.remote.dto.ArrivalDto
import com.tubondi.widget.data.remote.dto.ArrivalsResponseDto
import com.tubondi.widget.data.remote.dto.LineDto
import com.tubondi.widget.data.remote.dto.RouteSelectionResponseDto
import com.tubondi.widget.data.remote.dto.StopDto
import com.tubondi.widget.data.remote.dto.StopLineDto
import com.tubondi.widget.domain.model.Arrival
import com.tubondi.widget.domain.model.Line
import com.tubondi.widget.domain.model.Stop
import com.tubondi.widget.domain.model.StopArrivals
import com.tubondi.widget.domain.model.StopSelection
import com.tubondi.widget.domain.model.WidgetConfiguration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fuente de datos que combina respuestas de red con reglas de dominio.
 */
@Singleton
class TransitRepository @Inject constructor(
    private val client: TuBondiHttpClient
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
        for (selection in config.selections) {
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
            .filter { allowed.isEmpty() || allowed.contains(it.lineIdGuess()) }
            .map { it.toDomain() }
    }

    private fun LineDto.toDomainLine(): Line = Line(
        id = id.toIntOrNull() ?: id.hashCode(),
        name = name,
        color = color,
        operator = null
    )

    private fun StopLineDto.toDomainLine(): Line = Line(
        id = lineId.toIntOrNull() ?: lineId.hashCode(),
        name = name,
        color = color,
        operator = operador
    )

    private fun StopDto.toDomainStop(): Stop = Stop(
        code = codigo,
        name = descripcion,
        latitude = lat,
        longitude = lon,
        lines = lineas.map { it.toDomainLine() }
    )

    private fun ArrivalDto.lineIdGuess(): Int = lineName.filter { it.isDigit() }.toIntOrNull() ?: lineName.hashCode()

    private fun ArrivalDto.toDomain(): Arrival = Arrival(
        stopCode = stopCode,
        lineName = lineName,
        etaMinutes = etaMinutes,
        distanceMeters = distanceMeters,
        direction = direction,
        vehicleId = vehicleId,
        operator = operator,
        color = color
    )
}