package net.nekomo.nekoshield.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Remote blocklist catalog + on-disk domain sets. Hosts-format lines
 * ("0.0.0.0 domain", "127.0.0.1 domain") and bare domains are both accepted.
 */
object Blocklists {

    data class Remote(val id: String, val name: String, val url: String, val desc: String)

    val CATALOG = listOf(
        Remote("steven-black", "StevenBlack Unified", "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
            "Ads, trackers, malware — the classic unified list"),
        Remote("hagezi-light", "HaGeZi Light", "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/hosts/light.txt",
            "Light ads + trackers, low false positives"),
        Remote("oisd-small", "OISD Small", "https://small.oisd.nl/",
            "Curated, designed to break as little as possible"),
        Remote("adguard-filter", "AdGuard DNS Filter", "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt",
            "AdGuard's own ad/tracker filter (domains)"),
        Remote("mvt", "MVT Indicators", "https://raw.githubusercontent.com/mvt-project/mvt-indicators/main/domains.txt",
            "Amnesty MVT spyware indicators"),
    )

    private fun dir(ctx: Context) = File(ctx.filesDir, "blocklists").apply { mkdirs() }
    private fun file(ctx: Context, id: String) = File(dir(ctx), "$id.txt")

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun update(ctx: Context, remote: Remote): Int = withContext(Dispatchers.IO) {
        val body = http.newCall(Request.Builder().url(remote.url).build()).execute().use {
            if (!it.isSuccessful) throw IllegalStateException("HTTP ${it.code}")
            it.body?.string() ?: ""
        }
        val domains = parseHosts(body)
        file(ctx, remote.id).writeText(domains.joinToString("\n"))
        domains.size
    }

    /** Parses hosts/plain-list formats into a normalized domain set. */
    fun parseHosts(text: String): Set<String> {
        val out = HashSet<String>(4096)
        for (line in text.lineSequence()) {
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("#") || t.startsWith("!")) continue
            val host = when {
                t.startsWith("0.0.0.0") || t.startsWith("127.0.0.1") -> t.split(Regex("\\s+")).getOrNull(1)
                t.startsWith("||") -> t.removePrefix("||").substringBefore("^") // adblock syntax
                else -> t.substringBefore(' ').substringBefore('#')
            }?.lowercase()?.trimEnd('.') ?: continue
            if (host.isEmpty() || host == "localhost" || host == "localhost.localdomain" ||
                host == "broadcasthost" || host == "local" || host.contains(':')) continue
            if (host.all { it.isLetterOrDigit() || it == '.' || it == '-' } && host.contains('.')) out += host
        }
        return out
    }

    fun load(ctx: Context, ids: Set<String>): Set<String> {
        val out = HashSet<String>()
        for (id in ids) {
            val f = file(ctx, id)
            if (f.exists()) f.readLines().forEach { l -> val d = l.trim(); if (d.isNotEmpty()) out += d }
        }
        return out
    }

    fun sizeOnDisk(ctx: Context, id: String): Int =
        file(ctx, id).takeIf { it.exists() }?.readLines()?.size ?: 0

    fun lastUpdate(ctx: Context, id: String): Long =
        file(ctx, id).takeIf { it.exists() }?.lastModified() ?: 0
}
