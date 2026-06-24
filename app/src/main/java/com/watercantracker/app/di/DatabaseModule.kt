package com.watercantracker.app.di

import android.content.Context
import com.watercantracker.app.data.local.WaterCanDatabase
import com.watercantracker.app.data.local.dao.MemberDao
import com.watercantracker.app.data.local.dao.NotificationDao
import com.watercantracker.app.data.local.dao.PaymentDao
import com.watercantracker.app.data.local.dao.SettingsDao
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
    fun provideDatabase(@ApplicationContext context: Context): WaterCanDatabase =
        WaterCanDatabase.getInstance(context)

    @Provides fun provideMemberDao(db: WaterCanDatabase): MemberDao = db.memberDao()
    @Provides fun providePaymentDao(db: WaterCanDatabase): PaymentDao = db.paymentDao()
    @Provides fun provideSettingsDao(db: WaterCanDatabase): SettingsDao = db.settingsDao()
    @Provides fun provideNotificationDao(db: WaterCanDatabase): NotificationDao = db.notificationDao()
}
