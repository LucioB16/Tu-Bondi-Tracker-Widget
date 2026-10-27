package com.tubondi.widget.work

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.tubondi.widget.data.repo.TransitRepository
import com.tubondi.widget.di.dataStore
import com.tubondi.widget.domain.friendlyErrorMessage
import com.tubondi.widget.domain.model.WidgetConfiguration
import com.tubondi.widget.prefs.WidgetPreferences
import com.tubondi.widget.widget.TransitAppWidget
import com.tubondi.widget.widget.WidgetStateStorage
import com.tubondi.widget.widget.WidgetUiState
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.hilt.work.HiltWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Refresca datos y coordina notificaciones en segundo plano.
 * Ante error retiene el último dato válido (SA-8) y purga configs de
 * widgets eliminados (SA-9, self-heal).
 */
@HiltWorker
class RefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val prefs: WidgetPreferences,
    private val repository: TransitRepository,
    private val notifier: ArrivalNotifier
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val widgetId = inputData.getInt(KEY_WIDGET_ID, ALL_WIDGETS)
        val configs = if (widgetId == ALL_WIDGETS) {
            prefs.allConfigurations()
        } else {
            prefs.readConfiguration(widgetId)?.let { listOf(it) } ?: emptyList()
        }
        if (configs.isEmpty()) {
            return Result.success()
        }
        val manager = GlanceAppWidgetManager(applicationContext)
        configs.forEach { config ->
            val glanceId = manager.getGlanceIds(TransitAppWidget::class.java)
                .firstOrNull { manager.getAppWidgetId(it) == config.appWidgetId }
            if (glanceId == null) {
                // SA-9: el widget ya no existe -> purgar su config (self-heal).
                purgeConfig(config.appWidgetId)
                return@forEach
            }
            try {
                val arrivals = repository.fetchArrivalsForConfig(config)
                val state = WidgetUiState(
                    arrivals = arrivals,
                    lastUpdatedEpochMillis = System.currentTimeMillis(),
                    errorMessage = null,
                    highContrast = config.highContrast
                )
                WidgetStateStorage.writeState(applicationContext, glanceId, state)
                TransitAppWidget().update(applicationContext, glanceId)
                notifier.dispatch(config, arrivals)
            } catch (ex: Exception) {
 // SA-8: retener el último dato válido; el error se anota sin
 // borrar los arribos previos. D5: mensaje genérico para 408 nocturno.
                val previous = WidgetStateStorage.readState(applicationContext, glanceId)
                val state = WidgetUiState.errorState(previous, friendlyErrorMessage(ex), config.highContrast)
                WidgetStateStorage.writeState(applicationContext, glanceId, state)
                TransitAppWidget().update(applicationContext, glanceId)
            }
        }
        return Result.success()
    }

    private fun purgeConfig(appWidgetId: Int) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            prefs.removeConfiguration(appWidgetId)
        }
    }

    companion object {
        private const val KEY_WIDGET_ID = "widget_id"
        private const val ALL_WIDGETS = -1
        private const val UNIQUE_WORK = "transit_periodic"

        fun enqueuePeriodic(context: Context, config: WidgetConfiguration) {
            val interval = config.refreshIntervalMinutes.coerceAtLeast(15)
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(interval.toLong(), TimeUnit.MINUTES)
                .setInputData(workDataOf(KEY_WIDGET_ID to config.appWidgetId))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "$UNIQUE_WORK-${config.appWidgetId}",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun enqueueOneTime(context: Context, appWidgetId: Int) {
            val request = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setInputData(workDataOf(KEY_WIDGET_ID to appWidgetId))
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }

        fun enqueueAll(context: Context) {
            val request = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setInputData(workDataOf(KEY_WIDGET_ID to ALL_WIDGETS))
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }

        /**
 * SA-9: al eliminar un widget se cancela su work periódico y se
         * purga su config de DataStore (fire-and-forget en IO).
         */
        fun cleanup(context: Context, appWidgetIds: IntArray) {
            val wm = WorkManager.getInstance(context)
            val appContext = context.applicationContext
            appWidgetIds.forEach { id ->
                wm.cancelUniqueWork("$UNIQUE_WORK-$id")
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    WidgetPreferences(appContext.dataStore).removeConfiguration(id)
                }
            }
        }
    }
}
