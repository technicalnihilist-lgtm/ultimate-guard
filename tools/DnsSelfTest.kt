import com.ultimateguard.core.DnsPacket
import com.ultimateguard.core.DomainMatcher
import com.ultimateguard.core.IpPacket

var failures = 0
fun check(name: String, cond: Boolean) {
    if (!cond) { failures++; println("FAIL  $name") } else println("ok    $name")
}

fun buildQuery(name: String, type: Int, id: Int): ByteArray {
    val out = ArrayList<Byte>()
    fun u16(v: Int) { out.add(((v ushr 8) and 0xFF).toByte()); out.add((v and 0xFF).toByte()) }
    u16(id); u16(0x0100); u16(1); u16(0); u16(0); u16(0)
    for (label in name.split(".")) { out.add(label.length.toByte()); label.forEach { out.add(it.code.toByte()) } }
    out.add(0); u16(type); u16(1)
    return out.toByteArray()
}

fun main() {
    // --- query parsing -------------------------------------------------
    val q = DnsPacket.parseQuery(buildQuery("Ads.Example.COM", 1, 0x1234), buildQuery("Ads.Example.COM", 1, 0x1234).size)!!
    check("parses name lowercase", q.name == "ads.example.com")
    check("parses id", q.id == 0x1234)
    check("parses qtype", q.qtype == 1)

    // --- blocked response ---------------------------------------------
    val resp = DnsPacket.buildBlockResponse(q)
    check("response id echoed", DnsPacket.u16(resp, 0) == 0x1234)
    check("QR bit set", DnsPacket.u16(resp, 2) and 0x8000 != 0)
    check("RA bit set", DnsPacket.u16(resp, 2) and 0x0080 != 0)
    check("rcode 0", DnsPacket.u16(resp, 2) and 0x000F == 0)
    check("qdcount 1", DnsPacket.u16(resp, 4) == 1)
    check("ancount 1", DnsPacket.u16(resp, 6) == 1)
    check("answer type A", DnsPacket.u16(resp, q.questionEnd + 2) == 1)
    check("answer rdata 0.0.0.0", resp.copyOfRange(resp.size - 4, resp.size).all { it == 0.toByte() })
    check("ttl 60", DnsPacket.u16(resp, q.questionEnd + 6) == 0 && DnsPacket.u16(resp, q.questionEnd + 8) == 60)

    val q6 = DnsPacket.parseQuery(buildQuery("track.example.com", 28, 7), 200)!!
    val r6 = DnsPacket.buildBlockResponse(q6)
    check("AAAA answered with ::", r6.size == q6.questionEnd + 28 && r6.copyOfRange(r6.size - 16, r6.size).all { it == 0.toByte() })

    val qh = DnsPacket.parseQuery(buildQuery("x.example.com", 65, 9), 200)!!
    val rh = DnsPacket.buildBlockResponse(qh)
    check("HTTPS gets NODATA", DnsPacket.u16(rh, 6) == 0 && rh.size == qh.questionEnd)

    // --- matcher -------------------------------------------------------
    val m = DomainMatcher()
    m.load(listOf("doubleclick.net", "ads.example.com", "tracker.io"), listOf("good.tracker.io"))
    check("exact blocked", m.isBlocked("doubleclick.net"))
    check("subdomain blocked", m.isBlocked("ad.g.doubleclick.net"))
    check("sibling allowed", !m.isBlocked("example.com"))
    check("unrelated allowed", !m.isBlocked("google.com"))
    check("whitelist wins", !m.isBlocked("good.tracker.io"))
    check("case insensitive", m.isBlocked("AD.G.DoubleClick.NET"))
    check("hosts-file format", DomainMatcher.normalize("0.0.0.0 ads.tracker.io") == null)
    check("adblock format", DomainMatcher.normalize("||ads.tracker.io^") == "ads.tracker.io")
    check("rejects bare tld", !m.isBlocked("net"))

    // --- IPv4 UDP round trip ------------------------------------------
    val client = byteArrayOf(10, 111, 222.toByte(), 1)
    val server = byteArrayOf(10, 111, 222.toByte(), 2)
    val payload = buildQuery("ads.example.com", 1, 0x4321)
    val pkt = IpPacket.buildUdp(client, server, 40000, 53, payload)
    check("v4 total length", DnsPacket.u16(pkt, 2) == pkt.size)
    val inb = IpPacket.parse(pkt, pkt.size)!!
    check("v4 parse ports", inb.sourcePort == 40000 && inb.destPort == 53)
    check("v4 parse proto", inb.protocol == IpPacket.PROTO_UDP)
    check("v4 parse src", IpPacket.formatIp(inb.sourceIp) == "10.111.222.1")
    check("v4 payload intact", pkt.copyOfRange(inb.payloadOffset, inb.payloadOffset + inb.payloadLength).contentEquals(payload))
    // verify IPv4 header checksum sums to 0xFFFF
    var hsum = 0
    for (i in 0 until 20 step 2) hsum += DnsPacket.u16(pkt, i)
    while (hsum shr 16 != 0) hsum = (hsum and 0xFFFF) + (hsum shr 16)
    check("v4 header checksum valid", hsum == 0xFFFF)

    // --- IPv6 UDP round trip ------------------------------------------
    val c6 = ByteArray(16).also { it[15] = 1; it[0] = 0xfd.toByte() }
    val s6 = ByteArray(16).also { it[15] = 2; it[0] = 0xfd.toByte() }
    val p6 = IpPacket.buildUdp(c6, s6, 5000, 53, payload)
    check("v6 next header", p6[6].toInt() == 17)
    val inb6 = IpPacket.parse(p6, p6.size)!!
    check("v6 parse", inb6.ipVersion == 6 && inb6.destPort == 53 && inb6.sourcePort == 5000)
    check("v6 payload intact", p6.copyOfRange(inb6.payloadOffset, inb6.payloadOffset + inb6.payloadLength).contentEquals(payload))

    // --- garbage in ----------------------------------------------------
    check("short packet rejected", IpPacket.parse(ByteArray(5), 5) == null)
    check("tcp dns parses", run {
        val tcp = IpPacket.buildUdp(client, server, 1234, 53, payload)
        tcp[9] = 6; tcp[10] = 0; tcp[11] = 0
        IpPacket.parse(tcp, tcp.size)?.protocol == IpPacket.PROTO_TCP
    })
    check("truncated dns rejected", DnsPacket.parseQuery(ByteArray(8), 8) == null)

    println(if (failures == 0) "\nALL TESTS PASSED" else "\n$failures TEST(S) FAILED")
    if (failures > 0) System.exit(1)
}
