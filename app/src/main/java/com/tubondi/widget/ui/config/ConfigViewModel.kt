package com.tubondi.widget.ui.config

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tubondi.widget.data.catalog.CatalogRepository
import com.tubondi.widget.data.catalog.CatalogStop
import com.tubondi.widget.data.remote.dto.LineDto
import com.tubondi.widget.data.remote.dto.LinesRoutesResponseDto
import com.tubondi.widget.data.repo.TransitRepository
import com.tubondi.widget.domain.LineColorResolver
import com.tubondi.widget.domain.friendlyErrorMessage
import com.tubondi.widget.domain.isDescriptiveStopName
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
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
    private var searchJob: Job? = null

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
                val canonicalLineColors = LineColorResolver.canonicalColors(
                    linesPayload.lineas.map { line ->
                        Line(id = line.id, name = line.name, color = line.color, clientId = line.cliente)
                    }
                )
                val baseline = (existing ?: WidgetConfiguration(
                    appWidgetId = appWidgetId,
                    selections = emptyList(),
                    conf = confToUse
                )).copy(
                    selections = existing?.selections.orEmpty().map { selection ->
                        val resolvedStop = catalogRepository.resolveStop(selection.stop)
                        selection.copy(
                            stop = resolvedStop,
                            selectedLines = migrateSelectedLines(resolvedStop, selection.selectedLines)
                        )
                    },
                    refreshIntervalMinutes = (existing?.refreshIntervalMinutes ?: DEFAULT_REFRESH_MINUTES)
                        .coerceIn(MIN_REFRESH_INTERVAL_MINUTES, MAX_REFRESH_INTERVAL_MINUTES),
                    nearThresholdMinutes = (existing?.nearThresholdMinutes ?: 5).coerceIn(1, 30),
                    conf = confToUse
                )
                _state.update {
                    it.copy(
                        loading = false,
                        initialized = true,
                        availableRoutes = linesPayload.lineas.flatMap { line ->
                            val canonicalColor = line.cliente?.let { clientId ->
                                canonicalLineColors[com.tubondi.widget.domain.LineKey(clientId, line.id)]
                            }
                            line.rutas.map { route ->
                                RouteUiModel(
                                    lineName = line.name,
                                    routeName = route.name.orEmpty(),
                                    routeId = route.routeId.toIntOrNull() ?: route.routeId.hashCode(),
                                    lineId = line.id,
                                    clientId = line.cliente ?: 0,
                                    color = canonicalColor
                                )
                            }
                        },
                        refreshMinutes = baseline.refreshIntervalMinutes,
                        notificationsEnabled = baseline.notificationsEnabled,
                        thresholdMinutes = baseline.nearThresholdMinutes,
                        highContrast = existing?.highContrast ?: false,
                        conf = confToUse,
                        hubs = catalogRepository.hubStops(),
                        selectedStops = baseline.selections.map {
                            StopUiModel(stop = it.stop, isSelected = true, selectedLines = it.selectedLines.toSet())
                        },
                        originalConfiguration = baseline,
                        error = null
                    )
                }
            } catch (ex: Exception) {
                _state.update { it.copy(loading = false, error = friendlyErrorMessage(ex)) }
            }
        }
    }

    fun search(query: String) {
        searchJob?.cancel()
        if (STOP_CODE_QUERY.matches(query.trim())) {
            _state.update {
                it.copy(
                    searchQuery = "",
                    searchResults = emptyList(),
                    searching = false,
                    error = "Buscá por calle, barrio o nombre."
                )
            }
            return
        }
        _state.update {
            it.copy(searchQuery = query, searchResults = emptyList(), searching = query.isNotBlank(), error = null)
        }
        if (query.isBlank()) {
            _state.update { it.copy(searchResults = emptyList(), searching = false, error = null) }
            return
        }
        searchJob = viewModelScope.launch {
            try {
                delay(160)
                val results = withContext(Dispatchers.Default) { catalogRepository.search(query) }
                _state.update { current ->
                    if (current.searchQuery == query) {
                        current.copy(searchResults = results, searching = false, error = null)
                    } else current
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (ex: Exception) {
                _state.update { current ->
                    if (current.searchQuery == query) {
                        current.copy(searchResults = emptyList(), searching = false, error = friendlyErrorMessage(ex))
                    } else current
                }
            }
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

    fun selectRoute(route: RouteUiModel) {
        _state.update { it.copy(selectedRoute = route, loadingRoute = true, error = null) }
        viewModelScope.launch {
            try {
                val stops = repository.fetchStopsForRoute(
                    routeId = route.routeId,
                    clientId = route.clientId,
                    conf = state.value.conf,
                    lineId = route.lineId,
                    lineName = route.lineName,
                    lineColor = route.color
                )
                    .distinctBy { it.code }
                _state.update { current ->
                    val routeStops = stops.map { catalogRepository.resolveStop(it) }
                    current.copy(
                        routeStops = routeStops,
                        error = null,
                        loadingRoute = false
                    )
                }
            } catch (ex: Exception) {
                _state.update { it.copy(error = friendlyErrorMessage(ex), loadingRoute = false) }
            }
        }
    }

    fun removeStop(code: String) {
        _state.update { state ->
            state.copy(selectedStops = state.selectedStops.filterNot { it.stop.code == code }, error = null)
        }
    }

    /** A route is a source of candidates; the user adds each stop explicitly. */
    fun addRouteStop(code: String) {
        _state.update { state ->
            val candidate = state.routeStops.firstOrNull { it.code == code } ?: return@update state
            val index = state.selectedStops.indexOfFirst { it.stop.code == code }
            when {
                index < 0 -> state.copy(
                    selectedStops = state.selectedStops + StopUiModel(stop = candidate, isSelected = true),
                    error = null
                )
                state.selectedStops[index].isSelected -> {
                    val selected = state.selectedStops[index]
                    val mergedStop = selected.stop.copy(
                        latitude = selected.stop.latitude ?: candidate.latitude,
                        longitude = selected.stop.longitude ?: candidate.longitude,
                        lines = (selected.stop.lines + candidate.lines).distinctBy { it.selectionKey },
                        hood = selected.stop.hood ?: candidate.hood
                    )
                    val addedLineKeys = candidate.lines.mapTo(mutableSetOf()) { it.selectionKey }
                    val selectedLines = if (selected.selectedLines.isEmpty()) {
                        emptySet()
                    } else {
                        selected.selectedLines + addedLineKeys
                    }
                    if (mergedStop == selected.stop && selectedLines == selected.selectedLines) state else state.copy(
                        selectedStops = state.selectedStops.mapIndexed { i, stop ->
                            if (i == index) stop.copy(stop = mergedStop, selectedLines = selectedLines) else stop
                        },
                        error = null
                    )
                }
                else -> state.copy(
                    selectedStops = state.selectedStops.mapIndexed { i, stop ->
                        if (i == index) stop.copy(isSelected = true) else stop
                    },
                    error = null
                )
            }
        }
    }

    /** Applies the current route's map selection to this widget's unsaved draft. */
    fun applyRouteMapSelection(selectedCodes: Set<String>) {
        _state.update { current ->
            val candidates = current.routeStops.associateBy { it.code }
            if (candidates.isEmpty()) return@update current
            val selectedByCode = current.selectedStops.associateBy { it.stop.code }
            val candidateCodes = candidates.keys
            val updated = current.selectedStops.filterNot { it.stop.code in candidateCodes }.toMutableList()
            candidates.forEach { (code, candidate) ->
                val existing = selectedByCode[code]
                val shouldSelect = code in selectedCodes
                if (!shouldSelect) {
                    if (existing != null) updated += existing.copy(isSelected = false)
                    return@forEach
                }
                if (existing == null) {
                    updated += StopUiModel(stop = candidate, isSelected = true)
                } else {
                    val configured = existing.stop
                    val merged = candidate.copy(
                        name = configured.name.ifBlank { candidate.name },
                        latitude = configured.latitude ?: candidate.latitude,
                        longitude = configured.longitude ?: candidate.longitude,
                        lines = (configured.lines + candidate.lines).distinctBy { it.selectionKey },
                        hood = configured.hood ?: candidate.hood,
                        customName = configured.customName
                    )
                    updated += existing.copy(stop = merged, isSelected = true)
                }
            }
            current.copy(selectedStops = updated, error = null)
        }
    }

    fun updateCustomName(code: String, name: String) {
        val cleaned = name.trim()
        if (cleaned.isNotEmpty() && !isDescriptiveStopName(cleaned, code)) {
            _state.update { it.copy(error = "Usá un nombre de lugar, no un identificador técnico.") }
            return
        }
        _state.update { state ->
            state.copy(
                selectedStops = state.selectedStops.map { stopUi ->
                    if (stopUi.stop.code == code) {
                        stopUi.copy(stop = stopUi.stop.copy(customName = cleaned.ifEmpty { null }))
                    } else stopUi
                },
                error = null
            )
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
                        val allLines = stopUi.stop.lines.mapTo(mutableSetOf()) { it.selectionKey }
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
                val config = current.toConfiguration()
                prefs.saveConfiguration(config)
                RefreshWorker.enqueuePeriodic(appContext, config)
                RefreshWorker.enqueueOneTime(appContext, config.appWidgetId)
            } catch (ex: Exception) {
                _state.update { it.copy(error = friendlyErrorMessage(ex), saving = false) }
                return@launch
            }
            _state.update { it.copy(saving = false, originalConfiguration = current.toConfiguration()) }
            onDone()
        }
    }
}

const val DEFAULT_CONF = "cbaciudad"

/** D2: intervalo periódico por defecto = mínimo de WorkManager. */
const val DEFAULT_REFRESH_MINUTES = MIN_REFRESH_INTERVAL_MINUTES

 /** Fixture embebido para el fallback offline de líneas (D4). */
private const val LINES_ASSET = "api-examples/01-lines_routes.json"

data class ConfigUiState(
    val appWidgetId: Int = -1,
    val availableRoutes: List<RouteUiModel> = emptyList(),
    val selectedRoute: RouteUiModel? = null,
    val selectedStops: List<StopUiModel> = emptyList(),
    val routeStops: List<Stop> = emptyList(),
    val refreshMinutes: Int = DEFAULT_REFRESH_MINUTES,
    val notificationsEnabled: Boolean = true,
    val thresholdMinutes: Int = 5,
    val highContrast: Boolean = false,
    val conf: String = DEFAULT_CONF,
    val searchQuery: String = "",
    val searchResults: List<CatalogStop> = emptyList(),
    val searching: Boolean = false,
    val hubs: List<CatalogStop> = emptyList(),
    val loading: Boolean = false,
    val loadingRoute: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val initialized: Boolean = false,
    val originalConfiguration: WidgetConfiguration? = null
) {
    fun toConfiguration(): WidgetConfiguration = WidgetConfiguration(
        appWidgetId = appWidgetId,
        selections = selectedStops.filter { it.isSelected }.map {
            StopSelection(it.stop, it.selectedLines.toList())
        },
        refreshIntervalMinutes = refreshMinutes,
        notificationsEnabled = notificationsEnabled,
        nearThresholdMinutes = thresholdMinutes,
        highContrast = highContrast,
        conf = conf
    )

    val hasUnsavedChanges: Boolean
        get() = initialized && originalConfiguration != toConfiguration()
}

data class RouteUiModel(
    val lineName: String,
    val routeName: String,
    val routeId: Int,
    val clientId: Int,
    val color: String?,
    val lineId: String = lineName
)

data class StopUiModel(
    val stop: Stop,
    val isSelected: Boolean = false,
    val selectedLines: Set<String> = stop.lines.map { it.selectionKey }.toSet()
) {
    fun isLineSelected(lineId: String): Boolean = selectedLines.isEmpty() || lineId in selectedLines
}

private val STOP_CODE_QUERY = Regex("^[A-Z]{1,4}\\d{2,5}$", RegexOption.IGNORE_CASE)

private fun migrateSelectedLines(stop: Stop, selected: List<String>): List<String> {
    if (selected.isEmpty()) return emptyList()
    val converted = selected.map { value ->
        if (':' in value) {
            stop.lines.singleOrNull { it.selectionKey == value }?.selectionKey
        } else {
            stop.lines.filter { it.id == value }
                .singleOrNull()
                ?.takeIf { it.clientId != null }
                ?.selectionKey
        }
    }
    return converted.filterNotNull().takeIf { it.size == selected.size }.orEmpty()
}
