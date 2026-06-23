package com.watercantracker.app.data.repository

import com.watercantracker.app.data.local.dao.SettingsDao
import com.watercantracker.app.data.local.entity.SettingsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val settingsDao: SettingsDao
) {
    fun observeSettings(): Flow<SettingsEntity> =
        settingsDao.observeSettings().map { it ?: SettingsEntity() }

    suspend fun getSettings(): SettingsEntity =
        settingsDao.getSettings() ?: SettingsEntity()

    suspend fun saveSettings(settings: SettingsEntity) {
        settingsDao.insertSettings(settings.copy(id = 1))
    }

    suspend fun updateTheme(mode: String) {
        val s = getSettings()
        saveSettings(s.copy(themeMode = mode))
    }

    suspend fun updateReminders(enabled: Boolean) {
        val s = getSettings()
        saveSettings(s.copy(remindersEnabled = enabled))
    }

    suspend fun updateOverdueReminders(enabled: Boolean) {
        val s = getSettings()
        saveSettings(s.copy(overdueRemindersEnabled = enabled))
    }

    suspend fun updateReminderTime(hour: Int, minute: Int) {
        val s = getSettings()
        saveSettings(s.copy(reminderHour = hour, reminderMinute = minute))
    }

    suspend fun updateDefaultPrice(price: Double) {
        val s = getSettings()
        saveSettings(s.copy(defaultPricePerCan = price))
    }

    suspend fun recordMonthlyReset() {
        val s = getSettings()
        saveSettings(s.copy(lastMonthlyResetAt = System.currentTimeMillis()))
    }
}
