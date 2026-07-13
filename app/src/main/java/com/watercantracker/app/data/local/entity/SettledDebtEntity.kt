package com.watercantracker.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Records a manual debt settlement between two members — e.g. tapping
 * "A owes C AED 12 — Settle now" on the Balances tab. This is money that
 * changed hands directly between members (not toward buying cans), so it's
 * tracked separately from PaymentEntity and factored into balance calculations
 * as an adjustment rather than counted toward group spending or can quotas.
 */
@Entity(tableName = "settled_debts")
data class SettledDebtEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fromMemberId: Long,
    val fromMemberName: String,
    val toMemberId: Long,
    val toMemberName: String,
    val amount: Double,
    val settledAt: Long = System.currentTimeMillis()
)
