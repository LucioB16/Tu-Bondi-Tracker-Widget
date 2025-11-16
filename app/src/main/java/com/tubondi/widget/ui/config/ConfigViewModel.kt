package com.tubondi.widget.ui.config

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tubondi.widget.data.remote.dto.LineDto
import com.tubondi.widget.data.repo.TransitRepository
import com.tubondi.widget.domain.model.Line
import com.tubondi.widget.domain.model.Stop
import com.tubondi.widget.domain.model.StopSelection
import com.tubondi.widget.domain.model.WidgetConfiguration
import com.tubondi.widget.prefs.WidgetPreferences
import com.tubondi.widget.work.RefreshWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel para la pantalla de configuración del widget.
 */
@HiltViewModel
class ConfigViewModel @Inject constructor(
    private val repository: TransitRepository,
    private val prefs: WidgetPreferences,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(ConfigUiState())
    val state: StateFlow<ConfigUiState> = _state

    fun load(appWidgetId: Int) {
        if (_state.value.initialized) return
        _state.update { it.copy(loading = true, appWidgetId = appWidgetId) }
        viewModelScope.launch {
            try {
                val linesPayload = repository.fetchLinesPayload(DEFAULT_CONF)
                val existing = prefs.readConfiguration(appWidgetId)
                _state.update {
                    it.copy(
                        loading = false,
                        initialized = true,
                        availableRoutes = linesPayload.lineas.flatMap { line ->
                            line.rutas.map { route ->
                                RouteUiModel(
                                    lineName = line.name,
                                    routeName = route.name,
                                    routeId = route.routeId.toIntOrNull() ?: route.routeId.hashCode(),
                                    clientId = line.cliente ?: 0,
                                    color = line.color
                                )
                            }
                        },
                        refreshMinutes = existing?.refreshIntervalMinutes ?: 5,
                        notificationsEnabled = existing?.notificationsEnabled ?: true,
                        thresholdMinutes = existing?.nearThresholdMinutes ?: 5,
                        highContrast = existing?.highContrast ?: false,
                        conf = existing?.conf ?: DEFAULT_CONF,
                        selectedStops = existing?.selections?.map {
                            StopUiModel(stop = it.stop, isSelected = true, selectedLines = it.selectedLines.toSet())
                        } ?: emptyList()
                    )
                }
            } catch (ex: Exception) {
                _state.update { it.copy(loading = false, error = ex.message) }
            }
        }
    }

    fun selectRoute(route: RouteUiModel) {
        _state.update { it.copy(selectedRoute = route) }
        viewModelScope.launch {
            try {
                val stops = repository.fetchStopsForRoute(route.routeId, route.clientId, state.value.conf)
                val merged = (state.value.selectedStops + stops.map { StopUiModel(stop = it) })
                    .distinctBy { it.stop.code }
                _state.update { it.copy(selectedStops = merged, error = null) }
            } catch (ex: Exception) {
                _state.update { it.copy(error = ex.message) }
            }
        }
    }

    fun toggleStop(code: String, isSelected: Boolean) {
        _state.update { st ->
            st.copy(
                selectedStops = st.selectedStops.map {
                    if (it.stop.code == code) it.copy(isSelected = isSelected) else it
                }
            )
        }
    }

    fun toggleLine(stopCode: String, lineId: Int, enabled: Boolean) {
        _state.update { st ->
            st.copy(
                selectedStops = st.selectedStops.map {
                    if (it.stop.code == stopCode) {
                        val newLines = if (enabled) {
                            it.selectedLines + lineId
                        } else {
                            it.selectedLines - lineId
                        }
                        it.copy(selectedLines = newLines)
                    } else it
                }
            )
        }
    }

    fun updateRefresh(value: Int) {
        _state.update { it.copy(refreshMinutes = value) }
    }

    fun updateNotifications(enabled: Boolean) {
        _state.update { it.copy(notificationsEnabled = enabled) }
    }

    fun updateThreshold(value: Int) {
        _state.update { it.copy(thresholdMinutes = value) }
    }

    fun toggleHighContrast(enabled: Boolean) {
        _state.update { it.copy(highContrast = enabled) }
    }

    fun save(onDone: () -> Unit) {
        val current = _state.value
        val selected = current.selectedStops.filter { it.isSelected }
        if (selected.isEmpty()) {
            _state.update { it.copy(error = "Selecciona al menos una parada") }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val config = WidgetConfiguration(
                    appWidgetId = current.appWidgetId,
                    selections = selected.map {
                        StopSelection(it.stop, it.selectedLines.toList())
                    },
                    refreshIntervalMinutes = current.refreshMinutes,
                    notificationsEnabled = current.notificationsEnabled,
                    nearThresholdMinutes = current.thresholdMinutes,
                    highContrast = current.highContrast,
                    conf = current.conf
                )
                prefs.saveConfiguration(config)
                RefreshWorker.enqueuePeriodic(appContext, config)
                RefreshWorker.enqueueOneTime(appContext, config.appWidgetId)
            } catch (ex: Exception) {
                _state.update { it.copy(error = ex.message, saving = false) }
                return@launch
            }
            _state.update { it.copy(saving = false) }
            onDone()
        }
    }

    companion object {
        private const val DEFAULT_CONF = "cbaciudad"
    }
}

data class ConfigUiState(
    val appWidgetId: Int = -1,
    val availableRoutes: List<RouteUiModel> = emptyList(),
    val selectedRoute: RouteUiModel? = null,
    val selectedStops: List<StopUiModel> = emptyList(),
    val refreshMinutes: Int = 5,
    val notificationsEnabled: Boolean = true,
    val thresholdMinutes: Int = 5,
    val highContrast: Boolean = false,
    val conf: String = DEFAULT_CONF,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val initialized: Boolean = false
)

data class RouteUiModel(
    val lineName: String,
    val routeName: String,
    val routeId: Int,
    val clientId: Int,
    val color: String?
)

data class StopUiModel(
    val stop: Stop,
    val isSelected: Boolean = false,
    val selectedLines: Set<Int> = stop.lines.map { it.id }.toSet()
)