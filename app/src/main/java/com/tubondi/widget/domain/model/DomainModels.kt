package com.tubondi.widget.domain.model

import com.tubondi.widget.domain.displayName
import kotlinx.serialization.Serializable

const val MIN_REFRESH_INTERVAL_MINUTES = 15
const val MAX_REFRESH_INTERVAL_MINUTES = 60

/**
 * Modelo de línea simplificado. id = line_id REAL de la API (string: "42", "C1").
 */
@Serializable
data class Line(
    val id: String,
    val name: String,
    val color: String? = null,
    val operator: String? = null,
    val clientId: Int? = null
) {
    /** API identity; the visible line name is never an identifier. */
    val selectionKey: String get() = clientId?.let { "$it:$id" } ?: id
}

/**
 * Representa una parada disponible.
 */
@Serializable
data class Stop(
    val code: String,
    val name: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val lines: List<Line> = emptyList(),
    val hood: String? = null,
    val customName: String? = null
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
    val clientId: Int? = null,
    val notification: String? = null
)

/**
 * Configuración de selección por parada.
 * selectedLines usa claves compuestas cliente:line_id; vacío = todas.
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
    val refreshIntervalMinutes: Int = MIN_REFRESH_INTERVAL_MINUTES,
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
fun List<StopArrivals>.collectAvisos(): List<String> {
    val labelsByCode = associate { it.stop.code to it.stop.displayName() }
    return flatMap { stopArrivals ->
        listOfNotNull(stopArrivals.backendMessage?.takeIf { it.isNotBlank() }) +
            stopArrivals.arrivals.mapNotNull { it.notification?.takeIf(String::isNotBlank) }
    }.map { com.tubondi.widget.domain.HtmlText.stripEntities(it) }
        .map { message ->
            labelsByCode.entries.fold(message) { text, (code, displayName) ->
                if (code.isBlank()) text else Regex(
                    "(?<![A-Za-z0-9])${Regex.escape(code)}(?![A-Za-z0-9])",
                    RegexOption.IGNORE_CASE
                ).replace(text, displayName)
            }
        }
        .filter { it.isNotBlank() }
        .distinct()
}
