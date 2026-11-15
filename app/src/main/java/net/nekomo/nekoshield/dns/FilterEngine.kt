package net.nekomo.nekoshield.dns

/**
 * Domain filtering with suffix matching — a blocked entry matches itself and
 * every subdomain (block "ads.com" → "x.ads.com" too). Allowlist wins over
 * every list so users can always un-break a site.
 */
class FilterEngine(
    private val blocked: Set<String> = emptySet(),
    private val allowed: Set<String> = emptySet(),
) {
    fun isBlocked(domain: String): Boolean {
        val d = domain.lowercase().trimEnd('.')
        if (d.isEmpty()) return false
        if (matches(allowed, d)) return false
        return matches(blocked, d)
    }

    private fun matches(set: Set<String>, domain: String): Boolean {
        var cur = domain
        while (true) {
            if (set.contains(cur)) return true
            val dot = cur.indexOf('.')
            if (dot < 0) return false
            cur = cur.substring(dot + 1)
        }
    }
}
