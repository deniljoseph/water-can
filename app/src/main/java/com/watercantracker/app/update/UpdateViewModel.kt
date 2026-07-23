package com.watercantracker.app.update

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watercantracker.app.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UpdateUiState(
    val currentVersionName: String = BuildConfig.VERSION_NAME,
    val isChecking: Boolean = false,
    val updateInfo: UpdateInfo? = null,
    val downloadState: DownloadState = DownloadState.Idle,
    val error: String? = null,
    val checkedOnce: Boolean = false
)

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateChecker: UpdateChecker,
    private val updateDownloader: UpdateDownloader
) : ViewModel() {

    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state

    fun checkForUpdate() = viewModelScope.launch {
        _state.update { it.copy(isChecking = true, error = null) }
        when (val result = updateChecker.checkForUpdate()) {
            is UpdateCheckResult.UpdateAvailable -> _state.update {
                it.copy(isChecking = false, updateInfo = result.info, checkedOnce = true)
            }
            is UpdateCheckResult.UpToDate -> _state.update {
                it.copy(isChecking = false, updateInfo = null, checkedOnce = true)
            }
            is UpdateCheckResult.Error -> _state.update {
                it.copy(isChecking = false, error = result.message, checkedOnce = true)
            }
        }
    }

    fun downloadUpdate(context: Context) = viewModelScope.launch {
        val info = _state.value.updateInfo ?: return@launch
        updateDownloader.downloadApk(context, info.apkUrl, info.versionName).collect { progress ->
            _state.update { it.copy(downloadState = progress) }
            if (progress is DownloadState.Done) {
                updateDownloader.installApk(context, info.versionName)
            }
        }
    }

    fun dismissUpdate() = _state.update { it.copy(updateInfo = null) }
    fun clearError() = _state.update { it.copy(error = null) }
}
