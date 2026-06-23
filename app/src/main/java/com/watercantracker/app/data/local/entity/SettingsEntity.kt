package com.watercantracker.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Singleton settings row (id is always 1). Using a Room entity (rather than only DataStore)
 * satisfies the "Settings table" requirement while keeping it queryable alongside other data
 * for backup/restore purposes.
 */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey
    val id: Int = 1,
    val themeMode: String = "SYSTEM", // LIGHT, DARK, SYSTEM
    val defaultPricePerCan: Double = 0.0,
    val remindersEnabled: Boolean = true,
    val overdueRemindersEnabled: Boolean = true,
    val reminderHour: Int = 9,
    val reminderMinute: Int = 0,
    val overdueThresholdDays: Int = 3,
    val lastMonthlyResetAt: Long? = null,
    val currencySymbol: String = "$"
)
