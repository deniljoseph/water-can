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
import kotlinx.coroutines.flow.MutableStateFlow
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
    val totalGroupSpend: Double = 0.0,
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
        paymentRepository.observeTotalGroupSpend(),
        settingsRepository.observeSettings()
    ) { lastPayment, monthSummary, activeCount, totalSpend, settings ->
        val nextPayer  = memberRepository.resolveNextPayer(lastPayment?.paidByMemberId)
        val lastMember = lastPayment?.paidByMemberId?.let { memberRepository.getMemberById(it) }

        DashboardUiState(
            nextPayerResult   = nextPayer,
            lastPayment       = lastPayment,
            lastPaymentMember = lastMember,
            monthSummary      = monthSummary,
            activeMemberCount = activeCount,
            totalGroupSpend   = totalSpend,
            currencySymbol    = settings.currencySymbol,
            syncState         = syncManager.syncState.value,
            isLoading         = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    fun setManualNextPayer(memberId: Long) = viewModelScope.launch {
        memberRepository.setManualNextPayer(memberId)
    }
}
