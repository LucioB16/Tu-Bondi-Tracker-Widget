package com.tubondi.widget.ui.config

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import dagger.hilt.android.AndroidEntryPoint

/**
 * Pantalla principal de configuración del widget.
 */
@AndroidEntryPoint
class ConfigActivity : ComponentActivity() {

    private val viewModel: ConfigViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setResult(RESULT_CANCELED)
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            LaunchedEffect(appWidgetId) {
                viewModel.load(appWidgetId)
            }
            MaterialTheme {
                ConfigScreen(
                    state = state,
                    onRouteSelected = viewModel::selectRoute,
                    onStopToggled = viewModel::toggleStop,
                    onLineToggled = viewModel::toggleLine,
                    onRefreshChanged = viewModel::updateRefresh,
                    onNotificationsChanged = viewModel::updateNotifications,
                    onThresholdChanged = viewModel::updateThreshold,
                    onHighContrast = viewModel::toggleHighContrast,
                    onSave = {
                        viewModel.save {
                            val data = Intent().apply {
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                            }
                            setResult(RESULT_OK, data)
                            finish()
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ConfigScreen(
    state: ConfigUiState,
    onRouteSelected: (RouteUiModel) -> Unit,
    onStopToggled: (String, Boolean) -> Unit,
    onLineToggled: (String, Int, Boolean) -> Unit,
    onRefreshChanged: (Int) -> Unit,
    onNotificationsChanged: (Boolean) -> Unit,
    onThresholdChanged: (Int) -> Unit,
    onHighContrast: (Boolean) -> Unit,
    onSave: () -> Unit
) {
    if (state.loading) {
        Column(modifier = Modifier.padding(24.dp)) {
            CircularProgressIndicator()
        }
        return
    }
    LazyColumn(modifier = Modifier.padding(16.dp)) {
        item {
            Text(text = state.error ?: "", color = MaterialTheme.colorScheme.error)
        }
        item {
            Text(text = "${state.availableRoutes.size} rutas disponibles", fontWeight = FontWeight.Bold)
            Text(text = "Toca una ruta para cargar sus paradas")
        }
        items(state.availableRoutes.take(20)) { route ->
            RouteCard(route = route, onClick = { onRouteSelected(route) })
        }
        item {
            Spacer(modifier = Modifier.padding(4.dp))
            StopPickerScreen(state.selectedStops, onStopToggled, onLineToggled)
        }
        item {
            Spacer(modifier = Modifier.padding(4.dp))
            SettingsSection(state, onRefreshChanged, onNotificationsChanged, onThresholdChanged, onHighContrast)
        }
        item {
            Button(onClick = onSave, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Guardar")
            }
        }
    }
}

@Composable
private fun RouteCard(route: RouteUiModel, onClick: () -> Unit) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp)) {
        Text(text = "${route.lineName} • ${route.routeName}", fontWeight = FontWeight.Medium)
        Button(onClick = onClick, modifier = Modifier.padding(top = 4.dp)) {
            Text(text = "Agregar paradas")
        }
    }
}

@Composable
fun StopPickerScreen(
    stops: List<StopUiModel>,
    onStopToggled: (String, Boolean) -> Unit,
    onLineToggled: (String, Int, Boolean) -> Unit
) {
    Column {
        Text(text = "Paradas seleccionadas", fontWeight = FontWeight.Bold)
        stops.forEach { stopUi ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stopUi.stop.name)
                    Text(text = stopUi.stop.code)
                }
                Checkbox(checked = stopUi.isSelected, onCheckedChange = { onStopToggled(stopUi.stop.code, it) })
            }
            if (stopUi.isSelected) {
                LinePickerScreen(stopUi, onLineToggled)
            }
        }
    }
}

@Composable
fun LinePickerScreen(stopUi: StopUiModel, onLineToggled: (String, Int, Boolean) -> Unit) {
    Column(modifier = Modifier.padding(start = 16.dp)) {
        stopUi.stop.lines.forEach { line ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = line.name)
                Checkbox(
                    checked = stopUi.selectedLines.contains(line.id),
                    onCheckedChange = { onLineToggled(stopUi.stop.code, line.id, it) }
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    state: ConfigUiState,
    onRefreshChanged: (Int) -> Unit,
    onNotificationsChanged: (Boolean) -> Unit,
    onThresholdChanged: (Int) -> Unit,
    onHighContrast: (Boolean) -> Unit
) {
    Column {
        Text(text = "Frecuencia: ${state.refreshMinutes} min")
        Slider(value = state.refreshMinutes.toFloat(), onValueChange = { onRefreshChanged(it.toInt()) }, valueRange = 1f..60f)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Notificaciones")
            Switch(checked = state.notificationsEnabled, onCheckedChange = onNotificationsChanged)
        }
        Text(text = "Umbral: ${state.thresholdMinutes} min")
        Slider(value = state.thresholdMinutes.toFloat(), onValueChange = { onThresholdChanged(it.toInt()) }, valueRange = 1f..30f)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Alto contraste")
            Switch(checked = state.highContrast, onCheckedChange = onHighContrast)
        }
    }
}