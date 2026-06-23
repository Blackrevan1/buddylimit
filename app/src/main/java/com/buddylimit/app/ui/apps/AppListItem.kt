package com.buddylimit.app.ui.apps

import com.buddylimit.app.data.InstalledApp
import com.buddylimit.app.data.local.MonitoredApp

/** One row in the app picker: an installed app annotated with its monitor state + usage. */
data class AppListItem(
    val packageName: String,
    val label: String,
    val isMonitored: Boolean,
    val budgetMinutes: Int?,
    val usedSeconds: Long = 0L
)

/**
 * Pure merge of the live installed-app list with the persisted monitored set and today's
 * usage. Free of Android deps so it is unit-testable. The list is driven by installed apps,
 * so stale monitored rows for uninstalled apps simply don't appear.
 */
fun mergeAppList(
    installed: List<InstalledApp>,
    monitored: List<MonitoredApp>,
    usage: Map<String, Long> = emptyMap()
): List<AppListItem> {
    val byPackage = monitored.associateBy { it.packageName }
    return installed.map { app ->
        val m = byPackage[app.packageName]
        AppListItem(
            packageName = app.packageName,
            label = app.label,
            isMonitored = m != null,
            budgetMinutes = m?.dailyBudgetMinutes,
            usedSeconds = usage[app.packageName] ?: 0L
        )
    }
}
