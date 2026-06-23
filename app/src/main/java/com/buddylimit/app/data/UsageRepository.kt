package com.buddylimit.app.data

import kotlinx.coroutines.flow.Flow

/**
 * Per-app accumulated foreground usage, bucketed by day-window.
 * Separate from [AppRepository] (config) since this is high-frequency runtime data.
 */
interface UsageRepository {
    /** package -> seconds used, for the given window. Emits as the monitor flushes. */
    fun observeUsageForDay(dayKey: String): Flow<Map<String, Long>>
    suspend fun getUsedSeconds(packageName: String, dayKey: String): Long
    suspend fun addUsage(packageName: String, dayKey: String, deltaSeconds: Long)
}
