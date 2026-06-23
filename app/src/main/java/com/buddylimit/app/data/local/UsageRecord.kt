package com.buddylimit.app.data.local

import androidx.room.Entity

/**
 * Accumulated foreground time for one app within one day-window (see [com.buddylimit.app.monitor.DayWindow]).
 * Keyed by (package, dayKey) so each window has its own counter and survives process death.
 */
@Entity(tableName = "usage_records", primaryKeys = ["packageName", "dayKey"])
data class UsageRecord(
    val packageName: String,
    /** Window identifier from [com.buddylimit.app.monitor.DayWindow.dayKey] (e.g. "2026-06-23"). */
    val dayKey: String,
    val usedSeconds: Long
)
