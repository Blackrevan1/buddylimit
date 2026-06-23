package com.buddylimit.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class UsageDao {

    @Query("SELECT * FROM usage_records WHERE dayKey = :dayKey")
    abstract fun observeForDay(dayKey: String): Flow<List<UsageRecord>>

    @Query("SELECT usedSeconds FROM usage_records WHERE packageName = :packageName AND dayKey = :dayKey")
    abstract suspend fun getUsedSeconds(packageName: String, dayKey: String): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIfAbsent(record: UsageRecord)

    @Query("UPDATE usage_records SET usedSeconds = usedSeconds + :deltaSeconds WHERE packageName = :packageName AND dayKey = :dayKey")
    abstract suspend fun increment(packageName: String, dayKey: String, deltaSeconds: Long)

    /**
     * Atomically add [deltaSeconds] to the counter, creating the row at 0 first if needed.
     * Two statements (INSERT OR IGNORE, then UPDATE) instead of SQL UPSERT, which requires
     * SQLite 3.24+ (not guaranteed on minSdk 26 devices).
     */
    @Transaction
    open suspend fun addUsage(packageName: String, dayKey: String, deltaSeconds: Long) {
        insertIfAbsent(UsageRecord(packageName, dayKey, 0))
        increment(packageName, dayKey, deltaSeconds)
    }
}
