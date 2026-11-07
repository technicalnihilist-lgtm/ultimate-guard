package com.ultimateguard

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean
)

/**
 * Installed-app inventory for the app-management use case.
 * Android package visibility and Google Play policy requirements still apply.
 */
class InstalledAppRepository(private val context: Context) {
    @Suppress("DEPRECATION")
    fun getInstalledApps(): List<InstalledApp> {
        val pm = context.packageManager
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .map { info ->
                InstalledApp(
                    packageName = info.packageName,
                    label = pm.getApplicationLabel(info).toString(),
                    isSystemApp = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            }
            .sortedBy { it.label.lowercase() }
    }
}
