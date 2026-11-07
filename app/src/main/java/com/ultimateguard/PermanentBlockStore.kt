package com.ultimateguard

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Append-only block registry for the app's normal UI/API.
 *
 * This intentionally exposes no unblock/remove operation. It is not
 * cryptographically immutable: an Android factory reset, privileged system
 * access, app-data wipe, or compromised device can defeat local storage.
 */
class PermanentBlockStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("permanent_blocks_v1", Context.MODE_PRIVATE)
    private val dpm = context.getSystemService(DevicePolicyManager::class.java)
    private val admin = ComponentName(context, GuardAdminReceiver::class.java)

    fun blockedPackages(): Set<String> =
        prefs.getStringSet("packages", emptySet())?.toSet() ?: emptySet()

    fun blockedDomains(): Set<String> =
        prefs.getStringSet("domains", emptySet())?.toSet() ?: emptySet()

    /** Permanently records a package for this installation and suspends it when Device Owner. */
    fun permanentlyBlockPackage(packageName: String): Result<Unit> = runCatching {
        require(packageName.isNotBlank())
        require(packageName != context.packageName) { "The protection app cannot block itself." }
        require(isInstalled(packageName)) { "Package is not installed." }
        val updated = blockedPackages() + packageName
        prefs.edit().putStringSet("packages", updated).commit()
        check(prefs.getStringSet("packages", emptySet())?.contains(packageName) == true) {
            "Could not persist permanent block."
        }
        check(dpm.isDeviceOwnerApp(context.packageName)) {
            "Package saved locally. Device Owner mode is required to suspend it."
        }
        val failed = dpm.setPackagesSuspended(admin, arrayOf(packageName), true)
        check(failed.isEmpty()) {
            "Android refused to suspend this package. It remains recorded as blocked."
        }
    }

    /** Permanently records a domain for local DNS-filter decisions. */
    fun permanentlyBlockDomain(rawDomain: String): Result<String> = runCatching {
        val domain = normalizeDomain(rawDomain)
        val updated = blockedDomains() + domain
        prefs.edit().putStringSet("domains", updated).commit()
        check(prefs.getStringSet("domains", emptySet())?.contains(domain) == true) {
            "Could not persist permanent domain block."
        }
        domain
    }

    fun isDomainBlocked(rawHost: String): Boolean {
        val host = normalizeDomain(rawHost)
        return blockedDomains().any { host == it || host.endsWith(".$it") }
    }

    fun reapplyPackageBlocks() {
        check(dpm.isDeviceOwnerApp(context.packageName)) {
            "Device Owner mode is required to reapply package blocks."
        }
        blockedPackages().forEach { pkg ->
            if (isInstalled(pkg)) dpm.setPackagesSuspended(admin, arrayOf(pkg), true)
        }
    }

    fun protectThisAppFromUninstall() {
        check(dpm.isDeviceOwnerApp(context.packageName)) {
            "Device Owner mode is required."
        }
        dpm.setUninstallBlocked(admin, context.packageName, true)
    }

    private fun isInstalled(pkg: String): Boolean = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getApplicationInfo(pkg, 0)
        true
    }.getOrDefault(false)

    private fun normalizeDomain(raw: String): String {
        val value = raw.trim().lowercase().trimEnd('.')
        require(value.isNotBlank() && value.length <= 253)
        require(!value.contains("/") && !value.contains(":") && !value.contains(" "))
        val labels = value.split(".")
        require(labels.size >= 2 && labels.all {
            it.isNotBlank() && it.length <= 63 &&
            it.first().isLetterOrDigit() && it.last().isLetterOrDigit() &&
            it.all { c -> c.isLetterOrDigit() || c == '-' }
        }) { "Enter a valid domain name, without https:// or a path." }
        return value
    }
}
