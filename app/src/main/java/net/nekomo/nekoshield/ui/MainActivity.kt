package net.nekomo.nekoshield.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.VpnService
import android.os.Build
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import net.nekomo.nekoshield.R
import net.nekomo.nekoshield.data.Prefs
import net.nekomo.nekoshield.vpn.ShieldVpnService

class MainActivity : BaseActivity() {

    private lateinit var statusDot: TextView
    private lateinit var activateBtn: TextView
    private lateinit var statusText: TextView
    private lateinit var statusSub: TextView
    private lateinit var queriesTv: TextView
    private lateinit var blockedTv: TextView

    private val vpnPerm = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { res ->
        android.util.Log.i("NekoShield", "vpn consent result=${res.resultCode}")
        if (res.resultCode == Activity.RESULT_OK) startVpn()
    }

    private val notifPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()) { }

    private val stateRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        ContextCompat.registerReceiver(this, stateRx,
            IntentFilter(ShieldVpnService.ACTION_STATE),
            ContextCompat.RECEIVER_NOT_EXPORTED)
        refreshStatus()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onPause() {
        super.onPause()
        runCatching { unregisterReceiver(stateRx) }
    }

    override fun build() {
        title("NekoShield")
        subtitle("Private DNS and filtering — your queries, your rules.")

        // Status hero
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = GradientDrawable().apply {
                setColor(getColor(R.color.card)); cornerRadius = dp(16).toFloat()
            }
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        statusDot = TextView(this).apply { text = "●"; textSize = 18f }
        statusText = TextView(this).apply {
            textSize = 18f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
            setPadding(dp(10), 0, 0, 0)
        }
        top.addView(statusDot); top.addView(statusText)
        statusSub = TextView(this).apply {
            setTextColor(getColor(R.color.muted)); textSize = 12f
            setPadding(0, dp(4), 0, 0)
        }
        hero.addView(top); hero.addView(statusSub)
        body.addView(hero)

        // Stats
        val stats = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, dp(4))
        }
        queriesTv = statCard(stats, "Queries")
        blockedTv = statCard(stats, "Blocked")
        body.addView(stats)

        activateBtn = action("Activate protection") { toggleVpn() }
        note("Android allows one VPN at a time — turning this on asks other VPN apps to stop. " +
             "Only DNS lookups pass through NekoShield; the rest of your traffic never touches it.")

        section("Configure")
        row("DNS provider", "Choose who resolves your lookups — or run system DNS", tag = "") {
            startActivity(Intent(this, ProvidersActivity::class.java))
        }
        row("Filters & blocklists", "Ads, trackers, and your own allow/block lists") {
            startActivity(Intent(this, FiltersActivity::class.java))
        }
        row("Apps", "Pick apps that bypass the shield") {
            startActivity(Intent(this, AppsActivity::class.java))
        }
        row("Query log", "Recent lookups answered on this device — stored only here") {
            startActivity(Intent(this, LogActivity::class.java))
        }
        row("Diagnostics", "Test the resolver, check for leaks, inspect the tunnel") {
            startActivity(Intent(this, ToolsActivity::class.java))
        }
        row("Settings", "Startup, logging, and about") {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun statCard(parent: LinearLayout, label: String): TextView {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                setColor(getColor(R.color.card)); cornerRadius = dp(14).toFloat()
            }
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.setMargins(0, 0, dp(8), 0)
            layoutParams = lp
        }
        val v = TextView(this).apply { text = "0"; textSize = 22f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE) }
        val l = TextView(this).apply { text = label; textSize = 12f; setTextColor(getColor(R.color.muted)) }
        card.addView(v); card.addView(l)
        parent.addView(card)
        return v
    }

    private fun toggleVpn() {
        if (ShieldVpnService.running) {
            startService(Intent(this, ShieldVpnService::class.java).setAction(ShieldVpnService.ACTION_STOP))
        } else {
            val prep = VpnService.prepare(this)
            android.util.Log.i("NekoShield", "prepare -> ${if (prep == null) "null (consented)" else "dialog"}")
            if (prep != null) vpnPerm.launch(prep) else startVpn()
        }
    }

    private fun startVpn() {
        Prefs.get(this).enabled = true
        ContextCompat.startForegroundService(this,
            Intent(this, ShieldVpnService::class.java).setAction(ShieldVpnService.ACTION_START))
    }

    private fun refreshStatus() {
        val p = Prefs.get(this)
        if (ShieldVpnService.running) {
            statusDot.setTextColor(getColor(R.color.good))
            statusText.text = "Protection active"
            statusSub.text = "Upstream: ${ShieldVpnService.upstreamLabel.ifEmpty { "system" }} · DNS-only tunnel"
            activateBtn.text = "Stop protection"
            (activateBtn.background as? android.graphics.drawable.GradientDrawable)
                ?.setColor(getColor(R.color.bad))
        } else {
            statusDot.setTextColor(getColor(R.color.bad))
            statusText.text = "Protection off"
            statusSub.text = "Your DNS is unfiltered"
            activateBtn.text = "Activate protection"
            (activateBtn.background as? android.graphics.drawable.GradientDrawable)
                ?.setColor(getColor(R.color.accent))
        }
        queriesTv.text = p.queryCount.toString()
        blockedTv.text = p.blockedCount.toString()
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= 33) {
            // nothing extra — receiver unregistered in onPause
        }
        super.onDestroy()
    }
}
