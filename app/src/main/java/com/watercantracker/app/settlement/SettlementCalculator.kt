package com.watercantracker.app.settlement

import com.watercantracker.app.data.local.entity.MemberEntity
import com.watercantracker.app.data.local.entity.PaymentEntity
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.min

/**
 * Pure business logic — no Android/Room dependencies.
 * Fully testable with plain JUnit.
 *
 * Algorithm for minimum-transaction settlement (greedy matching):
 *   1. Compute each member's balance = totalPaid - fairShare
 *   2. Split into creditors (balance > 0) and debtors (balance < 0)
 *   3. Sort both lists by absolute value descending
 *   4. Greedily match largest debtor with largest creditor:
 *      - transfer = min(|debtor|, creditor)
 *      - reduce both balances by that amount
 *      - if one reaches 0, remove from list and advance
 *   5. Repeat until all balances are ~0
 *
 * This produces at most (N-1) transactions for N members, which is optimal
 * for the general case.
 */
@Singleton
class SettlementCalculator @Inject constructor() {

    fun generateMonthlySettlement(
        members: List<MemberEntity>,
        payments: List<PaymentEntity>,
        month: Int,
        year: Int
    ): MonthlySettlement {
        // Filter payments to the requested month/year
        val monthPayments = payments.filter { payment ->
            val cal = Calendar.getInstance().apply { timeInMillis = payment.purchaseDate }
            cal.get(Calendar.MONTH) + 1 == month && cal.get(Calendar.YEAR) == year
        }

        val activeMembers = members.filter { it.isActive }
        val memberCount = activeMembers.size

        // Edge case: no active members
        if (memberCount == 0) {
            return MonthlySettlement(month, year, 0.0, 0.0, 0, emptyList(), emptyList())
        }

        // Total spent this month (round to avoid fp drift)
        val totalSpent = monthPayments.sumOf { it.amount }.roundAed()

        // Fair share per active member
        val fairShare = if (memberCount > 0) (totalSpent / memberCount).roundAed() else 0.0

        // Per-member paid totals
        val paidByMember: Map<Long, Double> = monthPayments
            .groupBy { it.paidByMemberId ?: -1L }
            .mapValues { (_, pmts) -> pmts.sumOf { it.amount }.roundAed() }

        // Build MemberBalance list
        val memberBalances = activeMembers.map { member ->
            val paid = paidByMember[member.id] ?: 0.0
            MemberBalance(
                memberId   = member.id,
                memberName = member.name,
                paidAmount = paid,
                fairShare  = fairShare,
                balance    = (paid - fairShare).roundAed()
            )
        }

        // Generate settlement transactions
        val transactions = calculateMinTransactions(memberBalances)

        return MonthlySettlement(
            month          = month,
            year           = year,
            totalSpent     = totalSpent,
            fairShare      = fairShare,
            memberCount    = memberCount,
            memberBalances = memberBalances,
            transactions   = transactions
        )
    }

    /**
     * Greedy minimum-transactions algorithm.
     * Works on mutable copies of balances — does not mutate the input list.
     */
    internal fun calculateMinTransactions(
        memberBalances: List<MemberBalance>
    ): List<SettlementTransaction> {
        // Mutable working copies — only members with non-zero balance matter
        val creditors = memberBalances
            .filter { it.isCreditor }
            .map { Triple(it.memberId, it.memberName, it.balance) }
            .sortedByDescending { it.third }
            .toMutableList()

        val debtors = memberBalances
            .filter { it.isDebtor }
            .map { Triple(it.memberId, it.memberName, abs(it.balance)) }
            .sortedByDescending { it.third }
            .toMutableList()

        val transactions = mutableListOf<SettlementTransaction>()

        var ci = 0  // creditor index
        var di = 0  // debtor index

        // Mutable balance accumulators
        val creditorBalances = creditors.map { it.third }.toMutableList()
        val debtorBalances   = debtors.map  { it.third }.toMutableList()

        while (ci < creditors.size && di < debtors.size) {
            val creditor      = creditors[ci]
            val debtor        = debtors[di]
            val creditRemain  = creditorBalances[ci]
            val debtRemain    = debtorBalances[di]

            val transferAmt = min(creditRemain, debtRemain).roundAed()

            if (transferAmt > 0.005) {
                transactions.add(
                    SettlementTransaction(
                        fromMemberId   = debtor.first,
                        fromMemberName = debtor.second,
                        toMemberId     = creditor.first,
                        toMemberName   = creditor.second,
                        amount         = transferAmt
                    )
                )
            }

            creditorBalances[ci] = (creditRemain - transferAmt).roundAed()
            debtorBalances[di]   = (debtRemain  - transferAmt).roundAed()

            if (creditorBalances[ci] < 0.005) ci++
            if (debtorBalances[di]   < 0.005) di++
        }

        return transactions
    }
}
