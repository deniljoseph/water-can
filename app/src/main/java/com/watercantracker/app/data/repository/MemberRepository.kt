package com.watercantracker.app.data.repository

import com.watercantracker.app.data.local.dao.MemberDao
import com.watercantracker.app.data.local.entity.MemberEntity
import com.watercantracker.app.domain.model.NextPayerReason
import com.watercantracker.app.domain.model.NextPayerResult
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit
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

    suspend fun addMember(
        name: String,
        phoneNumber: String?,
        avatarUri: String?
    ): Long {
        val nextOrder = (memberDao.getMaxRotationOrder() ?: -1) + 1
        val member = MemberEntity(
            name = name.trim(),
            phoneNumber = phoneNumber?.trim()?.takeIf { it.isNotEmpty() },
            avatarUri = avatarUri,
            rotationOrder = nextOrder
        )
        return memberDao.insertMember(member)
    }

    suspend fun updateMember(member: MemberEntity) = memberDao.updateMember(member)

    suspend fun deleteMember(member: MemberEntity) {
        memberDao.deleteMember(member)
        // Compress rotation order to remove gaps
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
            val current = all[idx]
            val above = all[idx - 1]
            memberDao.updateRotationOrder(current.id, above.rotationOrder)
            memberDao.updateRotationOrder(above.id, current.rotationOrder)
        }
    }

    suspend fun moveDown(memberId: Long) {
        val all = memberDao.getAllMembers().toMutableList()
        val idx = all.indexOfFirst { it.id == memberId }
        if (idx >= 0 && idx < all.size - 1) {
            val current = all[idx]
            val below = all[idx + 1]
            memberDao.updateRotationOrder(current.id, below.rotationOrder)
            memberDao.updateRotationOrder(below.id, current.rotationOrder)
        }
    }

    /**
     * Resolves who should pay next using the following priority:
     * 1. Manual override (isManualNextPayer = true)
     * 2. First active, non-skipped member in rotation order who hasn't paid most recently
     */
    suspend fun resolveNextPayer(lastPayerMemberId: Long?): NextPayerResult {
        val active = memberDao.getActiveMembers()
        if (active.isEmpty()) {
            return NextPayerResult(null, NextPayerReason.NO_ACTIVE_MEMBERS, null)
        }

        val manual = memberDao.getManualNextPayer()
        if (manual != null && manual.isActive) {
            return NextPayerResult(manual, NextPayerReason.MANUAL_OVERRIDE, null)
        }

        // Rotate: find who comes after the last payer in the sorted list, skipping skipped members
        val nonSkipped = active.filter { !it.isSkipped }
        if (nonSkipped.isEmpty()) {
            return NextPayerResult(active.first(), NextPayerReason.ALL_SKIPPED, null)
        }

        if (lastPayerMemberId == null) {
            return NextPayerResult(nonSkipped.first(), NextPayerReason.ROTATION_ORDER, null)
        }

        val lastIdx = nonSkipped.indexOfFirst { it.id == lastPayerMemberId }
        val nextMember = if (lastIdx == -1 || lastIdx == nonSkipped.size - 1) {
            nonSkipped.first()
        } else {
            nonSkipped[lastIdx + 1]
        }
        return NextPayerResult(nextMember, NextPayerReason.ROTATION_ORDER, null)
    }

    private suspend fun reorderRotation() {
        val all = memberDao.getAllMembers()
        all.forEachIndexed { idx, member ->
            memberDao.updateRotationOrder(member.id, idx)
        }
    }
}
