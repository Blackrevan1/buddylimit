package com.buddylimit.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [MonitoredApp::class, UsageRecord::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun monitoredAppDao(): MonitoredAppDao
    abstract fun usageDao(): UsageDao

    companion object {
        /** v1 -> v2: add the usage_records table. Migrated (not destructive) so existing budgets survive. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `usage_records` (
                        `packageName` TEXT NOT NULL,
                        `dayKey` TEXT NOT NULL,
                        `usedSeconds` INTEGER NOT NULL,
                        PRIMARY KEY(`packageName`, `dayKey`)
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
