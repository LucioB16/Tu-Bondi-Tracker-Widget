package com.tubondi.widget.ui.config

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import com.tubondi.widget.domain.model.MAX_REFRESH_INTERVAL_MINUTES
import com.tubondi.widget.domain.model.MIN_REFRESH_INTERVAL_MINUTES
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.view.WindowCompat
import com.tubondi.widget.data.catalog.CatalogStop
import com.tubondi.widget.widget.TransitGlanceReceiver
import dagger.hilt.android.AndroidEntryPoint

/**
 * Pantalla principal de configuración del widget.
 * Incluye buscador de paradas sobre catálogo offline (RF-8) y request de
 * POST_NOTIFICATIONS (SA-7).
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
            val state by viewModel.state.collectAsState()
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
                    onConfChanged = viewModel::updateConf,
                    onSearchChanged = viewModel::search,
                    onAddStop = viewModel::addCatalogStop,
                    onAddManualStopCode = viewModel::addManualStopCode,
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
    onLineToggled: (String, String, Boolean) -> Unit,
    onRefreshChanged: (Int) -> Unit,
    onNotificationsChanged: (Boolean) -> Unit,
    onThresholdChanged: (Int) -> Unit,
    onHighContrast: (Boolean) -> Unit,
    onConfChanged: (String) -> Unit,
    onSearchChanged: (String) -> Unit,
    onAddStop: (CatalogStop) -> Unit,
    onAddManualStopCode: (String) -> Boolean,
    onSave: () -> Unit
) {
    if (state.loading) {
        Column(modifier = Modifier.padding(24.dp)) {
            CircularProgressIndicator()
        }
        return
    }
    var manualStopCode by rememberSaveable { mutableStateOf("") }
    LazyColumn(modifier = Modifier.safeDrawingPadding().padding(16.dp)) {
        item {
            Text(text = state.error ?: "", color = MaterialTheme.colorScheme.error)
        }
        item {
            Text(text = "Buscar parada", fontWeight = FontWeight.Bold)
            TextField(
                value = state.searchQuery,
                onValueChange = onSearchChanged,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Código, calle o barrio") },
                singleLine = true
            )
            state.searchResults.forEach { result ->
                val already = state.selectedStops.any { it.stop.code == result.code && it.isSelected }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = result.name, style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = "${result.code} · ${result.hood ?: ""}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Button(
                        onClick = { onAddStop(result) },
                        enabled = !already
                    ) {
                        Text(if (already) "Agregada" else "+ Agregar")
                    }
                }
            }
        }
        item {
            Text(text = "Agregar por código", fontWeight = FontWeight.Bold)
            TextField(
                value = manualStopCode,
                onValueChange = { manualStopCode = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Código de parada") },
                singleLine = true
            )
            Button(
                onClick = {
                    if (onAddManualStopCode(manualStopCode)) manualStopCode = ""
                },
                enabled = manualStopCode.isNotBlank()
            ) {
                Text("Agregar código")
            }
            Text(
                text = "Ingresá el código exacto. Agregar no consulta la API; el nombre inicial es provisional.",
                style = MaterialTheme.typography.labelSmall
            )
        }
        item {
            if (state.hubs.isNotEmpty()) {
                Text(text = "Hubs", fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    state.hubs.forEach { hub ->
                        OutlinedButton(
                            onClick = { onAddStop(CatalogStop(code = hub, name = hub)) },
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text(hub)
                        }
                    }
                }
            }
        }
        item {
            Text(text = "${state.availableRoutes.size} rutas disponibles", fontWeight = FontWeight.Bold)
            Text(text = "Toca una ruta para cargar sus paradas")
        }
        items(state.availableRoutes) { route ->
            RouteCard(route = route, onClick = { onRouteSelected(route) })
        }
        item {
            Spacer(modifier = Modifier.padding(4.dp))
            StopPickerScreen(state.selectedStops, onStopToggled, onLineToggled)
        }
        item {
            Spacer(modifier = Modifier.padding(4.dp))
            SettingsSection(
                state = state,
                onRefreshChanged = onRefreshChanged,
                onNotificationsChanged = onNotificationsChanged,
                onThresholdChanged = onThresholdChanged,
                onHighContrast = onHighContrast,
                onConfChanged = onConfChanged
            )
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
    onLineToggled: (String, String, Boolean) -> Unit
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
fun LinePickerScreen(stopUi: StopUiModel, onLineToggled: (String, String, Boolean) -> Unit) {
    Column(modifier = Modifier.padding(start = 16.dp)) {
        if (stopUi.stop.lines.isNotEmpty()) {
            Text(
                text = "Todas por defecto; si desmarcas la última, se vuelven a marcar todas.",
                style = MaterialTheme.typography.labelSmall
            )
        }
        stopUi.stop.lines.forEach { line ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = line.name)
                Checkbox(
                    checked = stopUi.isLineSelected(line.id),
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
    onHighContrast: (Boolean) -> Unit,
    onConfChanged: (String) -> Unit
) {
    val context = LocalContext.current
    // Android deja de mostrar el diálogo tras la segunda negativa o un `pm revoke`;
    // sin este aviso el switch rebotaba a OFF sin explicación.
    var notificationsBlocked by remember { mutableStateOf(false) }
    val lifecycleOwner = context as? ComponentActivity
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            ) notificationsBlocked = false
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // SA-7: el switch refleja el resultado real, no la intención.
        onNotificationsChanged(granted)
        notificationsBlocked = !granted && (context as? Activity)?.let {
            !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.POST_NOTIFICATIONS)
        } == true
    }
    Column {
        Text(text = "Intervalo automático: ${state.refreshMinutes} min")
        Text(
            text = "Mínimo 15 min; Android puede demorarlo. Actualizar a pedido no depende de este intervalo.",
            style = MaterialTheme.typography.labelSmall
        )
        Slider(
            value = state.refreshMinutes.toFloat(),
            onValueChange = { onRefreshChanged(it.toInt()) },
            valueRange = MIN_REFRESH_INTERVAL_MINUTES.toFloat()..MAX_REFRESH_INTERVAL_MINUTES.toFloat()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Notificaciones")
            Switch(checked = state.notificationsEnabled && (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED), onCheckedChange = { enabled ->
                when {
                    !enabled -> onNotificationsChanged(false)
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED -> onNotificationsChanged(true)
                    else -> {
                        notificationsBlocked = false
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            })
        }
        if (notificationsBlocked) {
            Text(
                text = "Permiso de notificaciones bloqueado por el sistema",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
            TextButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                )
            }) {
                Text(text = "Abrir ajustes de notificaciones")
            }
        }
        Text(text = "Umbral: ${state.thresholdMinutes} min")
        Slider(value = state.thresholdMinutes.toFloat(), onValueChange = { onThresholdChanged(it.toInt()) }, valueRange = 1f..30f)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Alto contraste")
            Switch(checked = state.highContrast, onCheckedChange = onHighContrast)
        }
        Text(text = "Conf")
        TextField(
            value = state.conf,
            onValueChange = onConfChanged,
            singleLine = true
        )
        OutlinedButton(onClick = {
            val manager = context.getSystemService(AppWidgetManager::class.java)
            val provider = ComponentName(context, TransitGlanceReceiver::class.java)
            if (manager.isRequestPinAppWidgetSupported) {
                // API del sistema: el launcher coloca el widget en una celda
                // libre sin drag manual (SA-1 alternativa).
                manager.requestPinAppWidget(provider, null, null)
            }
        }) {
            Text(text = "Agregar widget al inicio")
        }
    }
}
