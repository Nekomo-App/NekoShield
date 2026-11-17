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

    data class Remote(val id: String, val name: String, val url: String,
                      val desc: String, val category: String = CAT_ADS)

    const val CAT_ADS = "Ad blocking"
    const val CAT_PRIVACY = "Tracking protection"
    const val CAT_SECURITY = "Security"
    const val CAT_ANNOY = "Annoyances"
    const val CAT_ADULT = "Adult & other"
    const val CAT_CUSTOM = "Custom filters"

    /** Display order for the categories above. */
    val CATEGORIES = listOf(CAT_ADS, CAT_PRIVACY, CAT_SECURITY, CAT_ANNOY, CAT_ADULT)

    private const val HAGEZI = "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock"

    val CATALOG = listOf(
        // — Ad blocking —
        Remote("steven-black", "StevenBlack Unified", "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
            "Ads, trackers, malware — the classic unified list", CAT_ADS),
        Remote("hagezi-light", "HaGeZi Multi Light", "$HAGEZI/light.txt",
            "Light ads + trackers, low false positives", CAT_ADS),
        Remote("hagezi-normal", "HaGeZi Multi Normal", "$HAGEZI/multi.txt",
            "Balanced ads, trackers, telemetry and scam", CAT_ADS),
        Remote("hagezi-pro", "HaGeZi Multi Pro", "$HAGEZI/pro.txt",
            "Aggressive blocking for advanced users", CAT_ADS),
        Remote("oisd-small", "OISD Small", "https://small.oisd.nl/",
            "Curated, designed to break as little as possible", CAT_ADS),
        Remote("oisd-big", "OISD Big", "https://big.oisd.nl/",
            "The full OISD set — broad coverage", CAT_ADS),
        Remote("adguard-filter", "AdGuard DNS Filter", "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt",
            "AdGuard's own ad/tracker filter (domains)", CAT_ADS),
        Remote("adaway", "AdAway Default", "https://adaway.org/hosts.txt",
            "The classic AdAway mobile ad list", CAT_ADS),
        Remote("pollock", "Dan Pollock's Hosts", "https://someonewhocares.org/hosts/zero/hosts",
            "Long-running community ad/malware list", CAT_ADS),
        Remote("1hosts-lite", "1Hosts Lite", "https://o0.pages.dev/Lite/hosts.txt",
            "Lean everyday ad/tracker blocking", CAT_ADS),

        // — Tracking protection —
        Remote("winspy", "WindowsSpyBlocker", "https://raw.githubusercontent.com/crazy-max/WindowsSpyBlocker/master/data/hosts/spy.txt",
            "Microsoft/Windows telemetry endpoints", CAT_PRIVACY),
        Remote("hagezi-tif-mini", "HaGeZi TIF Mini", "$HAGEZI/tif.mini.txt",
            "Compact threat + tracker intel feed", CAT_PRIVACY),

        // — Security —
        Remote("hagezi-tif", "HaGeZi Threat Intel", "$HAGEZI/tif.medium.txt",
            "Malware, phishing and botnet feed", CAT_SECURITY),
        Remote("phishing-army", "Phishing Army", "https://phishing.army/download/phishing_army_blocklist_extended.txt",
            "Crowd-sourced phishing domain feed", CAT_SECURITY),
        Remote("dsprout-malware", "Anti-Malware List", "https://raw.githubusercontent.com/DandelionSprout/adfilt/master/Dandelion%20Sprout's%20Anti-Malware%20List.txt",
            "Dandelion Sprout's malware + scam domains", CAT_SECURITY),
        Remote("hagezi-fake", "HaGeZi Fake Shops", "$HAGEZI/fake.txt",
            "Fraudulent shops and brand scams", CAT_SECURITY),
        Remote("amnesty-nso", "Amnesty NSO Indicators", "https://raw.githubusercontent.com/AmnestyTech/investigations/master/2021-07-18_nso/domains.txt",
            "Pegasus spyware indicators (Amnesty Tech)", CAT_SECURITY),

        // — Annoyances —
        Remote("hagezi-popupads", "HaGeZi Pop-Up Ads", "$HAGEZI/popupads.txt",
            "Pop-up, pop-under and intrusive ad domains", CAT_ANNOY),
        Remote("hagezi-native", "HaGeZi TikTok Tracker", "$HAGEZI/native.tiktok.txt",
            "TikTok tracking + telemetry domains", CAT_ANNOY),

        // — Adult & other —
        Remote("stevenblack-porn", "StevenBlack + Adult", "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn/hosts",
            "Unified list plus adult content domains", CAT_ADULT),
        Remote("oisd-nsfw", "OISD NSFW", "https://nsfw.oisd.nl/",
            "Adult content blocking set", CAT_ADULT),
        Remote("hagezi-gambling", "HaGeZi Gambling", "$HAGEZI/gambling.txt",
            "Betting and casino domains", CAT_ADULT),
    )

    /** Parses a stored "name|url" pref entry into a Remote (custom category). */
    fun customRemote(entry: String): Remote? {
        val i = entry.indexOf('|')
        if (i <= 0 || i >= entry.length - 1) return null
        val name = entry.substring(0, i)
        val url = entry.substring(i + 1)
        return Remote("custom-" + Integer.toHexString(entry.hashCode()), name, url,
            url.substringAfter("://").substringBefore('/'), CAT_CUSTOM)
    }

    /** Catalog lists plus the user's custom lists. */
    fun allLists(prefs: Prefs): List<Remote> =
        CATALOG + prefs.customLists.mapNotNull { customRemote(it) }

    fun delete(ctx: Context, id: String) { file(ctx, id).delete() }

    private fun dir(ctx: Context) = File(ctx.filesDir, "blocklists").apply { mkdirs() }
    private fun file(ctx: Context, id: String) = File(dir(ctx), "$id.txt")

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun update(ctx: Context, remote: Remote): Int = withContext(Dispatchers.IO) {
        val body = try {
            http.newCall(Request.Builder().url(remote.url).build()).execute().use {
                if (!it.isSuccessful) throw IllegalStateException("HTTP ${it.code}")
                it.body?.string() ?: ""
            }
        } catch (e: Exception) {
            android.util.Log.e("NekoShield", "list fetch failed ${remote.id}: $e")
            throw e
        }
        android.util.Log.i("NekoShield", "list fetched ${remote.id}")
        val domains = parseHosts(body)
        // Never clobber a good list with an empty/garbage fetch, and write
        // atomically — a mid-write kill must not leave a truncated file.
        if (domains.isEmpty()) throw IllegalStateException("parsed 0 domains")
        val target = file(ctx, remote.id)
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(domains.joinToString("\n"))
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
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
