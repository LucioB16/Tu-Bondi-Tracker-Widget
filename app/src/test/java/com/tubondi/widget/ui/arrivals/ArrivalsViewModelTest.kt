package com.tubondi.widget.ui.arrivals

import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.tubondi.widget.data.repo.TransitRepository
import com.tubondi.widget.domain.model.Arrival
import com.tubondi.widget.domain.model.Stop
import com.tubondi.widget.domain.model.StopArrivals
import com.tubondi.widget.domain.model.StopSelection
import com.tubondi.widget.domain.model.WidgetConfiguration
import com.tubondi.widget.prefs.WidgetPreferences
import com.tubondi.widget.util.MainDispatcherRule
import com.tubondi.widget.widget.TransitAppWidget
import com.tubondi.widget.widget.WidgetStateStorage
import com.tubondi.widget.widget.WidgetUiState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.unmockkConstructor
import io.mockk.unmockkObject
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ArrivalsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<TransitRepository>()
    private val prefs = mockk<WidgetPreferences>()

    @After
    fun tearDown() {
        unmockkConstructor(GlanceAppWidgetManager::class)
        unmockkConstructor(TransitAppWidget::class)
        unmockkObject(WidgetStateStorage)
    }

    @Test
    fun offlineLaunchShowsCachedArrivalsAndKeepsTheirTimestamp() = runTest {
        val config = WidgetConfiguration(
            appWidgetId = 42,
            selections = listOf(StopSelection(Stop("AA50", "Parada de prueba"))),
            highContrast = true
        )
        val arrivals = listOf(
            StopArrivals(
                stop = config.selections.single().stop,
                arrivals = listOf(Arrival(stopCode = "AA50", lineName = "10", etaText = "4min"))
            )
        )
        val glanceId = mockk<GlanceId>()
        coEvery { prefs.readConfiguration(42) } returns config
        mockkConstructor(GlanceAppWidgetManager::class)
        mockkConstructor(TransitAppWidget::class)
        coEvery { anyConstructed<TransitAppWidget>().update(any(), glanceId) } returns Unit
        coEvery {
            anyConstructed<GlanceAppWidgetManager>().getGlanceIds(TransitAppWidget::class.java)
        } returns listOf(glanceId)
        every { anyConstructed<GlanceAppWidgetManager>().getAppWidgetId(glanceId) } returns 42
        mockkObject(WidgetStateStorage)
        coEvery { WidgetStateStorage.readState(any(), glanceId) } returns WidgetUiState(
            arrivals = arrivals,
            lastUpdatedEpochMillis = 123L,
            errorMessage = "Sin conexión previa",
            degraded = true
        )
        coEvery { WidgetStateStorage.writeState(any(), glanceId, any()) } returns Unit
        coEvery { repository.fetchArrivalsForConfig(any()) } throws IOException("offline")

        val viewModel = ArrivalsViewModel(
            repository,
            prefs,
            ApplicationProvider.getApplicationContext()
        )
        viewModel.load(42)
        advanceUntilIdle()

        assertThat(viewModel.state.value.stops).isEqualTo(arrivals)
        assertThat(viewModel.state.value.lastUpdated).isEqualTo(123L)
        assertThat(viewModel.state.value.error).isNotNull()
        assertThat(viewModel.state.value.highContrast).isTrue()
        coVerify(exactly = 1) { WidgetStateStorage.readState(any(), glanceId) }
        coVerify(exactly = 1) {
            WidgetStateStorage.writeState(
                any(),
                glanceId,
                WidgetUiState(
                    arrivals = arrivals,
                    lastUpdatedEpochMillis = 123L,
                    errorMessage = "Sin conexión con TuBondi",
                    highContrast = true,
                    degraded = true
                )
            )
        }
        coVerify(exactly = 1) { repository.fetchArrivalsForConfig(any()) }
    }

    @Test
    fun missingWidgetConfigurationIsPresentedAsAnEmptyState() = runTest {
        coEvery { prefs.readConfiguration(44) } returns null

        val viewModel = ArrivalsViewModel(
            repository,
            prefs,
            ApplicationProvider.getApplicationContext()
        )
        viewModel.load(44)
        advanceUntilIdle()

        assertThat(viewModel.state.value.loading).isFalse()
        assertThat(viewModel.state.value.stops).isEmpty()
        assertThat(viewModel.state.value.lastUpdated).isEqualTo(0L)
        assertThat(viewModel.state.value.error).isNull()
        assertThat(viewModel.state.value.highContrast).isFalse()
        coVerify(exactly = 0) { repository.fetchArrivalsForConfig(any()) }
    }

    @Test
    fun resumeSyncUsesSuccessfulWidgetRefreshAndClearsPreviousError() = runTest {
        val config = WidgetConfiguration(
            appWidgetId = 45,
            selections = listOf(StopSelection(Stop("AA50", "Parada de prueba")))
        )
        val previousArrivals = listOf(
            StopArrivals(
                stop = config.selections.single().stop,
                arrivals = listOf(Arrival(stopCode = "AA50", lineName = "10", etaText = "9min"))
            )
        )
        val refreshedArrivals = listOf(
            StopArrivals(
                stop = config.selections.single().stop,
                arrivals = listOf(Arrival(stopCode = "AA50", lineName = "10", etaText = "4min"))
            )
        )
        val glanceId = mockk<GlanceId>()
        coEvery { prefs.readConfiguration(45) } returns config
        mockkConstructor(GlanceAppWidgetManager::class)
        mockkConstructor(TransitAppWidget::class)
        coEvery { anyConstructed<TransitAppWidget>().update(any(), glanceId) } returns Unit
        coEvery {
            anyConstructed<GlanceAppWidgetManager>().getGlanceIds(TransitAppWidget::class.java)
        } returns listOf(glanceId)
        every { anyConstructed<GlanceAppWidgetManager>().getAppWidgetId(glanceId) } returns 45
        mockkObject(WidgetStateStorage)
        coEvery { WidgetStateStorage.readState(any(), glanceId) } returns WidgetUiState(
            arrivals = previousArrivals,
            lastUpdatedEpochMillis = 123L,
            errorMessage = "Sin servicio ahora",
            degraded = true
        )
        coEvery { WidgetStateStorage.writeState(any(), glanceId, any()) } returns Unit
        coEvery { repository.fetchArrivalsForConfig(any()) } throws IOException("offline")

        val viewModel = ArrivalsViewModel(
            repository,
            prefs,
            ApplicationProvider.getApplicationContext()
        )
        viewModel.load(45)
        advanceUntilIdle()
        assertThat(viewModel.state.value.error).isNotNull()

        coEvery { WidgetStateStorage.readState(any(), glanceId) } returns WidgetUiState(
            arrivals = refreshedArrivals,
            lastUpdatedEpochMillis = 456L
        )
        viewModel.syncFromWidgetCache(45)
        advanceUntilIdle()

        assertThat(viewModel.state.value.stops).isEqualTo(refreshedArrivals)
        assertThat(viewModel.state.value.lastUpdated).isEqualTo(456L)
        assertThat(viewModel.state.value.error).isNull()
        coVerify(exactly = 2) { WidgetStateStorage.readState(any(), glanceId) }
    }

    @Test
    fun successfulFullScreenRefreshUpdatesTheWidgetCache() = runTest {
        val config = WidgetConfiguration(
            appWidgetId = 43,
            selections = listOf(StopSelection(Stop("BB17", "Parada de prueba"))),
            highContrast = true
        )
        val arrivals = listOf(
            StopArrivals(
                stop = config.selections.single().stop,
                arrivals = listOf(Arrival(stopCode = "BB17", lineName = "20", etaText = "5min"))
            )
        )
        val glanceId = mockk<GlanceId>()
        coEvery { prefs.readConfiguration(43) } returns config
        mockkConstructor(GlanceAppWidgetManager::class)
        mockkConstructor(TransitAppWidget::class)
        coEvery { anyConstructed<TransitAppWidget>().update(any(), glanceId) } returns Unit
        coEvery {
            anyConstructed<GlanceAppWidgetManager>().getGlanceIds(TransitAppWidget::class.java)
        } returns listOf(glanceId)
        every { anyConstructed<GlanceAppWidgetManager>().getAppWidgetId(glanceId) } returns 43
        mockkObject(WidgetStateStorage)
        coEvery { WidgetStateStorage.readState(any(), glanceId) } returns WidgetUiState()
        coEvery { WidgetStateStorage.writeState(any(), glanceId, any()) } returns Unit
        coEvery { repository.fetchArrivalsForConfig(config) } returns arrivals

        val viewModel = ArrivalsViewModel(
            repository,
            prefs,
            ApplicationProvider.getApplicationContext()
        )
        viewModel.load(43)
        advanceUntilIdle()

        val updatedAt = viewModel.state.value.lastUpdated
        assertThat(viewModel.state.value.stops).isEqualTo(arrivals)
        assertThat(updatedAt).isGreaterThan(0L)
        coVerify(exactly = 1) {
            WidgetStateStorage.writeState(
                any(),
                glanceId,
                WidgetUiState(
                    arrivals = arrivals,
                    lastUpdatedEpochMillis = updatedAt,
                    highContrast = true
                )
            )
        }
        coVerify(exactly = 1) { anyConstructed<TransitAppWidget>().update(any(), glanceId) }
    }
}
