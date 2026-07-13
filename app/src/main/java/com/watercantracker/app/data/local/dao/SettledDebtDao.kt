package com.watercantracker.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.watercantracker.app.data.local.entity.SettledDebtEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettledDebtDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettledDebt(debt: SettledDebtEntity): Long

    @Query("SELECT * FROM settled_debts ORDER BY settledAt DESC")
    fun observeAllSettledDebts(): Flow<List<SettledDebtEntity>>

    /** Net adjustment for a member: money they've paid OUT to settle debts (reduces their owed) */
    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM settled_debts WHERE fromMemberId = :memberId")
    suspend fun getTotalPaidOut(memberId: Long): Double

    /** Net adjustment for a member: money they've received to settle debts (reduces their credit) */
    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM settled_debts WHERE toMemberId = :memberId")
    suspend fun getTotalReceived(memberId: Long): Double
}
