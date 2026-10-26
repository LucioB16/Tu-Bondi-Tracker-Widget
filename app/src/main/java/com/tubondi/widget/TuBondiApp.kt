package com.tubondi.widget

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Punto de entrada de Hilt para toda la app.
 * Provee el HiltWorkerFactory a WorkManager para que los @HiltWorker
 * (RefreshWorker) se instancien con sus dependencias inyectadas.
 */
@HiltAndroidApp
class TuBondiApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
