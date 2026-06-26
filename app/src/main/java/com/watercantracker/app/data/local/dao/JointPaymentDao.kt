package com.watercantracker.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.watercantracker.app.data.local.entity.JointPaymentContributorEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JointPaymentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContributors(contributors: List<JointPaymentContributorEntity>)

    @Query("SELECT * FROM joint_payment_contributors WHERE paymentId = :paymentId")
    fun observeContributors(paymentId: Long): Flow<List<JointPaymentContributorEntity>>

    @Query("SELECT * FROM joint_payment_contributors WHERE paymentId = :paymentId")
    suspend fun getContributors(paymentId: Long): List<JointPaymentContributorEntity>

    @Query("DELETE FROM joint_payment_contributors WHERE paymentId = :paymentId")
    suspend fun deleteContributorsForPayment(paymentId: Long)

    /** Total contributed by a member across all joint payments */
    @Query("""
        SELECT COALESCE(SUM(amountContributed), 0.0)
        FROM joint_payment_contributors
        WHERE memberId = :memberId
    """)
    suspend fun getTotalJointContributionByMember(memberId: Long): Double

    /** All partial contributions (memberId's amount < their fair share of that payment) */
    @Query("""
        SELECT jpc.*
        FROM joint_payment_contributors jpc
        INNER JOIN payments p ON p.id = jpc.paymentId
        WHERE jpc.memberId = :memberId
          AND p.isJointPayment = 1
    """)
    fun observeJointContributionsByMember(memberId: Long): Flow<List<JointPaymentContributorEntity>>
}
