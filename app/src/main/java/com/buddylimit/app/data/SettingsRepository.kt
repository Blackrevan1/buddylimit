package com.buddylimit.app.data

import kotlinx.coroutines.flow.Flow

/**
 * User-configurable app settings (local, persisted). Backed by DataStore.
 *
 * Changing the reset hour will become buddy-gated in M2 (SYSTEM_DESIGN.md §6, D4);
 * for M1 it is a plain local setting.
 */
interface SettingsRepository {
    /** Hour of day (0–23, local) at which daily budgets reset. Default 4 (04:00). */
    val resetHour: Flow<Int>
    suspend fun setResetHour(hour: Int)
}
