package com.tubondi.widget.widget

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.preferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.PreferencesGlanceStateDefinition
import androidx.glance.appwidget.GlanceId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persiste el estado renderizable del widget.
 */
object WidgetStateStorage {
    private val json = Json { ignoreUnknownKeys = true }
    val STATE_KEY = preferencesKey<String>("widget_state")

    suspend fun writeState(context: Context, glanceId: GlanceId, state: WidgetUiState) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs[STATE_KEY] = json.encodeToString(state)
        }
    }

    suspend fun readState(context: Context, glanceId: GlanceId): WidgetUiState {
        val prefs = androidx.glance.appwidget.state.getAppWidgetState(
            context,
            PreferencesGlanceStateDefinition,
            glanceId
        )
        val raw = prefs[STATE_KEY]
        return raw?.let { json.decodeFromString<WidgetUiState>(it) } ?: WidgetUiState()
    }
}