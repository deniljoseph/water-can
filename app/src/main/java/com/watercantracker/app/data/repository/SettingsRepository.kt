package com.watercantracker.app.data.repository

import com.watercantracker.app.data.local.dao.SettingsDao
import com.watercantracker.app.data.local.entity.SettingsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(private val settingsDao: SettingsDao) {

    fun observeSettings(): Flow<SettingsEntity> =
        settingsDao.observeSettings().map { it ?: SettingsEntity() }

    suspend fun getSettings(): SettingsEntity = settingsDao.getSettings() ?: SettingsEntity()

    suspend fun saveSettings(settings: SettingsEntity) =
        settingsDao.insertSettings(settings.copy(id = 1))

    private suspend fun update(block: SettingsEntity.() -> SettingsEntity) =
        saveSettings(getSettings().block())

    suspend fun updateTheme(mode: String)               = update { copy(themeMode = mode) }
    suspend fun updateDarkVariant(variant: String)      = update { copy(darkModeVariant = variant) }
    suspend fun updateAccentColor(color: String)        = update { copy(accentColor = color) }
    suspend fun updateReminders(enabled: Boolean)       = update { copy(remindersEnabled = enabled) }
    suspend fun updateOverdueReminders(enabled: Boolean)= update { copy(overdueRemindersEnabled = enabled) }
    suspend fun updateReminderTime(h: Int, m: Int)      = update { copy(reminderHour = h, reminderMinute = m) }
    suspend fun updateDefaultPrice(price: Double)       = update { copy(defaultPricePerCan = price) }
    suspend fun recordMonthlyReset()                    = update { copy(lastMonthlyResetAt = System.currentTimeMillis()) }
    suspend fun updateFirebaseRoom(roomId: String, isMaster: Boolean) =
        update { copy(firebaseRoomId = roomId, isMasterDevice = isMaster, lastSyncAt = System.currentTimeMillis()) }
    suspend fun clearFirebaseRoom() = update { copy(firebaseRoomId = null, isMasterDevice = true, lastSyncAt = null) }
    suspend fun updateLastSync()    = update { copy(lastSyncAt = System.currentTimeMillis()) }
}

    suspend fun updateCansPerTurn(count: Int) = update { copy(cansPerTurn = count.coerceAtLeast(1)) }
