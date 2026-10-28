package com.tubondi.widget.widget

import com.tubondi.widget.domain.model.StopArrivals
import kotlinx.serialization.Serializable

/**
 * Estado serializado que Glance consume.
 * degraded=true significa que el ÚLTIMO refresh falló: se retienen los
 * arribos anteriores y lastUpdatedEpochMillis queda concretado a esa última
 * actualización exitosa (SA-8). refreshing=true es un estado transitorio
 * de feedback manual y no altera el timestamp ni marca degradación.
 */
@Serializable
data class WidgetUiState(
    val arrivals: List<StopArrivals> = emptyList(),
    val lastUpdatedEpochMillis: Long = 0L,
    val errorMessage: String? = null,
    val highContrast: Boolean = false,
    val degraded: Boolean = false,
    val refreshing: Boolean = false
) {
    fun statusText(): String? = when {
        refreshing -> "Actualizando…"
        degraded -> errorMessage ?: "Sin conexión con TuBondi"
        else -> null
    }

    companion object {
        /**
         * Estado tras un refresh fallido: retiene el último dato válido
         * (arrivals + lastUpdated) y marca degradado en lugar de borrar
         * los arribos (SA-8: retención offline).
         */
        fun errorState(
            previous: WidgetUiState,
            errorMessage: String?,
            highContrast: Boolean
        ): WidgetUiState = WidgetUiState(
            arrivals = previous.arrivals,
            lastUpdatedEpochMillis = previous.lastUpdatedEpochMillis,
            errorMessage = errorMessage,
            highContrast = highContrast,
            degraded = true,
            refreshing = false
        )
    }
}
