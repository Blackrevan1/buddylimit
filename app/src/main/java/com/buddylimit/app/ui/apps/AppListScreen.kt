package com.buddylimit.app.ui.apps

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buddylimit.app.monitor.OverlayPermission
import com.buddylimit.app.monitor.UsageAccess
import com.buddylimit.app.monitor.UsageMonitorService

private const val BUDGET_STEP = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    modifier: Modifier = Modifier,
    viewModel: AppListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var usageGranted by remember { mutableStateOf(UsageAccess.isGranted(context)) }
    var overlayGranted by remember { mutableStateOf(OverlayPermission.isGranted(context)) }
    var monitoring by remember { mutableStateOf(UsageMonitorService.isRunning) }

    // The special permissions are granted in system Settings (off-app), so re-check on return.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        usageGranted = UsageAccess.isGranted(context)
        overlayGranted = OverlayPermission.isGranted(context)
        monitoring = UsageMonitorService.isRunning
    }

    // The foreground-service notification needs POST_NOTIFICATIONS on Android 13+; start
    // monitoring regardless of the user's choice (denial only hides the notification).
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        UsageMonitorService.start(context)
        monitoring = true
    }

    fun startMonitoring() {
        val needsNotifPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsNotifPermission) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            UsageMonitorService.start(context)
            monitoring = true
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("Monitored apps") }) }
    ) { padding ->
        if (state.loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                item {
                    MonitorControls(
                        usageGranted = usageGranted,
                        overlayGranted = overlayGranted,
                        monitoring = monitoring,
                        resetHour = state.resetHour,
                        onGrantUsageAccess = { context.startActivity(UsageAccess.settingsIntent()) },
                        onGrantOverlay = { context.startActivity(OverlayPermission.settingsIntent(context)) },
                        onStart = { startMonitoring() },
                        onStop = {
                            UsageMonitorService.stop(context)
                            monitoring = false
                        },
                        onResetHourChange = { viewModel.onResetHourChange(it) }
                    )
                    HorizontalDivider()
                }
                items(state.items, key = { it.packageName }) { item ->
                    AppRow(
                        item = item,
                        onToggle = { viewModel.onToggleMonitored(item, it) },
                        onBudgetChange = { viewModel.onBudgetChange(item, it) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun MonitorControls(
    usageGranted: Boolean,
    overlayGranted: Boolean,
    monitoring: Boolean,
    resetHour: Int,
    onGrantUsageAccess: () -> Unit,
    onGrantOverlay: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onResetHourChange: (Int) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!usageGranted) {
                Text(
                    text = "Usage Access is required to track app time.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(onClick = onGrantUsageAccess) { Text("Grant Usage Access") }
            } else {
                Text(
                    text = if (monitoring) "Monitoring is on." else "Usage Access granted.",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (monitoring) {
                    OutlinedButton(onClick = onStop) { Text("Stop monitoring") }
                } else {
                    Button(onClick = onStart) { Text("Start monitoring") }
                }
            }

            if (!overlayGranted) {
                Text(
                    text = "Allow “Display over other apps” so blocked apps can be covered.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(onClick = onGrantOverlay) { Text("Grant overlay permission") }
            }

            HorizontalDivider()
            ResetHourRow(resetHour = resetHour, onChange = onResetHourChange)
        }
    }
}

@Composable
private fun ResetHourRow(resetHour: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Daily reset at %02d:00".format(resetHour),
            style = MaterialTheme.typography.bodyMedium
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onChange(resetHour - 1) }) { Text("−") }
            TextButton(onClick = { onChange(resetHour + 1) }) { Text("+") }
        }
    }
}

@Composable
private fun AppRow(
    item: AppListItem,
    onToggle: (Boolean) -> Unit,
    onBudgetChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.isMonitored) {
                Text(
                    text = "used ${formatUsed(item.usedSeconds)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (item.isMonitored && item.budgetMinutes != null) {
            BudgetStepper(minutes = item.budgetMinutes, onChange = onBudgetChange)
        }

        Switch(checked = item.isMonitored, onCheckedChange = onToggle)
    }
}

@Composable
private fun BudgetStepper(minutes: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onChange(minutes - BUDGET_STEP) }) { Text("−") }
        Text(
            text = "$minutes m",
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 44.dp)
        )
        TextButton(onClick = { onChange(minutes + BUDGET_STEP) }) { Text("+") }
    }
}

private fun formatUsed(seconds: Long): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return if (minutes > 0) "${minutes}m ${secs}s" else "${secs}s"
}
