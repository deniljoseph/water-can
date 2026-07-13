package com.watercantracker.app.data.repository

import android.content.Context
import com.watercantracker.app.data.local.dao.MemberDao
import com.watercantracker.app.data.local.dao.PaymentDao
import com.watercantracker.app.data.local.entity.MemberEntity
import com.watercantracker.app.domain.model.NextPayerReason
import com.watercantracker.app.domain.model.NextPayerResult
import com.watercantracker.app.notification.TurnNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemberRepository @Inject constructor(
    private val memberDao: MemberDao,
    private val paymentDao: PaymentDao,
    @ApplicationContext private val context: Context,
    private val turnNotifier: TurnNotifier
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
     * Called after every payment save. The payer's OWN can quota always updates,
     * regardless of queue position. If this completes their quota, they rotate
     * to the back and a push notification fires immediately for whoever is now
     * at the front of the queue.
     */
    suspend fun advanceRotationIfNeeded(payerMemberId: Long, cansJustPaid: Int, cansPerTurn: Int) {
        val payer = memberDao.getMemberById(payerMemberId) ?: return
        val newTotal = payer.cansPaidThisTurn + cansJustPaid

        if (newTotal >= cansPerTurn) {
            val active   = memberDao.getActiveMembers()
            val maxOrder = active.maxOfOrNull { it.rotationOrder } ?: 0
            memberDao.updateRotationOrder(payer.id, maxOrder + 1)
            memberDao.updateCansPaidThisTurn(payer.id, 0)
            reorderRotation()

            // Notify whoever is now at the front of the queue — instant, not daily
            val newNext = resolveNextPayer(payerMemberId)
            newNext.member?.let { nextMember ->
                if (nextMember.id != payer.id) {
                    turnNotifier.notifyTurnChanged(context, nextMember.name)
                }
            }
        } else {
            memberDao.updateCansPaidThisTurn(payer.id, newTotal)
        }
    }

    private suspend fun reorderRotation() {
        memberDao.getAllMembers().forEachIndexed { idx, member ->
            memberDao.updateRotationOrder(member.id, idx)
        }
    }
}
