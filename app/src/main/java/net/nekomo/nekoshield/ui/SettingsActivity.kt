package net.nekomo.nekoshield.ui

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.nekomo.nekoshield.data.Prefs
import net.nekomo.nekoshield.data.QueryLog
import net.nekomo.nekoshield.data.Updater

class SettingsActivity : BaseActivity() {

    override fun build() {
        val p = Prefs.get(this)
        title("Settings")

        section("Startup")
        toggle("Start on boot", "Activate protection automatically after the device restarts",
            get = { p.startOnBoot }, set = { p.startOnBoot = it })
        row("Always-on VPN", "Ask Android to keep NekoShield running and block lookups when it stops") {
            startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
        }

        section("Privacy")
        toggle("Query log", "Keep a short in-memory history on this device (never uploaded)",
            get = { p.logQueries }, set = { p.logQueries = it })
        row("Reset statistics", "Clear the query and block counters") {
            AlertDialog.Builder(this)
                .setMessage("Reset all counters?")
                .setPositiveButton("Reset") { _, _ -> p.resetStats(); recreate() }
                .setNegativeButton("Cancel", null).show()
        }
        row("Clear query log now") { QueryLog.clear(); recreate() }

        section("Updates")
        toggle("Check on startup", "Look for a new release once a day when the app opens",
            get = { p.autoUpdate }, set = { p.autoUpdate = it })
        row("Check for updates", "Fetch the latest release from GitHub") { checkUpdate(manual = true) }

        section("About")
        val info = if (Build.VERSION.SDK_INT >= 33)
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        else @Suppress("DEPRECATION") packageManager.getPackageInfo(packageName, 0)
        val vc = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode
            else @Suppress("DEPRECATION") info.versionCode.toLong()
        row("Version", "${info.versionName} ($vc)") {}
        row("Privacy notes", "What NekoShield does and does not see") { privacyDialog() }
        row("Open-source licenses", "OkHttp · AndroidX · Kotlin") {}
    }

    private fun checkUpdate(manual: Boolean) {
        val ctx = this
        lifecycleScope.launch {
            val rel = withContext(Dispatchers.IO) { Updater.fetchUpdate() }
            Prefs.get(ctx).lastUpdateCheck = System.currentTimeMillis()
            if (rel == null) {
                if (manual) AlertDialog.Builder(ctx)
                    .setMessage("You're on the newest release.")
                    .setPositiveButton("OK", null).show()
            } else UpdateUi.offer(ctx, rel)
        }
    }

    private fun privacyDialog() {
        AlertDialog.Builder(this)
            .setTitle("Privacy")
            .setMessage("NekoShield answers DNS lookups on this device. " +
                "It does not collect analytics, does not run an account, and does not send your " +
                "queries anywhere except to the DNS provider you choose.\n\n" +
                "The log lives only in memory and clears when the app closes. " +
                "Blocklists download over HTTPS and stay on the device.")
            .setPositiveButton("OK", null)
            .show()
    }
}
