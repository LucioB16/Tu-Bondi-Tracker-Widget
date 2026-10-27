package com.tubondi.widget.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.text.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider
import com.tubondi.widget.R
import com.tubondi.widget.domain.HtmlText
import com.tubondi.widget.domain.model.Arrival
import com.tubondi.widget.domain.model.StopArrivals
import com.tubondi.widget.ui.arrivals.ArrivalsActivity
import com.tubondi.widget.ui.config.ConfigActivity
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Glance App Widget que construye el tablero de arribos (RF-3/RF-11/RF-12):
 * fila de arribo con color de línea, destino, distancia y avisos del
 * backend; tap -> pantalla completa (D1), ⚙ -> configuración.
 *
 * Colores: fábrica day/night de androidx.glance.color - el ColorProvider fijo (unit) crashea en RemoteViews
 */
class TransitAppWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = androidx.glance.appwidget.state.getAppWidgetState(
            context,
            PreferencesGlanceStateDefinition,
            id
        )
        val raw = prefs[WidgetStateStorage.STATE_KEY]
        val state = raw?.let { json.decodeFromString<WidgetUiState>(it) } ?: WidgetUiState()
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val configIntent = Intent(context, ConfigActivity::class.java).apply {
            putExtra("appWidgetId", appWidgetId)
        }
        val arrivalsIntent = Intent(context, ArrivalsActivity::class.java).apply {
            putExtra("appWidgetId", appWidgetId)
        }
        provideContent {
            GlanceTheme {
                WidgetContent(state = state, configIntent = configIntent, arrivalsIntent = arrivalsIntent)
            }
        }
    }
}

private fun background(highContrast: Boolean) =
    if (highContrast) {
        ColorProvider(day = Color(0xFF000000), night = Color(0xFF000000))
    } else {
        ColorProvider(day = Color(0xFF1F1B24), night = Color(0xFF1F1B24))
    }

private fun onBackground() = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFFFFFFFF))

private fun muted() = ColorProvider(day = Color(0xFFB0BEC5), night = Color(0xFFB0BEC5))

private fun warn() = ColorProvider(day = Color(0xFFFFAB91), night = Color(0xFFFFAB91))

private fun stopHeaderColor() = ColorProvider(day = Color(0xFFBBDEFB), night = Color(0xFFBBDEFB))

 /** Umbrales de color de Oma (widget.owl:63): ≤ 5 verde, ≤ 15 ámbar, resto neutro. */
private fun etaColor(etaMinutes: Int?) = when {
    etaMinutes == null -> ColorProvider(day = Color(0xFFE0E0E0), night = Color(0xFFE0E0E0))
    etaMinutes <= 5 -> ColorProvider(day = Color(0xFF4CAF50), night = Color(0xFF4CAF50))
    etaMinutes <= 15 -> ColorProvider(day = Color(0xFFFFC107), night = Color(0xFFFFC107))
    else -> ColorProvider(day = Color(0xFFE0E0E0), night = Color(0xFFE0E0E0))
}

private fun colorDot(hex: String?) = try {
    ColorProvider(day = Color(android.graphics.Color.parseColor(hex ?: "#ffb300")), night = Color(android.graphics.Color.parseColor(hex ?: "#ffb300")))
} catch (ex: IllegalArgumentException) {
    ColorProvider(day = Color(0xFFFFB300), night = Color(0xFFFFB300))
}

@Composable
private fun WidgetContent(state: WidgetUiState, configIntent: Intent, arrivalsIntent: Intent) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(background(state.highContrast))
            .padding(12.dp)
    ) {
        HeaderRow(state, configIntent, arrivalsIntent)
        Spacer(modifier = GlanceModifier.height(8.dp))
        when {
            state.arrivals.isEmpty() -> {
                Text(
                    text = state.errorMessage ?: "Sin datos",
                    style = TextStyle(color = if (state.degraded) muted() else onBackground())
                )
            }
            else -> {
                if (state.degraded) {
                    Text(
        text = "⚠ ${state.errorMessage ?: "?ltimo refresh falló"}",
                        style = TextStyle(color = warn(), fontSize = 12.sp),
                        maxLines = 1
                    )
                }
                LazyColumn(modifier = GlanceModifier.fillMaxWidth()) {
                    state.arrivals.forEach { stopArrivals ->
                        item { StopHeader(stopArrivals) }
                        items(stopArrivals.arrivals) { arrival ->
                            ArrivalRow(arrival)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderRow(state: WidgetUiState, configIntent: Intent, arrivalsIntent: Intent) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(actionStartActivity(arrivalsIntent)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = context.getString(R.string.widget_header),
                style = TextStyle(fontWeight = FontWeight.Bold, color = onBackground())
            )
            if (state.lastUpdatedEpochMillis > 0) {
                val formatted = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(state.lastUpdatedEpochMillis))
                val label = if (state.degraded) {
                    "última $formatted"
                } else {
                    "Actualizado $formatted"
                }
                Text(text = label, style = TextStyle(color = muted(), fontSize = 12.sp), maxLines = 1)
            }
        }
        androidx.glance.Button(
        text = "⚙",
            modifier = GlanceModifier.semantics { contentDescription = "Configurar widget" },
            onClick = actionStartActivity(configIntent)
        )
        androidx.glance.Button(
            text = context.getString(R.string.action_refresh),
            modifier = GlanceModifier.semantics { contentDescription = "Actualizar arribos" },
            onClick = actionRunCallback<RefreshActionCallback>()
        )
    }
}

@Composable
private fun StopHeader(stopArrivals: StopArrivals) {
    Text(
        text = stopArrivals.stop.name,
        style = TextStyle(fontWeight = FontWeight.Bold, color = stopHeaderColor()),
        modifier = GlanceModifier.padding(top = 8.dp)
    )
    stopArrivals.backendMessage?.let {
        Text(
        text = "⚠ ${HtmlText.stripEntities(it)}",
            style = TextStyle(color = warn(), fontSize = 12.sp),
            maxLines = 2
        )
    }
}

/**
 * Fila de arribo enriquecida (RF-3/RF-11): dot de color de línea,
 * destino, distancia, aviso por arribo y ETA coloreada por umbral
 * (≤ 5 verde, ≤ 15 ámbar, resto neutro — paridad Oma widget.owl:63).
 */
@Composable
private fun ArrivalRow(arrival: Arrival) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .size(10.dp)
                .background(colorDot(arrival.color))
        ) {}
        Spacer(modifier = GlanceModifier.width(6.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = arrival.lineName,
                    style = TextStyle(fontWeight = FontWeight.Bold, color = onBackground())
                )
                arrival.distanceKm?.let { dist ->
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Text(text = "%.1f km".format(dist), style = TextStyle(color = muted(), fontSize = 12.sp), maxLines = 1)
                }
            }
            arrival.routeDescription?.takeIf { it.isNotBlank() }?.let { dest ->
                Text(text = dest, style = TextStyle(color = muted(), fontSize = 12.sp), maxLines = 1)
            }
            arrival.notification?.takeIf { it.isNotBlank() }?.let { notif ->
                Text(
        text = "⚠ ${HtmlText.stripEntities(notif)}",
                    style = TextStyle(color = warn(), fontSize = 12.sp),
                    maxLines = 2
                )
            }
        }
        Text(
        text = arrival.etaText.ifBlank { "—" },
            style = TextStyle(fontWeight = FontWeight.Bold, color = etaColor(arrival.etaMinutes))
        )
    }
}
