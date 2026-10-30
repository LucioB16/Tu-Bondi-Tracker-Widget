package com.tubondi.widget.domain

import com.tubondi.widget.domain.model.Arrival

/** Arrival estimates are hidden when the configured refresh interval has elapsed since success. */
object ArrivalFreshness {
    const val MAX_ETA_AGE_MINUTES = 15L
    const val MINUTE_MILLIS = 60_000L
    private const val HOUR_MINUTES = 60L
    private const val DAY_MINUTES = 24L * HOUR_MINUTES

    fun ageMillis(lastSuccessEpochMillis: Long, nowEpochMillis: Long): Long? =
        lastSuccessEpochMillis.takeIf { it > 0L }?.let { (nowEpochMillis - it).coerceAtLeast(0L) }

    fun ageMinutes(lastSuccessEpochMillis: Long, nowEpochMillis: Long): Long? =
        ageMillis(lastSuccessEpochMillis, nowEpochMillis)?.div(MINUTE_MILLIS)

    fun isStale(
        lastSuccessEpochMillis: Long,
        nowEpochMillis: Long,
        refreshIntervalMinutes: Int = MAX_ETA_AGE_MINUTES.toInt()
    ): Boolean {
        val freshnessWindow = refreshIntervalMinutes.coerceIn(
            MAX_ETA_AGE_MINUTES.toInt(),
            60
        ) * MINUTE_MILLIS
        return ageMillis(lastSuccessEpochMillis, nowEpochMillis)?.let { it >= freshnessWindow } ?: true
    }

    fun ageLabel(lastSuccessEpochMillis: Long, nowEpochMillis: Long): String? {
        val minutes = ageMinutes(lastSuccessEpochMillis, nowEpochMillis) ?: return null
        return when {
            minutes == 0L -> "hace menos de 1 min"
            minutes < HOUR_MINUTES -> "hace $minutes min"
            minutes < DAY_MINUTES -> {
                val hours = minutes / HOUR_MINUTES
                if (hours == 1L) "hace 1 h" else "hace $hours h"
            }
            else -> {
                val days = minutes / DAY_MINUTES
                if (days == 1L) "hace 1 día" else "hace $days días"
            }
        }
    }

    /**
     * ETA values are measured from the successful response time. Count them down
     * while fresh; once the prediction time has passed, don't imply it is live.
     */
    fun etaLabel(
        arrival: Arrival,
        lastSuccessEpochMillis: Long,
        nowEpochMillis: Long,
        refreshIntervalMinutes: Int = MAX_ETA_AGE_MINUTES.toInt()
    ): String {
        if (isStale(lastSuccessEpochMillis, nowEpochMillis, refreshIntervalMinutes)) return "Sin horario estimado"
        val elapsed = ageMinutes(lastSuccessEpochMillis, nowEpochMillis) ?: return "Sin horario estimado"
        val etaMinutes = arrival.etaMinutes ?: return arrival.etaText.ifBlank { "Sin horario" }
        val remaining = etaMinutes - elapsed
        return when {
            remaining > 0L -> if (remaining == 1L) "1 min" else "$remaining min"
            remaining == 0L -> "Llegando"
            else -> "Estimación vencida"
        }
    }
}
