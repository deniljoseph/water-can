package com.watercantracker.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents one contributor's share in a joint payment.
 * A single water can purchase can have multiple contributors
 * each paying a different portion of the total cost.
 *
 * Example: 2 cans at AED 5.50 each = AED 11.00 total
 *   Ahmed contributed AED 8.00
 *   Ali   contributed AED 3.00
 *
 * The parent PaymentEntity.amount stores the TOTAL cost (11.00).
 * Each JointPaymentContributorEntity stores what each person paid.
 */
@Entity(
    tableName = "joint_payment_contributors",
    foreignKeys = [
        ForeignKey(
            entity = PaymentEntity::class,
            parentColumns = ["id"],
            childColumns = ["paymentId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["memberId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("paymentId"),
        Index("memberId")
    ]
)
data class JointPaymentContributorEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val paymentId: Long,
    val memberId: Long?,
    /** Snapshot of member name at time of payment — survives member deletion */
    val memberNameSnapshot: String,
    val amountContributed: Double
)
