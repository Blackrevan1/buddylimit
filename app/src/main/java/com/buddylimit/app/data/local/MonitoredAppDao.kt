package com.buddylimit.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MonitoredAppDao {

    @Query("SELECT * FROM monitored_apps ORDER BY label COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<MonitoredApp>>

    @Upsert
    suspend fun upsert(app: MonitoredApp)

    @Query("UPDATE monitored_apps SET dailyBudgetMinutes = :minutes WHERE packageName = :packageName")
    suspend fun updateBudget(packageName: String, minutes: Int)

    @Query("DELETE FROM monitored_apps WHERE packageName = :packageName")
    suspend fun delete(packageName: String)
}
