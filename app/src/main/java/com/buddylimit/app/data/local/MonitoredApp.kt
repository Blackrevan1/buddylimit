package com.buddylimit.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** An app the user has chosen to monitor, with its daily budget. */
@Entity(tableName = "monitored_apps")
data class MonitoredApp(
    @PrimaryKey val packageName: String,
    val label: String,
    val dailyBudgetMinutes: Int
)
