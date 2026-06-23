package com.buddylimit.app.data

import com.buddylimit.app.data.local.UsageDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageRepositoryImpl @Inject constructor(
    private val dao: UsageDao
) : UsageRepository {

    override fun observeUsageForDay(dayKey: String): Flow<Map<String, Long>> =
        dao.observeForDay(dayKey).map { records ->
            records.associate { it.packageName to it.usedSeconds }
        }

    override suspend fun getUsedSeconds(packageName: String, dayKey: String): Long =
        dao.getUsedSeconds(packageName, dayKey) ?: 0L

    override suspend fun addUsage(packageName: String, dayKey: String, deltaSeconds: Long) {
        if (deltaSeconds <= 0L) return
        dao.addUsage(packageName, dayKey, deltaSeconds)
    }
}
