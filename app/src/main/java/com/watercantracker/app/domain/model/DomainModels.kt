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

    fun balanceStatus(groupAverage: Double, tolerance: Double = 0.01): BalanceStatus = when {
        totalAmountContributed > groupAverage * (1 + tolerance) -> BalanceStatus.ABOVE_AVERAGE
        totalAmountContributed < groupAverage * (1 - tolerance) -> BalanceStatus.BELOW_AVERAGE
        else -> BalanceStatus.ON_AVERAGE
    }
}

data class MonthlySpendingSummary(
    val yearMonth: String?,
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
    MANUAL_OVERRIDE, ROTATION_ORDER, NO_ACTIVE_MEMBERS, ALL_SKIPPED
}

data class PriceTrendPoint(
    val date: Long,
    val pricePerCan: Double
)

/**
 * Running balance for a single member across ALL payments.
 *
 * fairShare   = member's equal slice of the total group spend
 * totalPaid   = what they have actually paid
 * netBalance  = totalPaid - fairShare
 *               positive → credit (overpaid)
 *               negative → owes (underpaid)
 */
data class MemberBalance(
    val memberId: Long,
    val memberName: String,
    val avatarUri: String?,
    val isActive: Boolean,
    val totalPaid: Double,       // sum of all their payment amounts
    val fairShare: Double,       // their equal slice of total group spend
    val netBalance: Double       // totalPaid - fairShare  (+ = credit, - = owes)
) {
    val owes: Double get() = if (netBalance < 0) -netBalance else 0.0
    val credit: Double get() = if (netBalance > 0) netBalance else 0.0

    enum class Status { CREDIT, SETTLED, OWES }
    val status: Status get() = when {
        netBalance > 0.01  -> Status.CREDIT
        netBalance < -0.01 -> Status.OWES
        else               -> Status.SETTLED
    }
}
