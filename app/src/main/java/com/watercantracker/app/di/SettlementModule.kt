package com.watercantracker.app.di

import com.watercantracker.app.data.local.dao.MemberDao
import com.watercantracker.app.data.local.dao.PaymentDao
import com.watercantracker.app.data.local.dao.SettlementDao
import com.watercantracker.app.data.repository.SettlementRepository
import com.watercantracker.app.settlement.SettlementCalculator
import com.watercantracker.app.settlement.pdf.SettlementPdfExporter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SettlementModule {

    @Provides
    @Singleton
    fun provideSettlementCalculator(): SettlementCalculator = SettlementCalculator()

    @Provides
    @Singleton
    fun provideSettlementPdfExporter(): SettlementPdfExporter = SettlementPdfExporter()

    @Provides
    @Singleton
    fun provideSettlementRepository(
        settlementDao: SettlementDao,
        memberDao: MemberDao,
        paymentDao: PaymentDao,
        calculator: SettlementCalculator
    ): SettlementRepository = SettlementRepository(settlementDao, memberDao, paymentDao, calculator)
}
