package com.ultimateguard.core

/**
 * Pure-JVM DNS message parsing/building plus IP+UDP encapsulation helpers.
 *
 * Nothing in this file touches the Android framework, so it can be unit-tested
 * on a plain JVM (see tools/DnsSelfTest.kt).
 */
object DnsPacket {

    const val TYPE_A = 1
    const val TYPE_AAAA = 28
    const val TYPE_HTTPS = 65
    const val TYPE_ANY = 255

    const val RCODE_NOERROR = 0
    const val RCODE_SERVFAIL = 2

    class Query(
        val id: Int,
        val flags: Int,
        val name: String,
        val qtype: Int,
        /** Offset in [payload] just past the question section (QTYPE+QCLASS). */
        val questionEnd: Int,
        val payload: ByteArray
    )

    fun u16(b: ByteArray, off: Int): Int = ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)

    private fun putU16(b: ByteArray, off: Int, v: Int) {
        b[off] = ((v ushr 8) and 0xFF).toByte()
        b[off + 1] = (v and 0xFF).toByte()
    }

    private fun putU32(b: ByteArray, off: Int, v: Int) {
        b[off] = ((v ushr 24) and 0xFF).toByte()
        b[off + 1] = ((v ushr 16) and 0xFF).toByte()
        b[off + 2] = ((v ushr 8) and 0xFF).toByte()
        b[off + 3] = (v and 0xFF).toByte()
    }

    /**
     * Parses a DNS query. Returns null for anything we should not answer
     * ourselves (responses, malformed packets, multi-question packets).
     */
    fun parseQuery(data: ByteArray, length: Int): Query? {
        if (length < 12) return null
        val id = u16(data, 0)
        val flags = u16(data, 2)
        if (flags and 0x8000 != 0) return null                 // it is a response, not a query
        val qdCount = u16(data, 4)
        if (qdCount < 1) return null
        if (flags and 0x7800 != 0) return null                 // opcode != QUERY

        var off = 12
        val sb = StringBuilder()
        var guard = 0
        while (true) {
            if (off >= length || guard++ > 128) return null
            val len = data[off].toInt() and 0xFF
            if (len == 0) { off++; break }
            if (len and 0xC0 != 0) return null                 // compression in a question: unsupported
            if (off + 1 + len > length) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (i in 0 until len) sb.append(Char((data[off + 1 + i].toInt() and 0xFF)))
            off += 1 + len
        }
        if (off + 4 > length) return null
        val qtype = u16(data, off)
        off += 4
        var name = sb.toString().lowercase()
        if (name.endsWith(".")) name = name.dropLast(1)
        if (name.isEmpty()) return null
        return Query(id, flags, name, qtype, off, data.copyOf(length))
    }

    /** Copies the header + question of [q] and returns a writer positioned after it. */
    private fun responseBuilder(q: Query, answerCount: Int, rcode: Int): ByteArray {
        val qlen = q.questionEnd
        val out = ByteArray(qlen)
        System.arraycopy(q.payload, 0, out, 0, qlen)
        // QR=1, copy opcode/RD from the query, RA=1, rcode
        var flags = 0x8000 or (q.flags and 0x0100) or 0x0080 or (rcode and 0x0F)
        putU16(out, 2, flags)
        putU16(out, 4, 1)            // QDCOUNT
        putU16(out, 6, answerCount)  // ANCOUNT
        putU16(out, 8, 0)            // NSCOUNT
        putU16(out, 10, 0)           // ARCOUNT
        return out
    }

    /**
     * Builds the response we send for a blocked name.
     *
     * A  -> 0.0.0.0, AAAA -> ::, everything else -> NOERROR with no answers
     * (NODATA). Answering with the null address rather than NXDOMAIN keeps
     * clients from hammering other resolvers for the same name.
     */
    fun buildBlockResponse(q: Query): ByteArray {
        val wantV4 = q.qtype == TYPE_A || q.qtype == TYPE_ANY
        val wantV6 = q.qtype == TYPE_AAAA || q.qtype == TYPE_ANY
        val answerCount = (if (wantV4) 1 else 0) + (if (wantV6) 1 else 0)
        val base = responseBuilder(q, answerCount, RCODE_NOERROR)
        if (answerCount == 0) return base
        val out = base.copyOf(base.size + answerCount * 28)
        var off = base.size
        if (wantV4) {
            off = writeAnswer(out, off, TYPE_A, ByteArray(4))
        }
        if (wantV6) {
            off = writeAnswer(out, off, TYPE_AAAA, ByteArray(16))
        }
        return out.copyOf(off)
    }

    private fun writeAnswer(out: ByteArray, offset: Int, type: Int, rdata: ByteArray): Int {
        var off = offset
        putU16(out, off, 0xC00C); off += 2     // name: pointer to offset 12 (the question)
        putU16(out, off, type); off += 2
        putU16(out, off, 1); off += 2          // class IN
        putU32(out, off, 60); off += 4         // TTL 60s
        putU16(out, off, rdata.size); off += 2
        System.arraycopy(rdata, 0, out, off, rdata.size); off += rdata.size
        return off
    }

    fun buildErrorResponse(q: Query, rcode: Int): ByteArray = responseBuilder(q, 0, rcode)
}
