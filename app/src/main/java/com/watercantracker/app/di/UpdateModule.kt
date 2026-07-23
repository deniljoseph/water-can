package com.watercantracker.app.di

import com.watercantracker.app.update.UpdateChecker
import com.watercantracker.app.update.UpdateDownloader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UpdateModule {

    @Provides @Singleton
    fun provideUpdateChecker(): UpdateChecker = UpdateChecker()

    @Provides @Singleton
    fun provideUpdateDownloader(): UpdateDownloader = UpdateDownloader()
}
