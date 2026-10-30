package com.tubondi.widget.ui.arrivals

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tubondi.widget.domain.ArrivalFreshness
import com.tubondi.widget.domain.HtmlText
import com.tubondi.widget.domain.displayName
import com.tubondi.widget.domain.model.Arrival
import com.tubondi.widget.domain.model.StopArrivals
import com.tubondi.widget.domain.model.collectAvisos
import com.tubondi.widget.ui.config.ConfigActivity
import com.tubondi.widget.ui.theme.TuBondiTheme
import com.tubondi.widget.ui.theme.TuBondiSpacing
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@AndroidEntryPoint
class ArrivalsActivity : ComponentActivity() {

    private val viewModel: ArrivalsViewModel by viewModels()
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setContent {
            val state by viewModel.state.collectAsState()
            TuBondiTheme(highContrast = state.highContrast) {
                ArrivalsScreen(
                    state = state,
                    onRefresh = viewModel::refresh,
                    onConfig = {
                        startActivity(
                            Intent(this, ConfigActivity::class.java).putExtra(
                                AppWidgetManager.EXTRA_APPWIDGET_ID,
                                appWidgetId
                            )
                        )
                    }
                )
                LaunchedEffect(appWidgetId) { viewModel.load(appWidgetId) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            viewModel.syncFromWidgetCache(appWidgetId)
        }
    }
}

@Composable
fun ArrivalsScreen(state: ArrivalsUiState, onRefresh: () -> Unit, onConfig: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.lastUpdated, state.loading) {
        now = System.currentTimeMillis()
        while (true) {
            val wait = ArrivalFreshness.MINUTE_MILLIS - (System.currentTimeMillis() % ArrivalFreshness.MINUTE_MILLIS)
            delay(wait)
            now = System.currentTimeMillis()
        }
    }
    val stale = ArrivalFreshness.isStale(state.lastUpdated, now)
    val age = ArrivalFreshness.ageLabel(state.lastUpdated, now)
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val notices = remember(state.stops) { state.stops.collectAvisos() }
    val narrowHeader = LocalConfiguration.current.screenWidthDp < 360

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = TuBondiSpacing.content, vertical = 10.dp)
    ) {
        if (narrowHeader) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Column {
                    Text(
                        "Tu Bondi",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text("Arribos por parada", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onConfig,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text("Ajustes", maxLines = 1)
                    }
                    Button(
                        onClick = onRefresh,
                        enabled = !state.loading,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text("Actualizar", maxLines = 1)
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        "Tu Bondi",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text("Arribos por parada", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = onConfig, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Ajustes")
                }
                Spacer(Modifier.width(6.dp))
                Button(onClick = onRefresh, enabled = !state.loading, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Actualizar")
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        FreshnessCard(
            lastUpdated = state.lastUpdated,
            formattedTime = if (state.lastUpdated > 0L) timeFormatter.format(Date(state.lastUpdated)) else null,
            age = age,
            stale = stale,
            loading = state.loading,
            error = state.error
        )
        if (state.loading) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(10.dp))

        when {
            state.stops.isEmpty() && state.loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(Modifier.semantics { contentDescription = "Buscando arribos" })
                        Spacer(Modifier.height(12.dp))
                        Text("Buscando arribos")
                    }
                }
            }
            state.stops.isEmpty() -> {
                EmptyArrivals(onConfig = onConfig, onRefresh = onRefresh, error = state.error)
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
                ) {
                    items(state.stops, key = { "stop-${it.stop.code}" }) { stop ->
                        StopArrivalsCard(stop = stop, stale = stale, lastUpdated = state.lastUpdated, now = now)
                    }
                    if (notices.isNotEmpty()) {
                        item(key = "notices") {
                            NoticesCard(notices)
                        }
                    }
                    if (stale) {
                        item(key = "stale-endnote") {
                            Text(
                                "Las ETA reaparecen después de completar una actualización exitosa.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FreshnessCard(
    lastUpdated: Long,
    formattedTime: String?,
    age: String?,
    stale: Boolean,
    loading: Boolean,
    error: String?
) {
    val background = when {
        stale && lastUpdated > 0L -> MaterialTheme.colorScheme.tertiaryContainer
        error != null && lastUpdated == 0L -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val foreground = when {
        stale && lastUpdated > 0L -> MaterialTheme.colorScheme.onTertiaryContainer
        error != null && lastUpdated == 0L -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        color = background,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 1.dp
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when {
                        lastUpdated == 0L -> "Todavía no hay una actualización correcta"
                        stale -> "Datos desactualizados"
                        else -> "Última actualización correcta"
                    },
                    modifier = Modifier.weight(1f),
                    color = foreground,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall
                )
                if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            if (formattedTime != null && age != null) {
                Text("$formattedTime · $age", color = foreground, style = MaterialTheme.typography.bodyMedium)
            }
            if (stale && lastUpdated > 0L) {
                Text(
                    "Ocultamos los horarios porque podrían no coincidir con el servicio actual.",
                    color = foreground,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (error != null) {
                Text(
                    if (lastUpdated > 0L) "No se pudo actualizar: $error. Conservamos el último dato válido."
                    else error,
                    color = if (lastUpdated == 0L) foreground else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (loading && lastUpdated > 0L) {
                Text("Actualizando; la hora corresponde al último éxito.", color = foreground, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun EmptyArrivals(onConfig: () -> Unit, onRefresh: () -> Unit, error: String?) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = viewportHeight)
                    .padding(vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    if (error == null) "Sin paradas configuradas" else "No se pudieron cargar los arribos",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (error == null) "Agregá lugares para ver sus colectivos." else "Revisá la conexión e intentá de nuevo.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                if (error == null) {
                    Button(onClick = onConfig, modifier = Modifier.heightIn(min = 48.dp)) { Text("Configurar widget") }
                } else {
                    OutlinedButton(onClick = onRefresh, modifier = Modifier.heightIn(min = 48.dp)) { Text("Reintentar") }
                    Spacer(Modifier.height(6.dp))
                    androidx.compose.material3.TextButton(onClick = onConfig) { Text("Abrir ajustes") }
                }
            }
        }
    }
}

@Composable
private fun StopArrivalsCard(stop: StopArrivals, stale: Boolean, lastUpdated: Long, now: Long) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(TuBondiSpacing.largeCardRadius)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column {
                Text(stop.stop.displayName(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                stop.stop.hood?.takeIf { stop.stop.displayName() != "Parada en $it" }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (stop.arrivals.isEmpty()) {
                Text("Sin arribos para mostrar", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                stop.arrivals.forEachIndexed { index, arrival ->
                    if (index > 0) HorizontalDivider()
                    ArrivalRow(arrival, stale, lastUpdated, now)
                }
            }
        }
    }
}

@Composable
private fun ArrivalRow(arrival: Arrival, stale: Boolean, lastUpdated: Long, now: Long) {
    val eta = if (stale) "Horario oculto" else ArrivalFreshness.etaLabel(arrival, lastUpdated, now)
    val etaSurface = when {
        stale || eta == "Estimación vencida" -> MaterialTheme.colorScheme.tertiaryContainer
        arrival.etaMinutes != null && arrival.etaMinutes <= 5 -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val etaTextColor = when {
        stale || eta == "Estimación vencida" -> MaterialTheme.colorScheme.onTertiaryContainer
        arrival.etaMinutes != null && arrival.etaMinutes <= 5 -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Línea ${arrival.lineName}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            arrival.routeDescription?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val details = buildString {
                arrival.distanceKm?.let { append("%.1f km".format(Locale.getDefault(), it)) }
                arrival.operator?.takeIf { it.isNotBlank() }?.let { operator ->
                    if (isNotEmpty()) append(" · ")
                    append(operator)
                }
            }
            if (details.isNotEmpty()) {
                Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Surface(color = etaSurface, shape = RoundedCornerShape(14.dp)) {
            Text(
                eta,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                color = etaTextColor,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun NoticesCard(notices: List<String>) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(TuBondiSpacing.cardRadius)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Avisos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(50)) {
                    Text(
                        notices.size.toString(),
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            notices.forEachIndexed { index, notice ->
                if (index > 0) HorizontalDivider()
                Row(verticalAlignment = Alignment.Top) {
                    Text("!", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text(HtmlText.stripEntities(notice), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
