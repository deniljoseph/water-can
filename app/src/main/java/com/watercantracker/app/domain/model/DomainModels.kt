package com.watercantracker.app.domain.model

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
 * yearMonth is nullable so Room can map rows where strftime returns NULL
 * (e.g. when the payments table is empty or purchaseDate is 0).
 */
data class MonthlySpendingSummary(
    val yearMonth: String?,   // "2026-06" — nullable to survive empty-table queries
    val totalCans: Int,
    val totalAmount: Double,
    val paymentCount: Int
)

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
