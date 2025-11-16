package com.tubondi.widget.ui.config

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.tubondi.widget.data.remote.dto.LineDto
import com.tubondi.widget.data.remote.dto.LinesRoutesResponseDto
import com.tubondi.widget.data.remote.dto.RouteSummaryDto
import com.tubondi.widget.data.repo.TransitRepository
import com.tubondi.widget.domain.model.Line
import com.tubondi.widget.domain.model.Stop
import com.tubondi.widget.prefs.WidgetPreferences
import com.tubondi.widget.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ConfigViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<TransitRepository>()
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
        viewModel = ConfigViewModel(repository, prefs, ApplicationProvider.getApplicationContext())
    }

    @Test
    fun togglesStopSelectionAndLines() = runTest {
        viewModel.load(5)
        advanceUntilIdle()
        val route = viewModel.state.value.availableRoutes.first()
        coEvery { repository.fetchStopsForRoute(route.routeId, route.clientId, any()) } returns listOf(
            Stop(code = "100", name = "San Juan", lines = listOf(Line(1, "1"), Line(2, "2")))
        )

        viewModel.selectRoute(route)
        advanceUntilIdle()
        viewModel.toggleStop("100", true)
        viewModel.toggleLine("100", 2, false)

        val stopUi = viewModel.state.value.selectedStops.first { it.stop.code == "100" }
        assertThat(stopUi.isSelected).isTrue()
        assertThat(stopUi.selectedLines).containsExactly(1)
    }
}