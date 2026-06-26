package com.watercantracker.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "members")
data class MemberEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val phoneNumber: String? = null,
    val avatarUri: String? = null,
    val isActive: Boolean = true,
    val rotationOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val isManualNextPayer: Boolean = false,
    val isSkipped: Boolean = false,
    /** Firebase Realtime DB key — null until synced */
    val firebaseSyncId: String? = null
)
