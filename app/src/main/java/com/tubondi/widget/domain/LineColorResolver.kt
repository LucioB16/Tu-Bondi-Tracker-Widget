package com.tubondi.widget.domain

import com.tubondi.widget.domain.model.Line

data class LineKey(val clientId: Int, val lineId: String)

fun Line.keyOrNull(): LineKey? = clientId?.let { LineKey(it, id) }

object LineColorResolver {
    private val apiColor = Regex("^#[0-9A-Fa-f]{6}$")

    fun normalize(color: String?): String? = color
        ?.takeIf(apiColor::matches)
        ?.uppercase()

    /** A duplicate API key with conflicting or invalid colors has no trusted swatch. */
    fun canonicalColors(lines: Iterable<Line>): Map<LineKey, String?> = lines
        .mapNotNull { line -> line.keyOrNull()?.let { it to line.color } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, values) ->
            val normalized = values.map(::normalize)
            normalized.filterNotNull().distinct().singleOrNull()
                ?.takeIf { normalized.all { value -> value != null } }
        }

    /** Arrival color is a consistency check, not an independent identity source. */
    fun forArrival(canonical: String?, arrivalColor: String?): String? {
        val trusted = normalize(canonical) ?: return null
        val observed = normalize(arrivalColor) ?: return trusted
        return trusted.takeIf { it == observed }
    }
}
