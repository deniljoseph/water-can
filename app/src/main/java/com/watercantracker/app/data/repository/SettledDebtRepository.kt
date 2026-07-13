package com.watercantracker.app.data.repository

import com.watercantracker.app.data.local.dao.SettledDebtDao
import com.watercantracker.app.data.local.entity.SettledDebtEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettledDebtRepository @Inject constructor(
    private val settledDebtDao: SettledDebtDao
) {
    fun observeAllSettledDebts(): Flow<List<SettledDebtEntity>> =
        settledDebtDao.observeAllSettledDebts()

    suspend fun recordSettlement(
        fromMemberId: Long, fromMemberName: String,
        toMemberId: Long, toMemberName: String,
        amount: Double
    ): Long = settledDebtDao.insertSettledDebt(
        SettledDebtEntity(
            fromMemberId   = fromMemberId,
            fromMemberName = fromMemberName,
            toMemberId     = toMemberId,
            toMemberName   = toMemberName,
            amount         = amount
        )
    )

    suspend fun getTotalPaidOut(memberId: Long): Double = settledDebtDao.getTotalPaidOut(memberId)
    suspend fun getTotalReceived(memberId: Long): Double = settledDebtDao.getTotalReceived(memberId)
}
