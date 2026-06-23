package com.watercantracker.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single water can purchase/payment record.
 */
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
    val amount: Double,
    val paidByMemberId: Long?,
    /** Denormalized snapshot of payer name at time of payment, so history survives member deletion. */
    val paidByNameSnapshot: String,
    val purchaseDate: Long,
    val notes: String? = null,
    val vendorName: String? = null,
    val receiptImageUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
