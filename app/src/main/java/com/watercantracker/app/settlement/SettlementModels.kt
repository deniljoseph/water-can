package com.watercantracker.app.settlement

import kotlin.math.abs
import kotlin.math.roundToInt

// ── Core data classes ─────────────────────────────────────────────────────────

/**
 * Balance for a single member in a given settlement period.
 * [balance] = paidAmount - fairShare
 *   positive → overpaid (should receive money)
 *   negative → underpaid (owes money)
 */
data class MemberBalance(
    val memberId: Long,
    val memberName: String,
    val paidAmount: Double,
    val fairShare: Double,
    val balance: Double
) {
    val isCreditor: Boolean get() = balance > 0.005
    val isDebtor: Boolean   get() = balance < -0.005
    val isSettled: Boolean  get() = !isCreditor && !isDebtor
}

/**
 * A single transfer that settles part or all of a debt.
 * [fromMemberName] pays [toMemberName] the given [amount].
 */
data class SettlementTransaction(
    val fromMemberId: Long,
    val fromMemberName: String,
    val toMemberId: Long,
    val toMemberName: String,
    val amount: Double          // always positive, rounded to 2 dp
)

/**
 * Complete result for one monthly settlement.
 */
data class MonthlySettlement(
    val month: Int,                              // 1–12
    val year: Int,
    val totalSpent: Double,
    val fairShare: Double,                       // per member
    val memberCount: Int,
    val memberBalances: List<MemberBalance>,
    val transactions: List<SettlementTransaction>
) {
    val hasTransactions: Boolean get() = transactions.isNotEmpty()
    val monthLabel: String get() {
        val months = listOf("","Jan","Feb","Mar","Apr","May","Jun",
            "Jul","Aug","Sep","Oct","Nov","Dec")
        return "${months.getOrElse(month) { month.toString() }} $year"
    }
}

// ── Helper: round to 2 decimal places avoiding floating-point drift ───────────
internal fun Double.roundAed(): Double =
    (this * 100.0).roundToInt() / 100.0
