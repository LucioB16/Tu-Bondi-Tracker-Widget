package com.tubondi.widget.domain.model

import kotlinx.serialization.Serializable

/**
 * Modelo de línea simplificado. id = line_id REAL de la API (string: "42", "C1").
 */
@Serializable
data class Line(
    val id: String,
    val name: String,
    val color: String? = null,
    val operator: String? = null
)

/**
 * Representa una parada disponible.
 */
@Serializable
data class Stop(
    val code: String,
    val name: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val lines: List<Line> = emptyList()
)

/**
 * Ruta seleccionada con identificadores requeridos para la API.
 */
@Serializable
data class Route(
    val id: Int,
    val clientId: Int,
    val name: String,
    val stops: List<Stop> = emptyList()
)

/**
 * Arribo normalizado para mostrar en el widget.
 * etaMinutes deriva de etaText vía EtaParser (null = sin dato);
 * etaText es el texto crudo del backend ("11min", "arribando", "227min").
 * notification = alerta del backend por arribo (cortes/anulaciones/redirecciones).
 */
@Serializable
data class Arrival(
    val stopCode: String = "",
    val lineId: String = "",
    val lineName: String = "",
    val etaMinutes: Int? = null,
    val etaText: String = "",
    val distanceKm: Double? = null,
    val direction: String? = null,
    val routeDescription: String? = null,
    val vehicleId: String? = null,
    val operator: String? = null,
    val color: String? = null,
    val notification: String? = null
)

/**
 * Configuración de selección por parada.
 * selectedLines usa los line_id REALES de la API (strings); vacío = todas.
 */
@Serializable
data class StopSelection(
    val stop: Stop,
    val selectedLines: List<String> = emptyList()
)

/**
 * Configuración completa por widget.
 */
@Serializable
data class WidgetConfiguration(
    val appWidgetId: Int,
    val selections: List<StopSelection>,
    val refreshIntervalMinutes: Int = 5,
    val notificationsEnabled: Boolean = true,
    val nearThresholdMinutes: Int = 5,
    val highContrast: Boolean = false,
    val conf: String = "cbaciudad"
)

/**
 * Resultado agrupado por parada.
 */
@Serializable
data class StopArrivals(
    val stop: Stop,
    val arrivals: List<Arrival>,
    val backendMessage: String? = null
)

/**
 * Avisos agregados para la pantalla/página de avisos (paridad Oma):
 * backendMessage por parada + notificacion por arribo, limpiados de
 * entidades HTML y sin duplicados.
 */
fun List<StopArrivals>.collectAvisos(): List<String> =
    flatMap { stop ->
        listOfNotNull(stop.backendMessage?.takeIf { it.isNotBlank() }) +
            stop.arrivals.mapNotNull { it.notification?.takeIf { n -> n.isNotBlank() } }
    }.map { com.tubondi.widget.domain.HtmlText.stripEntities(it) }
        .filter { it.isNotBlank() }
        .distinct()