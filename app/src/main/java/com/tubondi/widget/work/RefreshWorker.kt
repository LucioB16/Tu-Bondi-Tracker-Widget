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
import com.tubondi.widget.domain.model.WidgetConfiguration
import com.tubondi.widget.prefs.WidgetPreferences
import com.tubondi.widget.widget.TransitAppWidget
import com.tubondi.widget.widget.WidgetStateStorage
import com.tubondi.widget.widget.WidgetUiState
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.work.HiltWorker
import java.util.concurrent.TimeUnit

/**
 * Refresca datos y coordina notificaciones en segundo plano.
 */
@HiltWorker
class RefreshWorker @AssistedInject constructor(
    @Assisted @ApplicationContext context: Context,
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
            try {
                val arrivals = repository.fetchArrivalsForConfig(config)
                val glanceId = manager.getGlanceIds(TransitAppWidget::class.java)
                    .firstOrNull { manager.getAppWidgetId(it) == config.appWidgetId }
                if (glanceId != null) {
                    val state = WidgetUiState(
                        arrivals = arrivals,
                        lastUpdatedEpochMillis = System.currentTimeMillis(),
                        errorMessage = null,
                        highContrast = config.highContrast
                    )
                    WidgetStateStorage.writeState(applicationContext, glanceId, state)
                    TransitAppWidget().update(applicationContext, glanceId)
                }
                notifier.dispatch(config, arrivals)
            } catch (ex: Exception) {
                val glanceId = manager.getGlanceIds(TransitAppWidget::class.java)
                    .firstOrNull { manager.getAppWidgetId(it) == config.appWidgetId }
                if (glanceId != null) {
                    val state = WidgetUiState(
                        arrivals = emptyList(),
                        lastUpdatedEpochMillis = System.currentTimeMillis(),
                        errorMessage = ex.message,
                        highContrast = config.highContrast
                    )
                    WidgetStateStorage.writeState(applicationContext, glanceId, state)
                    TransitAppWidget().update(applicationContext, glanceId)
                }
            }
        }
        return Result.success()
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
    }
}