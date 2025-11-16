package com.tubondi.widget.domain.model

import kotlinx.serialization.Serializable

/**
 * Modelo de línea simplificado.
 */
@Serializable
data class Line(
    val id: Int,
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
 */
@Serializable
data class Arrival(
    val stopCode: String,
    val lineName: String,
    val etaMinutes: Int,
    val distanceMeters: Int?,
    val direction: String? = null,
    val vehicleId: String? = null,
    val operator: String? = null,
    val color: String? = null
)

/**
 * Configuración de selección por parada.
 */
@Serializable
data class StopSelection(
    val stop: Stop,
    val selectedLines: List<Int>
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