package com.tubondi.widget

import com.google.common.truth.Truth.assertThat
import com.tubondi.widget.data.remote.dto.ArrivalsResponseDto
import com.tubondi.widget.data.remote.dto.LinesRoutesResponseDto
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Test

class ApiExamplesParsingTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parsesLinesAndRoutesExample() {
        val file = File("app/src/main/assets/api-examples/01-lines_routes.json")
        val payload = json.decodeFromString<LinesRoutesResponseDto>(file.readText())
        assertThat(payload.lineas).isNotEmpty()
        assertThat(payload.clientes.map { it.nombre }).contains("CONIFERAL")
    }

    @Test
    fun parsesArrivalsExample() {
        val file = File("app/src/main/assets/api-examples/06-arrivals.json")
        val payload = json.decodeFromString<ArrivalsResponseDto>(file.readText())
        assertThat(payload.arrivals).isNotEmpty()
        assertThat(payload.parada?.descripcion).isNotEmpty()
    }
}