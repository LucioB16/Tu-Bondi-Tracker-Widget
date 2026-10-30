package com.tubondi.widget.widget

import android.content.Context
import android.content.Intent
import android.appwidget.AppWidgetManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.currentState
import androidx.glance.ButtonDefaults
import androidx.glance.action.clickable
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider as GlanceColorProvider
import com.tubondi.widget.domain.ArrivalFreshness
import com.tubondi.widget.domain.displayName
import com.tubondi.widget.domain.model.Arrival
import com.tubondi.widget.domain.model.StopArrivals
import com.tubondi.widget.ui.arrivals.ArrivalsActivity
import com.tubondi.widget.ui.theme.TuBondiPalette
import com.tubondi.widget.ui.theme.TuBondiSpacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** A size-aware arrival summary; every tap on its body opens the full arrivals screen. */
class TransitAppWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = SizeMode.Exact

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val arrivalsIntent = Intent(context, ArrivalsActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        provideContent {
            // Read persisted Glance state inside the composition so an update
            // during a live Glance session can render the latest worker result.
            val raw = currentState<Preferences>()[WidgetStateStorage.STATE_KEY]
            val state = remember(raw) {
                raw?.let { json.decodeFromString<WidgetUiState>(it) } ?: WidgetUiState()
            }
            GlanceTheme {
                WidgetContent(state = state, arrivalsIntent = arrivalsIntent)
            }
        }
    }
}

private data class WidgetColors(
    val background: GlanceColorProvider,
    val text: GlanceColorProvider,
    val muted: GlanceColorProvider,
    val primary: GlanceColorProvider,
    val onPrimary: GlanceColorProvider,
    val eta: GlanceColorProvider,
    val warning: GlanceColorProvider,
    val warningSurface: GlanceColorProvider
)

private fun widgetColors(highContrast: Boolean) = if (highContrast) {
    WidgetColors(
        background = pair(Color.White, Color.Black),
        text = pair(Color.Black, Color.White),
        muted = pair(Color(0xFF202124), Color.White),
        primary = pair(Color(0xFF003B4A), Color.Yellow),
        onPrimary = pair(Color.White, Color.Black),
        eta = pair(Color(0xFF005A36), Color(0xFF7CFFB0)),
        warning = pair(Color(0xFF6B3900), Color(0xFFFFD18E)),
        warningSurface = pair(Color(0xFFFFF0D6), Color(0xFF43331E))
    )
} else {
    WidgetColors(
        background = pair(TuBondiPalette.lightBackground, TuBondiPalette.darkBackground),
        text = pair(TuBondiPalette.lightText, TuBondiPalette.darkText),
        muted = pair(TuBondiPalette.lightMuted, TuBondiPalette.darkMuted),
        primary = pair(TuBondiPalette.lightPrimary, TuBondiPalette.darkPrimary),
        onPrimary = pair(TuBondiPalette.lightOnPrimary, TuBondiPalette.darkOnPrimary),
        eta = pair(TuBondiPalette.lightEta, TuBondiPalette.darkEta),
        warning = pair(TuBondiPalette.lightWarning, TuBondiPalette.darkWarning),
        warningSurface = pair(TuBondiPalette.lightWarningContainer, TuBondiPalette.darkWarningContainer)
    )
}

private fun pair(day: Color, night: Color) = ColorProvider(day = day, night = night)

internal fun widgetArrivalRowLimit(compact: Boolean, medium: Boolean, fontScale: Float): Int = when {
    compact -> 1
    medium -> if (fontScale >= 1.2f) 1 else 2
    fontScale >= 1.2f -> 2
    else -> 4
}

@Composable
private fun WidgetContent(state: WidgetUiState, arrivalsIntent: Intent) {
    val size: DpSize = LocalSize.current
    val fontScale = LocalContext.current.resources.configuration.fontScale
    val compact = size.width < 220.dp || size.height < 140.dp
    val medium = !compact && (size.width < 320.dp || size.height < 210.dp)
    val rowLimit = widgetArrivalRowLimit(compact, medium, fontScale)
    val groupLimit = if (compact) 1 else 2
    val now = System.currentTimeMillis()
    val colors = widgetColors(state.highContrast)
    val stale = ArrivalFreshness.isStale(state.lastUpdatedEpochMillis, now)
    val visibleGroups = state.prioritizedArrivals(rowLimit, groupLimit)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(colors.background)
            .clickable(actionStartActivity(arrivalsIntent))
            .semantics { contentDescription = "Abrir arribos de Tu Bondi" }
            .padding(if (compact) TuBondiSpacing.compact else TuBondiSpacing.standard)
    ) {
        HeaderRow(state, colors, compact, medium, fontScale, arrivalsIntent)
        if (state.arrivals.isEmpty()) {
            Spacer(GlanceModifier.height(6.dp))
            Text(
                text = when {
                    state.degraded -> "No hay datos actuales. Tocá para reintentar."
                    state.lastUpdatedEpochMillis == 0L -> "Tocá para ver arribos o configurar el widget."
                    else -> "Sin arribos para las paradas configuradas."
                },
                style = TextStyle(color = if (state.degraded) colors.warning else colors.muted, fontSize = 13.sp),
                maxLines = if (compact) 2 else 3
            )
            return@Column
        }

        if (state.degraded && !compact) {
            Text(
                text = "No pudimos actualizar; conservamos el último dato válido.",
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(colors.warningSurface)
                    .padding(horizontal = TuBondiSpacing.compact, vertical = 3.dp),
                style = TextStyle(color = colors.warning, fontSize = 12.sp),
                maxLines = 1
            )
        }
        if (stale) {
            Text(
                text = "Datos antiguos · ETA ocultas",
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(colors.warningSurface)
                    .padding(horizontal = TuBondiSpacing.compact, vertical = 3.dp),
                style = TextStyle(color = colors.warning, fontWeight = FontWeight.Bold, fontSize = 13.sp),
                maxLines = 1
            )
            return@Column
        }

        if (visibleGroups.isEmpty()) {
            Spacer(GlanceModifier.height(6.dp))
            Text(
                text = "Sin arribos para las paradas configuradas.",
                style = TextStyle(color = colors.muted, fontSize = 13.sp),
                maxLines = 2
            )
            if (state.arrivals.any(::hasNotice) && !compact) {
                Text(
                    text = "Avisos disponibles · ver en Arribos",
                    style = TextStyle(color = colors.warning, fontSize = 12.sp),
                    maxLines = 1
                )
            }
            return@Column
        }

        Spacer(GlanceModifier.height(if (compact || medium) 4.dp else 6.dp))
        visibleGroups.forEach { group ->
            Column(modifier = GlanceModifier.fillMaxWidth()) {
                StopHeader(group, colors)
                group.arrivals.forEach { arrival ->
                    ArrivalRow(
                        arrival = arrival,
                        lastUpdated = state.lastUpdatedEpochMillis,
                        now = now,
                        colors = colors,
                        compact = compact
                    )
                }
            }
        }
        if (state.arrivals.any(::hasNotice) && !stale && !compact && !medium) {
            Spacer(GlanceModifier.height(3.dp))
            Text(
                text = "⚠ Aviso en Arribos",
                style = TextStyle(color = colors.warning, fontSize = 12.sp),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun HeaderRow(
    state: WidgetUiState,
    colors: WidgetColors,
    compact: Boolean,
    medium: Boolean,
    fontScale: Float,
    arrivalsIntent: Intent
) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = if (fontScale >= 1.2f) {
                    "Próximos arribos"
                } else {
                    context.getString(com.tubondi.widget.R.string.widget_header)
                },
                modifier = GlanceModifier
                    .clickable(actionStartActivity(arrivalsIntent))
                    .semantics { contentDescription = "Abrir arribos de Tu Bondi" },
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    color = colors.text,
                    fontSize = when {
                        compact -> 14.sp
                        medium -> 15.sp
                        else -> 16.sp
                    }
                ),
                maxLines = 1
            )
            val lastUpdated = state.lastUpdatedEpochMillis
            if (lastUpdated > 0L) {
                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(lastUpdated))
                val age = ArrivalFreshness.ageLabel(lastUpdated, System.currentTimeMillis()).orEmpty()
                Text(
                    text = if (fontScale >= 1.2f) "$time · $age" else "Actualizado $time · $age",
                    style = TextStyle(color = colors.muted, fontSize = 12.sp),
                    maxLines = 1
                )
            } else {
                Text(
                    text = if (fontScale >= 1.2f) "Sin datos todavía" else "Esperando el primer dato",
                    style = TextStyle(color = colors.muted, fontSize = 12.sp),
                    maxLines = 1
                )
            }
            if (state.degraded && compact) {
                Text(
                    text = "Actualización fallida",
                    modifier = GlanceModifier.background(colors.warningSurface).padding(horizontal = 6.dp, vertical = 2.dp),
                    style = TextStyle(color = colors.warning, fontSize = 12.sp),
                    maxLines = 1
                )
            }
        }
        if (!compact) {
            androidx.glance.Button(
                text = "Actualizar",
                modifier = GlanceModifier.semantics { contentDescription = "Actualizar arribos" },
                onClick = actionRunCallback<com.tubondi.widget.widget.RefreshActionCallback>(),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = colors.primary,
                    contentColor = colors.onPrimary
                )
            )
        }
    }
}

@Composable
private fun StopHeader(group: StopArrivals, colors: WidgetColors) {
    Text(
        text = group.stop.displayName(),
        style = TextStyle(
            fontWeight = FontWeight.Bold,
            color = colors.primary,
            fontSize = 12.sp
        ),
        maxLines = 1
    )
}

@Composable
private fun ArrivalRow(
    arrival: Arrival,
    lastUpdated: Long,
    now: Long,
    colors: WidgetColors,
    compact: Boolean
) {
    val eta = ArrivalFreshness.etaLabel(arrival, lastUpdated, now)
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = arrival.lineName,
                style = TextStyle(fontWeight = FontWeight.Bold, color = colors.text, fontSize = 14.sp),
                maxLines = 1
            )
            arrival.routeDescription?.takeIf { it.isNotBlank() }?.let { destination ->
                Text(
                    text = destination,
                    style = TextStyle(color = colors.muted, fontSize = 12.sp),
                    maxLines = 1
                )
            }
        }
        Spacer(GlanceModifier.width(6.dp))
        Text(
            text = eta,
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                color = if (eta == "Estimación vencida") colors.warning else colors.eta,
                fontSize = when {
                    compact -> 19.sp
                    else -> 16.sp
                }
            ),
            maxLines = 1
        )
    }
}

private fun hasNotice(group: StopArrivals): Boolean =
    !group.backendMessage.isNullOrBlank() || group.arrivals.any { !it.notification.isNullOrBlank() }
