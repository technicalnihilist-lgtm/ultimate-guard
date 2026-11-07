package com.ultimateguard.core

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.ultimateguard.GuardAdminReceiver

/**
 * Thin wrapper over DevicePolicyManager. Package suspension and uninstall
 * blocking only work when this app is Device Owner (or Profile Owner);
 * without that, the UI falls back to recording the block locally.
 */
object Admin {

    fun dpm(context: Context): DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    fun component(context: Context): ComponentName =
        ComponentName(context, GuardAdminReceiver::class.java)

    fun isDeviceOwner(context: Context): Boolean =
        runCatching { dpm(context).isDeviceOwnerApp(context.packageName) }.getOrDefault(false)

    fun isProfileOwner(context: Context): Boolean =
        runCatching { dpm(context).isProfileOwnerApp(context.packageName) }.getOrDefault(false)

    fun isAdminActive(context: Context): Boolean =
        runCatching { dpm(context).isAdminActive(component(context)) }.getOrDefault(false)

    /**
     * Suspends the given packages. Returns the packages Android refused.
     * Only the first 100 are attempted per call to stay inside binder limits.
     */
    fun suspendPackages(context: Context, packages: Collection<String>): List<String> {
        if (!isDeviceOwner(context) && !isProfileOwner(context)) return packages.toList()
        if (packages.isEmpty()) return emptyList()
        return runCatching {
            val batch = packages.take(100).toTypedArray()
            dpm(context).setPackagesSuspended(component(context), batch, true).toList()
        }.getOrDefault(packages.toList())
    }

    fun unsuspendPackages(context: Context, packages: Collection<String>): List<String> {
        if (!isDeviceOwner(context) && !isProfileOwner(context)) return packages.toList()
        if (packages.isEmpty()) return emptyList()
        return runCatching {
            dpm(context).setPackagesSuspended(component(context), packages.take(100).toTypedArray(), false).toList()
        }.getOrDefault(packages.toList())
    }

    fun setUninstallBlocked(context: Context, packageName: String, blocked: Boolean): Boolean {
        if (!isDeviceOwner(context) && !isProfileOwner(context)) return false
        return runCatching {
            dpm(context).setUninstallBlocked(component(context), packageName, blocked)
            true
        }.getOrDefault(false)
    }

    /** Blocks removing this app and disabling it while it is Device Owner. */
    fun applySelfProtection(context: Context, enabled: Boolean): Boolean {
        if (!isDeviceOwner(context)) return false
        return runCatching {
            dpm(context).setUninstallBlocked(component(context), context.packageName, enabled)
            if (enabled) {
                dpm(context).addUserRestriction(component(context), android.os.UserManager.DISALLOW_UNINSTALL_APPS)
            } else {
                dpm(context).clearUserRestriction(component(context), android.os.UserManager.DISALLOW_UNINSTALL_APPS)
            }
            true
        }.getOrDefault(false)
    }

    fun isSuspended(context: Context, packageName: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return runCatching {
            context.packageManager.getApplicationInfo(packageName, 0).flags and
                android.content.pm.ApplicationInfo.FLAG_SUSPENDED != 0
        }.getOrDefault(false)
    }

    fun canManagePackages(context: Context): Boolean = isDeviceOwner(context) || isProfileOwner(context)

    fun isInstalled(context: Context, packageName: String): Boolean = runCatching {
        context.packageManager.getApplicationInfo(packageName, 0)
        true
    }.getOrDefault(false)

    @Suppress("unused")
    fun isQueryable(context: Context): Boolean =
        context.packageManager.resolveActivity(
            android.content.Intent(android.content.Intent.ACTION_MAIN).setPackage(context.packageName),
            PackageManager.MATCH_DEFAULT_ONLY
        ) != null
}
