package com.watercantracker.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a single person who participates in the water can payment rotation.
 *
 * [rotationOrder] determines this member's position in the predefined payer sequence.
 * Lower values come first. When a member is inserted, they're appended to the end of the
 * queue (max existing order + 1). Inactive members are skipped during automatic rotation
 * but their historical payment data is preserved.
 */
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
    /** True if this member is currently flagged as the manually-overridden next payer. */
    val isManualNextPayer: Boolean = false,
    /** True if this member's turn was skipped and they should be revisited next cycle. */
    val isSkipped: Boolean = false
)
