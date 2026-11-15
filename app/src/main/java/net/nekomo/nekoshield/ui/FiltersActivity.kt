package net.nekomo.nekoshield.ui

import android.app.AlertDialog
import android.content.Intent
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import net.nekomo.nekoshield.data.Blocklists
import net.nekomo.nekoshield.data.Prefs
import net.nekomo.nekoshield.vpn.ShieldVpnService
import java.text.DateFormat
import java.util.Date

/** Filter control — remote blocklists plus the user's own allow/block lists. */
class FiltersActivity : BaseActivity() {

    private lateinit var p: Prefs

    override fun build() {
        p = Prefs.get(this)
        title("Filters")
        subtitle("Blocked domains never resolve — apps just see 'no such host'. " +
                 "Your allow list always wins over every blocklist.")

        toggle("Enable remote blocklists", "Ads, trackers, and malware lists downloaded to this device",
            get = { p.blocklistsEnabled },
            set = { v -> p.blocklistsEnabled = v; restartIfRunning() })

        section("Remote lists")
        for (list in Blocklists.CATALOG) {
            val enabled = p.enabledLists.contains(list.id)
            val n = Blocklists.sizeOnDisk(this, list.id)
            val stamp = Blocklists.lastUpdate(this, list.id)
            val sub = buildString {
                append(list.desc)
                if (n > 0) {
                    append(" · $n domains")
                    append(" · updated ${DateFormat.getDateInstance().format(Date(stamp))}")
                }
            }
            row(list.name, sub, if (enabled) "ON" else "OFF") {
                val ids = p.enabledLists.toMutableSet()
                if (enabled) ids.remove(list.id) else {
                    ids.add(list.id)
                    download(list)  // fetch in the background if never downloaded
                }
                p.enabledLists = ids
                restartIfRunning()
                recreate()
            }
        }

        action("Update lists now") { updateAll() }
        note("Lists download over HTTPS straight to this device. Nothing else is fetched or sent.")

        section("Your rules")
        row("Blocked domains", "${p.userBlock.size} custom block rules") { editList(block = true) }
        row("Allowed domains", "${p.userAllow.size} always-allow rules") { editList(block = false) }
        note("Tip: add a parent domain (ads.example.com) and every subdomain is covered.")
    }

    private fun download(list: Blocklists.Remote) {
        lifecycleScope.launch {
            try {
                Blocklists.update(this@FiltersActivity, list)
            } catch (_: Exception) {
                Toast.makeText(this@FiltersActivity, "Couldn't fetch ${list.name} — will retry on update", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateAll() {
        val ctx = this
        val enabled = Blocklists.CATALOG.filter { p.enabledLists.contains(it.id) }
        lifecycleScope.launch {
            for (list in enabled) {
                try { Blocklists.update(ctx, list) } catch (_: Exception) {}
            }
            restartIfRunning()
            recreate()
        }
    }

    private fun editList(block: Boolean) {
        val ctx = this
        val current = if (block) p.userBlock else p.userAllow
        val input = EditText(ctx).apply {
            hint = "domain.com — one per line"
            setText(current.sorted().joinToString("\n"))
            minLines = 8
            gravity = android.view.Gravity.TOP
        }
        AlertDialog.Builder(ctx)
            .setTitle(if (block) "Blocked domains" else "Allowed domains")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val set = input.text.toString()
                    .lines()
                    .map { it.trim().lowercase().trimEnd('.').replace(Regex("\\s+"), "") }
                    .filter { it.isNotEmpty() }
                    .toSet()
                if (block) p.userBlock = set else p.userAllow = set
                restartIfRunning()
                recreate()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun restartIfRunning() {
        if (ShieldVpnService.running)
            startService(Intent(this, ShieldVpnService::class.java).setAction(ShieldVpnService.ACTION_START))
    }
}
