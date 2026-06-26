package com.watercantracker.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = MemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["paidByMemberId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("paidByMemberId"), Index("purchaseDate")]
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val quantity: Int,
    /** Total amount for this purchase (sum of all contributors if joint) */
    val amount: Double,
    /** Primary payer — null if purely joint with no single primary */
    val paidByMemberId: Long?,
    val paidByNameSnapshot: String,
    val purchaseDate: Long,
    val notes: String? = null,
    val vendorName: String? = null,
    val receiptImageUri: String? = null,
    /** True if this payment has multiple contributors in joint_payment_contributors */
    val isJointPayment: Boolean = false,
    /** Firebase Realtime DB key for sync — null until synced */
    val firebaseSyncId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
