package com.tubondi.widget.domain

import com.tubondi.widget.domain.model.Stop

/** Human-readable name for UI, notifications, accessibility labels and widgets. */
fun Stop.displayName(): String {
    val custom = customName?.let { safePlaceText(it, code) }
    if (custom != null) return custom

    val descriptive = safePlaceText(name, code)
    if (descriptive != null && !descriptive.equals("Parada", ignoreCase = true)) {
        return descriptive
    }

    val area = safePlaceText(hood, code)
    return area?.let { "Parada en $it" } ?: "Ubicación sin nombre"
}

private fun safePlaceText(value: String?, stopCode: String): String? {
    if (value.isNullOrBlank()) return null
    if (value.trim().equals(stopCode, ignoreCase = true)) return null
    // Remove the actual stop ID, not every code-shaped word: place labels such
    // as "UES21" and road names such as "E53" can be meaningful.
    val idToken = stopCode.takeIf { it.any(Char::isLetter) }?.let {
        Regex("(?<![\\p{L}\\p{N}])${Regex.escape(it)}(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
    }
    val cleaned = idToken?.replace(value, " ") ?: value
    val normalized = cleaned.replace(Regex("\\s+"), " ").trim(' ', '·', '-', '–', ':')
    return normalized.takeIf { it.isNotBlank() }
}

/** Reject this stop's technical ID from its optional user-facing name. */
fun isDescriptiveStopName(value: String, stopCode: String): Boolean {
    if (value.isBlank()) return false
    val trimmed = value.trim()
    if (trimmed.equals(stopCode, ignoreCase = true)) return false
    if (stopCode.any(Char::isLetter)) {
        val idToken = Regex("(?<![\\p{L}\\p{N}])${Regex.escape(stopCode)}(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
        if (idToken.containsMatchIn(trimmed)) return false
    }
    return true
}
