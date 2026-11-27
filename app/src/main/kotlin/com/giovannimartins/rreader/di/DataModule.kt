package com.giovannimartins.rreader.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.work.WorkManager
import com.giovannimartins.rreader.data.db.BookDao
import com.giovannimartins.rreader.data.db.RReaderDatabase
import com.giovannimartins.rreader.data.db.ReadingPositionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
    ): RReaderDatabase = Room.databaseBuilder(context, RReaderDatabase::class.java, RReaderDatabase.NAME).build()

    @Provides
    fun bookDao(database: RReaderDatabase): BookDao = database.bookDao()

    @Provides
    fun readingPositionDao(database: RReaderDatabase): ReadingPositionDao = database.readingPositionDao()

    @Provides
    fun workManager(
        @ApplicationContext context: Context,
    ): WorkManager = WorkManager.getInstance(context)

    @Provides
    fun clock(): Clock = Clock.systemUTC()

    @Provides
    @Singleton
    fun settingsDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }
}
