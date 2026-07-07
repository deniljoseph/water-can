package com.watercantracker.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.watercantracker.app.data.local.dao.JointPaymentDao
import com.watercantracker.app.data.local.dao.MemberDao
import com.watercantracker.app.data.local.dao.NotificationDao
import com.watercantracker.app.data.local.dao.PaymentDao
import com.watercantracker.app.data.local.dao.SettlementDao
import com.watercantracker.app.data.local.dao.SettingsDao
import com.watercantracker.app.data.local.entity.JointPaymentContributorEntity
import com.watercantracker.app.data.local.entity.MemberEntity
import com.watercantracker.app.data.local.entity.NotificationEntity
import com.watercantracker.app.data.local.entity.PaymentEntity
import com.watercantracker.app.data.local.entity.SettlementEntity
import com.watercantracker.app.data.local.entity.SettingsEntity
import com.watercantracker.app.data.local.migration.MIGRATION_1_2
import com.watercantracker.app.data.local.migration.MIGRATION_2_3
import com.watercantracker.app.data.local.migration.MIGRATION_3_4
import com.watercantracker.app.data.local.migration.MIGRATION_4_5

@Database(
    entities = [
        MemberEntity::class,
        PaymentEntity::class,
        SettingsEntity::class,
        NotificationEntity::class,
        SettlementEntity::class,
        JointPaymentContributorEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class WaterCanDatabase : RoomDatabase() {
    abstract fun memberDao(): MemberDao
    abstract fun paymentDao(): PaymentDao
    abstract fun settingsDao(): SettingsDao
    abstract fun notificationDao(): NotificationDao
    abstract fun settlementDao(): SettlementDao
    abstract fun jointPaymentDao(): JointPaymentDao

    companion object {
        @Volatile private var INSTANCE: WaterCanDatabase? = null

        fun getInstance(context: Context): WaterCanDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WaterCanDatabase::class.java,
                    "water_can_tracker.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
