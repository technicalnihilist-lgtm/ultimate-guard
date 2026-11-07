package com.ultimateguard.core

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Loads the bundled domain lists from APK assets plus the user's own lists
 * from app-private storage, and publishes them to a [DomainMatcher].
 * Works completely offline; nothing is downloaded.
 */
class BlocklistRepository(private val context: Context) {

    private val prefs = Prefs(context)
    val matcher = DomainMatcher()

    private val customFile = File(context.filesDir, "custom_blocked.txt")
    private val allowFile = File(context.filesDir, "allowlist.txt")

    @Volatile var adsCount = 0; private set
    @Volatile var trackersCount = 0; private set
    @Volatile var adultCount = 0; private set
    @Volatile var customCount = 0; private set
    @Volatile var allowCount = 0; private set
    @Volatile var totalCount = 0; private set

    /** Rebuilds the active rule set. Safe to call from a background thread. */
    @Synchronized
    fun reload() {
        val blocked = HashSet<String>(4096)
        var ads = 0
        var trackers = 0
        var adult = 0

        if (prefs.listAds) {
            val set = readAsset("blocklists/ads.txt")
            ads = set.size
            blocked.addAll(set)
        }
        if (prefs.listTrackers) {
            val set = readAsset("blocklists/trackers.txt")
            trackers = set.size
            blocked.addAll(set)
        }
        if (prefs.listAdult) {
            val set = readAsset("blocklists/adult.txt")
            adult = set.size
            blocked.addAll(set)
        }

        val custom = readDomains(customFile)
        blocked.addAll(custom)
        val allow = readDomains(allowFile)

        adsCount = ads
        trackersCount = trackers
        adultCount = adult
        customCount = custom.size
        allowCount = allow.size
        totalCount = blocked.size
        matcher.load(blocked, allow)
        GuardState.domainsLoaded = blocked.size
    }

    fun isBlocked(host: String): Boolean = matcher.isBlocked(host)

    // ---- user lists -----------------------------------------------------

    fun customDomains(): List<String> = readDomains(customFile).sorted()

    fun allowDomains(): List<String> = readDomains(allowFile).sorted()

    fun addCustom(raw: String): Boolean {
        val d = DomainMatcher.normalize(raw) ?: return false
        val current = readDomains(customFile)
        if (current.contains(d)) return false
        current.add(d)
        writeDomains(customFile, current)
        reload()
        return true
    }

    fun removeCustom(raw: String) {
        val d = DomainMatcher.normalize(raw) ?: return
        val current = readDomains(customFile)
        if (current.remove(d)) {
            writeDomains(customFile, current)
            reload()
        }
    }

    fun addAllowed(raw: String): Boolean {
        val d = DomainMatcher.normalize(raw) ?: return false
        val current = readDomains(allowFile)
        if (current.contains(d)) return false
        current.add(d)
        writeDomains(allowFile, current)
        reload()
        return true
    }

    fun removeAllowed(raw: String) {
        val d = DomainMatcher.normalize(raw) ?: return
        val current = readDomains(allowFile)
        if (current.remove(d)) {
            writeDomains(allowFile, current)
            reload()
        }
    }

    /**
     * Imports a text file of domains. Accepts plain domains, hosts-file lines
     * ("0.0.0.0 ads.example.com") and Adblock-style "||domain^" entries.
     * Returns how many new domains were added.
     */
    fun importFrom(uri: Uri): Int {
        val added = HashSet<String>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader().useLines { lines ->
                for (raw in lines) {
                    if (added.size > MAX_IMPORT) break
                    var line = raw.trim()
                    if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) continue
                    // hosts format: "0.0.0.0 domain" / "127.0.0.1 domain"
                    val parts = line.split(Regex("\\s+"))
                    if (parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1" || parts[0] == "::1")) {
                        line = parts[1]
                    }
                    val d = DomainMatcher.normalize(line) ?: continue
                    if (d == "localhost" || d.endsWith(".local")) continue
                    added.add(d)
                }
            }
        }
        val current = readDomains(customFile)
        var count = 0
        for (d in added) if (current.add(d)) count++
        writeDomains(customFile, current)
        reload()
        return count
    }

    fun clearCustom() {
        writeDomains(customFile, HashSet())
        reload()
    }

    // ---- io -------------------------------------------------------------

    private fun readAsset(path: String): Set<String> {
        val out = HashSet<String>(1024)
        runCatching {
            context.assets.open(path).bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val t = line.trim()
                    if (t.isEmpty() || t.startsWith("#")) return@forEach
                    DomainMatcher.normalize(t)?.let { out.add(it) }
                }
            }
        }
        return out
    }

    private fun readDomains(file: File): HashSet<String> {
        val out = HashSet<String>()
        runCatching {
            if (file.exists()) {
                file.readLines().forEach { line ->
                    val t = line.trim()
                    if (t.isNotEmpty() && !t.startsWith("#")) DomainMatcher.normalize(t)?.let { out.add(it) }
                }
            }
        }
        return out
    }

    private fun writeDomains(file: File, domains: Set<String>) {
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(domains.sorted().joinToString("\n"))
            tmp.renameTo(file)
        }
    }

    companion object { private const val MAX_IMPORT = 300_000 }
}
