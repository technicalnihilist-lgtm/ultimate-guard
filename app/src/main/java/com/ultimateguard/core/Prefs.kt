package com.ultimateguard.core

import android.content.Context
import android.content.SharedPreferences

/** All persistent settings. Everything stays on the device. */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("guard_settings", Context.MODE_PRIVATE)

    var upstreamPrimary: String
        get() = sp.getString("upstream1", "1.1.1.1") ?: "1.1.1.1"
        set(v) { sp.edit().putString("upstream1", v).apply() }

    var upstreamSecondary: String
        get() = sp.getString("upstream2", "1.0.0.1") ?: "1.0.0.1"
        set(v) { sp.edit().putString("upstream2", v).apply() }

    /** Also route the well-known public resolvers into the tunnel so apps that hardcode DNS get filtered. */
    var interceptHardcodedDns: Boolean
        get() = sp.getBoolean("intercept_hardcoded", true)
        set(v) { sp.edit().putBoolean("intercept_hardcoded", v).apply() }

    var autoStartOnBoot: Boolean
        get() = sp.getBoolean("autostart", true)
        set(v) { sp.edit().putBoolean("autostart", v).apply() }

    var wasActive: Boolean
        get() = sp.getBoolean("was_active", false)
        set(v) { sp.edit().putBoolean("was_active", v).apply() }

    var listAds: Boolean
        get() = sp.getBoolean("list_ads", true)
        set(v) { sp.edit().putBoolean("list_ads", v).apply() }

    var listTrackers: Boolean
        get() = sp.getBoolean("list_trackers", true)
        set(v) { sp.edit().putBoolean("list_trackers", v).apply() }

    var listAdult: Boolean
        get() = sp.getBoolean("list_adult", false)
        set(v) { sp.edit().putBoolean("list_adult", v).apply() }

    var pinHash: String?
        get() = sp.getString("pin_hash", null)
        set(v) { sp.edit().putString("pin_hash", v).apply() }

    var pinSalt: String?
        get() = sp.getString("pin_salt", null)
        set(v) { sp.edit().putString("pin_salt", v).apply() }

    var onboardingDone: Boolean
        get() = sp.getBoolean("onboarding_done", false)
        set(v) { sp.edit().putBoolean("onboarding_done", v).apply() }

    var uninstallProtection: Boolean
        get() = sp.getBoolean("uninstall_protection", false)
        set(v) { sp.edit().putBoolean("uninstall_protection", v).apply() }

    var totalQueries: Long
        get() = sp.getLong("total_queries", 0L)
        set(v) { sp.edit().putLong("total_queries", v).apply() }

    var totalBlocked: Long
        get() = sp.getLong("total_blocked", 0L)
        set(v) { sp.edit().putLong("total_blocked", v).apply() }

    var blockedToday: Long
        get() = if (sp.getString("today_date", "") == today()) sp.getLong("today_blocked", 0L) else 0L
        set(v) { sp.edit().putLong("today_blocked", v).putString("today_date", today()).apply() }

    fun addBlockedToday(count: Long) {
        if (count <= 0) return
        val d = today()
        val current = if (sp.getString("today_date", "") == d) sp.getLong("today_blocked", 0L) else 0L
        sp.edit().putLong("today_blocked", current + count).putString("today_date", d).apply()
    }

    fun resetStats() {
        sp.edit().putLong("total_queries", 0L).putLong("total_blocked", 0L)
            .putLong("today_blocked", 0L).putString("today_date", today()).apply()
    }

    private fun today(): String {
        val c = java.util.Calendar.getInstance()
        return "%04d-%02d-%02d".format(c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH))
    }
}
