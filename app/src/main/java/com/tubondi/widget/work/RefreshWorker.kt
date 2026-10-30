package com.tubondi.widget.work

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
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
import com.tubondi.widget.domain.model.MAX_REFRESH_INTERVAL_MINUTES
import com.tubondi.widget.domain.model.MIN_REFRESH_INTERVAL_MINUTES
import com.tubondi.widget.domain.model.WidgetConfiguration
import com.tubondi.widget.prefs.WidgetPreferences
import com.tubondi.widget.widget.TransitAppWidget
import com.tubondi.widget.widget.WidgetStateStorage
import com.tubondi.widget.widget.WidgetUiState
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.hilt.work.HiltWorker
import kotlinx.coroutines.CancellationException
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
        val widgetId = inputData.getInt(KEY_WIDGET_ID, INVALID_WIDGET)
        if (widgetId == INVALID_WIDGET) return Result.success()
        val config = prefs.readConfiguration(widgetId) ?: return Result.success()
        val manager = GlanceAppWidgetManager(applicationContext)
        val glanceId = manager.getGlanceIds(TransitAppWidget::class.java)
            .firstOrNull { manager.getAppWidgetId(it) == widgetId }
        if (glanceId == null) {
            // SA-9: el widget ya no existe -> purgar su config (self-heal).
            purgeConfig(widgetId)
            return Result.success()
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
            TransitAppWidget().updateAll(applicationContext)
            notifier.dispatch(config, arrivals)
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            // SA-8: retener el último dato válido; el error se anota sin
            // borrar los arribos previos. D5: mensaje genérico para 408 nocturno.
            val previous = WidgetStateStorage.readState(applicationContext, glanceId)
            val state = WidgetUiState.errorState(previous, friendlyErrorMessage(ex), config.highContrast)
            WidgetStateStorage.writeState(applicationContext, glanceId, state)
            // A host can keep showing its previous RemoteViews after the
            // failed background action. Re-render all instances from their
            // persisted states so the error reaches the launcher as well.
            TransitAppWidget().updateAll(applicationContext)
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
        private const val INVALID_WIDGET = -1
        private const val UNIQUE_WORK = "transit_periodic"
        private const val UNIQUE_AGE = "transit_age"

        private fun widgetWorkTag(appWidgetId: Int) = "transit_widget_$appWidgetId"
        private fun widgetAgeWorkTag(appWidgetId: Int) = "transit_age_widget_$appWidgetId"

        fun enqueuePeriodic(context: Context, config: WidgetConfiguration) {
            val interval = config.refreshIntervalMinutes.coerceIn(
                MIN_REFRESH_INTERVAL_MINUTES,
                MAX_REFRESH_INTERVAL_MINUTES
            )
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(interval.toLong(), TimeUnit.MINUTES)
                .setInputData(workDataOf(KEY_WIDGET_ID to config.appWidgetId))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(widgetWorkTag(config.appWidgetId))
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "$UNIQUE_WORK-${config.appWidgetId}",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )

            val ageRequest = PeriodicWorkRequestBuilder<WidgetAgeWorker>(
                MIN_REFRESH_INTERVAL_MINUTES.toLong(),
                TimeUnit.MINUTES
            )
                .setInputData(workDataOf(WidgetAgeWorker.KEY_WIDGET_ID to config.appWidgetId))
                .addTag(widgetAgeWorkTag(config.appWidgetId))
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "$UNIQUE_AGE-${config.appWidgetId}",
                ExistingPeriodicWorkPolicy.KEEP,
                ageRequest
            )
        }

        fun enqueueOneTime(context: Context, appWidgetId: Int) {
            val request = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setInputData(workDataOf(KEY_WIDGET_ID to appWidgetId))
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag(widgetWorkTag(appWidgetId))
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }

        /**
         * SA-9: cancela trabajo acotado a cada ID y purga su configuración.
         */
        fun cleanup(context: Context, appWidgetIds: IntArray) {
            val wm = WorkManager.getInstance(context)
            val appContext = context.applicationContext
            appWidgetIds.forEach { id ->
                val cancelPeriodic = wm.cancelUniqueWork("$UNIQUE_WORK-$id")
                val cancelAge = wm.cancelUniqueWork("$UNIQUE_AGE-$id")
                val cancelTagged = wm.cancelAllWorkByTag(widgetWorkTag(id))
                val cancelAgeTagged = wm.cancelAllWorkByTag(widgetAgeWorkTag(id))
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    runCatching { cancelPeriodic.result.get(10, TimeUnit.SECONDS) }
                    runCatching { cancelAge.result.get(10, TimeUnit.SECONDS) }
                    runCatching { cancelTagged.result.get(10, TimeUnit.SECONDS) }
                    runCatching { cancelAgeTagged.result.get(10, TimeUnit.SECONDS) }
                    WidgetPreferences(appContext.dataStore).removeConfiguration(id)
                }
            }
        }
    }
}
