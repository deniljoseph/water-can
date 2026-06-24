package com.watercantracker.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
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
    exportSchema = false
)
abstract class WaterCanDatabase : RoomDatabase() {
    abstract fun memberDao(): MemberDao
    abstract fun paymentDao(): PaymentDao
    abstract fun settingsDao(): SettingsDao
    abstract fun notificationDao(): NotificationDao

    companion object {
        @Volatile private var INSTANCE: WaterCanDatabase? = null

        // Used by the Glance widget which cannot access Hilt's DI graph
        fun getInstance(context: Context): WaterCanDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WaterCanDatabase::class.java,
                    "water_can_tracker.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
