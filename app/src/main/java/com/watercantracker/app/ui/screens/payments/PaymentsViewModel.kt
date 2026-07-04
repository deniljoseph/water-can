package com.watercantracker.app.ui.screens.payments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watercantracker.app.data.local.entity.MemberEntity
import com.watercantracker.app.data.local.entity.PaymentEntity
import com.watercantracker.app.data.repository.MemberRepository
import com.watercantracker.app.data.repository.PaymentRepository
import com.watercantracker.app.data.repository.SettingsRepository
import com.watercantracker.app.sync.FirebaseSyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PaymentsFilter(
    val query: String = "",
    val memberId: Long? = null,
    val startDate: Long? = null,
    val endDate: Long? = null
)

data class PaymentsUiState(
    val payments: List<PaymentEntity> = emptyList(),
    val members: List<MemberEntity> = emptyList(),
    val filter: PaymentsFilter = PaymentsFilter(),
    val isLoading: Boolean = true
)

@HiltViewModel
class PaymentsViewModel @Inject constructor(
    private val paymentRepository: PaymentRepository,
    private val memberRepository: MemberRepository,
    private val settingsRepository: SettingsRepository,
    private val syncManager: FirebaseSyncManager
) : ViewModel() {

    private val _filter = MutableStateFlow(PaymentsFilter())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _payments = _filter.flatMapLatest { f ->
        paymentRepository.observeFilteredPayments(
            memberId  = f.memberId,
            startDate = f.startDate,
            endDate   = f.endDate,
            query     = f.query
        )
    }

    val uiState: StateFlow<PaymentsUiState> = combine(
        _payments,
        memberRepository.observeAllMembers(),
        _filter
    ) { payments, members, filter ->
        PaymentsUiState(payments = payments, members = members, filter = filter, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PaymentsUiState())

    fun updateFilter(filter: PaymentsFilter) { _filter.update { filter } }

    fun addPayment(
        quantity: Int, amount: Double, paidByMember: MemberEntity,
        purchaseDate: Long, notes: String?, vendorName: String?, receiptImageUri: String?
    ) = viewModelScope.launch {
        val id = paymentRepository.addPayment(
            quantity, amount, paidByMember.id, paidByMember.name,
            purchaseDate, notes, vendorName, receiptImageUri
        )
        memberRepository.clearManualNextPayer()
        // Advance rotation ONLY if the correct person paid; out-of-turn payments leave queue intact
        memberRepository.advanceRotationIfNeeded(paidByMember.id)

        // Push to Firebase if sync is active
        val settings = settingsRepository.getSettings()
        settings.firebaseRoomId?.let { roomId ->
            val payment = paymentRepository.getPaymentById(id)
            payment?.let { syncManager.pushPayment(roomId, it) }
        }
    }

    fun updatePayment(payment: PaymentEntity) = viewModelScope.launch {
        paymentRepository.updatePayment(payment)
        val settings = settingsRepository.getSettings()
        settings.firebaseRoomId?.let { roomId ->
            syncManager.pushPayment(roomId, payment)
        }
    }

    fun deletePayment(payment: PaymentEntity) = viewModelScope.launch {
        paymentRepository.deletePayment(payment)
        val settings = settingsRepository.getSettings()
        settings.firebaseRoomId?.let { roomId ->
            payment.firebaseSyncId?.let { fbId ->
                syncManager.deletePayment(roomId, fbId)
            }
        }
    }
}
