package com.tubondi.widget.domain

/** Extracts only an explicit terminal from a route description; never guesses one. */
object RouteDestination {
    private val separators = Regex("\\s+(?:A|->|→|–|—|-)\\s+", RegexOption.IGNORE_CASE)

    fun fromRouteDescription(description: String?): String? {
        val normalized = description?.trim()?.replace(Regex("\\s+"), " ") ?: return null
        val parts = separators.split(normalized).map(String::trim).filter(String::isNotEmpty)
        return parts.lastOrNull()?.takeIf { parts.size >= 2 }
    }
}
