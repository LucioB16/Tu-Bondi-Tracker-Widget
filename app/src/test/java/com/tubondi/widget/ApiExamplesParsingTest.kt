package com.tubondi.widget

import com.google.common.truth.Truth.assertThat
import com.tubondi.widget.data.remote.dto.ArrivalsResponseDto
import com.tubondi.widget.data.remote.dto.LinesRoutesResponseDto
import com.tubondi.widget.data.remote.dto.RouteSelectionResponseDto
import com.tubondi.widget.domain.EtaParser
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Test

/**
 * Valida que los DTOs parsean los 6 fixtures REALES de la API
 * (ver SDD_API_CONTRACT.md). Ruta relativa al módulo app/ (workingDir
 * por defecto de las unit tests de Gradle).
 */
class ApiExamplesParsingTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun fixture(name: String): String =
        File("src/main/assets/api-examples/$name").readText()

    @Test
    fun parsesLinesAndRoutesExample() {
        val payload = json.decodeFromString<LinesRoutesResponseDto>(fixture("01-lines_routes.json"))
        assertThat(payload.lineas).hasSize(72)
        assertThat(payload.clientes.map { it.nombre }).contains("CONIFERAL")
        assertThat(payload.grupos).isNotEmpty()
    }

    @Test
    fun parsesArrivalsExampleWithRealEta() {
        val payload = json.decodeFromString<ArrivalsResponseDto>(fixture("06-arrivals.json"))
        assertThat(payload.arrivals).isNotEmpty()
        assertThat(payload.parada?.descripcion).isNotEmpty()
        // Regla de Oma sobre demora // proximo: el fixture trae 11 / 21 / 49.
        val etas = payload.arrivals.map { EtaParser.parse(EtaParser.etaText(it.demora, it.proximo)) }
        assertThat(etas).containsExactly(11, 21, 49).inOrder()
    }

    @Test
    fun parsesViewBoundsExample() {
        val obj = json.parseToJsonElement(fixture("02-view_bounds.json")).jsonObject
        assertThat(obj.keys).containsAtLeast("min_lat", "max_lat", "min_lon", "max_lon")
    }

    @Test
    fun parsesRouteSelectionExample() {
        val payload = json.decodeFromString<RouteSelectionResponseDto>(fixture("03-route_selection.json"))
        assertThat(payload.paradas).isNotEmpty()
        val first = payload.paradas.first()
        assertThat(first.codigo).isNotEmpty()
        assertThat(first.paradaNombre).isNotEmpty()
        assertThat(first.lineId).isEqualTo("42")
        assertThat(first.clientId).isEqualTo(411)
        assertThat(first.lat?.toString()?.trim('"')?.toDoubleOrNull()).isWithin(0.000001).of(-31.411505)
        assertThat(first.lon?.toString()?.trim('"')?.toDoubleOrNull()).isWithin(0.000001).of(-64.255723)
    }

    @Test
    fun parsesVehiclesByRouteExamples() {
        for (name in listOf("04-vehicles_by_route_no_stop.json", "05-vehicles_by_route_with_stop.json")) {
            val obj = json.parseToJsonElement(fixture(name)).jsonObject
            assertThat(obj["coches"]?.jsonArray).isNotEmpty()
        }
    }
}
