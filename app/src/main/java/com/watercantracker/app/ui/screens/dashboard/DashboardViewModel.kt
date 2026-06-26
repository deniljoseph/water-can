package com.watercantracker.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watercantracker.app.data.local.entity.MemberEntity
import com.watercantracker.app.data.local.entity.PaymentEntity
import com.watercantracker.app.data.repository.MemberRepository
import com.watercantracker.app.data.repository.PaymentRepository
import com.watercantracker.app.data.repository.SettingsRepository
import com.watercantracker.app.domain.model.MemberBalance
import com.watercantracker.app.domain.model.MonthlySpendingSummary
import com.watercantracker.app.domain.model.NextPayerResult
import com.watercantracker.app.sync.FirebaseSyncManager
import com.watercantracker.app.sync.SyncState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val nextPayerResult: NextPayerResult? = null,
    val lastPayment: PaymentEntity? = null,
    val lastPaymentMember: MemberEntity? = null,
    val monthSummary: MonthlySpendingSummary? = null,
    val activeMemberCount: Int = 0,
    val memberBalances: List<MemberBalance> = emptyList(),
    val currencySymbol: String = "AED",
    val syncState: SyncState = SyncState(),
    val isLoading: Boolean = true
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val memberRepository: MemberRepository,
    private val paymentRepository: PaymentRepository,
    private val settingsRepository: SettingsRepository,
    private val syncManager: FirebaseSyncManager
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        paymentRepository.observeLastPayment(),
        paymentRepository.observeCurrentMonthSummary(),
        memberRepository.observeActiveMemberCount(),
        memberRepository.observeActiveMembers(),
        settingsRepository.observeSettings()
    ) { lastPayment, monthSummary, activeCount, activeMembers, settings ->
        val nextPayer  = memberRepository.resolveNextPayer(lastPayment?.paidByMemberId)
        val lastMember = lastPayment?.paidByMemberId?.let { memberRepository.getMemberById(it) }

        // Compute balances for dashboard partial-payment widget
        val totalSpend = paymentRepository.observeTotalGroupSpend()
            .let { flow ->
                var v = 0.0
                try { kotlinx.coroutines.flow.first(flow).also { v = it } } catch (_: Exception) {}
                v
            }
        val fairShare = if (activeMembers.isNotEmpty()) totalSpend / activeMembers.size else 0.0
        val balances = activeMembers.map { member ->
            val paid = 0.0 // simplified — full balance computed in MembersScreen
            MemberBalance(
                memberId   = member.id,
                memberName = member.name,
                avatarUri  = member.avatarUri,
                isActive   = true,
                totalPaid  = paid,
                fairShare  = fairShare,
                netBalance = paid - fairShare
            )
        }

        DashboardUiState(
            nextPayerResult    = nextPayer,
            lastPayment        = lastPayment,
            lastPaymentMember  = lastMember,
            monthSummary       = monthSummary,
            activeMemberCount  = activeCount,
            memberBalances     = balances,
            currencySymbol     = settings.currencySymbol,
            syncState          = syncManager.syncState.value,
            isLoading          = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    fun setManualNextPayer(memberId: Long) = viewModelScope.launch {
        memberRepository.setManualNextPayer(memberId)
    }
}
