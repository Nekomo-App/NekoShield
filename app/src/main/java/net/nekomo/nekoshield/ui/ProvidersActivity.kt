package net.nekomo.nekoshield.ui

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import net.nekomo.nekoshield.data.Prefs
import net.nekomo.nekoshield.data.Providers
import net.nekomo.nekoshield.vpn.ShieldVpnService

/** Pick the upstream resolver — preset or fully custom (UDP / DoT / DoH). */
class ProvidersActivity : BaseActivity() {

    override fun build() {
        val p = Prefs.get(this)
        title("DNS provider")
        subtitle("This is who answers your lookups. Encrypted providers (DoT/DoH) hide queries " +
                 "from your network but can still see them themselves — pick who you trust.")

        if (p.useCustom) {
            row("Custom server", p.customUpstream, "ACTIVE") {}
        }

        for (preset in Providers.PRESETS) {
            val active = !p.useCustom && p.providerId == preset.id
            val proto = when (preset.upstream.proto) {
                net.nekomo.nekoshield.dns.DnsResolver.Proto.UDP -> "UDP"
                net.nekomo.nekoshield.dns.DnsResolver.Proto.DOT -> "DoT"
                net.nekomo.nekoshield.dns.DnsResolver.Proto.DOH -> "DoH"
            }
            row(preset.name, "${preset.desc} · $proto", if (active) "ACTIVE" else proto) {
                p.providerId = preset.id
                p.useCustom = false
                restartIfRunning()
                recreate()
            }
        }

        section("Custom server")
        row("Add custom DNS", "Plain UDP, DNS-over-TLS, or DNS-over-HTTPS endpoint") { customDialog() }
        if (p.useCustom) {
            row("Remove custom", p.customUpstream, "REMOVE") {
                p.useCustom = false
                restartIfRunning()
                recreate()
            }
        }
    }

    private fun customDialog() {
        val ctx = this
        val host = EditText(ctx).apply { hint = "Host, IP, or https:// URL" }
        val port = EditText(ctx).apply { hint = "Port (53 / 853 / 443)"; setText("53") }
        val name = EditText(ctx).apply { hint = "Label (e.g. My Pi-hole)" }
        val protos = arrayOf("Plain UDP", "DNS-over-TLS", "DNS-over-HTTPS")
        var proto = 0
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
            addView(name); addView(host); addView(port)
        }
        AlertDialog.Builder(ctx)
            .setTitle("Custom DNS server")
            .setSingleChoiceItems(protos, 0) { _, w -> proto = w }
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val p = Prefs.get(ctx)
                val protoStr = when (proto) { 0 -> "udp"; 1 -> "dot"; else -> "doh" }
                val line = "$protoStr|${host.text}|${port.text}|${name.text.ifEmpty { "Custom" }}"
                if (Providers.parseCustom(line) != null) {
                    p.customUpstream = line
                    p.useCustom = true
                    restartIfRunning()
                    recreate()
                } else Toast.makeText(ctx, "Check the host and port", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun restartIfRunning() {
        if (ShieldVpnService.running)
            startService(Intent(this, ShieldVpnService::class.java).setAction(ShieldVpnService.ACTION_START))
    }
}
