package com.ultimateguard.core

import android.content.Context

/**
 * The append-only registry of blocked packages. Entries survive reboots and
 * are re-applied on boot and on package install/replace. Removing an entry
 * requires the admin PIN, because the whole UI is behind the PIN.
 */
class BlockStore(context: Context) {

    private val appContext = context.applicationContext
    private val sp = appContext.getSharedPreferences("guard_blocks", Context.MODE_PRIVATE)

    fun blockedPackages(): MutableSet<String> =
        HashSet(sp.getStringSet("packages", emptySet()) ?: emptySet())

    fun isBlocked(packageName: String): Boolean = blockedPackages().contains(packageName)

    fun add(packageName: String): Boolean {
        val current = blockedPackages()
        if (!current.add(packageName)) return false
        sp.edit().putStringSet("packages", current).commit()
        Admin.suspendPackages(appContext, listOf(packageName))
        return true
    }

    fun remove(packageName: String): Boolean {
        val current = blockedPackages()
        if (!current.remove(packageName)) return false
        sp.edit().putStringSet("packages", current).commit()
        Admin.unsuspendPackages(appContext, listOf(packageName))
        return true
    }

    fun clearAll() {
        val current = blockedPackages()
        sp.edit().putStringSet("packages", emptySet()).commit()
        Admin.unsuspendPackages(appContext, current)
    }

    /** Re-applies every stored block. Returns how many could not be applied. */
    fun reapply(): Int {
        val all = blockedPackages().filter { Admin.isInstalled(appContext, it) }
        if (all.isEmpty()) return 0
        var failed = 0
        all.chunked(100).forEach { chunk ->
            failed += Admin.suspendPackages(appContext, chunk).size
        }
        return failed
    }
}
