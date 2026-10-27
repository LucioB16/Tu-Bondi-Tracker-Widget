package com.tubondi.widget.ui.arrivals

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tubondi.widget.data.repo.TransitRepository
import com.tubondi.widget.domain.friendlyErrorMessage
import com.tubondi.widget.domain.model.StopArrivals
import com.tubondi.widget.domain.model.WidgetConfiguration
import com.tubondi.widget.prefs.WidgetPreferences
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
    val error: String? = null
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
        _state.update { it.copy(loading = true, appWidgetId = appWidgetId) }
        viewModelScope.launch {
            val config = prefs.readConfiguration(appWidgetId)
            if (config == null) {
                _state.update { it.copy(loading = false, error = "Configura el widget primero") }
                return@launch
            }
            refreshInternal(config)
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

    private suspend fun refreshInternal(config: WidgetConfiguration) {
        _state.update { it.copy(loading = true, error = null) }
        try {
            val arrivals = repository.fetchArrivalsForConfig(config)
            _state.update {
                it.copy(loading = false, stops = arrivals, lastUpdated = System.currentTimeMillis())
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            // D5: mensaje genérico para el 408 nocturno/sin servicio.
            _state.update { it.copy(loading = false, error = friendlyErrorMessage(ex)) }
        }
    }
}
