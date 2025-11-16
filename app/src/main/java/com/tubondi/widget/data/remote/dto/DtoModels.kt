package com.tubondi.widget.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs alineados con los JSON observados en TuBondi.
 */
@Serializable
data class LinesRoutesResponseDto(
    val lineas: List<LineDto> = emptyList(),
    val rutas: List<RouteDto> = emptyList(),
    val clientes: List<ClientDto> = emptyList()
)

@Serializable
data class LineDto(
    @SerialName("linea_id") val id: String,
    @SerialName("linea_nombre") val name: String,
    val color: String? = null,
    val grupo: String? = null,
    val cliente: Int? = null,
    val rutas: List<RouteSummaryDto> = emptyList()
)

@Serializable
data class RouteSummaryDto(
    @SerialName("ruta_id") val routeId: String,
    @SerialName("ruta_nombre") val name: String,
    val sentido: String? = null,
    val longitud: String? = null
)

@Serializable
data class RouteDto(
    @SerialName("ruta_id") val id: Int,
    @SerialName("linea_id") val lineId: Int,
    @SerialName("cliente_id") val clientId: Int,
    val nombre: String,
    val sentido: String? = null
)

@Serializable
data class ClientDto(
    val id: Int,
    val nombre: String,
    val color: String? = null
)

@Serializable
data class RouteSelectionResponseDto(
    val paradas: List<StopDto> = emptyList(),
    val notificaciones: List<BackendNotificationDto> = emptyList()
)

@Serializable
data class StopDto(
    val codigo: String,
    val descripcion: String,
    @SerialName("lat") val lat: Double? = null,
    @SerialName("lon") val lon: Double? = null,
    val lineas: List<StopLineDto> = emptyList()
)

@Serializable
data class StopLineDto(
    @SerialName("linea_id") val lineId: String,
    @SerialName("linea_nombre") val name: String,
    val color: String? = null,
    val operador: String? = null
)

@Serializable
data class ArrivalsResponseDto(
    @SerialName("arribos") val arrivals: List<ArrivalDto> = emptyList(),
    val parada: StopDto? = null,
    val notificacion: BackendNotificationDto? = null,
    val err: String? = null
)

@Serializable
data class ArrivalDto(
    @SerialName("linea_nombre") val lineName: String,
    @SerialName("linea_color") val color: String? = null,
    @SerialName("operador_nombre") val operator: String? = null,
    @SerialName("minutos_arribo") val etaMinutes: Int = 0,
    @SerialName("distancia") val distanceMeters: Int? = null,
    @SerialName("codigo_parada") val stopCode: String,
    @SerialName("sentido") val direction: String? = null,
    @SerialName("interno") val vehicleId: String? = null
)

@Serializable
data class BackendNotificationDto(
    val tipo: String? = null,
    val mensaje: String? = null
)