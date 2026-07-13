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

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `settings` ADD COLUMN `cansPerTurn` INTEGER NOT NULL DEFAULT 1")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `members` ADD COLUMN `cansPaidThisTurn` INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `settled_debts` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `fromMemberId` INTEGER NOT NULL,
                `fromMemberName` TEXT NOT NULL,
                `toMemberId` INTEGER NOT NULL,
                `toMemberName` TEXT NOT NULL,
                `amount` REAL NOT NULL,
                `settledAt` INTEGER NOT NULL
            )
        """)
    }
}
