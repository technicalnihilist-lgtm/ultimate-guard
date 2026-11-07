package com.ultimateguard.core

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Live, in-process state shared between the VPN engine and the UI. */
object GuardState {
    val active = AtomicBoolean(false)
    val queries = AtomicLong(0)
    val blocked = AtomicLong(0)
    @Volatile var domainsLoaded: Int = 0
    @Volatile var lastBlockedDomain: String? = null
    @Volatile var lastError: String? = null
    @Volatile var startedAt: Long = 0L
    @Volatile var upstreamInUse: String = ""
}
