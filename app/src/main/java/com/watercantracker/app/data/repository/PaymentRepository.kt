package com.watercantracker.app.data.repository

import com.watercantracker.app.data.local.dao.PaymentDao
import com.watercantracker.app.data.local.entity.MemberEntity
import com.watercantracker.app.data.local.entity.PaymentEntity
import com.watercantracker.app.domain.model.MemberBalance
import com.watercantracker.app.domain.model.MemberStats
import com.watercantracker.app.domain.model.MonthlySpendingSummary
import com.watercantracker.app.domain.model.PriceTrendPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    fun observeTotalGroupSpend(): Flow<Double> = paymentDao.observeTotalGroupSpend()

    suspend fun getLastPayment(): PaymentEntity? = paymentDao.getLastPayment()

    /**
     * Computes running balance for every active member.
     *
     * fairShare = totalGroupSpend / activeMembers
     * netBalance = totalPaid - fairShare
     *
     * [pricePerCan] is used only if > 0 to calculate the "expected" cost per can
     * when some payments were partial (amount < quantity × pricePerCan).
     * The fair-share calculation always uses ACTUAL money paid, not theoretical cost,
     * so it works correctly even when the price per can varies over time.
     */
    fun observeMemberBalances(
        activeMembers: List<MemberEntity>
    ): Flow<List<MemberBalance>> = combine(
        paymentDao.observeTotalGroupSpend(),
        paymentDao.observeMemberStats()
    ) { totalSpend, stats ->
        val count = activeMembers.size.coerceAtLeast(1)
        val fairShare = totalSpend / count

        activeMembers.map { member ->
            val paid = stats.firstOrNull { it.memberId == member.id }
                ?.totalAmountContributed ?: 0.0
            MemberBalance(
                memberId    = member.id,
                memberName  = member.name,
                avatarUri   = member.avatarUri,
                isActive    = member.isActive,
                totalPaid   = paid,
                fairShare   = fairShare,
                netBalance  = paid - fairShare
            )
        }.sortedBy { it.netBalance } // most-owed first
    }

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
            quantity              = quantity,
            amount                = amount,
            paidByMemberId        = paidByMemberId,
            paidByNameSnapshot    = paidByNameSnapshot,
            purchaseDate          = purchaseDate,
            notes                 = notes?.trim()?.takeIf { it.isNotEmpty() },
            vendorName            = vendorName?.trim()?.takeIf { it.isNotEmpty() },
            receiptImageUri       = receiptImageUri
        )
    )

    suspend fun updatePayment(payment: PaymentEntity) =
        paymentDao.updatePayment(payment.copy(updatedAt = System.currentTimeMillis()))

    suspend fun deletePayment(payment: PaymentEntity) = paymentDao.deletePayment(payment)
    suspend fun getPaymentById(id: Long): PaymentEntity? = paymentDao.getPaymentById(id)

    private fun currentMonthRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        return start to cal.timeInMillis
    }
}
