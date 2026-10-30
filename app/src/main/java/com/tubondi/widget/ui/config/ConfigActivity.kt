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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tubondi.widget.data.catalog.CatalogStop
import com.tubondi.widget.domain.displayName
import com.tubondi.widget.domain.LineColorResolver
import com.tubondi.widget.domain.model.Stop
import com.tubondi.widget.domain.model.MAX_REFRESH_INTERVAL_MINUTES
import com.tubondi.widget.domain.model.MIN_REFRESH_INTERVAL_MINUTES
import com.tubondi.widget.ui.theme.TuBondiTheme
import com.tubondi.widget.ui.theme.TuBondiSpacing
import com.tubondi.widget.widget.TransitGlanceReceiver
import com.tubondi.widget.ui.map.StopMapActivity
import com.tubondi.widget.ui.map.MapStopPayload
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.roundToInt

@AndroidEntryPoint
class ConfigActivity : ComponentActivity() {

    private val viewModel: ConfigViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setResult(RESULT_CANCELED)
        val mapLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val selectedCodes = result.data?.getStringArrayListExtra(StopMapActivity.EXTRA_SELECTED_CODES)
            if (result.resultCode == RESULT_OK && selectedCodes != null) {
                viewModel.applyRouteMapSelection(selectedCodes.toSet())
            }
        }
        setContent {
            val state by viewModel.state.collectAsState()
            LaunchedEffect(appWidgetId) { viewModel.load(appWidgetId) }
            TuBondiTheme(highContrast = state.highContrast) {
                ConfigScreen(
                    state = state,
                    onRouteSelected = viewModel::selectRoute,
                    onLineToggled = viewModel::toggleLine,
                    onRemoveStop = viewModel::removeStop,
                    onCustomNameChanged = viewModel::updateCustomName,
                    onRefreshChanged = viewModel::updateRefresh,
                    onNotificationsChanged = viewModel::updateNotifications,
                    onThresholdChanged = viewModel::updateThreshold,
                    onHighContrast = viewModel::toggleHighContrast,
                    onConfChanged = viewModel::updateConf,
                    onSearchChanged = viewModel::search,
                    onAddStop = viewModel::addCatalogStop,
                    onAddRouteStop = { viewModel.addRouteStop(it.code) },
                    onShowMap = { stops ->
                        mapLauncher.launch(
                            Intent(this, StopMapActivity::class.java).putExtra(
                                StopMapActivity.EXTRA_STOPS,
                                Json.encodeToString(
                                    ListSerializer(MapStopPayload.serializer()),
                                    stops
                                )
                            )
                        )
                    },
                    onBack = { finish() },
                    onSave = {
                        viewModel.save {
                            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                            finish()
                        }
                    }
                )
            }
        }
    }
}

private enum class ConfigSection { STOPS, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ConfigScreen(
    state: ConfigUiState,
    onRouteSelected: (RouteUiModel) -> Unit,
    onLineToggled: (String, String, Boolean) -> Unit,
    onRemoveStop: (String) -> Unit,
    onCustomNameChanged: (String, String) -> Unit,
    onRefreshChanged: (Int) -> Unit,
    onNotificationsChanged: (Boolean) -> Unit,
    onThresholdChanged: (Int) -> Unit,
    onHighContrast: (Boolean) -> Unit,
    onConfChanged: (String) -> Unit,
    onSearchChanged: (String) -> Unit,
    onAddStop: (CatalogStop) -> Unit,
    onAddRouteStop: (Stop) -> Unit,
    onShowMap: (List<MapStopPayload>) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    var section by rememberSaveable { mutableIntStateOf(ConfigSection.STOPS.ordinal) }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    val closeOrConfirm = {
        if (state.hasUnsavedChanges) showDiscardDialog = true else onBack()
    }
    BackHandler { closeOrConfirm() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurar widget") },
                navigationIcon = {
                    IconButton(onClick = closeOrConfirm, modifier = Modifier.size(48.dp)) {
                        Text(
                            "‹",
                            modifier = Modifier.semantics { contentDescription = "Volver" },
                            fontSize = 30.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (state.saving || state.hasUnsavedChanges) {
                        Text(
                            text = if (state.saving) "Guardando…" else "Cambios sin guardar",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    Button(
                        onClick = onSave,
                        enabled = !state.saving && state.selectedStops.any { it.isSelected },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) {
                        if (state.saving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                        }
                        Text("Guardar cambios")
                    }
                }
            }
        }
    ) { innerPadding ->
        if (state.loading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Cargando configuración")
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            ) {
                TabRow(selectedTabIndex = section) {
                    Tab(
                        selected = section == ConfigSection.STOPS.ordinal,
                        onClick = { section = ConfigSection.STOPS.ordinal },
                        text = { Text("Paradas") }
                    )
                    Tab(
                        selected = section == ConfigSection.SETTINGS.ordinal,
                        onClick = { section = ConfigSection.SETTINGS.ordinal },
                        text = { Text("Ajustes") }
                    )
                }
                state.error?.let { message ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            message,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                when (section) {
                    ConfigSection.STOPS.ordinal -> StopsSection(
                        state, onSearchChanged, onAddStop, onAddRouteStop, onShowMap, onRemoveStop, onCustomNameChanged,
                        onLineToggled, onRouteSelected
                    )
                    else -> SettingsSection(
                        state = state,
                        onRefreshChanged = onRefreshChanged,
                        onNotificationsChanged = onNotificationsChanged,
                        onThresholdChanged = onThresholdChanged,
                        onHighContrast = onHighContrast,
                        onConfChanged = onConfChanged
                    )
                }
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("¿Descartar cambios?") },
            text = { Text("Se perderán los cambios sin guardar.") },
            confirmButton = {
                TextButton(onClick = { showDiscardDialog = false; onBack() }) { Text("Descartar") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("Seguir editando") }
            }
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun StopsSection(
    state: ConfigUiState,
    onSearchChanged: (String) -> Unit,
    onAddStop: (CatalogStop) -> Unit,
    onAddRouteStop: (Stop) -> Unit,
    onShowMap: (List<MapStopPayload>) -> Unit,
    onRemoveStop: (String) -> Unit,
    onCustomNameChanged: (String, String) -> Unit,
    onLineToggled: (String, String, Boolean) -> Unit,
    onRouteSelected: (RouteUiModel) -> Unit
) {
    val keyboard = LocalSoftwareKeyboardController.current
    var showAllHubs by rememberSaveable { mutableStateOf(false) }
    var showRoutes by rememberSaveable { mutableStateOf(false) }
    val selectedStops = state.selectedStops.filter { it.isSelected }
    val visibleHubs = if (showAllHubs) state.hubs else state.hubs.take(8)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = TuBondiSpacing.content, top = 14.dp, end = TuBondiSpacing.content, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Buscar paradas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Calle, barrio o nombre") },
                    placeholder = { Text("Ej.: Duarte Quirós") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() })
                )
            }
        }

        if (state.searching && state.searchResults.isEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Buscando paradas…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (state.searchQuery.isNotBlank() && !state.searching && state.searchResults.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("No encontramos paradas", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Probá con otro nombre, calle o barrio.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        items(state.searchResults, key = { "search-${it.code}" }) { result ->
            val alreadyAdded = selectedStops.any { it.stop.code == result.code }
            StopSearchResult(result, alreadyAdded) {
                keyboard?.hide()
                onAddStop(result)
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Mis paradas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${selectedStops.size} seleccionadas", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.routeStops.isEmpty()) {
                    OutlinedButton(
                        onClick = { onShowMap(buildMapPayload(state.routeStops, selectedStops)) },
                        enabled = selectedStops.isNotEmpty(),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) { Text("Mapa") }
                }
            }
        }
        if (selectedStops.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Todavía no agregaste paradas", fontWeight = FontWeight.SemiBold)
                        Text("Buscá un lugar arriba.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        items(selectedStops, key = { "selected-${it.stop.code}" }) { stopUi ->
            SelectedStopCard(
                stopUi = stopUi,
                onRemove = { onRemoveStop(stopUi.stop.code) },
                onCustomNameChanged = { onCustomNameChanged(stopUi.stop.code, it) },
                onLineToggled = { lineId, selected -> onLineToggled(stopUi.stop.code, lineId, selected) }
            )
        }

        if (state.searchQuery.isBlank() && state.hubs.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Lugares frecuentes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            if (state.hubs.size > 8) {
                                TextButton(onClick = { showAllHubs = !showAllHubs }) {
                                    Text(if (showAllHubs) "Ver menos" else "Ver todos")
                                }
                            }
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            visibleHubs.forEach { hub ->
                                val alreadyAdded = selectedStops.any { it.stop.code == hub.code }
                                AssistChip(
                                    onClick = { onAddStop(hub) },
                                    enabled = !alreadyAdded,
                                    modifier = Modifier.widthIn(max = 300.dp).heightIn(min = 48.dp),
                                    label = {
                                        Text(
                                            text = if (alreadyAdded) "${hub.displayName()} · Agregada" else hub.displayName(),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Explorar líneas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("${state.availableRoutes.size} recorridos disponibles", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { showRoutes = !showRoutes }) {
                            Text(if (showRoutes) "Ocultar" else "Explorar")
                        }
                    }
                    if (showRoutes) {
                        if (state.loadingRoute) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Cargando paradas del recorrido…")
                            }
                        }
                        state.availableRoutes.forEach { route ->
                            RouteRow(route = route, enabled = !state.loadingRoute) { onRouteSelected(route) }
                        }
                    }
                }
            }
        }

        if (state.routeStops.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Paradas de la línea ${state.selectedRoute?.lineName.orEmpty()}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(state.selectedRoute?.routeName.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                OutlinedButton(
                    onClick = { onShowMap(buildMapPayload(state.routeStops, selectedStops)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text("Ver paradas en el mapa") }
            }
            items(state.routeStops, key = { "route-stop-${it.code}" }) { candidate ->
                val selectedStop = selectedStops.firstOrNull { it.stop.code == candidate.code }
                val alreadyAdded = selectedStop != null
                val routeLineKnown = selectedStop?.stop?.lines?.any { line ->
                    candidate.lines.any { it.selectionKey == line.selectionKey }
                } == true
                val canEnrich = alreadyAdded && (!routeLineKnown ||
                    (selectedStop?.stop?.latitude == null && candidate.latitude != null))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(candidate.displayName(), fontWeight = FontWeight.SemiBold)
                            candidate.hood?.takeIf { candidate.displayName() != "Parada en $it" }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Button(
                            onClick = { onAddRouteStop(candidate) },
                            enabled = !alreadyAdded || canEnrich,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(when {
                                !alreadyAdded -> "Agregar"
                                !routeLineKnown -> "Sumar línea"
                                canEnrich -> "Ubicar"
                                else -> "Agregada"
                            })
                        }
                    }
                }
            }
        }

    }
}

private fun buildMapPayload(routeStops: List<Stop>, selectedStops: List<StopUiModel>): List<MapStopPayload> {
    val selectedByCode = selectedStops.filter { it.isSelected }.associateBy { it.stop.code }
    val routeCodes = routeStops.mapTo(mutableSetOf()) { it.code }
    val mapStops = if (routeStops.isNotEmpty()) {
        routeStops + selectedByCode.values.map { it.stop }.filterNot { it.code in routeCodes }
    } else {
        selectedByCode.values.map { it.stop }
    }
    return mapStops.distinctBy { it.code }.map { stop ->
        val configured = selectedByCode[stop.code]?.stop
        val displayStop = if (configured == null) stop else stop.copy(
            name = configured.name,
            hood = configured.hood ?: stop.hood,
            customName = configured.customName,
            latitude = stop.latitude ?: configured.latitude,
            longitude = stop.longitude ?: configured.longitude
        )
        MapStopPayload(
            code = displayStop.code,
            name = displayStop.displayName(),
            latitude = displayStop.latitude,
            longitude = displayStop.longitude,
            selectable = stop.code in routeCodes,
            selected = selectedByCode.containsKey(stop.code)
        )
    }
}

@Composable
private fun StopSearchResult(stop: CatalogStop, alreadyAdded: Boolean, onAdd: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(stop.displayName(), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                stop.displayHood()?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Button(onClick = onAdd, enabled = !alreadyAdded, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (alreadyAdded) "Agregada" else "Agregar")
            }
        }
    }
}

@Composable
private fun RouteRow(route: RouteUiModel, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.size(10.dp).background(
                        route.color.toComposeColor() ?: MaterialTheme.colorScheme.outlineVariant,
                        CircleShape
                    )
                )
                Text("Línea ${route.lineName}", fontWeight = FontWeight.SemiBold)
            }
            Text(route.routeName, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Ver paradas")
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SelectedStopCard(
    stopUi: StopUiModel,
    onRemove: () -> Unit,
    onCustomNameChanged: (String) -> Unit,
    onLineToggled: (String, Boolean) -> Unit
) {
    val stop = stopUi.stop
    val displayName = stop.displayName()
    var customName by rememberSaveable(stop.code) { mutableStateOf(stop.customName.orEmpty()) }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(TuBondiSpacing.cardRadius)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(displayName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    stop.hood?.takeIf { displayName != "Parada en $it" }?.let {
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
                OutlinedButton(onClick = onRemove, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Quitar")
                }
            }

            if (displayName == "Ubicación sin nombre") {
                OutlinedTextField(
                    value = customName,
                    onValueChange = {
                        customName = it
                        onCustomNameChanged(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nombre personalizado") },
                    placeholder = { Text("Ej.: esquina o referencia cercana") },
                    singleLine = true
                )
            }

            if (stopUi.stop.lines.isNotEmpty()) {
                HorizontalDivider()
                Text("Líneas de esta parada", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                if (stopUi.selectedLines.isEmpty()) {
                    Text("Todas las líneas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    stopUi.stop.lines.forEach { line ->
                        val selected = stopUi.isLineSelected(line.selectionKey)
                        FilterChip(
                            selected = selected,
                            onClick = { onLineToggled(line.selectionKey, !selected) },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(
                                        Modifier.size(9.dp).background(
                                            line.color.toComposeColor() ?: MaterialTheme.colorScheme.outlineVariant,
                                            CircleShape
                                        )
                                    )
                                    if (selected) Text("✓", color = MaterialTheme.colorScheme.primary)
                                    Text(line.name)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun String?.toComposeColor(): Color? = LineColorResolver.normalize(this)?.let { normalized ->
    runCatching { Color(android.graphics.Color.parseColor(normalized)) }.getOrNull()
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
    var notificationsBlocked by rememberSaveable { mutableStateOf(false) }
    var showServiceValue by rememberSaveable { mutableStateOf(state.conf != DEFAULT_CONF) }
    val lifecycleOwner = context as? ComponentActivity
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && hasNotificationPermission(context)) {
                notificationsBlocked = false
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onNotificationsChanged(granted)
        notificationsBlocked = !granted && (context as? Activity)?.let {
            !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.POST_NOTIFICATIONS)
        } == true
    }
    val permissionGranted = hasNotificationPermission(context)
    val notificationsEnabled = state.notificationsEnabled && permissionGranted

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = TuBondiSpacing.content, top = 14.dp, end = TuBondiSpacing.content, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text("Ajustes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Valores actuales para este widget", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SettingsCard {
                Text("Actualización automática", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Cada ${state.refreshMinutes} min", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Text(
                                    "Sin datos nuevos, los horarios se ocultan al llegar este intervalo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = state.refreshMinutes.toFloat(),
                    onValueChange = { onRefreshChanged(it.roundToInt()) },
                    valueRange = MIN_REFRESH_INTERVAL_MINUTES.toFloat()..MAX_REFRESH_INTERVAL_MINUTES.toFloat(),
                    steps = 8,
                    modifier = Modifier.semantics {
                        contentDescription = "Intervalo de actualización automática"
                        stateDescription = "Cada ${state.refreshMinutes} minutos"
                    }
                )
            }
        }
        item {
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 10.dp)) {
                        Text("Notificaciones de arribos", fontWeight = FontWeight.SemiBold)
                        Text(
                            when {
                                !permissionGranted -> "Necesitan permiso del sistema"
                                notificationsEnabled -> "Activadas"
                                else -> "Desactivadas"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { enabled ->
                            when {
                                !enabled -> onNotificationsChanged(false)
                                permissionGranted -> onNotificationsChanged(true)
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                                    notificationsBlocked = false
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                else -> onNotificationsChanged(true)
                            }
                        },
                        modifier = Modifier.semantics {
                            contentDescription = "Notificaciones de arribos"
                            stateDescription = if (notificationsEnabled) "Activadas" else "Desactivadas"
                        }
                    )
                }
                if (notificationsBlocked) {
                    Text("El permiso está bloqueado por el sistema.", color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        )
                    }) { Text("Abrir ajustes de notificaciones") }
                }
            }
        }
        item {
            SettingsCard {
                Text("Avisarme cuando llegue en", fontWeight = FontWeight.SemiBold)
                Text("${state.thresholdMinutes} min o menos", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Slider(
                    value = state.thresholdMinutes.toFloat(),
                    onValueChange = { onThresholdChanged(it.roundToInt()) },
                    valueRange = 1f..30f,
                    steps = 28,
                    modifier = Modifier.semantics {
                        contentDescription = "Umbral de notificación"
                        stateDescription = "${state.thresholdMinutes} minutos o menos"
                    }
                )
            }
        }
        item {
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 10.dp)) {
                        Text("Alto contraste", fontWeight = FontWeight.SemiBold)
                        Text(if (state.highContrast) "Activado" else "Desactivado", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = state.highContrast,
                        onCheckedChange = onHighContrast,
                        modifier = Modifier.semantics {
                            contentDescription = "Alto contraste"
                            stateDescription = if (state.highContrast) "Activado" else "Desactivado"
                        }
                    )
                }
            }
        }
        item {
            SettingsCard {
                Text("Configuración avanzada del servicio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Servicio de datos", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (state.conf == DEFAULT_CONF) "Predeterminado" else "Personalizado",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { showServiceValue = !showServiceValue }) {
                        Text(if (showServiceValue) "Ocultar" else "Editar")
                    }
                }
                if (showServiceValue) {
                    OutlinedTextField(
                        value = state.conf,
                        onValueChange = onConfChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Servicio de datos") },
                        singleLine = true
                    )
                    if (state.conf != DEFAULT_CONF) {
                        TextButton(onClick = { onConfChanged(DEFAULT_CONF) }) {
                            Text("Usar configuración predeterminada")
                        }
                    }
                } else {
                    Text("Usa la configuración estándar de Tu Bondi.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            OutlinedButton(
                onClick = {
                    val manager = context.getSystemService(AppWidgetManager::class.java)
                    val provider = ComponentName(context, TransitGlanceReceiver::class.java)
                    if (manager.isRequestPinAppWidgetSupported) {
                        manager.requestPinAppWidget(provider, null, null)
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) {
                Text("Agregar widget al inicio")
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(TuBondiSpacing.cardRadius)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

private fun hasNotificationPermission(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
