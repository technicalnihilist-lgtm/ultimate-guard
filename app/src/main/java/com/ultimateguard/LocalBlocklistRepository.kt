package com.ultimateguard

import android.content.Context

/**
 * Offline-first domain list storage.
 *
 * Bundled lists are loaded from APK assets. User additions are stored locally
 * in SharedPreferences. Matching is exact-or-subdomain and requires no network.
 *
 * The bundled files are deliberately small starter examples, not exhaustive
 * threat feeds. Replace/update them with appropriately licensed, maintained
 * lists before production release.
 */
class LocalBlocklistRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("local_blocklists", Context.MODE_PRIVATE)

    fun isBlocked(hostname: String): Boolean {
        val host = normalize(hostname) ?: return false
        return allDomains().any { domain -> host == domain || host.endsWith(".$domain") }
    }

    fun addUserDomain(hostname: String) {
        val domain = normalize(hostname) ?: throw IllegalArgumentException("Invalid domain")
        val updated = userDomains() + domain
        prefs.edit().putStringSet("user_domains", updated).apply()
    }

    fun removeUserDomain(hostname: String) {
        val domain = normalize(hostname) ?: return
        prefs.edit().putStringSet("user_domains", userDomains() - domain).apply()
    }

    fun listDomains(): Set<String> = allDomains()

    private fun allDomains(): Set<String> =
        loadAsset("blocklists/ad_domains.txt") +
        loadAsset("blocklists/adult_domains.txt") +
        userDomains()

    private fun userDomains(): Set<String> =
        prefs.getStringSet("user_domains", emptySet())?.toSet() ?: emptySet()

    private fun loadAsset(path: String): Set<String> =
        runCatching {
            context.assets.open(path).bufferedReader().useLines { lines ->
                lines.mapNotNull(::normalize).toSet()
            }
        }.getOrDefault(emptySet())

    private fun normalize(raw: String): String? {
        val candidate = raw.trim().lowercase().trimEnd('.')
        if (candidate.isEmpty() || candidate.startsWith("#") || candidate.contains("/") ||
            candidate.contains(":") || candidate.contains(" ")) return null
        if (candidate.length > 253 || candidate.split(".").any { it.isBlank() || it.length > 63 }) return null
        return candidate
    }
}
