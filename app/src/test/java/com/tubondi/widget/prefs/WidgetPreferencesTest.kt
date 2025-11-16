package com.tubondi.widget.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import com.tubondi.widget.domain.model.Line
import com.tubondi.widget.domain.model.Stop
import com.tubondi.widget.domain.model.StopSelection
import com.tubondi.widget.domain.model.WidgetConfiguration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetPreferencesTest {
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var prefs: WidgetPreferences

    @Before
    fun setUp() {
        val dispatcher = UnconfinedTestDispatcher()
        val testScope = TestScope(dispatcher)
        val file = File.createTempFile("prefs", ".preferences-test")
        dataStore = PreferenceDataStoreFactory.create(scope = testScope) {
            file
        }
        prefs = WidgetPreferences(dataStore)
    }

    @Test
    fun savesAndReadsConfigurationPerWidget() = runTest {
        val config = WidgetConfiguration(
            appWidgetId = 42,
            selections = listOf(
                StopSelection(
                    stop = Stop(code = "10", name = "Test", lines = listOf(Line(1, "1"))),
                    selectedLines = listOf(1)
                )
            ),
            refreshIntervalMinutes = 15,
            nearThresholdMinutes = 3,
            notificationsEnabled = true
        )

        prefs.saveConfiguration(config)
        val stored = prefs.readConfiguration(42)

        assertThat(stored).isEqualTo(config)
    }
}