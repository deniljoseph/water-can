package com.watercantracker.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persists a generated monthly settlement.
 * [settlementJson] stores the full MonthlySettlement as serialized JSON
 * so it can be reconstructed without re-running the algorithm.
 */
@Entity(tableName = "settlements")
data class SettlementEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val month: Int,
    val year: Int,
    val generatedAt: Long = System.currentTimeMillis(),
    val totalSpent: Double,
    val fairShare: Double,
    val memberCount: Int,
    val transactionCount: Int,
    /** Full JSON blob of MonthlySettlement for display/export without recalculation */
    val settlementJson: String
)
