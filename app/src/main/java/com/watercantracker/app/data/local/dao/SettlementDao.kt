package com.watercantracker.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.watercantracker.app.data.local.entity.SettlementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettlementDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettlement(settlement: SettlementEntity): Long

    @Query("SELECT * FROM settlements ORDER BY year DESC, month DESC")
    fun observeAllSettlements(): Flow<List<SettlementEntity>>

    @Query("SELECT * FROM settlements WHERE month = :month AND year = :year LIMIT 1")
    suspend fun getSettlement(month: Int, year: Int): SettlementEntity?

    @Query("SELECT * FROM settlements WHERE month = :month AND year = :year LIMIT 1")
    fun observeSettlement(month: Int, year: Int): Flow<SettlementEntity?>

    @Query("DELETE FROM settlements WHERE month = :month AND year = :year")
    suspend fun deleteSettlement(month: Int, year: Int)

    @Query("SELECT * FROM settlements ORDER BY year DESC, month DESC LIMIT 1")
    fun observeLatestSettlement(): Flow<SettlementEntity?>
}
