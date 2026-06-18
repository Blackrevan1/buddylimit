package com.buddylimit.app.data

/** A launchable app discovered at runtime (not persisted). */
data class InstalledApp(
    val packageName: String,
    val label: String
)
