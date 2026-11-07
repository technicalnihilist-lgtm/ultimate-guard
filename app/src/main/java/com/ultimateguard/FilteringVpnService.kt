package com.ultimateguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import com.ultimateguard.core.BlocklistRepository
import com.ultimateguard.core.DnsEngine
import com.ultimateguard.core.GuardState
import com.ultimateguard.core.Prefs
import com.ultimateguard.core.RecentLog
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Foreground VPN service that runs the local DNS filter.
 *
 * The tunnel carries DNS only: the system resolver points at the virtual
 * address inside the tunnel, and (optionally) the well-known public resolver
 * addresses are routed in too so apps that hardcode DNS are filtered as well.
 */
class FilteringVpnService : VpnService() {

    private var tunnel: ParcelFileDescriptor? = null
    private var engine: DnsEngine? = null
    private lateinit var prefs: Prefs
    private lateinit var repository: BlocklistRepository
    private lateinit var recent: RecentLog
    private var ticker: ScheduledExecutorService? = null
    private var lastFlushedQueries = 0L
    private var lastFlushedBlocked = 0L

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        repository = BlocklistRepository(this)
        recent = RecentLog(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            stopSelf()
            return START_NOT_STICKY
        }

        if (GuardState.active.get()) return START_STICKY

        if (prepare(this) != null) {
            GuardState.lastError = "VPN permission not granted"
            stopSelf()
            return START_NOT_STICKY
        }

        startInForeground(buildNotification(0))

        repository.reload()

        val fd = runCatching { buildTunnel() }.getOrNull()
        if (fd == null) {
            GuardState.lastError = "Could not establish the tunnel"
            stopSelf()
            return START_NOT_STICKY
        }
        tunnel = fd

        engine = DnsEngine(
            tun = fd,
            protectSocket = { socket -> protect(socket) },
            matcher = { repository.matcher },
            upstreams = { upstreamList() },
            onQuery = { domain, blocked ->
                GuardState.queries.incrementAndGet()
                if (blocked) GuardState.blocked.incrementAndGet()
                recent.record(domain, blocked)
            }
        ).also { it.start() }

        GuardState.active.set(true)
        GuardState.startedAt = System.currentTimeMillis()
        GuardState.lastError = null
        GuardState.domainsLoaded = repository.totalCount
        prefs.wasActive = true

        startTicker()
        return START_STICKY
    }

    private fun buildTunnel(): ParcelFileDescriptor? {
        val builder = Builder()
        builder.setSession("Ultimate Guard")
        builder.setMtu(1500)
        builder.addAddress(V4_CLIENT, 32)
        builder.addDnsServer(V4_DNS)
        builder.addRoute(V4_DNS, 32)
        builder.addAddress(V6_CLIENT, 128)
        builder.addDnsServer(V6_DNS)
        builder.addRoute(V6_DNS, 128)
        if (prefs.interceptHardcodedDns) {
            for (ip in PUBLIC_RESOLVERS) {
                runCatching { builder.addRoute(ip, 32) }
            }
        }
        // Our own traffic must never loop back into the tunnel.
        runCatching { builder.addDisallowedApplication(packageName) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)
        builder.setBlocking(false)
        return builder.establish()
    }

    private fun upstreamList(): List<String> {
        val list = ArrayList<String>(3)
        prefs.upstreamPrimary.takeIf { it.isNotBlank() }?.let { list.add(it) }
        prefs.upstreamSecondary.takeIf { it.isNotBlank() }?.let { list.add(it) }
        if (!list.contains("1.1.1.1")) list.add("1.1.1.1")
        if (!list.contains("8.8.8.8")) list.add("8.8.8.8")
        return list
    }

    private fun startTicker() {
        val executor = Executors.newSingleThreadScheduledExecutor()
        ticker = executor
        executor.scheduleWithFixedDelay({
            try {
                flushStats()
                recent.flush()
                updateNotification()
            } catch (e: Exception) {
                // never let the ticker die
            }
        }, 5, 5, TimeUnit.SECONDS)
    }

    private fun flushStats() {
        val q = GuardState.queries.get()
        val b = GuardState.blocked.get()
        val newQueries = q - lastFlushedQueries
        val newBlocked = b - lastFlushedBlocked
        if (newQueries <= 0 && newBlocked <= 0) return
        lastFlushedQueries = q
        lastFlushedBlocked = b
        prefs.totalQueries = prefs.totalQueries + newQueries
        prefs.totalBlocked = prefs.totalBlocked + newBlocked
        prefs.addBlockedToday(newBlocked)
    }

    private fun shutdown() {
        runCatching { engine?.stop() }
        engine = null
        runCatching { tunnel?.close() }
        tunnel = null
        runCatching { ticker?.shutdownNow() }
        ticker = null
        runCatching { flushStats() }
        runCatching { recent.flush() }
        GuardState.active.set(false)
        prefs.wasActive = false
        stopForegroundCompat()
    }

    override fun onRevoke() {
        GuardState.lastError = "VPN permission was revoked"
        shutdown()
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    // ---- notification ---------------------------------------------------

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Protection status", NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Shows whether Ultimate Guard is filtering DNS"
            channel.setShowBadge(false)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(blockedCount: Long): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("Ultimate Guard is protecting this device")
            .setContentText("$blockedCount blocked · ${GuardState.domainsLoaded} domains on the list")
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(open)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private fun startInForeground(notification: Notification) {
        // FOREGROUND_SERVICE_TYPE_SPECIAL_USE only exists from Android 14; passing an
        // unknown type on Android 10-13 throws IllegalArgumentException.
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        if (!GuardState.active.get()) return
        runCatching {
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, buildNotification(GuardState.blocked.get()))
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    companion object {
        const val ACTION_START = "com.ultimateguard.action.START"
        const val ACTION_STOP = "com.ultimateguard.action.STOP"
        private const val CHANNEL_ID = "guard_status"
        private const val NOTIFICATION_ID = 41

        const val V4_CLIENT = "10.111.222.1"
        const val V4_DNS = "10.111.222.2"
        const val V6_CLIENT = "fd00:1:fd00:1::1"
        const val V6_DNS = "fd00:1:fd00:1::2"

        /** Public resolvers routed into the tunnel so hardcoded DNS cannot bypass the filter. */
        val PUBLIC_RESOLVERS = listOf(
            "8.8.8.8", "8.8.4.4",
            "1.1.1.1", "1.0.0.1",
            "9.9.9.9", "149.112.112.112",
            "208.67.222.222", "208.67.220.220",
            "64.6.64.6", "64.6.65.6",
            "84.200.69.80", "77.88.8.8"
        )
    }
}
