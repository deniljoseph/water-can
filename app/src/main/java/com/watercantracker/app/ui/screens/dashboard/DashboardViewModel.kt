package com.watercantracker.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watercantracker.app.data.local.entity.MemberEntity
import com.watercantracker.app.data.local.entity.PaymentEntity
import com.watercantracker.app.data.local.entity.SettingsEntity
import com.watercantracker.app.data.repository.MemberRepository
import com.watercantracker.app.data.repository.PaymentRepository
import com.watercantracker.app.data.repository.SettingsRepository
import com.watercantracker.app.domain.model.MonthlySpendingSummary
import com.watercantracker.app.domain.model.NextPayerResult
import com.watercantracker.app.sync.FirebaseSyncManager
import com.watercantracker.app.sync.SyncState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val nextPayerResult: NextPayerResult? = null,
    val nextPayerMember: MemberEntity? = null,
    val lastPayment: PaymentEntity? = null,
    val lastPaymentMember: MemberEntity? = null,
    val monthSummary: MonthlySpendingSummary? = null,
    val activeMemberCount: Int = 0,
    val totalGroupSpend: Double = 0.0,
    val currencySymbol: String = "AED",
    val cansPerTurn: Int = 1,
    val cansPaidThisTurn: Int = 0,
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

    // Split into two combines (max 5 params each) to avoid the array-style overload
    private val _baseFlow = combine(
        paymentRepository.observeLastPayment(),
        paymentRepository.observeCurrentMonthSummary(),
        memberRepository.observeActiveMemberCount(),
        paymentRepository.observeTotalGroupSpend(),
        settingsRepository.observeSettings()
    ) { lastPayment, monthSummary, activeCount, totalSpend, settings ->
        listOf(lastPayment, monthSummary, activeCount, totalSpend, settings)
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        _baseFlow,
        memberRepository.observeActiveMembers()
    ) { baseList, activeMembers ->
        @Suppress("UNCHECKED_CAST")
        val lastPayment   = baseList[0] as? PaymentEntity
        @Suppress("UNCHECKED_CAST")
        val monthSummary  = baseList[1] as? MonthlySpendingSummary
        val activeCount   = baseList[2] as Int
        val totalSpend    = baseList[3] as Double
        val settings      = baseList[4] as SettingsEntity

        val nextPayer  = memberRepository.resolveNextPayer(lastPayment?.paidByMemberId)
        val lastMember = lastPayment?.paidByMemberId?.let {
            activeMembers.firstOrNull { m -> m.id == it }
        }
        val nextMember = nextPayer.member
        val cansPaid   = nextMember?.cansPaidThisTurn ?: 0

        DashboardUiState(
            nextPayerResult   = nextPayer,
            nextPayerMember   = nextMember,
            lastPayment       = lastPayment,
            lastPaymentMember = lastMember,
            monthSummary      = monthSummary,
            activeMemberCount = activeCount,
            totalGroupSpend   = totalSpend,
            currencySymbol    = settings.currencySymbol,
            cansPerTurn       = settings.cansPerTurn,
            cansPaidThisTurn  = cansPaid,
            syncState         = syncManager.syncState.value,
            isLoading         = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    fun setManualNextPayer(memberId: Long) = viewModelScope.launch {
        memberRepository.setManualNextPayer(memberId)
    }

    fun refresh(onDone: () -> Unit) = viewModelScope.launch {
        try {
            val settings = settingsRepository.getSettings()
            settings.firebaseRoomId?.let { roomId ->
                syncManager.startListening(roomId, settings.isMasterDevice)
            }
        } finally {
            delay(800)
            onDone()
        }
    }
}
