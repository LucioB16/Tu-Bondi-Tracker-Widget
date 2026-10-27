package com.tubondi.widget.ui.config

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tubondi.widget.data.catalog.CatalogRepository
import com.tubondi.widget.data.catalog.CatalogStop
import com.tubondi.widget.data.remote.dto.LineDto
import com.tubondi.widget.data.remote.dto.LinesRoutesResponseDto
import com.tubondi.widget.data.repo.TransitRepository
import com.tubondi.widget.domain.friendlyErrorMessage
import com.tubondi.widget.domain.model.Line
import com.tubondi.widget.domain.model.MAX_REFRESH_INTERVAL_MINUTES
import com.tubondi.widget.domain.model.MIN_REFRESH_INTERVAL_MINUTES
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
                        refreshMinutes = (existing?.refreshIntervalMinutes ?: DEFAULT_REFRESH_MINUTES)
                            .coerceIn(MIN_REFRESH_INTERVAL_MINUTES, MAX_REFRESH_INTERVAL_MINUTES),
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
                _state.update { it.copy(loading = false, error = friendlyErrorMessage(ex)) }
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

    /** Agrega o reactiva una parada del catálogo sin duplicar su código. */
    fun addCatalogStop(catalogStop: CatalogStop) {
        _state.update { st ->
            val index = st.selectedStops.indexOfFirst { it.stop.code == catalogStop.code }
            when {
                index < 0 -> st.copy(
                    selectedStops = st.selectedStops + StopUiModel(
                        stop = catalogRepository.toStop(catalogStop),
                        isSelected = true,
                        selectedLines = emptySet()
                    ),
                    error = null
                )
                st.selectedStops[index].isSelected -> st
                else -> st.copy(
                    selectedStops = st.selectedStops.mapIndexed { i, stop ->
                        if (i == index) stop.copy(isSelected = true) else stop
                    },
                    error = null
                )
            }
        }
    }

    /** Agrega o reactiva un código localmente, sin consultar fuentes remotas. */
    fun addManualStopCode(rawCode: String): Boolean {
        val code = rawCode.trim()
        if (code.isBlank()) {
            _state.update { it.copy(error = "Ingresa el código de la parada") }
            return false
        }

        while (true) {
            val current = _state.value
            val index = current.selectedStops.indexOfFirst { it.stop.code == code }
            val added = index < 0 || !current.selectedStops[index].isSelected
            val updated = when {
                index < 0 -> current.copy(
                    selectedStops = current.selectedStops + StopUiModel(
                        stop = Stop(code = code, name = "Parada $code"),
                        isSelected = true
                    ),
                    error = null
                )
                added -> current.copy(
                    selectedStops = current.selectedStops.mapIndexed { i, stop ->
                        if (i == index) stop.copy(isSelected = true) else stop
                    },
                    error = null
                )
                else -> current.copy(error = "El código $code ya está agregado")
            }
            if (_state.compareAndSet(current, updated)) return added
        }
    }

    fun selectRoute(route: RouteUiModel) {
        _state.update { it.copy(selectedRoute = route) }
        viewModelScope.launch {
            try {
                val stops = repository.fetchStopsForRoute(route.routeId, route.clientId, state.value.conf)
                    .distinctBy { it.code }
                _state.update { current ->
                    val routeStopsByCode = stops.associateBy { it.code }
                    val existingCodes = current.selectedStops.mapTo(mutableSetOf()) { it.stop.code }
                    val updatedExisting = current.selectedStops.map { stopUi ->
                        val routeStop = routeStopsByCode[stopUi.stop.code] ?: return@map stopUi
                        val mergedStop = routeStop.copy(
                            lines = (stopUi.stop.lines + routeStop.lines).distinctBy { it.id }
                        )
                        val knownLineIds = mergedStop.lines.mapTo(mutableSetOf()) { it.id }
                        val selectedLines = if (stopUi.selectedLines.isEmpty()) {
                            emptySet()
                        } else {
                            stopUi.selectedLines.intersect(knownLineIds).ifEmpty { emptySet() }
                        }
                        stopUi.copy(stop = mergedStop, isSelected = true, selectedLines = selectedLines)
                    }
                    val additions = stops.filterNot { it.code in existingCodes }
                        .map { StopUiModel(stop = it) }
                    current.copy(selectedStops = updatedExisting + additions, error = null)
                }
            } catch (ex: Exception) {
                _state.update { it.copy(error = friendlyErrorMessage(ex)) }
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
                selectedStops = st.selectedStops.map { stopUi ->
                    if (stopUi.stop.code == stopCode) {
                        val allLines = stopUi.stop.lines.mapTo(mutableSetOf()) { it.id }
                        val currentLines = stopUi.selectedLines.ifEmpty { allLines }
                        val selected = if (enabled) currentLines + lineId else currentLines - lineId
                        stopUi.copy(selectedLines = if (selected.isEmpty() || selected == allLines) emptySet() else selected)
                    } else stopUi
                }
            )
        }
    }

    fun updateRefresh(value: Int) {
        _state.update {
            it.copy(refreshMinutes = value.coerceIn(MIN_REFRESH_INTERVAL_MINUTES, MAX_REFRESH_INTERVAL_MINUTES))
        }
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
                _state.update { it.copy(error = friendlyErrorMessage(ex), saving = false) }
                return@launch
            }
            _state.update { it.copy(saving = false) }
            onDone()
        }
    }
}

private const val DEFAULT_CONF = "cbaciudad"

/** D2: intervalo periódico por defecto = mínimo de WorkManager. */
const val DEFAULT_REFRESH_MINUTES = MIN_REFRESH_INTERVAL_MINUTES

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
) {
    fun isLineSelected(lineId: String): Boolean = selectedLines.isEmpty() || lineId in selectedLines
}
