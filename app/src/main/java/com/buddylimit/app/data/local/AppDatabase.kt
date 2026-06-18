package com.buddylimit.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [MonitoredApp::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun monitoredAppDao(): MonitoredAppDao
}
