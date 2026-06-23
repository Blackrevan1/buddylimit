package com.buddylimit.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.buddylimit.app.monitor.DayWindow
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {

    override val resetHour: Flow<Int> =
        context.settingsDataStore.data.map { prefs ->
            (prefs[RESET_HOUR] ?: DayWindow.DEFAULT_RESET_HOUR).coerceIn(0, 23)
        }

    override suspend fun setResetHour(hour: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[RESET_HOUR] = hour.coerceIn(0, 23)
        }
    }

    private companion object {
        val RESET_HOUR = intPreferencesKey("reset_hour")
    }
}
