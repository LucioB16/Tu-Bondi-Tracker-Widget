package com.tubondi.widget.ui.config

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.tubondi.widget.data.catalog.CatalogRepository
import com.tubondi.widget.data.catalog.CatalogStop
import com.tubondi.widget.data.remote.dto.LineDto
import com.tubondi.widget.data.remote.dto.LinesRoutesResponseDto
import com.tubondi.widget.data.remote.dto.RouteSummaryDto
import com.tubondi.widget.data.repo.TransitRepository
import com.tubondi.widget.domain.displayName
import com.tubondi.widget.domain.model.Line
import com.tubondi.widget.domain.model.Stop
import com.tubondi.widget.domain.model.WidgetConfiguration
import com.tubondi.widget.prefs.WidgetPreferences
import com.tubondi.widget.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
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
        every { catalogRepository.hubStops() } returns listOf(
            CatalogStop(code = "A911", name = "Duarte Quirós 1717-1728"),
            CatalogStop(code = "CE56", name = "Colón 545", hood = "CENTRO")
        )
        every { catalogRepository.resolveStop(any()) } answers { firstArg() }
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
        assertThat(viewModel.state.value.selectedStops).isEmpty()
        viewModel.addRouteStop("100")
        viewModel.toggleStop("100", true)
        viewModel.toggleLine("100", "2", false)

        val stopUi = viewModel.state.value.selectedStops.first { it.stop.code == "100" }
        assertThat(stopUi.isSelected).isTrue()
        assertThat(stopUi.selectedLines).containsExactly("1")

        viewModel.toggleStop("100", false)
        viewModel.toggleStop("100", true)
        assertThat(viewModel.state.value.selectedStops.first { it.stop.code == "100" }.selectedLines)
            .containsExactly("1")
    }

    @Test
    fun exposesRouteCandidatesAndExplicitlyAddingOneReactivatesStops() = runTest {
        val routes = (1..25).map { RouteSummaryDto(it.toString(), "Ruta $it", null, null) }
        coEvery { repository.fetchLinesPayload(any()) } returns LinesRoutesResponseDto(
            lineas = listOf(LineDto("10", "10", "#fff", null, 100, routes))
        )
        viewModel.load(5)
        advanceUntilIdle()
        assertThat(viewModel.state.value.availableRoutes).hasSize(25)

        val lastRoute = viewModel.state.value.availableRoutes.last()
        coEvery { repository.fetchStopsForRoute(lastRoute.routeId, lastRoute.clientId, any()) } returns listOf(
            Stop(code = "100", name = "San Juan", lines = listOf(Line("1", "1"), Line("2", "2")))
        )
        viewModel.selectRoute(lastRoute)
        advanceUntilIdle()
        assertThat(viewModel.state.value.routeStops.map { it.code }).containsExactly("100")
        assertThat(viewModel.state.value.selectedStops).isEmpty()
        viewModel.addRouteStop("100")
        assertThat(viewModel.state.value.selectedStops.single().isSelected).isTrue()
        viewModel.toggleStop("100", true)
        viewModel.toggleLine("100", "2", false)
        viewModel.toggleStop("100", false)
        viewModel.selectRoute(lastRoute)
        advanceUntilIdle()

        val stillDraft = viewModel.state.value.selectedStops.single { it.stop.code == "100" }
        assertThat(stillDraft.isSelected).isFalse()
        viewModel.addRouteStop("100")
        val reactivated = viewModel.state.value.selectedStops.single { it.stop.code == "100" }
        assertThat(reactivated.isSelected).isTrue()
        assertThat(reactivated.selectedLines).containsExactly("1")

        viewModel.toggleLine("100", "1", false)
        val allLines = viewModel.state.value.selectedStops.single { it.stop.code == "100" }
        assertThat(allLines.selectedLines).isEmpty()
        assertThat(allLines.isLineSelected("1")).isTrue()
        assertThat(allLines.isLineSelected("2")).isTrue()
    }

    @Test
    fun routeSelectionMergesLineMetadataIntoExistingSearchStop() = runTest {
        viewModel.load(5)
        advanceUntilIdle()
        val route = viewModel.state.value.availableRoutes.first()
        val catalogStop = CatalogStop(code = "AA50", name = "Alto Alberdi", hood = "ALTO ALBERDI")
        every { catalogRepository.toStop(catalogStop) } returns Stop(code = "AA50", name = "Alto Alberdi", hood = "Alto Alberdi")
        viewModel.addCatalogStop(catalogStop)
        coEvery { repository.fetchStopsForRoute(route.routeId, route.clientId, any()) } returns listOf(
            Stop(code = "AA50", name = "Alto Alberdi", lines = listOf(Line("1", "1"), Line("2", "2")))
        )

        viewModel.selectRoute(route)
        advanceUntilIdle()

        val stopUi = viewModel.state.value.selectedStops.single()
        assertThat(stopUi.isSelected).isTrue()
        assertThat(stopUi.stop.name).isEqualTo("Alto Alberdi")
        assertThat(stopUi.stop.hood).isEqualTo("Alto Alberdi")
        assertThat(stopUi.stop.lines.map { it.id }).containsExactly("1", "2")
        assertThat(stopUi.isLineSelected("1")).isTrue()
        assertThat(stopUi.isLineSelected("2")).isTrue()
    }

    @Test
    fun searchFindsAndAddsCatalogStop() = runTest {
        viewModel.load(5)
        advanceUntilIdle()
        every { catalogRepository.search("alberdi") } returns listOf(
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
        // Un duplicado activo no crea una segunda selección.
        viewModel.addCatalogStop(CatalogStop(code = "AA50", name = "Parada X", hood = "ALTO ALBERDI"))
        assertThat(viewModel.state.value.selectedStops.count { it.stop.code == "AA50" }).isEqualTo(1)
        viewModel.removeStop("AA50")
        viewModel.addCatalogStop(CatalogStop(code = "AA50", name = "Parada X", hood = "ALTO ALBERDI"))
        assertThat(viewModel.state.value.selectedStops.single { it.stop.code == "AA50" }.isSelected).isTrue()
    }

    @Test
    fun searchDoesNotAcceptTechnicalStopIdsAsQueries() = runTest {
        viewModel.load(5)
        advanceUntilIdle()

        viewModel.search("AA50")

        assertThat(viewModel.state.value.searchQuery).isEmpty()
        assertThat(viewModel.state.value.searchResults).isEmpty()
        assertThat(viewModel.state.value.error).isEqualTo("Buscá por calle, barrio o nombre.")
        coVerify(exactly = 1) { repository.fetchLinesPayload(any()) }
        coVerify(exactly = 0) { repository.fetchStopsForRoute(any(), any(), any()) }
    }

    @Test
    fun draftTracksAddsAndRemovesUntilSave() = runTest {
        viewModel.load(5)
        advanceUntilIdle()
        assertThat(viewModel.state.value.hasUnsavedChanges).isFalse()
        every { catalogRepository.toStop(any()) } answers {
            val stop = firstArg<CatalogStop>()
            Stop(code = stop.code, name = stop.name, hood = stop.hood)
        }

        viewModel.addCatalogStop(CatalogStop(code = "AA50", name = "Alto Alberdi"))
        assertThat(viewModel.state.value.hasUnsavedChanges).isTrue()
        viewModel.removeStop("AA50")
        assertThat(viewModel.state.value.hasUnsavedChanges).isFalse()
    }

    @Test
    fun unnamedLocationCanReceiveACustomNameButNotItsTechnicalId() = runTest {
        viewModel.load(5)
        advanceUntilIdle()
        every { catalogRepository.toStop(any()) } answers {
            val stop = firstArg<CatalogStop>()
            Stop(code = stop.code, name = stop.name, hood = stop.hood)
        }
        val unnamed = CatalogStop(code = "ZZ99", name = "")
        viewModel.addCatalogStop(unnamed)
        assertThat(viewModel.state.value.selectedStops.single().stop.displayName())
            .isEqualTo("Ubicación sin nombre")

        viewModel.updateCustomName("ZZ99", "Esquina del mercado")
        assertThat(viewModel.state.value.selectedStops.single().stop.displayName())
            .isEqualTo("Esquina del mercado")
        viewModel.updateCustomName("ZZ99", "ZZ99")

        assertThat(viewModel.state.value.selectedStops.single().stop.displayName())
            .isEqualTo("Esquina del mercado")
        assertThat(viewModel.state.value.error)
            .isEqualTo("Usá un nombre de lugar, no un identificador técnico.")
    }

    @Test
    fun legacyRefreshIsClampedAndUpdatesStayInRange() = runTest {
        coEvery { prefs.readConfiguration(any()) } returns WidgetConfiguration(
            appWidgetId = 5,
            selections = emptyList(),
            refreshIntervalMinutes = 5
        )
        viewModel.load(5)
        advanceUntilIdle()
        assertThat(viewModel.state.value.refreshMinutes).isEqualTo(15)

        viewModel.updateRefresh(1)
        assertThat(viewModel.state.value.refreshMinutes).isEqualTo(15)
        viewModel.updateRefresh(100)
        assertThat(viewModel.state.value.refreshMinutes).isEqualTo(60)
    }

    @Test
    fun defaultsMatchProductDecisions() = runTest {
        viewModel.load(5)
        advanceUntilIdle()
        // D2: frecuencia por defecto 15 min.
        assertThat(viewModel.state.value.refreshMinutes).isEqualTo(15)
        // Hubs del catálogo disponibles para acceso rápido.
        assertThat(viewModel.state.value.hubs.map { it.code }).containsExactly("A911", "CE56")
    }
}
