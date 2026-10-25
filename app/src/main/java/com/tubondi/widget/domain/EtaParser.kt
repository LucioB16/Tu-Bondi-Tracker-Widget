package com.tubondi.widget.domain

/**
 * Parser de ETA con la regla verificada de Oma (widget.owl:16, jq parse_eta):
 * - null o vacío -> null (sin dato)
 * - texto contiene "arrib" o "llega" (case-insensitive) -> 0 (en arribo)
 * - primer run de dígitos -> ese número ("11min" -> 11, "227min" -> 227)
 * - si no hay dígitos -> null
 */
object EtaParser {
    private val digits = Regex("\\d+")

    fun parse(raw: String?): Int? {
        if (raw.isNullOrEmpty()) return null
        val lower = raw.lowercase()
        if ("arrib" in lower || "llega" in lower) return 0
        return digits.find(lower)?.value?.toIntOrNull()
    }

    /**
     * Campo de texto de la ETA según el contrato real: `demora // proximo`.
     */
    fun etaText(demora: String?, proximo: String?): String? =
        demora?.takeIf { it.isNotBlank() } ?: proximo?.takeIf { it.isNotBlank() }
}
