package net.nekomo.nekoshield

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import net.nekomo.nekoshield.data.Prefs
import net.nekomo.nekoshield.vpn.ShieldVpnService

/** Restarts the VPN after reboot when the user asked for always-on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!Prefs.get(ctx).startOnBoot || !Prefs.get(ctx).enabled) return
        // Boot-time FGS starts are restricted on Android 12+; if the system
        // refuses, the user just opens the app once or enables always-on VPN.
        runCatching {
            ContextCompat.startForegroundService(ctx,
                Intent(ctx, ShieldVpnService::class.java).setAction(ShieldVpnService.ACTION_START))
        }
    }
}
