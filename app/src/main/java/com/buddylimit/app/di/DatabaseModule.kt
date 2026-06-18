package com.buddylimit.app.di

import android.content.Context
import androidx.room.Room
import com.buddylimit.app.data.local.AppDatabase
import com.buddylimit.app.data.local.MonitoredAppDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "buddylimit.db").build()

    @Provides
    fun provideMonitoredAppDao(db: AppDatabase): MonitoredAppDao = db.monitoredAppDao()
}
