package com.ultimateguard.core

import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The actual packet engine: reads IP packets from the TUN device, answers DNS
 * queries for blocked names locally, and forwards everything else to an
 * upstream resolver.
 *
 * Only DNS is routed into the tunnel (plus the public resolver addresses when
 * "intercept hardcoded DNS" is on), so all other traffic keeps using the
 * normal network path — no full-tunnel overhead.
 */
class DnsEngine(
    private val tun: ParcelFileDescriptor,
    private val protectSocket: (DatagramSocket) -> Boolean,
    private val matcher: () -> DomainMatcher,
    private val upstreams: () -> List<String>,
    private val onQuery: (domain: String, blocked: Boolean) -> Unit
) {

    private val running = AtomicBoolean(false)
    private val writeLock = Any()
    private val pool = ThreadPoolExecutor(
        4, 24, 30L, TimeUnit.SECONDS,
        LinkedBlockingQueue(1024),
        ThreadPoolExecutor.DiscardPolicy()
    )
    private val cache = ResponseCache(256, 30_000L)

    @Volatile private var reader: Thread? = null
    private var output: java.io.FileOutputStream? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        output = runCatching { java.io.FileOutputStream(tun.fileDescriptor) }.getOrNull()
        val t = Thread({ readLoop() }, "ug-dns-reader")
        t.priority = Thread.NORM_PRIORITY + 1
        reader = t
        t.start()
    }

    fun stop() {
        running.set(false)
        runCatching { tun.close() }
        pool.shutdownNow()
        reader?.interrupt()
    }

    private fun readLoop() {
        val input = FileInputStream(tun.fileDescriptor)
        val buffer = ByteArray(32_767)
        while (running.get()) {
            val n = try {
                input.read(buffer)
            } catch (e: IOException) {
                break
            }
            if (n <= 0) continue
            try {
                dispatch(buffer, n)
            } catch (e: Exception) {
                GuardState.lastError = "packet: ${e.message}"
            }
        }
        runCatching { input.close() }
    }

    private fun dispatch(buffer: ByteArray, length: Int) {
        val inbound = IpPacket.parse(buffer, length) ?: return
        // The tunnel carries DNS only. Anything else (DoT/DoH aimed at a routed
        // resolver address) is dropped, which is what makes bypass attempts fail.
        if (inbound.destPort != 53) return
        if (inbound.protocol != IpPacket.PROTO_UDP) return   // DNS-over-TCP is not tunnelled

        val payload = buffer.copyOfRange(
            inbound.payloadOffset,
            inbound.payloadOffset + inbound.payloadLength
        )
        val srcIp = inbound.sourceIp.copyOf()
        val dstIp = inbound.destIp.copyOf()
        val srcPort = inbound.sourcePort
        val dstPort = inbound.destPort

        pool.execute {
            try {
                val answer = answer(payload)
                if (answer != null) send(srcIp, dstIp, srcPort, dstPort, answer)
            } catch (e: Exception) {
                GuardState.lastError = "query: ${e.message}"
            }
        }
    }

    /** Returns the DNS response payload for a query payload, or null to stay silent. */
    private fun answer(payload: ByteArray): ByteArray? {
        val query = DnsPacket.parseQuery(payload, payload.size)
            ?: return forward(payload)   // not a simple query: pass through untouched

        val blocked = matcher().isBlocked(query.name)
        onQuery(query.name, blocked)

        if (blocked) {
            GuardState.lastBlockedDomain = query.name
            return DnsPacket.buildBlockResponse(query)
        }

        val key = query.name + "|" + query.qtype
        cache.get(key)?.let { cached ->
            val copy = cached.copyOf()
            // Patch the transaction id so the cached answer matches this query.
            copy[0] = payload[0]
            copy[1] = payload[1]
            return copy
        }

        val response = forward(payload) ?: return DnsPacket.buildErrorResponse(query, DnsPacket.RCODE_SERVFAIL)
        if (response.size > 12 && response[3].toInt() and 0x0F == 0) {
            val anCount = DnsPacket.u16(response, 6)
            if (anCount > 0) cache.put(key, response.copyOf())
        }
        return response
    }

    private fun forward(payload: ByteArray): ByteArray? {
        for (upstream in upstreams()) {
            try {
                DatagramSocket().use { socket ->
                    if (!protectSocket(socket)) return@use
                    socket.soTimeout = 3000
                    val address = InetAddress.getByName(upstream)
                    socket.send(DatagramPacket(payload, payload.size, address, 53))
                    val receiveBuffer = ByteArray(4096)
                    val packet = DatagramPacket(receiveBuffer, receiveBuffer.size)
                    socket.receive(packet)
                    GuardState.upstreamInUse = upstream
                    return receiveBuffer.copyOf(packet.length)
                }
            } catch (e: Exception) {
                // try the next upstream
            }
        }
        return null
    }

    private fun send(
        clientIp: ByteArray,
        serverIp: ByteArray,
        clientPort: Int,
        serverPort: Int,
        payload: ByteArray
    ) {
        val packet = IpPacket.buildUdp(serverIp, clientIp, serverPort, clientPort, payload)
        val out = output ?: return
        synchronized(writeLock) {
            try {
                out.write(packet)
            } catch (e: IOException) {
                running.set(false)
            }
        }
    }

    /** Tiny TTL-capped response cache keyed by name+type. */
    private class ResponseCache(private val max: Int, private val ttlMs: Long) {
        private val map = object : LinkedHashMap<String, Entry>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean = size > max
        }

        private class Entry(val bytes: ByteArray, val expiresAt: Long)

        @Synchronized
        fun get(key: String): ByteArray? {
            val e = map[key] ?: return null
            if (e.expiresAt < System.currentTimeMillis()) {
                map.remove(key)
                return null
            }
            return e.bytes
        }

        @Synchronized
        fun put(key: String, bytes: ByteArray) {
            map[key] = Entry(bytes, System.currentTimeMillis() + ttlMs)
        }
    }
}
