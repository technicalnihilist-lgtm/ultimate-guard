package com.ultimateguard.core

/**
 * Minimal IPv4/IPv6 + UDP packet parsing and construction, sufficient for a
 * DNS-only TUN tunnel. Pure JVM, unit-testable.
 */
object IpPacket {

    const val PROTO_TCP = 6
    const val PROTO_UDP = 17

    class Inbound(
        val ipVersion: Int,
        val sourceIp: ByteArray,
        val destIp: ByteArray,
        val protocol: Int,
        val sourcePort: Int,
        val destPort: Int,
        val payloadOffset: Int,
        val payloadLength: Int
    ) {
        val isFragment: Boolean get() = false
    }

    /**
     * Parses a packet read from the TUN device. Returns null for anything that
     * is not a complete, unfragmented UDP or TCP packet we understand.
     */
    fun parse(buffer: ByteArray, length: Int): Inbound? {
        if (length < 20) return null
        when ((buffer[0].toInt() and 0xF0) shr 4) {
            4 -> {
                val ihl = (buffer[0].toInt() and 0x0F) * 4
                if (ihl < 20 || length < ihl + 8) return null
                val totalLen = DnsPacket.u16(buffer, 2)
                if (totalLen > length) return null
                val fragField = DnsPacket.u16(buffer, 6)
                if (fragField and 0x1FFF != 0) return null       // fragment offset != 0: skip
                val proto = buffer[9].toInt() and 0xFF
                if (proto != PROTO_UDP && proto != PROTO_TCP) return null
                val src = buffer.copyOfRange(12, 16)
                val dst = buffer.copyOfRange(16, 20)
                val sport = DnsPacket.u16(buffer, ihl)
                val dport = DnsPacket.u16(buffer, ihl + 2)
                var payloadLen = totalLen - ihl - 8
                if (proto == PROTO_UDP) {
                    val udpLen = DnsPacket.u16(buffer, ihl + 4)
                    if (udpLen >= 8) payloadLen = minOf(payloadLen, udpLen - 8)
                }
                if (payloadLen < 0) return null
                return Inbound(4, src, dst, proto, sport, dport, ihl + 8, payloadLen)
            }
            6 -> {
                if (length < 48) return null
                val payloadLen = DnsPacket.u16(buffer, 4)
                var nextHeader = buffer[6].toInt() and 0xFF
                var off = 40
                var hops = 0
                // Skip a few common extension headers (hop-by-hop, routing, destination options).
                while ((nextHeader == 0 || nextHeader == 43 || nextHeader == 60) && hops++ < 8) {
                    if (off + 8 > length) return null
                    val hdrExtLen = (buffer[off + 1].toInt() and 0xFF) * 8 + 8
                    nextHeader = buffer[off].toInt() and 0xFF
                    off += hdrExtLen
                }
                if (nextHeader != PROTO_UDP && nextHeader != PROTO_TCP) return null
                if (off + 8 > length) return null
                val src = buffer.copyOfRange(8, 24)
                val dst = buffer.copyOfRange(24, 40)
                val sport = DnsPacket.u16(buffer, off)
                val dport = DnsPacket.u16(buffer, off + 2)
                var plen = payloadLen - (off - 40) - 8
                if (nextHeader == PROTO_UDP) {
                    val udpLen = DnsPacket.u16(buffer, off + 4)
                    if (udpLen >= 8) plen = minOf(plen, udpLen - 8)
                }
                if (plen < 0) return null
                return Inbound(6, src, dst, nextHeader, sport, dport, off + 8, plen)
            }
        }
        return null
    }

    /**
     * Wraps [payload] in a UDP datagram from [sourceIp]:[sourcePort] to
     * [destIp]:[destPort], choosing IPv4 or IPv6 from the address length.
     */
    fun buildUdp(
        sourceIp: ByteArray,
        destIp: ByteArray,
        sourcePort: Int,
        destPort: Int,
        payload: ByteArray
    ): ByteArray {
        val udpLen = 8 + payload.size
        return if (sourceIp.size == 4 && destIp.size == 4) {
            val out = ByteArray(20 + udpLen)
            out[0] = 0x45                                  // IPv4, IHL=5
            out[1] = 0
            putU16(out, 2, 20 + udpLen)
            putU16(out, 4, 0)                              // identification
            putU16(out, 6, 0x4000)                         // don't fragment
            out[8] = 64                                    // TTL
            out[9] = PROTO_UDP.toByte()
            System.arraycopy(sourceIp, 0, out, 12, 4)
            System.arraycopy(destIp, 0, out, 16, 4)
            putU16(out, 10, checksum(out, 0, 20))
            writeUdp(out, 20, sourceIp, destIp, sourcePort, destPort, payload)
            out
        } else {
            val out = ByteArray(40 + udpLen)
            out[0] = 0x60                                  // IPv6
            putU16(out, 4, udpLen)
            out[6] = PROTO_UDP.toByte()
            out[7] = 64
            System.arraycopy(sourceIp, 0, out, 8, 16)
            System.arraycopy(destIp, 0, out, 24, 16)
            writeUdp(out, 40, sourceIp, destIp, sourcePort, destPort, payload)
            out
        }
    }

    private fun writeUdp(
        out: ByteArray,
        off: Int,
        sourceIp: ByteArray,
        destIp: ByteArray,
        sourcePort: Int,
        destPort: Int,
        payload: ByteArray
    ) {
        putU16(out, off, sourcePort)
        putU16(out, off + 2, destPort)
        putU16(out, off + 4, 8 + payload.size)
        putU16(out, off + 6, 0)
        System.arraycopy(payload, 0, out, off + 8, payload.size)
        val sum = udpChecksum(sourceIp, destIp, out, off, 8 + payload.size)
        putU16(out, off + 6, if (sum == 0) 0xFFFF else sum)
    }

    private fun udpChecksum(
        sourceIp: ByteArray,
        destIp: ByteArray,
        udp: ByteArray,
        off: Int,
        udpLen: Int
    ): Int {
        var sum = 0L
        for (i in sourceIp.indices step 2) {
            sum += ((sourceIp[i].toInt() and 0xFF) shl 8) or (sourceIp[i + 1].toInt() and 0xFF)
        }
        for (i in destIp.indices step 2) {
            sum += ((destIp[i].toInt() and 0xFF) shl 8) or (destIp[i + 1].toInt() and 0xFF)
        }
        sum += if (sourceIp.size == 4) PROTO_UDP else PROTO_UDP
        sum += udpLen
        var i = 0
        while (i + 1 < udpLen) {
            sum += ((udp[off + i].toInt() and 0xFF) shl 8) or (udp[off + i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < udpLen) sum += (udp[off + i].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv().toInt() and 0xFFFF
    }

    private fun checksum(data: ByteArray, off: Int, len: Int): Int {
        var sum = 0L
        var i = 0
        while (i + 1 < len) {
            sum += ((data[off + i].toInt() and 0xFF) shl 8) or (data[off + i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < len) sum += (data[off + i].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv().toInt() and 0xFFFF
    }

    private fun putU16(b: ByteArray, off: Int, v: Int) {
        b[off] = ((v ushr 8) and 0xFF).toByte()
        b[off + 1] = (v and 0xFF).toByte()
    }

    /** Human-readable address for logs. */
    fun formatIp(addr: ByteArray): String {
        if (addr.size == 4) return addr.joinToString(".") { (it.toInt() and 0xFF).toString() }
        val parts = ArrayList<String>(8)
        for (i in 0 until 16 step 2) {
            parts.add(Integer.toHexString(((addr[i].toInt() and 0xFF) shl 8) or (addr[i + 1].toInt() and 0xFF)))
        }
        return parts.joinToString(":")
    }
}
