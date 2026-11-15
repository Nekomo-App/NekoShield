package net.nekomo.nekoshield.data

import net.nekomo.nekoshield.dns.DnsResolver

/** Built-in DNS provider presets + custom profile serialization. */
object Providers {

    data class Preset(val id: String, val name: String, val desc: String, val upstream: DnsResolver.Upstream)

    val PRESETS = listOf(
        Preset("auto", "System default", "Use the network's own DNS — filtering still applies",
            DnsResolver.Upstream(DnsResolver.Proto.UDP, "", 53, label = "System")),
        Preset("cloudflare", "Cloudflare", "1.1.1.1 — fast, unfiltered",
            DnsResolver.Upstream(DnsResolver.Proto.DOT, "1.1.1.1", 853, dohHost = "cloudflare-dns.com", label = "Cloudflare")),
        Preset("cloudflare-fam", "Cloudflare Family", "Blocks malware + adult content",
            DnsResolver.Upstream(DnsResolver.Proto.DOT, "1.1.1.3", 853, dohHost = "family.cloudflare-dns.com", label = "CF Family")),
        Preset("google", "Google", "8.8.8.8 — reliable, unfiltered",
            DnsResolver.Upstream(DnsResolver.Proto.DOT, "8.8.8.8", 853, dohHost = "dns.google", label = "Google")),
        Preset("quad9", "Quad9", "9.9.9.9 — blocks known malware",
            DnsResolver.Upstream(DnsResolver.Proto.DOT, "9.9.9.9", 853, dohHost = "dns.quad9.net", label = "Quad9")),
        Preset("adguard", "AdGuard DNS", "Blocks ads + trackers",
            DnsResolver.Upstream(DnsResolver.Proto.DOT, "94.140.14.14", 853, dohHost = "dns.adguard-dns.com", label = "AdGuard")),
        Preset("adguard-fam", "AdGuard Family", "Ads, trackers + adult content",
            DnsResolver.Upstream(DnsResolver.Proto.DOT, "94.140.14.15", 853, dohHost = "family.adguard-dns.com", label = "AG Family")),
        Preset("mullvad", "Mullvad", "194.242.2.2 — no logging",
            DnsResolver.Upstream(DnsResolver.Proto.DOT, "194.242.2.2", 853, dohHost = "dns.mullvad.net", label = "Mullvad")),
        Preset("cloudflare-doh", "Cloudflare (DoH)", "HTTPS endpoint — survives port-853 blocks",
            DnsResolver.Upstream(DnsResolver.Proto.DOH, "https://cloudflare-dns.com/dns-query", 443, label = "CF DoH")),
        Preset("google-doh", "Google (DoH)", "HTTPS endpoint",
            DnsResolver.Upstream(DnsResolver.Proto.DOH, "https://dns.google/dns-query", 443, label = "Google DoH")),
        Preset("adguard-doh", "AdGuard (DoH)", "HTTPS endpoint with ad blocking",
            DnsResolver.Upstream(DnsResolver.Proto.DOH, "https://dns.adguard-dns.com/dns-query", 443, label = "AG DoH")),
    )

    fun byId(id: String): Preset? = PRESETS.find { it.id == id }

    /** Custom profile: "proto|host|port|name". */
    fun parseCustom(line: String): DnsResolver.Upstream? {
        val p = line.split("|")
        if (p.size < 4) return null
        val proto = when (p[0]) { "udp" -> DnsResolver.Proto.UDP; "dot" -> DnsResolver.Proto.DOT; "doh" -> DnsResolver.Proto.DOH; else -> return null }
        return DnsResolver.Upstream(proto, p[1], p[2].toIntOrNull() ?: 53, dohHost = p[1], label = p[3])
    }

    fun serializeCustom(u: DnsResolver.Upstream): String {
        val p = when (u.proto) { DnsResolver.Proto.UDP -> "udp"; DnsResolver.Proto.DOT -> "dot"; DnsResolver.Proto.DOH -> "doh" }
        return "$p|${u.host}|${u.port}|${u.label}"
    }
}
