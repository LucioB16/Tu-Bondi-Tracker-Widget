package com.tubondi.widget.widget

import com.tubondi.widget.domain.model.StopArrivals
import kotlinx.serialization.Serializable

/**
 * Estado serializado que Glance consume.
 */
@Serializable
data class WidgetUiState(
    val arrivals: List<StopArrivals> = emptyList(),
    val lastUpdatedEpochMillis: Long = 0L,
    val errorMessage: String? = null,
    val highContrast: Boolean = false
)