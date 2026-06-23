package com.watercantracker.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.watercantracker.app.data.local.entity.PaymentEntity
import com.watercantracker.app.domain.model.MemberStats
import com.watercantracker.app.domain.model.MonthlySpendingSummary
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentDao {

    @Query("SELECT * FROM payments ORDER BY purchaseDate DESC")
    fun observeAllPayments(): Flow<List<PaymentEntity>>

    @Query(
        """
        SELECT * FROM payments
        WHERE (:memberId IS NULL OR paidByMemberId = :memberId)
        AND (:startDate IS NULL OR purchaseDate >= :startDate)
        AND (:endDate IS NULL OR purchaseDate <= :endDate)
        AND (
            :query = '' 
            OR paidByNameSnapshot LIKE '%' || :query || '%'
            OR vendorName LIKE '%' || :query || '%'
            OR notes LIKE '%' || :query || '%'
        )
        ORDER BY purchaseDate DESC
        """
    )
    fun observeFilteredPayments(
        memberId: Long?,
        startDate: Long?,
        endDate: Long?,
        query: String
    ): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE id = :id")
    suspend fun getPaymentById(id: Long): PaymentEntity?

    @Query("SELECT * FROM payments ORDER BY purchaseDate DESC LIMIT 1")
    fun observeLastPayment(): Flow<PaymentEntity?>

    @Query("SELECT * FROM payments ORDER BY purchaseDate DESC LIMIT 1")
    suspend fun getLastPayment(): PaymentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: PaymentEntity): Long

    @Update
    suspend fun updatePayment(payment: PaymentEntity)

    @Delete
    suspend fun deletePayment(payment: PaymentEntity)

    @Query("DELETE FROM payments")
    suspend fun deleteAllPayments()

    // ---- Aggregations -----------------------------------------------------------------------

    @Query(
        """
        SELECT strftime('%Y-%m', purchaseDate / 1000, 'unixepoch') as yearMonth,
               COALESCE(SUM(quantity), 0) as totalCans,
               COALESCE(SUM(amount), 0.0) as totalAmount,
               COUNT(*) as paymentCount
        FROM payments
        WHERE purchaseDate >= :monthStart AND purchaseDate < :monthEnd
        """
    )
    fun observeMonthlySummary(monthStart: Long, monthEnd: Long): Flow<MonthlySpendingSummary?>

    @Query(
        """
        SELECT strftime('%Y-%m', purchaseDate / 1000, 'unixepoch') as yearMonth,
               COALESCE(SUM(quantity), 0) as totalCans,
               COALESCE(SUM(amount), 0.0) as totalAmount,
               COUNT(*) as paymentCount
        FROM payments
        GROUP BY yearMonth
        ORDER BY yearMonth DESC
        """
    )
    fun observeAllMonthlySummaries(): Flow<List<MonthlySpendingSummary>>

    @Query(
        """
        SELECT 
            m.id as memberId,
            m.name as memberName,
            m.avatarUri as avatarUri,
            m.isActive as isActive,
            COUNT(p.id) as totalPayments,
            COALESCE(SUM(p.amount), 0.0) as totalAmountContributed,
            MAX(p.purchaseDate) as lastPaymentDate,
            CASE WHEN COUNT(p.id) > 0 THEN COALESCE(SUM(p.amount), 0.0) / COUNT(p.id) ELSE 0.0 END as averageContribution
        FROM members m
        LEFT JOIN payments p ON p.paidByMemberId = m.id
        GROUP BY m.id
        ORDER BY totalAmountContributed DESC
        """
    )
    fun observeMemberStats(): Flow<List<MemberStats>>

    @Query("SELECT COALESCE(AVG(amount), 0.0) FROM payments")
    fun observeAverageAmountPerPayment(): Flow<Double>

    @Query(
        """
        SELECT purchaseDate as date, (amount / quantity) as pricePerCan
        FROM payments
        WHERE quantity > 0
        ORDER BY purchaseDate ASC
        """
    )
    fun observePriceTrend(): Flow<List<com.watercantracker.app.domain.model.PriceTrendPoint>>

    @Query("SELECT COUNT(*) FROM payments WHERE purchaseDate >= :since")
    suspend fun countPaymentsSince(since: Long): Int
}
