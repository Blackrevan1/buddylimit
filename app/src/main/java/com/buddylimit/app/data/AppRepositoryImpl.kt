package com.buddylimit.app.data

import com.buddylimit.app.data.local.MonitoredApp
import com.buddylimit.app.data.local.MonitoredAppDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRepositoryImpl @Inject constructor(
    private val dao: MonitoredAppDao,
    private val installedApps: InstalledAppsDataSource
) : AppRepository {

    override fun observeMonitoredApps(): Flow<List<MonitoredApp>> = dao.observeAll()

    override suspend fun getInstalledApps(): List<InstalledApp> = installedApps.getLaunchableApps()

    override suspend fun setMonitored(packageName: String, label: String, monitored: Boolean) {
        if (monitored) {
            dao.upsert(
                MonitoredApp(
                    packageName = packageName,
                    label = label,
                    dailyBudgetMinutes = DEFAULT_BUDGET_MINUTES
                )
            )
        } else {
            dao.delete(packageName)
        }
    }

    override suspend fun setBudget(packageName: String, minutes: Int) {
        dao.updateBudget(packageName, minutes.coerceIn(MIN_BUDGET_MINUTES, MAX_BUDGET_MINUTES))
    }

    companion object {
        const val DEFAULT_BUDGET_MINUTES = 30
        const val MIN_BUDGET_MINUTES = 5
        const val MAX_BUDGET_MINUTES = 24 * 60
    }
}
