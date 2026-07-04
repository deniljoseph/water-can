package com.watercantracker.app.data.repository

import com.watercantracker.app.data.local.dao.MemberDao
import com.watercantracker.app.data.local.entity.MemberEntity
import com.watercantracker.app.domain.model.NextPayerReason
import com.watercantracker.app.domain.model.NextPayerResult
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemberRepository @Inject constructor(
    private val memberDao: MemberDao
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
     * KEY FIX: The rotation advances based on who was SUPPOSED to pay (the queue position),
     * NOT who actually paid last. If Person A jumps in and pays out of turn, the queue
     * stays on Person C (who was supposed to pay) — it does NOT skip to Person D.
     *
     * Logic:
     * 1. Manual override wins (set explicitly by user).
     * 2. Find the current queue position: the first active, non-skipped member whose
     *    rotation turn has not been satisfied.
     * 3. "Satisfied" = this member IS the lastPayerMemberId AND they are at the front
     *    of the queue. Only in this case do we advance to the next person.
     * 4. If someone else paid (out of turn), the queue position does NOT advance.
     */
    suspend fun resolveNextPayer(lastPayerMemberId: Long?): NextPayerResult {
        val active = memberDao.getActiveMembers()
        if (active.isEmpty()) return NextPayerResult(null, NextPayerReason.NO_ACTIVE_MEMBERS, null)

        // Manual override always wins
        val manual = memberDao.getManualNextPayer()
        if (manual != null && manual.isActive) {
            return NextPayerResult(manual, NextPayerReason.MANUAL_OVERRIDE, null)
        }

        val nonSkipped = active.filter { !it.isSkipped }
        if (nonSkipped.isEmpty()) return NextPayerResult(active.first(), NextPayerReason.ALL_SKIPPED, null)

        // No payments yet → first in queue
        if (lastPayerMemberId == null) {
            return NextPayerResult(nonSkipped.first(), NextPayerReason.ROTATION_ORDER, null)
        }

        // Find who is at the FRONT of the queue (index 0)
        val frontOfQueue = nonSkipped.first()

        // Was the last payment made by the person at the front of the queue?
        val lastPayerWasNext = frontOfQueue.id == lastPayerMemberId

        return if (lastPayerWasNext) {
            // The correct person paid → advance the queue to the next person
            val next = if (nonSkipped.size == 1) nonSkipped.first()
                       else nonSkipped[1]
            NextPayerResult(next, NextPayerReason.ROTATION_ORDER, null)
        } else {
            // Someone paid out of turn → queue does NOT advance, C is still next
            NextPayerResult(frontOfQueue, NextPayerReason.ROTATION_ORDER, null)
        }
    }

    /**
     * Called after a payment is saved. Advances the rotation queue by moving
     * the front member to the back — BUT ONLY if they were the designated next payer.
     * If someone paid out of turn, the queue is left unchanged.
     */
    suspend fun advanceRotationIfNeeded(payerMemberId: Long) {
        val active    = memberDao.getActiveMembers()
        val nonSkipped = active.filter { !it.isSkipped }
        if (nonSkipped.isEmpty()) return

        val frontOfQueue = nonSkipped.first()

        if (frontOfQueue.id == payerMemberId) {
            // Correct person paid → rotate: move front to back
            val maxOrder = active.maxOfOrNull { it.rotationOrder } ?: 0
            memberDao.updateRotationOrder(frontOfQueue.id, maxOrder + 1)
            // Compress to keep order clean
            reorderRotation()
        }
        // If someone else paid → don't touch the queue
    }

    private suspend fun reorderRotation() {
        val all = memberDao.getAllMembers()
        all.forEachIndexed { idx, member ->
            memberDao.updateRotationOrder(member.id, idx)
        }
    }
}
