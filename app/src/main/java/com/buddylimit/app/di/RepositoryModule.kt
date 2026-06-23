package com.buddylimit.app.di

import com.buddylimit.app.data.AppRepository
import com.buddylimit.app.data.AppRepositoryImpl
import com.buddylimit.app.data.SettingsRepository
import com.buddylimit.app.data.SettingsRepositoryImpl
import com.buddylimit.app.data.UsageRepository
import com.buddylimit.app.data.UsageRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAppRepository(impl: AppRepositoryImpl): AppRepository

    @Binds
    @Singleton
    abstract fun bindUsageRepository(impl: UsageRepositoryImpl): UsageRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
