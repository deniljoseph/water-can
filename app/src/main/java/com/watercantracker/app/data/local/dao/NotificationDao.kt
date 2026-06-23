package com.watercantracker.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.watercantracker.app.data.local.entity.NotificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity): Long

    @Query("SELECT * FROM notifications ORDER BY scheduledAt DESC LIMIT 50")
    fun observeRecentNotifications(): Flow<List<NotificationEntity>>

    @Query("UPDATE notifications SET wasShown = 1, firedAt = :firedAt WHERE id = :id")
    suspend fun markShown(id: Long, firedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM notifications WHERE scheduledAt < :before")
    suspend fun deleteOlderThan(before: Long)
}
