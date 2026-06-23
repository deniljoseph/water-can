package com.watercantracker.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Records local notifications that have been scheduled/fired, so we can avoid duplicate
 * reminders and show an in-app notification history if needed.
 */
@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String, // REMINDER, OVERDUE, UPCOMING
    val memberId: Long?,
    val memberNameSnapshot: String,
    val message: String,
    val scheduledAt: Long,
    val firedAt: Long? = null,
    val wasShown: Boolean = false
)
