package com.watercantracker.app.domain.model

/**
 * Aggregated statistics for a single member, computed from their payment history.
 */
data class MemberStats(
    val memberId: Long,
    val memberName: String,
    val avatarUri: String?,
    val isActive: Boolean,
    val totalPayments: Int,
    val totalAmountContributed: Double,
    val lastPaymentDate: Long?,
    val averageContribution: Double
) {
    enum class BalanceStatus { ABOVE_AVERAGE, BELOW_AVERAGE, ON_AVERAGE }

    fun balanceStatus(groupAverage: Double, tolerance: Double = 0.01): BalanceStatus {
        return when {
            totalAmountContributed > groupAverage * (1 + tolerance) -> BalanceStatus.ABOVE_AVERAGE
            totalAmountContributed < groupAverage * (1 - tolerance) -> BalanceStatus.BELOW_AVERAGE
            else -> BalanceStatus.ON_AVERAGE
        }
    }
}

/**
 * A snapshot of monthly aggregated spending, used for the dashboard and the monthly report.
 */
data class MonthlySpendingSummary(
    val yearMonth: String, // "2026-06"
    val totalCans: Int,
    val totalAmount: Double,
    val paymentCount: Int
)

/**
 * Result of resolving who should pay next, including the reasoning so the UI can explain it
 * (e.g. "manually set" vs "next in rotation").
 */
data class NextPayerResult(
    val member: com.watercantracker.app.data.local.entity.MemberEntity?,
    val reason: NextPayerReason,
    val daysSinceLastPayment: Int?
)

enum class NextPayerReason {
    MANUAL_OVERRIDE,
    ROTATION_ORDER,
    NO_ACTIVE_MEMBERS,
    ALL_SKIPPED
}

data class PriceTrendPoint(
    val date: Long,
    val pricePerCan: Double
)
