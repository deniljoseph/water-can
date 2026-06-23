package com.watercantracker.app.data.repository

import com.watercantracker.app.data.local.dao.PaymentDao
import com.watercantracker.app.data.local.entity.PaymentEntity
import com.watercantracker.app.domain.model.MemberStats
import com.watercantracker.app.domain.model.MonthlySpendingSummary
import com.watercantracker.app.domain.model.PriceTrendPoint
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PaymentRepository @Inject constructor(
    private val paymentDao: PaymentDao
) {
    fun observeAllPayments(): Flow<List<PaymentEntity>> = paymentDao.observeAllPayments()

    fun observeFilteredPayments(
        memberId: Long? = null,
        startDate: Long? = null,
        endDate: Long? = null,
        query: String = ""
    ): Flow<List<PaymentEntity>> =
        paymentDao.observeFilteredPayments(memberId, startDate, endDate, query)

    fun observeLastPayment(): Flow<PaymentEntity?> = paymentDao.observeLastPayment()

    fun observeCurrentMonthSummary(): Flow<MonthlySpendingSummary?> {
        val (start, end) = currentMonthRange()
        return paymentDao.observeMonthlySummary(start, end)
    }

    fun observeAllMonthlySummaries(): Flow<List<MonthlySpendingSummary>> =
        paymentDao.observeAllMonthlySummaries()

    fun observeMemberStats(): Flow<List<MemberStats>> = paymentDao.observeMemberStats()

    fun observePriceTrend(): Flow<List<PriceTrendPoint>> = paymentDao.observePriceTrend()

    suspend fun getLastPayment(): PaymentEntity? = paymentDao.getLastPayment()

    suspend fun addPayment(
        quantity: Int,
        amount: Double,
        paidByMemberId: Long,
        paidByNameSnapshot: String,
        purchaseDate: Long,
        notes: String?,
        vendorName: String?,
        receiptImageUri: String?
    ): Long = paymentDao.insertPayment(
        PaymentEntity(
            quantity = quantity,
            amount = amount,
            paidByMemberId = paidByMemberId,
            paidByNameSnapshot = paidByNameSnapshot,
            purchaseDate = purchaseDate,
            notes = notes?.trim()?.takeIf { it.isNotEmpty() },
            vendorName = vendorName?.trim()?.takeIf { it.isNotEmpty() },
            receiptImageUri = receiptImageUri
        )
    )

    suspend fun updatePayment(payment: PaymentEntity) = paymentDao.updatePayment(
        payment.copy(updatedAt = System.currentTimeMillis())
    )

    suspend fun deletePayment(payment: PaymentEntity) = paymentDao.deletePayment(payment)

    suspend fun getPaymentById(id: Long): PaymentEntity? = paymentDao.getPaymentById(id)

    private fun currentMonthRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        return start to cal.timeInMillis
    }
}
