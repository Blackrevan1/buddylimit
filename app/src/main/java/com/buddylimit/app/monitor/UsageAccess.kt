package com.buddylimit.app.monitor

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings

/**
 * Helpers for the special "Usage Access" permission ([android.Manifest.permission.PACKAGE_USAGE_STATS]).
 * It is not a runtime permission — the user must enable it from system Settings, so we
 * check the AppOps state and provide the intent to send them there.
 */
object UsageAccess {

    /** True if the user has granted Usage Access to this app. */
    fun isGranted(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Intent to the system Usage Access settings screen. */
    fun settingsIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
}
