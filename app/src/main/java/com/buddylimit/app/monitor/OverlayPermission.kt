package com.buddylimit.app.monitor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Helpers for the special "Display over other apps" permission
 * ([android.Manifest.permission.SYSTEM_ALERT_WINDOW]). Like Usage Access it is granted in
 * system Settings, not at runtime. Required to draw the blocking overlay (SYSTEM_DESIGN.md §7)
 * — and, as a side effect, it exempts the app from background-activity-start limits so the
 * service can send the user Home on a block.
 */
object OverlayPermission {

    fun isGranted(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun settingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
}
