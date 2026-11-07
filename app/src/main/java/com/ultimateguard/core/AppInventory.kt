package com.ultimateguard.core

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

/** Installed-app inventory for the app-blocking screen. */
class AppInventory(private val context: Context) {

    class AppInfo(
        val label: String,
        val packageName: String,
        val isSystem: Boolean,
        val icon: Drawable?
    )

    fun load(includeSystem: Boolean = false): List<AppInfo> {
        val pm = context.packageManager
        val flags = PackageManager.GET_META_DATA
        val apps = runCatching { pm.getInstalledApplications(flags) }.getOrDefault(emptyList())
        val out = ArrayList<AppInfo>(apps.size)
        for (info in apps) {
            if (info.packageName == context.packageName) continue
            val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            if (isSystem && !includeSystem) continue
            if (pm.getLaunchIntentForPackage(info.packageName) == null && !includeSystem) continue
            val label = runCatching { pm.getApplicationLabel(info).toString() }.getOrDefault(info.packageName)
            val icon = runCatching { pm.getApplicationIcon(info) }.getOrNull()
            out.add(AppInfo(label, info.packageName, isSystem, icon))
        }
        out.sortWith(compareBy({ it.label.lowercase() }, { it.packageName }))
        return out
    }

    fun openAppInfo(packageName: String) {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = android.net.Uri.parse("package:$packageName")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
