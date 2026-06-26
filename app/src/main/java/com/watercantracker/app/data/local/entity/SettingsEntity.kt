package com.watercantracker.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val themeMode: String = "SYSTEM",
    val darkModeVariant: String = "DARK",
    val accentColor: String = "TEAL",
    val defaultPricePerCan: Double = 0.0,
    val remindersEnabled: Boolean = true,
    val overdueRemindersEnabled: Boolean = true,
    val reminderHour: Int = 9,
    val reminderMinute: Int = 0,
    val overdueThresholdDays: Int = 3,
    val lastMonthlyResetAt: Long? = null,
    val currencySymbol: String = "AED",
    val firebaseRoomId: String? = null,
    val isMasterDevice: Boolean = true,
    val lastSyncAt: Long? = null
)
