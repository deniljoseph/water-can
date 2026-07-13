package com.watercantracker.app.ui.screens.members

import com.watercantracker.app.data.local.entity.MemberEntity
import com.watercantracker.app.data.local.entity.SettingsEntity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watercantracker.app.data.local.entity.SettledDebtEntity
import com.watercantracker.app.data.repository.MemberRepository
import com.watercantracker.app.data.repository.PaymentRepository
import com.watercantracker.app.data.repository.SettledDebtRepository
import com.watercantracker.app.data.repository.SettingsRepository
import com.watercantracker.app.domain.model.MemberBalance
import com.watercantracker.app.domain.model.MemberStats
import com.watercantracker.app.sync.FirebaseSyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MembersUiState(
    val members: List<MemberEntity> = emptyList(),
    val memberStats: List<MemberStats> = emptyList(),
    val memberBalances: List<MemberBalance> = emptyList(),
    val settings: SettingsEntity = SettingsEntity(),
    val groupAverageContribution: Double = 0.0,
    val totalGroupSpend: Double = 0.0,
    val isLoading: Boolean = true,
    val pendingDeleteMember: MemberEntity? = null,
    val settleSuccess: String? = null
)

@HiltViewModel
class MembersViewModel @Inject constructor(
    private val memberRepository: MemberRepository,
    private val paymentRepository: PaymentRepository,
    private val settingsRepository: SettingsRepository,
    private val settledDebtRepository: SettledDebtRepository,
    private val syncManager: FirebaseSyncManager
) : ViewModel() {

    private val _settleSuccess = MutableStateFlow<String?>(null)

    // Step 1: base data (5 flows max per combine overload)
    private val _baseFlow = combine(
        memberRepository.observeAllMembers(),
        paymentRepository.observeMemberStats(),
        paymentRepository.observeTotalGroupSpend(),
        settingsRepository.observeSettings(),
        settledDebtRepository.observeAllSettledDebts()
    ) { members, stats, totalSpend, settings, settledDebts ->
        listOf(members, stats, totalSpend, settings, settledDebts)
    }

    val uiState: StateFlow<MembersUiState> = combine(
        _baseFlow,
        _settleSuccess
    ) { baseList, settleMsg ->
        @Suppress("UNCHECKED_CAST")
        val members      = baseList[0] as List<MemberEntity>
        @Suppress("UNCHECKED_CAST")
        val stats        = baseList[1] as List<MemberStats>
        val totalSpend    = baseList[2] as Double
        val settings      = baseList[3] as SettingsEntity
        @Suppress("UNCHECKED_CAST")
        val settledDebts  = baseList[4] as List<SettledDebtEntity>

        val activeMembers = members.filter { it.isActive }
        val avg = if (stats.isNotEmpty()) stats.sumOf { it.totalAmountContributed } / stats.size else 0.0
        val fairShare = if (activeMembers.isNotEmpty()) totalSpend / activeMembers.size else 0.0

        val balances = activeMembers.map { member ->
            val paid = stats.firstOrNull { it.memberId == member.id }?.totalAmountContributed ?: 0.0
            // Settlements adjust the raw balance: money a debtor sent reduces what they still
            // owe; money a creditor received reduces what they're still owed.
            val paidOut  = settledDebts.filter { it.fromMemberId == member.id }.sumOf { it.amount }
            val received = settledDebts.filter { it.toMemberId == member.id }.sumOf { it.amount }
            val rawBalance = paid - fairShare
            // A debtor (negative balance) who pays out settlement money moves toward zero (+paidOut).
            // A creditor (positive balance) who receives settlement money moves toward zero (-received).
            val adjustedBalance = rawBalance + paidOut - received

            MemberBalance(
                memberId   = member.id,
                memberName = member.name,
                avatarUri  = member.avatarUri,
                isActive   = member.isActive,
                totalPaid  = paid,
                fairShare  = fairShare,
                netBalance = adjustedBalance
            )
        }.sortedBy { it.netBalance }

        MembersUiState(
            members                  = members,
            memberStats              = stats,
            memberBalances           = balances,
            settings                 = settings,
            groupAverageContribution = avg,
            totalGroupSpend          = totalSpend,
            isLoading                = false,
            settleSuccess            = settleMsg
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MembersUiState())

    fun addMember(name: String, phone: String?, avatarUri: String?) = viewModelScope.launch {
        val id = memberRepository.addMember(name, phone, avatarUri)
        val settings = settingsRepository.getSettings()
        settings.firebaseRoomId?.let { roomId ->
            memberRepository.getMemberById(id)?.let { syncManager.pushMember(roomId, it) }
        }
    }

    fun updateMember(member: MemberEntity) = viewModelScope.launch {
        memberRepository.updateMember(member)
        val settings = settingsRepository.getSettings()
        settings.firebaseRoomId?.let { roomId -> syncManager.pushMember(roomId, member) }
    }

    fun deleteMember(member: MemberEntity) = viewModelScope.launch {
        memberRepository.deleteMember(member)
        val settings = settingsRepository.getSettings()
        settings.firebaseRoomId?.let { roomId ->
            member.firebaseSyncId?.let { fbId -> syncManager.deleteMember(roomId, fbId) }
        }
    }

    fun setActiveStatus(memberId: Long, isActive: Boolean) = viewModelScope.launch {
        memberRepository.setActiveStatus(memberId, isActive)
        val settings = settingsRepository.getSettings()
        settings.firebaseRoomId?.let { roomId ->
            memberRepository.getMemberById(memberId)?.let { syncManager.pushMember(roomId, it) }
        }
    }

    fun setManualNextPayer(memberId: Long) = viewModelScope.launch { memberRepository.setManualNextPayer(memberId) }
    fun skipMember(memberId: Long) = viewModelScope.launch { memberRepository.skipMember(memberId) }
    fun moveUp(memberId: Long)     = viewModelScope.launch { memberRepository.moveUp(memberId) }
    fun moveDown(memberId: Long)   = viewModelScope.launch { memberRepository.moveDown(memberId) }

    /**
     * Records a settlement: [debtorId] pays [creditorId] the given [amount] to
     * clear (or reduce) the debt shown on the Balances tab. This doesn't affect
     * group spending totals or can quotas — it's purely a balance adjustment.
     */
    fun settleDebt(debtorId: Long, debtorName: String, creditorId: Long, creditorName: String, amount: Double) =
        viewModelScope.launch {
            settledDebtRepository.recordSettlement(debtorId, debtorName, creditorId, creditorName, amount)
            _settleSuccess.update { "$debtorName settled ${String.format("%.2f", amount)} with $creditorName" }
        }

    fun clearSettleSuccess() = _settleSuccess.update { null }
}
