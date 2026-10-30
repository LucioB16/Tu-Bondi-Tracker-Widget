package com.tubondi.widget.widget

import com.tubondi.widget.domain.model.StopArrivals
import com.tubondi.widget.domain.model.Arrival
import kotlinx.serialization.Serializable

/**
 * Estado serializado que Glance consume.
 * degraded=true significa que el ÚLTIMO refresh falló: se retienen los
 * arribos anteriores y lastUpdatedEpochMillis queda concretado a esa última
 * actualización exitosa (SA-8). Glance muestra el resultado después del worker;
 * el progreso transitorio se reserva para Arribos para evitar renders fuera de orden.
 */
@Serializable
data class WidgetUiState(
    val arrivals: List<StopArrivals> = emptyList(),
    val lastUpdatedEpochMillis: Long = 0L,
    val errorMessage: String? = null,
    val highContrast: Boolean = false,
    val degraded: Boolean = false
) {
    /**
     * Keeps one arrival from each highest-priority stop before filling spare
     * rows by ETA. Selection and tie-breaking are stable for a given response.
     */
    fun prioritizedArrivals(maxRows: Int, maxStops: Int = Int.MAX_VALUE): List<StopArrivals> {
        if (maxRows <= 0 || maxStops <= 0) return emptyList()
        val firstByStop = arrivals.mapIndexedNotNull { stopIndex, group ->
            group.arrivals.withIndex().minWithOrNull(
                compareBy<IndexedValue<Arrival>> { it.value.etaMinutes ?: Int.MAX_VALUE }
                    .thenBy { it.index }
            )?.let { indexed ->
                PriorityCandidate(stopIndex, indexed.index, indexed.value)
            }
        }.sortedWith(candidateComparator)

        val visibleStopIndexes = firstByStop.take(maxStops).mapTo(linkedSetOf()) { it.stopIndex }
        val selected = linkedMapOf<Int, MutableList<PriorityCandidate>>()
        firstByStop.filter { it.stopIndex in visibleStopIndexes }.take(maxRows).forEach { candidate ->
            selected.getOrPut(candidate.stopIndex) { mutableListOf() }.add(candidate)
        }
        val selectedKeys = selected.values.flatten().mapTo(mutableSetOf()) {
            it.stopIndex to it.arrivalIndex
        }
        val remaining = arrivals.flatMapIndexed { stopIndex, group ->
            if (stopIndex !in visibleStopIndexes) return@flatMapIndexed emptyList()
            group.arrivals.mapIndexedNotNull { arrivalIndex, arrival ->
                if ((stopIndex to arrivalIndex) in selectedKeys) null
                else PriorityCandidate(stopIndex, arrivalIndex, arrival)
            }
        }.sortedWith(candidateComparator)
        remaining.take(maxRows - selectedKeys.size).forEach { candidate ->
            selected.getOrPut(candidate.stopIndex) { mutableListOf() }.add(candidate)
        }

        return selected.map { (stopIndex, candidates) ->
            val group = arrivals[stopIndex]
            group.copy(arrivals = candidates.sortedWith(candidateComparator).map { it.arrival })
        }
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
            degraded = true
        )
    }
}

private data class PriorityCandidate(
    val stopIndex: Int,
    val arrivalIndex: Int,
    val arrival: Arrival
)

private val candidateComparator = compareBy<PriorityCandidate> { it.arrival.etaMinutes ?: Int.MAX_VALUE }
    .thenBy { it.stopIndex }
    .thenBy { it.arrivalIndex }
