package com.ultimateguard

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.UserManager

/**
 * These controls work only after the app has been provisioned as Device Owner.
 * Device Owner provisioning is normally performed on a freshly reset,
 * unmanaged device (QR/NFC/zero-touch/ADB test provisioning).
 */
class GuardPolicy(private val context: Context) {
    private val dpm = context.getSystemService(DevicePolicyManager::class.java)
    private val admin = ComponentName(context, GuardAdminReceiver::class.java)

    fun isDeviceOwner(): Boolean = dpm.isDeviceOwnerApp(context.packageName)

    fun applyBaselineRestrictions() {
        check(isDeviceOwner()) { "ULTIMATE GUARD is not the Device Owner." }
        dpm.addUserRestriction(admin, UserManager.DISALLOW_INSTALL_APPS)
        dpm.addUserRestriction(admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
        dpm.addUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)
        dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
        dpm.setUninstallBlocked(admin, context.packageName, true)
    }

    fun blockPackage(packageName: String) {
        check(isDeviceOwner()) { "Device Owner mode is required." }
        require(packageName != context.packageName) { "You cannot block the protection app itself." }
        dpm.setPackagesSuspended(admin, arrayOf(packageName), true)
    }

    fun unblockPackage(packageName: String) {
        check(isDeviceOwner()) { "Device Owner mode is required." }
        dpm.setPackagesSuspended(admin, arrayOf(packageName), false)
    }

    fun setPlayStoreBlocked(blocked: Boolean) {
        if (blocked) blockPackage("com.android.vending")
        else unblockPackage("com.android.vending")
    }

    /**
     * Recovery is deliberately administrator-controlled, not irreversible.
     * An authorized admin can remove restrictions during a supervised recovery.
     */
    fun beginAdminRecovery() {
        check(isDeviceOwner()) { "Device Owner mode is required." }
        dpm.setUninstallBlocked(admin, context.packageName, false)
        dpm.clearUserRestriction(admin, UserManager.DISALLOW_INSTALL_APPS)
        dpm.clearUserRestriction(admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
        dpm.clearUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)
        dpm.clearUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
    }
}
