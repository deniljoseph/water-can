package com.watercantracker.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.watercantracker.app.data.local.dao.MemberDao
import com.watercantracker.app.data.local.dao.NotificationDao
import com.watercantracker.app.data.local.dao.PaymentDao
import com.watercantracker.app.data.local.dao.SettingsDao
import com.watercantracker.app.data.local.entity.MemberEntity
import com.watercantracker.app.data.local.entity.NotificationEntity
import com.watercantracker.app.data.local.entity.PaymentEntity
import com.watercantracker.app.data.local.entity.SettingsEntity

@Database(
    entities = [
        MemberEntity::class,
        PaymentEntity::class,
        SettingsEntity::class,
        NotificationEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class WaterCanDatabase : RoomDatabase() {
    abstract fun memberDao(): MemberDao
    abstract fun paymentDao(): PaymentDao
    abstract fun settingsDao(): SettingsDao
    abstract fun notificationDao(): NotificationDao
}

// Companion object needed for widget (Glance widgets can't use Hilt DI)
// The Room singleton is shared if Hilt already built it; otherwise a new instance is created.
private var _instance: WaterCanDatabase? = null

fun WaterCanDatabase.Companion.getInstance(context: android.content.Context): WaterCanDatabase {
    return _instance ?: synchronized(WaterCanDatabase::class.java) {
        _instance ?: androidx.room.Room.databaseBuilder(
            context.applicationContext,
            WaterCanDatabase::class.java,
            "water_can_tracker.db"
        ).fallbackToDestructiveMigration().build().also { _instance = it }
    }
}
