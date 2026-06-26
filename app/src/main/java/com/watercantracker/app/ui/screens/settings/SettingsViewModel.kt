package com.watercantracker.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.watercantracker.app.data.local.entity.SettingsEntity
import com.watercantracker.app.data.repository.SettingsRepository
import com.watercantracker.app.notification.ReminderWorker
import com.watercantracker.app.ui.theme.AccentColor
import com.watercantracker.app.ui.theme.AppThemeMode
import com.watercantracker.app.ui.theme.DarkModeVariant
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val settings: SettingsEntity = SettingsEntity(),
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val darkModeVariant: DarkModeVariant = DarkModeVariant.DARK,
    val accentColor: AccentColor = AccentColor.TEAL
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val workManager: WorkManager
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = settingsRepository.observeSettings().map { s ->
        SettingsUiState(
            settings        = s,
            themeMode       = when (s.themeMode) { "LIGHT" -> AppThemeMode.LIGHT; "DARK" -> AppThemeMode.DARK; else -> AppThemeMode.SYSTEM },
            darkModeVariant = DarkModeVariant.values().firstOrNull { it.name == s.darkModeVariant } ?: DarkModeVariant.DARK,
            accentColor     = AccentColor.values().firstOrNull { it.name == s.accentColor } ?: AccentColor.TEAL
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun updateTheme(mode: AppThemeMode)             = viewModelScope.launch { settingsRepository.updateTheme(mode.name) }
    fun updateDarkVariant(v: DarkModeVariant)       = viewModelScope.launch { settingsRepository.updateDarkVariant(v.name) }
    fun updateAccentColor(c: AccentColor)           = viewModelScope.launch { settingsRepository.updateAccentColor(c.name) }
    fun setRemindersEnabled(enabled: Boolean)       = viewModelScope.launch {
        settingsRepository.updateReminders(enabled)
        if (enabled) ReminderWorker.schedule(workManager) else ReminderWorker.cancel(workManager)
    }
    fun setOverdueRemindersEnabled(enabled: Boolean)= viewModelScope.launch { settingsRepository.updateOverdueReminders(enabled) }
    fun setReminderTime(h: Int, m: Int)             = viewModelScope.launch { settingsRepository.updateReminderTime(h, m) }
    fun setDefaultPrice(price: Double)              = viewModelScope.launch { settingsRepository.updateDefaultPrice(price) }
    fun recordMonthlyReset()                        = viewModelScope.launch { settingsRepository.recordMonthlyReset() }
}
