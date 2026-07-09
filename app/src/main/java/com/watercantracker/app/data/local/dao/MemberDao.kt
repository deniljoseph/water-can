package com.watercantracker.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.watercantracker.app.data.local.entity.MemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemberDao {

    @Query("SELECT * FROM members ORDER BY rotationOrder ASC")
    fun observeAllMembers(): Flow<List<MemberEntity>>

    @Query("SELECT * FROM members WHERE isActive = 1 ORDER BY rotationOrder ASC")
    fun observeActiveMembers(): Flow<List<MemberEntity>>

    @Query("SELECT * FROM members ORDER BY rotationOrder ASC")
    suspend fun getAllMembers(): List<MemberEntity>

    @Query("SELECT * FROM members WHERE isActive = 1 ORDER BY rotationOrder ASC")
    suspend fun getActiveMembers(): List<MemberEntity>

    @Query("SELECT * FROM members WHERE id = :id")
    suspend fun getMemberById(id: Long): MemberEntity?

    @Query("SELECT * FROM members WHERE id = :id")
    fun observeMemberById(id: Long): Flow<MemberEntity?>

    @Query("SELECT COUNT(*) FROM members WHERE isActive = 1")
    fun observeActiveMemberCount(): Flow<Int>

    @Query("SELECT MAX(rotationOrder) FROM members")
    suspend fun getMaxRotationOrder(): Int?

    @Query("SELECT * FROM members WHERE isManualNextPayer = 1 LIMIT 1")
    suspend fun getManualNextPayer(): MemberEntity?

    @Query("UPDATE members SET isManualNextPayer = 0")
    suspend fun clearAllManualNextPayerFlags()

    @Query("UPDATE members SET isManualNextPayer = 1 WHERE id = :memberId")
    suspend fun setManualNextPayer(memberId: Long)

    @Query("UPDATE members SET isSkipped = 1 WHERE id = :memberId")
    suspend fun markSkipped(memberId: Long)

    @Query("UPDATE members SET isSkipped = 0 WHERE id = :memberId")
    suspend fun clearSkipped(memberId: Long)

    @Query("UPDATE members SET rotationOrder = :order WHERE id = :memberId")
    suspend fun updateRotationOrder(memberId: Long, order: Int)

    @Query("UPDATE members SET cansPaidThisTurn = :count WHERE id = :memberId")
    suspend fun updateCansPaidThisTurn(memberId: Long, count: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: MemberEntity): Long

    @Update
    suspend fun updateMember(member: MemberEntity)

    @Delete
    suspend fun deleteMember(member: MemberEntity)

    @Query("UPDATE members SET isActive = :isActive WHERE id = :memberId")
    suspend fun setActiveStatus(memberId: Long, isActive: Boolean)
}
