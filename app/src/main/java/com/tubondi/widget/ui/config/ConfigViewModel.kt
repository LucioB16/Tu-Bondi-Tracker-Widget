package com.tubondi.widget.ui.config

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tubondi.widget.data.catalog.CatalogRepository
import com.tubondi.widget.data.catalog.CatalogStop
import com.tubondi.widget.data.remote.dto.LineDto
import com.tubondi.widget.data.remote.dto.LinesRoutesResponseDto
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
import kotlinx.serialization.json.Json

/**
 * ViewModel para la pantalla de configuración del widget.
 * Líneas SIEMPRE frescas de la API (D4); sin red cae al fixture embebido.
 * El buscador de paradas usa el catálogo offline (RF-8/SA-10).
 */
@HiltViewModel
class ConfigViewModel @Inject constructor(
    private val repository: TransitRepository,
    private val catalogRepository: CatalogRepository,
    private val prefs: WidgetPreferences,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(ConfigUiState())
    val state: StateFlow<ConfigUiState> = _state

    private val json = Json { ignoreUnknownKeys = true }

    fun load(appWidgetId: Int) {
        if (_state.value.initialized) return
        _state.update { it.copy(loading = true, appWidgetId = appWidgetId) }
        viewModelScope.launch {
            try {
                // La config previa define la conf; se lee ANTES de pedir líneas.
                val existing = prefs.readConfiguration(appWidgetId)
                val confToUse = existing?.conf ?: DEFAULT_CONF
                val linesPayload = try {
                    repository.fetchLinesPayload(confToUse)
                } catch (ex: Exception) {
                    // Fallback offline: fixture embebido (D4).
                    json.decodeFromString<LinesRoutesResponseDto>(
                        appContext.assets.open(LINES_ASSET).bufferedReader().readText()
                    )
                }
                _state.update {
                    it.copy(
                        loading = false,
                        initialized = true,
                        availableRoutes = linesPayload.lineas.flatMap { line ->
                            line.rutas.map { route ->
                                RouteUiModel(
                                    lineName = line.name,
                                    routeName = route.name.orEmpty(),
                                    routeId = route.routeId.toIntOrNull() ?: route.routeId.hashCode(),
                                    clientId = line.cliente ?: 0,
                                    color = line.color
                                )
                            }
                        },
                        refreshMinutes = existing?.refreshIntervalMinutes ?: DEFAULT_REFRESH_MINUTES,
                        notificationsEnabled = existing?.notificationsEnabled ?: true,
                        thresholdMinutes = existing?.nearThresholdMinutes ?: 5,
                        highContrast = existing?.highContrast ?: false,
                        conf = confToUse,
                        hubs = catalogRepository.hubs(),
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

    fun search(query: String) {
        _state.update { it.copy(searchQuery = query) }
        viewModelScope.launch {
            val results = catalogRepository.search(query)
            _state.update { it.copy(searchResults = results) }
        }
    }

    /** Agrega una parada del catálogo al widget (RF-8); duplicados se ignoran. */
    fun addCatalogStop(catalogStop: CatalogStop) {
        _state.update { st ->
            if (st.selectedStops.any { it.stop.code == catalogStop.code }) return@update st
            st.copy(
                selectedStops = st.selectedStops + StopUiModel(
                    stop = catalogRepository.toStop(catalogStop),
                    isSelected = true,
                    // Sin líneas conocidas aún = todas (semántica del filtro).
                    selectedLines = emptySet()
                ),
                error = null
            )
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

    fun toggleLine(stopCode: String, lineId: String, enabled: Boolean) {
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

    fun updateConf(value: String) {
        _state.update { it.copy(conf = value.trim()) }
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
}

private const val DEFAULT_CONF = "cbaciudad"

/** D2: frecuencia por defecto = mínimo efectivo de WorkManager. */
const val DEFAULT_REFRESH_MINUTES = 15

/** Fixture embebido para el fallback offline de líneas (D4). */
private const val LINES_ASSET = "api-examples/01-lines_routes.json"

data class ConfigUiState(
    val appWidgetId: Int = -1,
    val availableRoutes: List<RouteUiModel> = emptyList(),
    val selectedRoute: RouteUiModel? = null,
    val selectedStops: List<StopUiModel> = emptyList(),
    val refreshMinutes: Int = DEFAULT_REFRESH_MINUTES,
    val notificationsEnabled: Boolean = true,
    val thresholdMinutes: Int = 5,
    val highContrast: Boolean = false,
    val conf: String = DEFAULT_CONF,
    val searchQuery: String = "",
    val searchResults: List<CatalogStop> = emptyList(),
    val hubs: List<String> = emptyList(),
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
    val selectedLines: Set<String> = stop.lines.map { it.id }.toSet()
)
