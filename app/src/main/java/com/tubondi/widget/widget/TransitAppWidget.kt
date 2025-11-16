package com.tubondi.widget.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.actionRunCallback
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceId
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.state.PreferencesGlanceStateDefinition
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.defaultWeight
import androidx.glance.layout.clickable
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.text.FontWeight
import com.tubondi.widget.R
import com.tubondi.widget.domain.model.StopArrivals
import com.tubondi.widget.ui.config.ConfigActivity
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Glance App Widget que construye el tablero de arribos.
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
        val intent = Intent(context, ConfigActivity::class.java).apply {
            putExtra("appWidgetId", appWidgetId)
        }
        provideContent {
            GlanceTheme {
                WidgetContent(state = state, configIntent = intent)
            }
        }
    }
}

@Composable
private fun WidgetContent(state: WidgetUiState, configIntent: Intent) {
    val background = if (state.highContrast) {
        ColorProvider(day = 0xFF000000L, night = 0xFF000000L)
    } else {
        ColorProvider(day = 0xFF1F1B24L, night = 0xFF1F1B24L)
    }
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(background)
            .padding(12.dp)
    ) {
        HeaderRow(state, configIntent)
        Spacer(modifier = GlanceModifier.height(8.dp))
        if (state.arrivals.isEmpty()) {
            Text(
                text = state.errorMessage ?: "Sin datos",
                style = TextStyle(color = ColorProvider(0xFFFFFFFFL))
            )
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxWidth()) {
                state.arrivals.forEach { stopArrivals ->
                    item { StopHeader(stopArrivals) }
                    items(stopArrivals.arrivals) { arrival ->
                        Row(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = arrival.lineName,
                                style = TextStyle(fontWeight = FontWeight.Bold, color = ColorProvider(0xFFFFFFFFL)),
                                modifier = GlanceModifier.defaultWeight()
                            )
                            Text(
                                text = "${arrival.etaMinutes} min",
                                style = TextStyle(color = ColorProvider(0xFFE0E0E0L))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderRow(state: WidgetUiState, configIntent: Intent) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(actionStartActivity(configIntent)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = context.getString(R.string.widget_header),
                style = TextStyle(fontWeight = FontWeight.Bold, color = ColorProvider(0xFFFFFFFFL))
            )
            if (state.lastUpdatedEpochMillis > 0) {
                val formatted = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(state.lastUpdatedEpochMillis))
                Text(text = "Actualizado $formatted", style = TextStyle(color = ColorProvider(0xFFB0BEC5L)))
            }
        }
        androidx.glance.Button(
            text = context.getString(R.string.action_refresh),
            onClick = actionRunCallback<RefreshActionCallback>()
        )
    }
}

@Composable
private fun StopHeader(stopArrivals: StopArrivals) {
    Text(
        text = stopArrivals.stop.name,
        style = TextStyle(fontWeight = FontWeight.Bold, color = ColorProvider(0xFFBBDEFBL)),
        modifier = GlanceModifier.padding(top = 8.dp)
    )
    stopArrivals.backendMessage?.let {
        Text(text = it, style = TextStyle(color = ColorProvider(0xFFFFAB91L)))
    }
}