package com.watercantracker.app.di

import com.watercantracker.app.data.local.dao.MemberDao
import com.watercantracker.app.data.local.dao.PaymentDao
import com.watercantracker.app.data.repository.SettingsRepository
import com.watercantracker.app.sync.FirebaseSyncManager
import com.watercantracker.app.sync.QrCodeHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    @Provides @Singleton
    fun provideFirebaseSyncManager(
        memberDao: MemberDao,
        paymentDao: PaymentDao,
        settingsRepository: SettingsRepository
    ): FirebaseSyncManager = FirebaseSyncManager(memberDao, paymentDao, settingsRepository)

    @Provides @Singleton
    fun provideQrCodeHelper(): QrCodeHelper = QrCodeHelper()
}
