package com.watercantracker.app.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database migrations — never use fallbackToDestructiveMigration in release builds.
 * Each migration runs the minimal SQL to evolve the schema without losing data.
 *
 * Version history:
 *  1 → 2 : Added settlements table
 *  2 → 3 : Added joint_payment_contributors table + firebaseSyncId columns
 *           + darkModeVariant / accentColor to settings
 */

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `settlements` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `month` INTEGER NOT NULL,
                `year` INTEGER NOT NULL,
                `generatedAt` INTEGER NOT NULL,
                `totalSpent` REAL NOT NULL,
                `fairShare` REAL NOT NULL,
                `memberCount` INTEGER NOT NULL,
                `transactionCount` INTEGER NOT NULL,
                `settlementJson` TEXT NOT NULL
            )
        """)
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Joint payment contributors table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `joint_payment_contributors` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `paymentId` INTEGER NOT NULL,
                `memberId` INTEGER NOT NULL,
                `memberNameSnapshot` TEXT NOT NULL,
                `amountContributed` REAL NOT NULL,
                FOREIGN KEY(`paymentId`) REFERENCES `payments`(`id`) ON DELETE CASCADE,
                FOREIGN KEY(`memberId`) REFERENCES `members`(`id`) ON DELETE SET NULL
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS `idx_jpc_paymentId` ON `joint_payment_contributors`(`paymentId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `idx_jpc_memberId` ON `joint_payment_contributors`(`memberId`)")

        // Firebase sync ID on payments
        db.execSQL("ALTER TABLE `payments` ADD COLUMN `firebaseSyncId` TEXT")

        // Firebase sync ID on members
        db.execSQL("ALTER TABLE `members` ADD COLUMN `firebaseSyncId` TEXT")

        // Settings: darkModeVariant + accentColor
        db.execSQL("ALTER TABLE `settings` ADD COLUMN `darkModeVariant` TEXT NOT NULL DEFAULT 'DARK'")
        db.execSQL("ALTER TABLE `settings` ADD COLUMN `accentColor` TEXT NOT NULL DEFAULT 'TEAL'")

        // Settings: sync config
        db.execSQL("ALTER TABLE `settings` ADD COLUMN `firebaseRoomId` TEXT")
        db.execSQL("ALTER TABLE `settings` ADD COLUMN `isMasterDevice` INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE `settings` ADD COLUMN `lastSyncAt` INTEGER")
    }
}
