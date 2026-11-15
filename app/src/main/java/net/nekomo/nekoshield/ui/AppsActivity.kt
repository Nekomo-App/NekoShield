package net.nekomo.nekoshield.ui

import android.content.Intent
import android.os.Build
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import net.nekomo.nekoshield.data.Prefs
import net.nekomo.nekoshield.vpn.ShieldVpnService
import net.nekomo.nekoshield.R

/** Per-app control — checked apps keep their normal DNS path (bypass the shield). */
class AppsActivity : BaseActivity() {

    override fun build() {
        val p = Prefs.get(this)
        title("Apps")
        subtitle("Mark apps to bypass NekoShield — their DNS resolves normally, outside the tunnel. " +
                 "Excluded apps can't use the custom resolver or filtering for their lookups.")
        note("Android routes everything else outside the VPN anyway; this only affects DNS lookups.")

        section("Installed apps")
        val pm = packageManager
        val apps = if (Build.VERSION.SDK_INT >= 33)
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        else @Suppress("DEPRECATION") pm.getInstalledApplications(0)
            .filter { it.packageName != packageName }
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null } // launchable = real apps
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }

        val excluded = p.excludedApps.toMutableSet()
        for (app in apps) {
            val label = pm.getApplicationLabel(app).toString()
            val isEx = excluded.contains(app.packageName)
            val r = row(label, app.packageName, if (isEx) "BYPASS" else "") {
                if (excluded.contains(app.packageName)) excluded.remove(app.packageName)
                else excluded.add(app.packageName)
                p.excludedApps = excluded
                recreate()
            }
            // Icon chip on the left
            val icon = ImageView(this).apply {
                runCatching { setImageDrawable(pm.getApplicationIcon(app)) }
                layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply { setMargins(0, 0, dp(12), 0) }
            }
            r.addView(icon, 0)
        }
        if (apps.isEmpty()) note("No launchable apps found.")
        note("${excluded.size} app(s) bypass the shield. Restart protection to apply.")
        action("Apply & restart protection") { restartIfRunning() }
    }

    private fun restartIfRunning() {
        if (ShieldVpnService.running)
            startService(Intent(this, ShieldVpnService::class.java).setAction(ShieldVpnService.ACTION_START))
    }
}
