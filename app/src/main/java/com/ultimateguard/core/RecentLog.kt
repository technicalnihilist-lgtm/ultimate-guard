package com.ultimateguard.core

import android.content.Context
import java.io.File
import java.util.ArrayDeque

/**
 * Rolling record of recent DNS decisions, kept in memory and flushed to a
 * small text file so the UI can show history without a database.
 */
class RecentLog(context: Context) {

    private val file = File(context.filesDir, "recent.log")
    private val entries = ArrayDeque<Entry>()
    private var dirty = false
    private val lock = Any()

    class Entry(val time: Long, val domain: String, val blocked: Boolean)

    init {
        runCatching {
            if (file.exists()) {
                file.readLines().forEach { line ->
                    val parts = line.split('|')
                    if (parts.size == 3) {
                        val t = parts[0].toLongOrNull() ?: return@forEach
                        entries.addLast(Entry(t, parts[1], parts[2] == "1"))
                    }
                }
            }
        }
    }

    fun record(domain: String, blocked: Boolean) {
        synchronized(lock) {
            entries.addFirst(Entry(System.currentTimeMillis(), domain, blocked))
            while (entries.size > MAX) entries.removeLast()
            dirty = true
        }
    }

    fun snapshot(limit: Int = 150): List<Entry> = synchronized(lock) {
        entries.take(limit)
    }

    fun clear() {
        synchronized(lock) {
            entries.clear()
            dirty = true
        }
        flush()
    }

    fun flush() {
        val copy = synchronized(lock) {
            if (!dirty) return
            dirty = false
            entries.toList()
        }
        runCatching {
            val tmp = File(file.parentFile, "recent.log.tmp")
            tmp.writeText(copy.joinToString("\n") { "${it.time}|${it.domain}|${if (it.blocked) 1 else 0}" })
            tmp.renameTo(file)
        }
    }

    companion object { private const val MAX = 200 }
}
