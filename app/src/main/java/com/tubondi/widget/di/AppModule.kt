package com.tubondi.widget.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.tubondi.widget.data.remote.TuBondiHttpClient
import com.tubondi.widget.data.repo.TransitRepository
import com.tubondi.widget.prefs.WidgetPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "widget_prefs")

/**
 * Módulo de dependencias generales.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideHttpClient(): TuBondiHttpClient = TuBondiHttpClient()

    @Provides
    @Singleton
    fun provideRepository(client: TuBondiHttpClient): TransitRepository = TransitRepository(client)

    @Provides
    @Singleton
    fun providePrefs(@ApplicationContext context: Context): WidgetPreferences = WidgetPreferences(context.dataStore)

    @Provides
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}