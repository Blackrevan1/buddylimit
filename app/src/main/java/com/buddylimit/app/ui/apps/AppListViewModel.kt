package com.buddylimit.app.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buddylimit.app.data.AppRepository
import com.buddylimit.app.data.InstalledApp
import com.buddylimit.app.data.UsageRepository
import com.buddylimit.app.monitor.DayWindow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

data class AppListUiState(
    val loading: Boolean = true,
    val items: List<AppListItem> = emptyList()
)

@HiltViewModel
class AppListViewModel @Inject constructor(
    private val repository: AppRepository,
    usageRepository: UsageRepository
) : ViewModel() {

    private val installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val loaded = MutableStateFlow(false)

    // Window key for "today". Phase 3 makes this reactive to the configurable reset.
    private val dayKey = DayWindow.dayKey(System.currentTimeMillis(), ZoneId.systemDefault())

    val uiState: StateFlow<AppListUiState> =
        combine(
            installedApps,
            loaded,
            repository.observeMonitoredApps(),
            usageRepository.observeUsageForDay(dayKey)
        ) { installed, loaded, monitored, usage ->
            AppListUiState(
                loading = !loaded,
                items = mergeAppList(installed, monitored, usage)
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
}
