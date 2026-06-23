package com.buddylimit.app.monitor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.buddylimit.app.R
import com.buddylimit.app.data.AppRepository
import com.buddylimit.app.data.SettingsRepository
import com.buddylimit.app.data.UsageRepository
import com.buddylimit.app.data.local.MonitoredApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

/**
 * Persistent foreground service implementing the M1 monitoring loop (SYSTEM_DESIGN.md §4–5):
 * poll [UsageStatsManager] ~1s, find the current foreground app from its events, accumulate
 * elapsed time for monitored apps into [UsageRepository] (bucketed by day-window), and — on
 * re-entry into a monitored app that is over budget — throw the [BlockOverlay] and send the
 * user Home (re-entry denial: an in-progress session is not interrupted).
 */
@AndroidEntryPoint
class UsageMonitorService : Service() {

    @Inject lateinit var appRepository: AppRepository
    @Inject lateinit var usageRepository: UsageRepository
    @Inject lateinit var settingsRepository: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val zone: ZoneId = ZoneId.systemDefault()
    private lateinit var overlay: BlockOverlay

    @Volatile private var monitored: Map<String, MonitoredApp> = emptyMap()
    @Volatile private var resetHour: Int = DayWindow.DEFAULT_RESET_HOUR

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        overlay = BlockOverlay(this)
        startForegroundNotification()
        observeMonitoredSet()
        startPolling()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        overlay.hide()
        scope.cancel()
        super.onDestroy()
    }

    private fun observeMonitoredSet() {
        scope.launch {
            appRepository.observeMonitoredApps().collect { apps ->
                monitored = apps.associateBy { it.packageName }
            }
        }
        scope.launch {
            settingsRepository.resetHour.collect { resetHour = it }
        }
    }

    private fun startPolling() {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        scope.launch {
            var lastQueryWall = System.currentTimeMillis()
            var lastTickMono = SystemClock.elapsedRealtime()
            var sinceFlushMs = 0L
            var currentPkg: String? = latestForegroundBetween(
                usm, lastQueryWall - SEED_LOOKBACK_MS, lastQueryWall
            )
            var lastForegroundPkg: String? = currentPkg
            val pendingMs = HashMap<String, Long>()

            while (isActive) {
                delay(POLL_INTERVAL_MS)
                val nowWall = System.currentTimeMillis()
                val nowMono = SystemClock.elapsedRealtime()
                val elapsed = nowMono - lastTickMono
                lastTickMono = nowMono

                currentPkg = latestForegroundBetween(usm, lastQueryWall, nowWall) ?: currentPkg
                lastQueryWall = nowWall

                val pkg = currentPkg
                // Cap elapsed: a long gap means the process was suspended (Doze / screen off),
                // not real foreground time, so we don't count it.
                if (pkg != null && monitored.containsKey(pkg) && elapsed in 1..MAX_ELAPSED_MS) {
                    pendingMs[pkg] = (pendingMs[pkg] ?: 0L) + elapsed
                }

                // Re-entry denial: only act on a foreground-ENTER into a monitored app, so an
                // in-progress session is never interrupted mid-use.
                if (pkg != null && pkg != lastForegroundPkg && monitored.containsKey(pkg) &&
                    isOverBudget(pkg, pendingMs, nowWall)
                ) {
                    block(pkg, nowWall)
                }
                lastForegroundPkg = pkg

                if (overlay.isShowing()) {
                    overlay.updateCountdown(DayWindow.nextReset(nowWall, zone, resetHour))
                }

                sinceFlushMs += elapsed
                if (sinceFlushMs >= FLUSH_INTERVAL_MS) {
                    flush(pendingMs, nowWall)
                    sinceFlushMs = 0L
                }
            }
        }
    }

    /** Used (persisted + not-yet-flushed) >= the app's budget for the current window. */
    private suspend fun isOverBudget(pkg: String, pendingMs: Map<String, Long>, nowWall: Long): Boolean {
        val budgetMinutes = monitored[pkg]?.dailyBudgetMinutes ?: return false
        val dayKey = DayWindow.dayKey(nowWall, zone, resetHour)
        val persistedSeconds = usageRepository.getUsedSeconds(pkg, dayKey)
        val pendingSeconds = (pendingMs[pkg] ?: 0L) / 1000L
        return persistedSeconds + pendingSeconds >= budgetMinutes.toLong() * 60L
    }

    /** Cover the over-budget app with the block overlay and bounce the user Home. */
    private fun block(pkg: String, nowWall: Long) {
        if (!OverlayPermission.isGranted(this)) return
        val label = monitored[pkg]?.label ?: pkg
        sendHome()
        overlay.show(
            appLabel = label,
            packageName = pkg,
            nextResetMillis = DayWindow.nextReset(nowWall, zone, resetHour),
            onDismiss = ::sendHome
        )
    }

    private fun sendHome() {
        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { startActivity(home) }
    }

    /** Persist whole accumulated seconds, keeping sub-second remainders pending. */
    private suspend fun flush(pendingMs: MutableMap<String, Long>, nowWall: Long) {
        val dayKey = DayWindow.dayKey(nowWall, zone, resetHour)
        for (entry in pendingMs.entries) {
            val seconds = entry.value / 1000L
            if (seconds > 0L) {
                usageRepository.addUsage(entry.key, dayKey, seconds)
                entry.setValue(entry.value - seconds * 1000L)
            }
        }
    }

    /** Package of the most recent foreground event in [from, to], or null if none. */
    private fun latestForegroundBetween(usm: UsageStatsManager, from: Long, to: Long): String? {
        if (to <= from) return null
        val events = usm.queryEvents(from, to)
        val event = UsageEvents.Event()
        var latest: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                latest = event.packageName
            }
        }
        return latest
    }

    private fun startForegroundNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Usage monitoring", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Keeps BuddyLimit tracking your app usage." }
            )
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BuddyLimit")
            .setContentText("Monitoring app usage")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    companion object {
        /** In-process flag so the UI can reflect monitoring state. */
        @Volatile var isRunning: Boolean = false
            private set

        private const val CHANNEL_ID = "usage_monitor"
        private const val NOTIF_ID = 1001
        private const val POLL_INTERVAL_MS = 1_000L
        private const val FLUSH_INTERVAL_MS = 5_000L
        private const val MAX_ELAPSED_MS = 5_000L
        private const val SEED_LOOKBACK_MS = 60_000L

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, UsageMonitorService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, UsageMonitorService::class.java))
        }
    }
}
