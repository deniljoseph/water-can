package com.watercantracker.app.sync

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watercantracker.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SyncUiState(
    val syncState: SyncState = SyncState(),
    val roomId: String? = null,
    val isMaster: Boolean = true,
    val qrBitmap: Bitmap? = null,
    val isCreatingRoom: Boolean = false,
    val isJoiningRoom: Boolean = false,
    val joinRoomId: String = "",
    val error: String? = null
)

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val syncManager: FirebaseSyncManager,
    private val qrHelper: QrCodeHelper,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _qrBitmap   = MutableStateFlow<Bitmap?>(null)
    private val _joinRoomId  = MutableStateFlow("")
    private val _isCreating  = MutableStateFlow(false)
    private val _isJoining   = MutableStateFlow(false)

    val uiState: StateFlow<SyncUiState> = combine(
        syncManager.syncState,
        settingsRepository.observeSettings(),
        _qrBitmap,
        _joinRoomId
    ) { syncState, settings, qr, joinId ->
        SyncUiState(
            syncState      = syncState,
            roomId         = settings.firebaseRoomId,
            isMaster       = settings.isMasterDevice,
            qrBitmap       = qr,
            isCreatingRoom = _isCreating.value,
            isJoiningRoom  = _isJoining.value,
            joinRoomId     = joinId,
            error          = syncState.error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncUiState())

    init {
        // Re-attach listener if we already have a room from a previous session
        viewModelScope.launch {
            val settings = settingsRepository.getSettings()
            val roomId = settings.firebaseRoomId
            if (roomId != null) {
                syncManager.startListening(roomId, settings.isMasterDevice)
                if (settings.isMasterDevice) {
                    _qrBitmap.update { qrHelper.generateRoomQr(roomId) }
                }
            }
        }
    }

    fun createRoom() = viewModelScope.launch {
        if (_isCreating.value) return@launch
        _isCreating.update { true }
        try {
            val roomId = syncManager.createRoom()
            _qrBitmap.update { qrHelper.generateRoomQr(roomId) }
        } catch (e: Exception) {
            // Error state is already set inside FirebaseSyncManager
        } finally {
            _isCreating.update { false }
        }
    }

    fun setJoinRoomId(id: String) = _joinRoomId.update { id.trim() }

    fun joinRoom() = viewModelScope.launch {
        val roomId = _joinRoomId.value.trim()
        if (roomId.isBlank() || _isJoining.value) return@launch
        _isJoining.update { true }
        try {
            syncManager.joinRoom(roomId)
        } finally {
            _isJoining.update { false }
        }
    }

    fun disconnect() = viewModelScope.launch {
        syncManager.disconnect()
        _qrBitmap.update { null }
        _joinRoomId.update { "" }
    }
}
