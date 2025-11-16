package com.tubondi.widget.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.tubondi.widget.domain.model.WidgetConfiguration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Maneja DataStore por instancia de widget.
 */
@Singleton
class WidgetPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {

    suspend fun saveConfiguration(config: WidgetConfiguration) {
        dataStore.edit { prefs ->
            prefs[keyFor(config.appWidgetId)] = json.encodeToString(config)
        }
    }

    suspend fun readConfiguration(appWidgetId: Int): WidgetConfiguration? {
        val prefs = dataStore.data.first()
        return prefs[keyFor(appWidgetId)]?.let { json.decodeFromString<WidgetConfiguration>(it) }
    }

    suspend fun removeConfiguration(appWidgetId: Int) {
        dataStore.edit { prefs -> prefs.remove(keyFor(appWidgetId)) }
    }

    suspend fun allConfigurations(): List<WidgetConfiguration> {
        val prefs = dataStore.data.first()
        return prefs.asMap()
            .filterKeys { it.name.startsWith(PREFIX) }
            .values
            .mapNotNull { it as? String }
            .map { json.decodeFromString<WidgetConfiguration>(it) }
    }

    private fun keyFor(appWidgetId: Int) = stringPreferencesKey("${PREFIX}${appWidgetId}")

    companion object {
        private const val PREFIX = "widget_config_"
    }
}