package com.buddylimit.app.ui.apps

import com.buddylimit.app.data.InstalledApp
import com.buddylimit.app.data.local.MonitoredApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppListMapperTest {

    private val installed = listOf(
        InstalledApp("com.insta", "Instagram"),
        InstalledApp("com.tiktok", "TikTok"),
        InstalledApp("com.maps", "Maps")
    )

    @Test
    fun unmonitoredApps_haveNoBudget() {
        val items = mergeAppList(installed, emptyList())
        assertEquals(3, items.size)
        assertTrue(items.all { !it.isMonitored })
        assertTrue(items.all { it.budgetMinutes == null })
    }

    @Test
    fun monitoredApp_isFlaggedWithItsBudget() {
        val monitored = listOf(MonitoredApp("com.insta", "Instagram", 30))
        val items = mergeAppList(installed, monitored).associateBy { it.packageName }

        assertTrue(items.getValue("com.insta").isMonitored)
        assertEquals(30, items.getValue("com.insta").budgetMinutes)
        assertFalse(items.getValue("com.tiktok").isMonitored)
        assertNull(items.getValue("com.tiktok").budgetMinutes)
    }

    @Test
    fun monitoredEntry_forMissingInstall_isNotShown() {
        val monitored = listOf(MonitoredApp("com.gone", "Gone", 15))
        val items = mergeAppList(installed, monitored)
        assertEquals(3, items.size)
        assertTrue(items.none { it.packageName == "com.gone" })
    }
}
