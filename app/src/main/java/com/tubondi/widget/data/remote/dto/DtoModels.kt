package com.tubondi.widget.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs alineados con los JSON REALES de TuBondi.
 *
 * Fuentes de verdad: fixtures app/src/main/assets/api-examples/ (01-06),
 * respuesta viva del 2026-10-25 (docs/evidence/arribos-fresco.json) y
 * docs/SDD_API_CONTRACT.md. La API devuelve subsets variables de campos:
 * todo es opcional salvo lo esencial para renderizar.
 */
@Serializable
data class LinesRoutesResponseDto(
    val lineas: List<LineDto> = emptyList(),
    val grupos: List<GroupDto> = emptyList(),
    val clientes: List<ClientDto> = emptyList()
)

@Serializable
data class GroupDto(
    @SerialName("cliente_id") val clientId: Int,
    @SerialName("grupo_id") val groupId: String,
    val nombre: String? = null,
    val troncal: String? = null
)

@Serializable
data class ClientDto(
    val id: Int,
    val nombre: String,
    val color: String? = null
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
    @SerialName("ruta_nombre") val name: String? = null,
    val sentido: String? = null,
    val longitud: String? = null
)

@Serializable
data class RouteSelectionResponseDto(
    val paradas: List<StopDto> = emptyList(),
    val notificaciones: List<BackendNotificationDto> = emptyList()
)

/**
 * Parada tolerante a las dos formas del backend:
 * - seleccionatraza: {codigo, parada_nombre|nombre, lat/lon como STRINGS, linea, ...}
 * - proximos_arribos: {codigo, descripcion}
 */
@Serializable
data class StopDto(
    val codigo: String,
    val descripcion: String = "",
    @SerialName("parada_nombre") val paradaNombre: String = "",
    val lat: Double? = null,
    val lon: Double? = null,
    val lineas: List<StopLineDto> = emptyList()
)

@Serializable
data class StopLineDto(
    @SerialName("linea_id") val lineId: String,
    @SerialName("linea_nombre") val name: String,
    val color: String? = null,
    val operador: String? = null
)

/**
 * Contrato REAL de proximos_arribos (fixture 06 + respuesta viva):
 * la ETA llega como texto en `demora` // `proximo` (ej. "227min", "11min",
 * "" o "arribando") — NO existe `minutos_arribo`. `codigo_parada` tampoco
 * viaja por arribo (el codigo lo aporta la parada seleccionada).
 * `notificacion` por arribo = alertas de corte/parada anulada/redirección.
 */
@Serializable
data class ArrivalsResponseDto(
    @SerialName("proximos_arribos") val arrivals: List<ArrivalDto> = emptyList(),
    val parada: StopDto? = null,
    val notificacion: BackendNotificationDto? = null,
    val err: String? = null
)

@Serializable
data class ArrivalDto(
    @SerialName("linea") val lineId: String? = null,
    @SerialName("linea_nombre") val lineName: String = "",
    val sentido: String? = null,
    val ruta: String? = null,
    @SerialName("ruta_descripcion") val routeDescription: String? = null,
    val proximo: String? = null,
    val demora: String? = null,
    val distancia: Double? = null,
    val color: String? = null,
    val cliente: Int? = null,
    @SerialName("cliente_nombre") val operator: String? = null,
    val coche: String? = null,
    val notificacion: String? = null
)

/**
 * Aviso del backend, tolerante a las dos formas observadas:
 * - proximos_arribos/notificacion: {tipo, mensaje}
 * - seleccionatraza/notificaciones[]: {icono, titulo, texto, desvio}
 */
@Serializable
data class BackendNotificationDto(
    val tipo: String? = null,
    val mensaje: String? = null,
    val titulo: String? = null,
    val texto: String? = null
)
