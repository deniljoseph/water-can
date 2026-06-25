package com.watercantracker.app.data.repository

import com.watercantracker.app.data.local.dao.MemberDao
import com.watercantracker.app.data.local.dao.PaymentDao
import com.watercantracker.app.data.local.dao.SettlementDao
import com.watercantracker.app.data.local.entity.SettlementEntity
import com.watercantracker.app.settlement.MemberBalance
import com.watercantracker.app.settlement.MonthlySettlement
import com.watercantracker.app.settlement.SettlementCalculator
import com.watercantracker.app.settlement.SettlementTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettlementRepository @Inject constructor(
    private val settlementDao: SettlementDao,
    private val memberDao: MemberDao,
    private val paymentDao: PaymentDao,
    private val calculator: SettlementCalculator
) {
    fun observeAllSettlements(): Flow<List<SettlementEntity>> = settlementDao.observeAllSettlements()
    fun observeLatestSettlement(): Flow<SettlementEntity?>   = settlementDao.observeLatestSettlement()
    fun observeSettlement(month: Int, year: Int): Flow<SettlementEntity?> =
        settlementDao.observeSettlement(month, year)

    suspend fun generateAndSave(month: Int, year: Int): MonthlySettlement {
        val members  = memberDao.getAllMembers()
        val payments = paymentDao.observeAllPayments().first()
        val result   = calculator.generateMonthlySettlement(members, payments, month, year)
        persist(result)
        return result
    }

    suspend fun regenerate(month: Int, year: Int): MonthlySettlement {
        settlementDao.deleteSettlement(month, year)
        return generateAndSave(month, year)
    }

    fun deserialize(entity: SettlementEntity): MonthlySettlement {
        val root = JSONObject(entity.settlementJson)
        val balancesArr = root.getJSONArray("balances")
        val txArr       = root.getJSONArray("transactions")

        val balances = (0 until balancesArr.length()).map { i ->
            val o = balancesArr.getJSONObject(i)
            MemberBalance(
                memberId   = o.getLong("memberId"),
                memberName = o.getString("memberName"),
                paidAmount = o.getDouble("paidAmount"),
                fairShare  = o.getDouble("fairShare"),
                balance    = o.getDouble("balance")
            )
        }
        val transactions = (0 until txArr.length()).map { i ->
            val o = txArr.getJSONObject(i)
            SettlementTransaction(
                fromMemberId   = o.getLong("fromMemberId"),
                fromMemberName = o.getString("fromMemberName"),
                toMemberId     = o.getLong("toMemberId"),
                toMemberName   = o.getString("toMemberName"),
                amount         = o.getDouble("amount")
            )
        }
        return MonthlySettlement(
            month          = entity.month,
            year           = entity.year,
            totalSpent     = entity.totalSpent,
            fairShare      = entity.fairShare,
            memberCount    = entity.memberCount,
            memberBalances = balances,
            transactions   = transactions
        )
    }

    private suspend fun persist(settlement: MonthlySettlement) {
        val balancesArr = JSONArray().also { arr ->
            settlement.memberBalances.forEach { mb ->
                arr.put(JSONObject().apply {
                    put("memberId",   mb.memberId)
                    put("memberName", mb.memberName)
                    put("paidAmount", mb.paidAmount)
                    put("fairShare",  mb.fairShare)
                    put("balance",    mb.balance)
                })
            }
        }
        val txArr = JSONArray().also { arr ->
            settlement.transactions.forEach { tx ->
                arr.put(JSONObject().apply {
                    put("fromMemberId",   tx.fromMemberId)
                    put("fromMemberName", tx.fromMemberName)
                    put("toMemberId",     tx.toMemberId)
                    put("toMemberName",   tx.toMemberName)
                    put("amount",         tx.amount)
                })
            }
        }
        val json = JSONObject().apply {
            put("balances",     balancesArr)
            put("transactions", txArr)
        }.toString()

        settlementDao.insertSettlement(
            SettlementEntity(
                month            = settlement.month,
                year             = settlement.year,
                totalSpent       = settlement.totalSpent,
                fairShare        = settlement.fairShare,
                memberCount      = settlement.memberCount,
                transactionCount = settlement.transactions.size,
                settlementJson   = json
            )
        )
    }
}
