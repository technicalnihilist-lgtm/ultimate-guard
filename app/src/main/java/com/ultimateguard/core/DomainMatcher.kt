package com.ultimateguard.core

import java.util.concurrent.atomic.AtomicReference

/**
 * Matches a host name against a set of blocked domains, including subdomains
 * of a blocked domain ("ads.example.com" is blocked when "example.com" is in
 * the list). Pure JVM, unit-testable.
 *
 * The matcher is immutable: a reload swaps the whole reference, so the DNS
 * threads never see a half-updated list.
 */
class DomainMatcher {

    class Rules(val exact: Set<String>, val whitelist: Set<String>)

    private val rules = AtomicReference(Rules(emptySet(), emptySet()))

    val size: Int get() = rules.get().exact.size

    fun load(blocked: Collection<String>, allowed: Collection<String> = emptyList()) {
        rules.set(Rules(blocked.mapNotNull { normalize(it) }.toHashSet(),
                        allowed.mapNotNull { normalize(it) }.toHashSet()))
    }

    /**
     * Walks the name's parent domains: a.b.example.com -> a.b.example.com,
     * b.example.com, example.com, com. O(labels) HashSet lookups.
     */
    fun isBlocked(host: String): Boolean {
        val name = normalize(host) ?: return false
        val r = rules.get()
        if (r.exact.isEmpty()) return false
        if (r.whitelist.isNotEmpty() && matches(name, r.whitelist)) return false
        return matches(name, r.exact)
    }

    private fun matches(name: String, set: Set<String>): Boolean {
        var candidate = name
        while (true) {
            if (set.contains(candidate)) return true
            val dot = candidate.indexOf('.')
            if (dot < 0) return false
            candidate = candidate.substring(dot + 1)
            if (candidate.indexOf('.') < 0) return false   // never match a bare TLD
        }
    }

    companion object {
        /** Lowercases, strips dots/spaces and rejects anything that is not a plausible host. */
        fun normalize(raw: String): String? {
            var s = raw.trim().lowercase()
            if (s.startsWith("||")) s = s.removePrefix("||")
            if (s.startsWith("|")) s = s.removePrefix("|")
            s = s.trimEnd('^', '/')
            if (s.contains('/')) s = s.substringBefore('/')
            s = s.trimEnd('.')
            if (s.isEmpty() || s.length > 253) return null
            if (!s.contains('.')) return null
            for (c in s) {
                if (!(c in 'a'..'z' || c in '0'..'9' || c == '.' || c == '-' || c == '_')) return null
            }
            return s
        }
    }
}
