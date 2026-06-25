package com.watercantracker.app.settlement

import com.watercantracker.app.data.local.entity.MemberEntity
import com.watercantracker.app.data.local.entity.PaymentEntity
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Calendar

class SettlementCalculatorTest {

    private lateinit var calculator: SettlementCalculator

    // Helpers
    private fun member(id: Long, name: String) =
        MemberEntity(id = id, name = name, isActive = true, rotationOrder = id.toInt())

    private fun payment(memberId: Long, amount: Double, month: Int = 6, year: Int = 2026): PaymentEntity {
        val cal = Calendar.getInstance()
        cal.set(year, month - 1, 15, 12, 0, 0)
        return PaymentEntity(
            id = memberId * 100,
            quantity = 1,
            amount = amount,
            paidByMemberId = memberId,
            paidByNameSnapshot = "Member$memberId",
            purchaseDate = cal.timeInMillis
        )
    }

    @Before
    fun setup() { calculator = SettlementCalculator() }

    // ── Example 1 from spec ───────────────────────────────────────────────────
    // Ahmed=150, Ali=50, Rahul=100, John=100 → total=400, share=100
    // Balances: Ahmed=+50, Ali=-50, Rahul=0, John=0
    // Expected: Ali pays Ahmed 50
    @Test
    fun `example 1 - single debtor single creditor`() {
        val members = listOf(
            member(1, "Ahmed"), member(2, "Ali"),
            member(3, "Rahul"), member(4, "John")
        )
        val payments = listOf(
            payment(1, 150.0), payment(2, 50.0),
            payment(3, 100.0), payment(4, 100.0)
        )
        val result = calculator.generateMonthlySettlement(members, payments, 6, 2026)

        assertEquals(400.0, result.totalSpent, 0.01)
        assertEquals(100.0, result.fairShare, 0.01)

        val ahmed = result.memberBalances.first { it.memberName == "Ahmed" }
        val ali   = result.memberBalances.first { it.memberName == "Ali" }
        val rahul = result.memberBalances.first { it.memberName == "Rahul" }
        val john  = result.memberBalances.first { it.memberName == "John" }

        assertEquals(+50.0, ahmed.balance, 0.01)
        assertEquals(-50.0, ali.balance,   0.01)
        assertEquals(  0.0, rahul.balance, 0.01)
        assertEquals(  0.0, john.balance,  0.01)

        assertEquals(1, result.transactions.size)
        assertEquals("Ali",   result.transactions[0].fromMemberName)
        assertEquals("Ahmed", result.transactions[0].toMemberName)
        assertEquals(50.0,    result.transactions[0].amount, 0.01)
    }

    // ── Example 2 from spec ───────────────────────────────────────────────────
    // Ahmed=200, Ali=50, Rahul=0, John=150 → total=400, share=100
    // Balances: Ahmed=+100, Ali=-50, Rahul=-100, John=+50
    // Expected 3 transactions: Ali→Ahmed 50, Rahul→Ahmed 50, Rahul→John 50
    @Test
    fun `example 2 - multiple debtors and creditors`() {
        val members = listOf(
            member(1, "Ahmed"), member(2, "Ali"),
            member(3, "Rahul"), member(4, "John")
        )
        val payments = listOf(
            payment(1, 200.0), payment(2, 50.0),
            payment(4, 150.0)
            // Rahul paid 0
        )
        val result = calculator.generateMonthlySettlement(members, payments, 6, 2026)

        assertEquals(400.0, result.totalSpent, 0.01)
        assertEquals(100.0, result.fairShare,  0.01)

        val ahmed = result.memberBalances.first { it.memberName == "Ahmed" }
        val ali   = result.memberBalances.first { it.memberName == "Ali" }
        val rahul = result.memberBalances.first { it.memberName == "Rahul" }
        val john  = result.memberBalances.first { it.memberName == "John" }

        assertEquals(+100.0, ahmed.balance, 0.01)
        assertEquals( -50.0, ali.balance,   0.01)
        assertEquals(-100.0, rahul.balance, 0.01)
        assertEquals( +50.0, john.balance,  0.01)

        // Verify all balances zeroed by transactions
        val netAfter = mutableMapOf<Long, Double>()
        result.memberBalances.forEach { netAfter[it.memberId] = it.balance }
        result.transactions.forEach { tx ->
            netAfter[tx.fromMemberId] = (netAfter[tx.fromMemberId] ?: 0.0) + tx.amount
            netAfter[tx.toMemberId]   = (netAfter[tx.toMemberId]   ?: 0.0) - tx.amount
        }
        netAfter.values.forEach { assertEquals(0.0, it, 0.01) }

        // Minimum transactions: at most N-1 = 3
        assertTrue(result.transactions.size <= 3)
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test
    fun `no payments - zero total, zero fair share, no transactions`() {
        val members = listOf(member(1, "Ahmed"), member(2, "Ali"))
        val result = calculator.generateMonthlySettlement(members, emptyList(), 6, 2026)
        assertEquals(0.0, result.totalSpent, 0.0)
        assertEquals(0.0, result.fairShare,  0.0)
        assertTrue(result.transactions.isEmpty())
        result.memberBalances.forEach { assertEquals(0.0, it.balance, 0.0) }
    }

    @Test
    fun `single member - owes nothing to nobody`() {
        val members = listOf(member(1, "Ahmed"))
        val payments = listOf(payment(1, 150.0))
        val result = calculator.generateMonthlySettlement(members, payments, 6, 2026)
        assertEquals(150.0, result.totalSpent, 0.01)
        assertEquals(150.0, result.fairShare,  0.01)
        assertEquals(0.0,   result.memberBalances[0].balance, 0.01)
        assertTrue(result.transactions.isEmpty())
    }

    @Test
    fun `all members pay equal share - no transactions needed`() {
        val members = listOf(member(1,"A"), member(2,"B"), member(3,"C"))
        val payments = listOf(payment(1,100.0), payment(2,100.0), payment(3,100.0))
        val result = calculator.generateMonthlySettlement(members, payments, 6, 2026)
        assertTrue(result.transactions.isEmpty())
        result.memberBalances.forEach { assertEquals(0.0, it.balance, 0.01) }
    }

    @Test
    fun `member joined but never paid - shows as debtor`() {
        val members = listOf(member(1,"Ahmed"), member(2,"Ali"), member(3,"NewGuy"))
        val payments = listOf(payment(1, 200.0), payment(2, 100.0))
        // NewGuy paid 0; total=300, share=100
        val result = calculator.generateMonthlySettlement(members, payments, 6, 2026)
        val newGuy = result.memberBalances.first { it.memberName == "NewGuy" }
        assertEquals(-100.0, newGuy.balance, 0.01)
        assertTrue(newGuy.isDebtor)
    }

    @Test
    fun `decimal values handled correctly - no fp drift`() {
        val members = listOf(member(1,"A"), member(2,"B"), member(3,"C"))
        val payments = listOf(payment(1, 33.33), payment(2, 33.33), payment(3, 33.34))
        val result = calculator.generateMonthlySettlement(members, payments, 6, 2026)
        // Total = 100.00, share = 33.33...
        assertEquals(100.0, result.totalSpent, 0.01)
        // Verify no transaction amount is negative or zero erroneously
        result.transactions.forEach { assertTrue(it.amount > 0.0) }
    }

    @Test
    fun `large group - 10 members, only 2 paid`() {
        val members = (1..10L).map { member(it, "Member$it") }
        // Member 1 paid 500, Member 2 paid 500 → total=1000, share=100 each
        val payments = listOf(payment(1, 500.0), payment(2, 500.0))
        val result = calculator.generateMonthlySettlement(members, payments, 6, 2026)
        assertEquals(1000.0, result.totalSpent, 0.01)
        assertEquals(100.0,  result.fairShare,  0.01)

        // Members 3-10 each owe 100; Members 1+2 each have +400 credit
        val m1 = result.memberBalances.first { it.memberId == 1L }
        val m3 = result.memberBalances.first { it.memberId == 3L }
        assertEquals(+400.0, m1.balance, 0.01)
        assertEquals(-100.0, m3.balance, 0.01)

        // All balances sum to zero
        val sum = result.memberBalances.sumOf { it.balance }
        assertEquals(0.0, sum, 0.1)

        // Max transactions ≤ N-1 = 9
        assertTrue(result.transactions.size <= 9)
    }

    @Test
    fun `payments in wrong month are excluded`() {
        val members = listOf(member(1,"A"), member(2,"B"))
        val junePayment = payment(1, 200.0, month = 6, year = 2026)
        val mayPayment  = payment(2, 200.0, month = 5, year = 2026) // different month
        val result = calculator.generateMonthlySettlement(members, listOf(junePayment, mayPayment), 6, 2026)
        // Only June payment counts
        assertEquals(200.0, result.totalSpent, 0.01)
        assertEquals(100.0, result.fairShare,  0.01)
    }

    @Test
    fun `all balances sum to zero after transactions applied`() {
        val members = (1..5L).map { member(it, "M$it") }
        val payments = listOf(
            payment(1, 80.0), payment(2, 200.0), payment(3, 50.0),
            payment(4, 120.0), payment(5, 0.0)
        )
        val result = calculator.generateMonthlySettlement(members, payments, 6, 2026)
        val net = mutableMapOf<Long, Double>()
        result.memberBalances.forEach { net[it.memberId] = it.balance }
        result.transactions.forEach { tx ->
            net[tx.fromMemberId] = (net[tx.fromMemberId] ?: 0.0) + tx.amount
            net[tx.toMemberId]   = (net[tx.toMemberId]   ?: 0.0) - tx.amount
        }
        net.values.forEach { assertEquals(0.0, it, 0.05) }
    }
}
