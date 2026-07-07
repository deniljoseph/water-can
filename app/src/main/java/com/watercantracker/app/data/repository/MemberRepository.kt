package com.watercantracker.app.data.repository

import com.watercantracker.app.data.local.dao.MemberDao
import com.watercantracker.app.data.local.dao.PaymentDao
import com.watercantracker.app.data.local.entity.MemberEntity
import com.watercantracker.app.domain.model.NextPayerReason
import com.watercantracker.app.domain.model.NextPayerResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemberRepository @Inject constructor(
    private val memberDao: MemberDao,
    private val paymentDao: PaymentDao
) {
    fun observeAllMembers(): Flow<List<MemberEntity>> = memberDao.observeAllMembers()
    fun observeActiveMembers(): Flow<List<MemberEntity>> = memberDao.observeActiveMembers()
    fun observeActiveMemberCount(): Flow<Int> = memberDao.observeActiveMemberCount()
    fun observeMemberById(id: Long): Flow<MemberEntity?> = memberDao.observeMemberById(id)

    suspend fun getMemberById(id: Long): MemberEntity? = memberDao.getMemberById(id)

    suspend fun addMember(name: String, phoneNumber: String?, avatarUri: String?): Long {
        val nextOrder = (memberDao.getMaxRotationOrder() ?: -1) + 1
        return memberDao.insertMember(
            MemberEntity(
                name          = name.trim(),
                phoneNumber   = phoneNumber?.trim()?.takeIf { it.isNotEmpty() },
                avatarUri     = avatarUri,
                rotationOrder = nextOrder
            )
        )
    }

    suspend fun updateMember(member: MemberEntity) = memberDao.updateMember(member)

    suspend fun deleteMember(member: MemberEntity) {
        memberDao.deleteMember(member)
        reorderRotation()
    }

    suspend fun setActiveStatus(memberId: Long, isActive: Boolean) =
        memberDao.setActiveStatus(memberId, isActive)

    suspend fun setManualNextPayer(memberId: Long) {
        memberDao.clearAllManualNextPayerFlags()
        memberDao.setManualNextPayer(memberId)
    }

    suspend fun clearManualNextPayer() = memberDao.clearAllManualNextPayerFlags()
    suspend fun skipMember(memberId: Long) = memberDao.markSkipped(memberId)
    suspend fun clearSkipped(memberId: Long) = memberDao.clearSkipped(memberId)

    suspend fun moveUp(memberId: Long) {
        val all = memberDao.getAllMembers().toMutableList()
        val idx = all.indexOfFirst { it.id == memberId }
        if (idx > 0) {
            val current = all[idx]; val above = all[idx - 1]
            memberDao.updateRotationOrder(current.id, above.rotationOrder)
            memberDao.updateRotationOrder(above.id, current.rotationOrder)
        }
    }

    suspend fun moveDown(memberId: Long) {
        val all = memberDao.getAllMembers().toMutableList()
        val idx = all.indexOfFirst { it.id == memberId }
        if (idx >= 0 && idx < all.size - 1) {
            val current = all[idx]; val below = all[idx + 1]
            memberDao.updateRotationOrder(current.id, below.rotationOrder)
            memberDao.updateRotationOrder(below.id, current.rotationOrder)
        }
    }

    /**
     * Resolves who should pay next.
     *
     * The person at the FRONT of the rotation queue (index 0) is always shown
     * as next — regardless of who paid last. Out-of-turn payments don't shift
     * the queue pointer.
     *
     * The queue advances only when the front person has bought their full quota
     * (cansPerTurn cans since the last time the queue advanced). Partial quota
     * payments (e.g. 1 of 2 cans) keep them at the front until done.
     */
    suspend fun resolveNextPayer(lastPayerMemberId: Long?): NextPayerResult {
        val active = memberDao.getActiveMembers()
        if (active.isEmpty()) return NextPayerResult(null, NextPayerReason.NO_ACTIVE_MEMBERS, null)

        val manual = memberDao.getManualNextPayer()
        if (manual != null && manual.isActive) {
            return NextPayerResult(manual, NextPayerReason.MANUAL_OVERRIDE, null)
        }

        val nonSkipped = active.filter { !it.isSkipped }
        if (nonSkipped.isEmpty()) return NextPayerResult(active.first(), NextPayerReason.ALL_SKIPPED, null)

        return NextPayerResult(nonSkipped.first(), NextPayerReason.ROTATION_ORDER, null)
    }

    /**
     * Called after every payment save.
     *
     * Checks if the front-of-queue member has now bought enough cans (their
     * full cansPerTurn quota). If yes, rotates them to the back.
     *
     * "Enough cans" = sum of all their payments since the last rotation advance,
     * i.e. since the member immediately behind them last had rotation order 0.
     * In practice: we look at all payments by the current front-of-queue member
     * and compare to the cansSinceLastAdvance stored on their entity.
     */
    suspend fun advanceRotationIfNeeded(payerMemberId: Long, cansJustPaid: Int, cansPerTurn: Int) {
        val active     = memberDao.getActiveMembers()
        val nonSkipped = active.filter { !it.isSkipped }
        if (nonSkipped.isEmpty()) return

        val frontOfQueue = nonSkipped.first()

        // Only the designated payer's cans count toward advancing the queue
        if (frontOfQueue.id != payerMemberId) return

        // Count total cans bought by this member since their turn started
        val totalCansThisTurn = frontOfQueue.cansPaidThisTurn + cansJustPaid

        if (totalCansThisTurn >= cansPerTurn) {
            // Full quota met → rotate to back, reset counter
            val maxOrder = active.maxOfOrNull { it.rotationOrder } ?: 0
            memberDao.updateRotationOrder(frontOfQueue.id, maxOrder + 1)
            memberDao.updateCansPaidThisTurn(frontOfQueue.id, 0)
            reorderRotation()
        } else {
            // Partial quota — keep at front, update running total
            memberDao.updateCansPaidThisTurn(frontOfQueue.id, totalCansThisTurn)
        }
    }

    private suspend fun reorderRotation() {
        memberDao.getAllMembers().forEachIndexed { idx, member ->
            memberDao.updateRotationOrder(member.id, idx)
        }
    }
}
