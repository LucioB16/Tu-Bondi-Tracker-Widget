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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tubondi.widget.domain.HtmlText
import com.tubondi.widget.domain.model.Arrival
import com.tubondi.widget.domain.model.collectAvisos
import com.tubondi.widget.ui.config.ConfigActivity
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * Pantalla completa de arribos (D1, paridad Oma widget.owl:108-160):
 * reloj vivo, arribos agrupados por parada, avisos agregados, refresh
 * manual y acceso a la configuración.
 */
private val defaultArrivalsColors = lightColorScheme()
private val highContrastArrivalsColors = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    secondary = Color(0xFF4E2600),
    onSecondary = Color.White,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color.White,
    onSurfaceVariant = Color(0xFF202124),
    error = Color(0xFF9B0016),
    onError = Color.White
)

@AndroidEntryPoint
class ArrivalsActivity : ComponentActivity() {

    private val viewModel: ArrivalsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setContent {
            val state by viewModel.state.collectAsState()
            MaterialTheme(
                colorScheme = if (state.highContrast) highContrastArrivalsColors else defaultArrivalsColors
            ) {
                ArrivalsScreen(
                    state = state,
                    onRefresh = viewModel::refresh,
                    onConfig = {
                        startActivity(
                            Intent(this, ConfigActivity::class.java).apply {
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                            }
                        )
                    }
                )
                LaunchedEffect(appWidgetId) { viewModel.load(appWidgetId) }
            }
        }
    }
}

@Composable
fun ArrivalsScreen(state: ArrivalsUiState, onRefresh: () -> Unit, onConfig: () -> Unit) {
    // Reloj vivo (Oma actualiza el reloj cada 30s; en pantalla, cada segundo).
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    val clockFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val updatedFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val avisos = remember(state.stops) { state.stops.collectAvisos() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Tu Bondi", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = clockFmt.format(Date(now)),
                fontFamily = FontFamily.Monospace,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (state.lastUpdated > 0) {
                    val prefix = if (state.loading || state.error != null) "Última actualización" else "Actualizado"
                    "$prefix ${updatedFmt.format(Date(state.lastUpdated))}"
                } else "",
                style = MaterialTheme.typography.labelMedium
            )
            Row {
                OutlinedButton(
                    onClick = onConfig,
                    modifier = Modifier.padding(end = 8.dp).semantics {
                        contentDescription = "Configurar widget"
                    }
                ) {
                    Text(text = "⚙ Configurar")
                }
                Button(
                    onClick = onRefresh,
                    enabled = !state.loading,
                    modifier = Modifier.semantics { contentDescription = "Actualizar arribos" }
                ) {
                    Text(text = "↻ Actualizar")
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (state.loading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp).semantics {
                        contentDescription = "Actualizando arribos"
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Actualizando…")
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        state.error?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { contentDescription = "Error de actualización: $error" }
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onRefresh, enabled = !state.loading) {
                Text(text = "Reintentar")
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        when {
            state.stops.isEmpty() && !state.loading && state.error == null -> {
                Text(text = "Sin datos todavía")
            }
            state.stops.isNotEmpty() -> {
                LazyColumn {
                    state.stops.forEach { stop ->
                        item(key = "header-${stop.stop.code}") {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                            Text(text = stop.stop.name, fontWeight = FontWeight.Bold)
                            Text(text = stop.stop.code, style = MaterialTheme.typography.labelSmall)
                        }
                        items(stop.arrivals.size) { index ->
                            ArrivalRowFull(stop.arrivals[index])
                        }
                    }
                    if (avisos.isNotEmpty()) {
                        item(key = "avisos") {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                            Text(text = "Avisos", fontWeight = FontWeight.Bold)
                            avisos.forEach { aviso ->
                                Text(
                                    text = "⚠ $aviso",
                                    color = MaterialTheme.colorScheme.secondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArrivalRowFull(arrival: Arrival) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .padding(end = 8.dp)
                .size(10.dp)
                .background(colorDot(arrival.color), RoundedCornerShape(5.dp))
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = arrival.lineName, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = arrival.routeDescription ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = arrival.etaText.ifBlank { "—" },
                    fontWeight = FontWeight.Bold,
                    color = etaColor(arrival.etaMinutes)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = buildString {
                        arrival.distanceKm?.let { append("%.1f km".format(it)) }
                        arrival.operator?.let { if (isNotEmpty()) append(" · $it") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HtmlText.notice(arrival.notification)?.let { notif ->
                Text(
                    text = "⚠ $notif",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun colorDot(hex: String?): Color =
    try {
        Color(android.graphics.Color.parseColor(hex ?: "#ffb300"))
    } catch (ex: IllegalArgumentException) {
        Color(0xFFFFB300)
    }

/** Umbrales accesibles: ≤ 5 verde, ≤ 15 ámbar, resto onSurface. */
@Composable
private fun etaColor(etaMinutes: Int?): Color = when {
    etaMinutes == null -> MaterialTheme.colorScheme.onSurface
    etaMinutes <= 5 -> Color(0xFF1B5E20)
    etaMinutes <= 15 -> Color(0xFF8D4E00)
    else -> MaterialTheme.colorScheme.onSurface
}
