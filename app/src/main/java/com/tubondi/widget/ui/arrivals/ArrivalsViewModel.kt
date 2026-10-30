package com.tubondi.widget.ui.arrivals

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.tubondi.widget.data.repo.TransitRepository
import com.tubondi.widget.domain.friendlyErrorMessage
import com.tubondi.widget.domain.model.StopArrivals
import com.tubondi.widget.domain.model.WidgetConfiguration
import com.tubondi.widget.prefs.WidgetPreferences
import com.tubondi.widget.widget.TransitAppWidget
import com.tubondi.widget.widget.WidgetStateStorage
import com.tubondi.widget.widget.WidgetUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Estado de la pantalla completa de arribos (D1, estilo Oma).
 */
data class ArrivalsUiState(
    val appWidgetId: Int = -1,
    val loading: Boolean = false,
    val stops: List<StopArrivals> = emptyList(),
    val lastUpdated: Long = 0L,
    val error: String? = null,
    val highContrast: Boolean = false,
    val refreshIntervalMinutes: Int = 15
)

@HiltViewModel
class ArrivalsViewModel @Inject constructor(
    private val repository: TransitRepository,
    private val prefs: WidgetPreferences,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(ArrivalsUiState())
    val state: StateFlow<ArrivalsUiState> = _state

    fun load(appWidgetId: Int) {
        if (_state.value.appWidgetId == appWidgetId && _state.value.stops.isNotEmpty()) return
        _state.update { it.copy(loading = true, appWidgetId = appWidgetId, error = null) }
        viewModelScope.launch {
            val config = prefs.readConfiguration(appWidgetId)
            if (config == null) {
                _state.update {
                    it.copy(
                        loading = false,
                        stops = emptyList(),
                        lastUpdated = 0L,
                        error = null,
                        highContrast = false
                    )
                }
                return@launch
            }
            _state.update {
                it.copy(
                    highContrast = config.highContrast,
                    refreshIntervalMinutes = config.refreshIntervalMinutes
                )
            }
            restoreCachedWidgetState(appWidgetId, config.highContrast)
            refreshInternal(config)
        }
    }

    /**
     * A launcher refresh can update Glance while this Activity stays in the
     * task back stack. Re-read that durable state on resume so Arribos does
     * not keep presenting an error or timestamp from before the launcher work.
     */
    fun syncFromWidgetCache(appWidgetId: Int) {
        if (_state.value.appWidgetId != appWidgetId || _state.value.loading) return
        viewModelScope.launch {
            if (_state.value.appWidgetId != appWidgetId || _state.value.loading) return@launch
            try {
                val glanceId = findGlanceId(appWidgetId) ?: return@launch
                val cached = WidgetStateStorage.readState(appContext, glanceId)
                _state.update { current ->
                    if (
                        current.appWidgetId != appWidgetId ||
                        current.loading ||
                        cached.lastUpdatedEpochMillis < current.lastUpdated
                    ) {
                        current
                    } else {
                        current.copy(
                            stops = cached.arrivals,
                            lastUpdated = cached.lastUpdatedEpochMillis,
                            error = cached.errorMessage.takeIf { cached.degraded },
                            highContrast = cached.highContrast,
                            refreshIntervalMinutes = cached.refreshIntervalMinutes
                        )
                    }
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (_: Exception) {
                // A transient cache read failure must not hide the current screen.
            }
        }
    }

    fun refresh() {
        val id = _state.value.appWidgetId
        if (id == -1) return
        viewModelScope.launch {
            val config = prefs.readConfiguration(id) ?: return@launch
            refreshInternal(config)
        }
    }

    private suspend fun restoreCachedWidgetState(appWidgetId: Int, highContrast: Boolean) {
        try {
            val glanceId = findGlanceId(appWidgetId) ?: return
            val cached = WidgetStateStorage.readState(appContext, glanceId)
            _state.update {
                it.copy(
                    stops = cached.arrivals,
                    lastUpdated = cached.lastUpdatedEpochMillis,
                    error = cached.errorMessage.takeIf { cached.degraded },
                    highContrast = highContrast,
                    refreshIntervalMinutes = cached.refreshIntervalMinutes
                )
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (_: Exception) {
            // A missing/corrupt Glance cache must not prevent the refresh attempt.
        }
    }

    private suspend fun findGlanceId(appWidgetId: Int) =
        GlanceAppWidgetManager(appContext).let { manager ->
            manager.getGlanceIds(TransitAppWidget::class.java)
                .firstOrNull { manager.getAppWidgetId(it) == appWidgetId }
        }

    private suspend fun persistWidgetState(appWidgetId: Int, widgetState: WidgetUiState) {
        try {
            val glanceId = findGlanceId(appWidgetId) ?: return
            WidgetStateStorage.writeState(appContext, glanceId, widgetState)
            TransitAppWidget().update(appContext, glanceId)
        } catch (ex: CancellationException) {
            throw ex
        } catch (_: Exception) {
            // The full-screen refresh remains useful if the widget cache is unavailable.
        }
    }

    private suspend fun refreshInternal(config: WidgetConfiguration) {
        _state.update {
            it.copy(
                loading = true,
                error = null,
                highContrast = config.highContrast,
                refreshIntervalMinutes = config.refreshIntervalMinutes
            )
        }
        try {
            val arrivals = repository.fetchArrivalsForConfig(config)
            val updatedAt = System.currentTimeMillis()
            _state.update { it.copy(loading = false, stops = arrivals, lastUpdated = updatedAt) }
            persistWidgetState(
                config.appWidgetId,
                WidgetUiState(
                    arrivals = arrivals,
                    lastUpdatedEpochMillis = updatedAt,
                    highContrast = config.highContrast,
                    refreshIntervalMinutes = config.refreshIntervalMinutes
                )
            )
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            // D5: mensaje genérico para el 408 nocturno/sin servicio.
            val message = friendlyErrorMessage(ex)
            val previous = _state.value
            _state.update { it.copy(loading = false, error = message) }
            persistWidgetState(
                config.appWidgetId,
                WidgetUiState.errorState(
                    previous = WidgetUiState(
                        arrivals = previous.stops,
                        lastUpdatedEpochMillis = previous.lastUpdated,
                        highContrast = config.highContrast,
                        refreshIntervalMinutes = config.refreshIntervalMinutes
                    ),
                    errorMessage = message,
                    highContrast = config.highContrast
                )
            )
        }
    }
}
