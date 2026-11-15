package net.nekomo.nekoshield.ui

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.nekomo.nekoshield.data.Prefs
import net.nekomo.nekoshield.data.Providers
import net.nekomo.nekoshield.dns.DnsMessage
import net.nekomo.nekoshield.dns.DnsResolver
import net.nekomo.nekoshield.vpn.ShieldVpnService
import java.net.Inet4Address

/** Diagnostics - resolver health, leak check, tunnel state, raw answers. */
class ToolsActivity : BaseActivity() {

    override fun build() {
        title("Diagnostics")
        subtitle("Test the resolver path end-to-end and inspect what the network sees.")

        section("Resolver test")
        row("Test lookup", "Send example.com through the configured upstream") { testLookup() }
        row("Latency check", "Time a lookup to your provider") { latencyCheck() }
        row("DNS leak check", "Show which servers your system would use without NekoShield") { leakCheck() }
        row("Tunnel state", "Inspect the live VPN interface") { tunnelState() }

        section("Raw output")
        row("Fetch raw answer", "Show the wire response for a domain you pick") { rawQuery() }
    }

    private fun currentUpstream(): DnsResolver.Upstream {
        val p = Prefs.get(this)
        if (p.useCustom) Providers.parseCustom(p.customUpstream)?.let { return it }
        return Providers.byId(p.providerId)?.upstream ?: Providers.PRESETS[1].upstream
    }

    private fun testLookup() {
        val ctx = this
        val up = currentUpstream()
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val q = DnsMessage.buildQuery("example.com")
                val r = DnsResolver.resolve(null, up, q, q.size)
                r?.let { DnsMessage.firstAddress(it) ?: "(no A record)" }
            }
            AlertDialog.Builder(ctx)
                .setTitle("Resolver test")
                .setMessage(result?.let { "example.com -> $it\n\nUpstream: ${up.label.ifEmpty { up.host }} (${up.proto})" }
                    ?: "Query failed - the upstream did not answer.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun latencyCheck() {
        val ctx = this
        val up = currentUpstream()
        lifecycleScope.launch {
            val ms = withContext(Dispatchers.IO) {
                val t = System.nanoTime()
                val q = DnsMessage.buildQuery("one.one.one.one")
                val ok = DnsResolver.resolve(null, up, q, q.size) != null
                if (ok) (System.nanoTime() - t) / 1_000_000 else -1L
            }
            AlertDialog.Builder(ctx)
                .setTitle("Latency")
                .setMessage(if (ms >= 0) "Round trip: ${ms} ms to ${up.label.ifEmpty { up.host }}" else "Upstream unreachable.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun leakCheck() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val sb = StringBuilder()
        for (net in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(net) ?: continue
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
            val lp: LinkProperties = cm.getLinkProperties(net) ?: continue
            val dns = lp.dnsServers.filterIsInstance<Inet4Address>().map { it.hostAddress }
            if (dns.isNotEmpty()) sb.append("Network ${lp.interfaceName ?: "?"}: ${dns.joinToString(", ")}\n")
        }
        if (sb.isEmpty()) sb.append("No DNS servers exposed - the VPN tunnel is handling lookups.")
        AlertDialog.Builder(this).setTitle("Leak check").setMessage(sb.toString().trim())
            .setPositiveButton("OK", null).show()
    }

    private fun tunnelState() {
        val sb = StringBuilder()
        sb.append("Running: ${ShieldVpnService.running}\n")
        sb.append("Upstream: ${ShieldVpnService.upstreamLabel}\n")
        sb.append("Queries: ${Prefs.get(this).queryCount}  Blocked: ${Prefs.get(this).blockedCount}\n")
        AlertDialog.Builder(this).setTitle("Tunnel state").setMessage(sb.toString())
            .setPositiveButton("OK", null).show()
    }

    private fun rawQuery() {
        val ctx = this
        val input = android.widget.EditText(ctx).apply { hint = "domain.com"; setText("cloudflare.com") }
        AlertDialog.Builder(ctx)
            .setTitle("Raw DNS answer")
            .setView(input)
            .setPositiveButton("Fetch") { _, _ ->
                val domain = input.text.toString().trim().ifEmpty { "example.com" }
                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        val q = DnsMessage.buildQuery(domain)
                        DnsResolver.resolve(null, currentUpstream(), q, q.size)?.let {
                            DnsMessage.describe(it)
                        }
                    }
                    AlertDialog.Builder(ctx)
                        .setTitle(domain)
                        .setMessage(result ?: "No answer.")
                        .setPositiveButton("Copy") { _, _ ->
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("dns", result ?: ""))
                            Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("Close", null)
                        .show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
