package com.buddylimit.app.data

import com.buddylimit.app.data.local.MonitoredApp
import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over local storage + the installed-apps source.
 * Keeping this an interface is our backend/storage lock-in hedge (see SYSTEM_DESIGN.md §8).
 */
interface AppRepository {
    fun observeMonitoredApps(): Flow<List<MonitoredApp>>
    suspend fun getInstalledApps(): List<InstalledApp>
    suspend fun setMonitored(packageName: String, label: String, monitored: Boolean)
    suspend fun setBudget(packageName: String, minutes: Int)
}
