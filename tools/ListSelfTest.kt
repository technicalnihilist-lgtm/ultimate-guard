import com.ultimateguard.core.DomainMatcher
import java.io.File

fun main() {
    val m = DomainMatcher()
    val all = HashSet<String>()
    val bad = ArrayList<String>()
    for (f in listOf("ads.txt", "trackers.txt", "adult.txt")) {
        File("app/src/main/assets/blocklists/$f").readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .forEach { line ->
                val n = DomainMatcher.normalize(line)
                if (n == null) bad.add("$f: $line") else all.add(n)
            }
    }
    println("loaded ${all.size} domains, ${bad.size} unparsable")
    bad.take(10).forEach { println("  BAD $it") }
    m.load(all)
    val expectBlocked = listOf("doubleclick.net", "ad.doubleclick.net", "www.google-analytics.com",
        "app-measurement.com", "pornhub.com", "static.hotjar.com", "s0.2mdn.net", "cdn.taboola.com")
    val expectAllowed = listOf("google.com", "facebook.com", "whatsapp.com", "github.com",
        "youtube.com", "amazon.in", "wikipedia.org", "flipkart.com")
    var fail = 0
    for (d in expectBlocked) if (!m.isBlocked(d)) { println("FAIL should block $d"); fail++ }
    for (d in expectAllowed) if (m.isBlocked(d)) { println("FAIL should allow $d"); fail++ }
    println(if (fail == 0) "LIST CHECKS PASSED" else "$fail LIST CHECK(S) FAILED")
    if (fail > 0) System.exit(1)
}
