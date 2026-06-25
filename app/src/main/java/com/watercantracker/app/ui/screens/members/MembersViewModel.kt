package com.watercantracker.app.ui.screens.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watercantracker.app.data.local.entity.MemberEntity
import com.watercantracker.app.data.local.entity.SettingsEntity
import com.watercantracker.app.data.repository.MemberRepository
import com.watercantracker.app.data.repository.PaymentRepository
import com.watercantracker.app.data.repository.SettingsRepository
import com.watercantracker.app.domain.model.MemberBalance
import com.watercantracker.app.domain.model.MemberStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
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
    val pendingDeleteMember: MemberEntity? = null
)

@HiltViewModel
class MembersViewModel @Inject constructor(
    private val memberRepository: MemberRepository,
    private val paymentRepository: PaymentRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<MembersUiState> = combine(
        memberRepository.observeAllMembers(),
        paymentRepository.observeMemberStats(),
        paymentRepository.observeTotalGroupSpend(),
        settingsRepository.observeSettings()
    ) { members, stats, totalSpend, settings ->
        val activeMembers = members.filter { it.isActive }
        val avg = if (stats.isNotEmpty()) stats.sumOf { it.totalAmountContributed } / stats.size else 0.0

        // Compute balances inline — fair share = totalSpend / activeMembers
        val fairShare = if (activeMembers.isNotEmpty()) totalSpend / activeMembers.size else 0.0
        val balances = activeMembers.map { member ->
            val paid = stats.firstOrNull { it.memberId == member.id }?.totalAmountContributed ?: 0.0
            MemberBalance(
                memberId   = member.id,
                memberName = member.name,
                avatarUri  = member.avatarUri,
                isActive   = member.isActive,
                totalPaid  = paid,
                fairShare  = fairShare,
                netBalance = paid - fairShare
            )
        }.sortedBy { it.netBalance }

        MembersUiState(
            members                 = members,
            memberStats             = stats,
            memberBalances          = balances,
            settings                = settings,
            groupAverageContribution = avg,
            totalGroupSpend         = totalSpend,
            isLoading               = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MembersUiState())

    fun addMember(name: String, phone: String?, avatarUri: String?) = viewModelScope.launch {
        memberRepository.addMember(name, phone, avatarUri)
    }
    fun updateMember(member: MemberEntity) = viewModelScope.launch { memberRepository.updateMember(member) }
    fun deleteMember(member: MemberEntity) = viewModelScope.launch { memberRepository.deleteMember(member) }
    fun setActiveStatus(memberId: Long, isActive: Boolean) = viewModelScope.launch { memberRepository.setActiveStatus(memberId, isActive) }
    fun setManualNextPayer(memberId: Long) = viewModelScope.launch { memberRepository.setManualNextPayer(memberId) }
    fun skipMember(memberId: Long) = viewModelScope.launch { memberRepository.skipMember(memberId) }
    fun moveUp(memberId: Long) = viewModelScope.launch { memberRepository.moveUp(memberId) }
    fun moveDown(memberId: Long) = viewModelScope.launch { memberRepository.moveDown(memberId) }
}
