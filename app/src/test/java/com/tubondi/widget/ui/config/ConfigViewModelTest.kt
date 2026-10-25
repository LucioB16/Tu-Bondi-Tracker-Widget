package com.tubondi.widget.ui.config

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.tubondi.widget.data.catalog.CatalogRepository
import com.tubondi.widget.data.catalog.CatalogStop
import com.tubondi.widget.data.remote.dto.LineDto
import com.tubondi.widget.data.remote.dto.LinesRoutesResponseDto
import com.tubondi.widget.data.remote.dto.RouteSummaryDto
import com.tubondi.widget.data.repo.TransitRepository
import com.tubondi.widget.domain.model.Line
import com.tubondi.widget.domain.model.Stop
import com.tubondi.widget.prefs.WidgetPreferences
import com.tubondi.widget.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConfigViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<TransitRepository>()
    private val catalogRepository = mockk<CatalogRepository>()
    private val prefs = mockk<WidgetPreferences>()
    private lateinit var viewModel: ConfigViewModel

    @Before
    fun setup() {
        coEvery { repository.fetchLinesPayload(any()) } returns LinesRoutesResponseDto(
            lineas = listOf(
                LineDto(id = "10", name = "10", color = "#fff", grupo = null, cliente = 100, rutas = listOf(RouteSummaryDto("1", "Centro", null, null)))
            )
        )
        coEvery { prefs.readConfiguration(any()) } returns null
        coEvery { catalogRepository.hubs() } returns listOf("A911", "CE56")
        viewModel = ConfigViewModel(repository, catalogRepository, prefs, ApplicationProvider.getApplicationContext())
    }

    @Test
    fun togglesStopSelectionAndLines() = runTest {
        viewModel.load(5)
        advanceUntilIdle()
        val route = viewModel.state.value.availableRoutes.first()
        coEvery { repository.fetchStopsForRoute(route.routeId, route.clientId, any()) } returns listOf(
            Stop(code = "100", name = "San Juan", lines = listOf(Line("1", "1"), Line("2", "2")))
        )

        viewModel.selectRoute(route)
        advanceUntilIdle()
        viewModel.toggleStop("100", true)
        viewModel.toggleLine("100", "2", false)

        val stopUi = viewModel.state.value.selectedStops.first { it.stop.code == "100" }
        assertThat(stopUi.isSelected).isTrue()
        assertThat(stopUi.selectedLines).containsExactly("1")
    }

    @Test
    fun searchFindsAndAddsCatalogStop() = runTest {
        viewModel.load(5)
        advanceUntilIdle()
        coEvery { catalogRepository.search("alberdi") } returns listOf(
            CatalogStop(code = "AA50", name = "Parada X", hood = "ALTO ALBERDI")
        )
        every { catalogRepository.toStop(any()) } returns Stop(code = "AA50", name = "Parada X")

        viewModel.search("alberdi")
        advanceUntilIdle()
        assertThat(viewModel.state.value.searchResults).hasSize(1)

        viewModel.addCatalogStop(viewModel.state.value.searchResults.first())

        val stopUi = viewModel.state.value.selectedStops.first { it.stop.code == "AA50" }
        assertThat(stopUi.isSelected).isTrue()
        assertThat(stopUi.selectedLines).isEmpty() // sin líneas = todas
        // Duplicados se ignoran.
        viewModel.addCatalogStop(CatalogStop(code = "AA50", name = "Parada X", hood = "ALTO ALBERDI"))
        assertThat(viewModel.state.value.selectedStops.count { it.stop.code == "AA50" }).isEqualTo(1)
    }

    @Test
    fun defaultsMatchProductDecisions() = runTest {
        viewModel.load(5)
        advanceUntilIdle()
        // D2: frecuencia por defecto 15 min.
        assertThat(viewModel.state.value.refreshMinutes).isEqualTo(15)
        // Hubs del catálogo disponibles para acceso rápido.
        assertThat(viewModel.state.value.hubs).containsExactly("A911", "CE56")
    }
}
