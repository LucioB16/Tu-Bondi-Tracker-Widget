package com.tubondi.widget.domain

/**
 * Limpieza de texto del backend, paridad con Oma (widget.owl:16 usa
 * gsub "&[a-zA-Z0-9#]+;" para quitar entidades HTML de las notificaciones,
 * p.ej. "&#128204;Parada Anulada..." -> "Parada Anulada...").
 */
object HtmlText {
    private val ENTITY = Regex("&[a-zA-Z0-9#]+;")
    private val WHITESPACE = Regex("\\s+")

    fun stripEntities(text: String): String =
        WHITESPACE.replace(ENTITY.replace(text, " "), " ").trim()
}
