package com.buddylimit.app.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buddylimit.app.data.AppRepository
import com.buddylimit.app.data.InstalledApp
import com.buddylimit.app.data.SettingsRepository
import com.buddylimit.app.data.UsageRepository
import com.buddylimit.app.monitor.DayWindow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

data class AppListUiState(
    val loading: Boolean = true,
    val items: List<AppListItem> = emptyList(),
    val resetHour: Int = DayWindow.DEFAULT_RESET_HOUR
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AppListViewModel @Inject constructor(
    private val repository: AppRepository,
    private val settings: SettingsRepository,
    usageRepository: UsageRepository
) : ViewModel() {

    private val installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val loaded = MutableStateFlow(false)
    private val zone: ZoneId = ZoneId.systemDefault()

    /**
     * Current day-window key, re-emitted when crossing the next reset boundary or when the
     * reset hour changes. This is how counters "present fresh" at reset: the read switches to
     * a new (empty) bucket without destroying the previous window's data.
     */
    private val currentDayKey: Flow<String> =
        settings.resetHour.flatMapLatest { hour ->
            flow {
                while (true) {
                    val now = System.currentTimeMillis()
                    emit(DayWindow.dayKey(now, zone, hour))
                    val waitMs = (DayWindow.nextReset(now, zone, hour) - now).coerceAtLeast(1_000L)
                    delay(waitMs)
                }
            }
        }.distinctUntilChanged()

    private val usageToday: Flow<Map<String, Long>> =
        currentDayKey.flatMapLatest { usageRepository.observeUsageForDay(it) }

    val uiState: StateFlow<AppListUiState> =
        combine(
            installedApps,
            loaded,
            repository.observeMonitoredApps(),
            usageToday,
            settings.resetHour
        ) { installed, loaded, monitored, usage, resetHour ->
            AppListUiState(
                loading = !loaded,
                items = mergeAppList(installed, monitored, usage),
                resetHour = resetHour
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppListUiState()
        )

    init {
        viewModelScope.launch {
            installedApps.value = repository.getInstalledApps()
            loaded.value = true
        }
    }

    fun onToggleMonitored(item: AppListItem, monitored: Boolean) {
        viewModelScope.launch {
            repository.setMonitored(item.packageName, item.label, monitored)
        }
    }

    fun onBudgetChange(item: AppListItem, minutes: Int) {
        viewModelScope.launch {
            repository.setBudget(item.packageName, minutes)
        }
    }

    fun onResetHourChange(hour: Int) {
        viewModelScope.launch {
            settings.setResetHour(((hour % 24) + 24) % 24)
        }
    }
}
