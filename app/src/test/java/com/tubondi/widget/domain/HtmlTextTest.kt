package com.tubondi.widget.domain

import com.google.common.truth.Truth.assertThat
import com.tubondi.widget.domain.model.Arrival
import com.tubondi.widget.domain.model.Stop
import com.tubondi.widget.domain.model.StopArrivals
import com.tubondi.widget.domain.model.collectAvisos
import org.junit.Test

/**
 * Paridad con la limpieza de entidades de Oma (widget.owl:16) y la
 * agregación de avisos de la pantalla completa (widget.owl:155-159).
 */
class HtmlTextTest {
    @Test
    fun stripsHtmlEntitiesLikeOma() {
        // Caso real capturado en la verificación viva del 2026-10-25.
        assertThat(
            HtmlText.stripEntities("&#128204;Parada Anulada: Av. Colón 545 &#128205;parada provisoria")
        ).isEqualTo("Parada Anulada: Av. Colón 545 parada provisoria")
    }

    @Test
    fun noticeIsNullWhenOnlyEntitiesRemain() {
        assertThat(HtmlText.notice("&#128204;")).isNull()
        assertThat(HtmlText.notice("")).isNull()
        assertThat(HtmlText.notice(null)).isNull()
        assertThat(HtmlText.notice("&#128204;Parada Anulada")).isEqualTo("Parada Anulada")
    }

    @Test
    fun collectAvisosDeduplicatesAndCleans() {
        val stop = Stop("0001", "A")
        val stops = listOf(
            StopArrivals(
                stop,
                listOf(
                    Arrival(stopCode = "0001", lineName = "40", notification = "&#128204;Parada Anulada"),
                    Arrival(stopCode = "0001", lineName = "42", notification = "&#128204;Parada Anulada")
                ),
                backendMessage = "&#128205;Desvío por obras"
            )
        )

        val avisos = stops.collectAvisos()

        assertThat(avisos).containsExactly("Desvío por obras", "Parada Anulada").inOrder()
    }

    @Test
    fun avisosReplaceKnownStopCodesWithDescriptiveNames() {
        val stop = Stop("A911", "Duarte Quirós 1717–1728")
        val avisos = listOf(
            StopArrivals(
                stop = stop,
                arrivals = listOf(Arrival(notification = "Desvío en A911 para la línea 42")),
                backendMessage = "La parada A911 está cerrada"
            )
        ).collectAvisos()

        assertThat(avisos).containsExactly(
            "La parada Duarte Quirós 1717–1728 está cerrada",
            "Desvío en Duarte Quirós 1717–1728 para la línea 42"
        ).inOrder()
        assertThat(avisos.joinToString()).doesNotContain("A911")
        assertThat(avisos.joinToString()).contains("42")
    }
}
