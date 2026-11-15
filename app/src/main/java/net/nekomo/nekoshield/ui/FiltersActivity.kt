package net.nekomo.nekoshield.ui

import android.app.AlertDialog
import android.content.Intent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.nekomo.nekoshield.data.Blocklists
import net.nekomo.nekoshield.data.Prefs
import net.nekomo.nekoshield.vpn.ShieldVpnService
import java.text.DateFormat
import java.util.Date

/** Filter control — categorized remote lists, custom lists, plus user rules. */
class FiltersActivity : BaseActivity() {

    private lateinit var p: Prefs

    override fun build() {
        live = this
        p = Prefs.get(this)
        title("Filters")
        subtitle("Enable the lists you want — they're downloaded to this device and " +
                 "updated on demand. Blocked domains never resolve. Your allow list " +
                 "always wins over every blocklist.")

        toggle("Enable remote blocklists", "Ads, trackers, and malware lists downloaded to this device",
            get = { p.blocklistsEnabled },
            set = { v -> p.blocklistsEnabled = v; restartIfRunning() })

        action("Update all lists") { updateAll() }

        for (cat in Blocklists.CATEGORIES) {
            val lists = Blocklists.CATALOG.filter { it.category == cat }
            if (lists.isEmpty()) continue
            section("$cat · ${lists.count { p.enabledLists.contains(it.id) }}/${lists.size} on")
            for (list in lists) listRow(list, removable = false)
        }

        // Fetch enabled lists that have never been downloaded (e.g. defaults on a fresh install).
        // One attempt per process — a failed fetch must not loop through recreate().
        for (list in Blocklists.CATALOG)
            if (p.enabledLists.contains(list.id) && Blocklists.sizeOnDisk(this, list.id) == 0
                && autoFetch.add(list.id))
                download(list)

        section("Custom filters")
        val customs = customRemotes()
        if (customs.isEmpty())
            note("No custom lists yet. Add any public hosts/domain list URL.")
        for (list in customs) listRow(list, removable = true)
        action("Add custom filter list") { addCustom() }

        note("Lists download over HTTPS straight to this device. Nothing else is fetched or sent.")

        section("Your rules")
        row("Blocked domains", "${p.userBlock.size} custom block rules") { editList(block = true) }
        row("Allowed domains", "${p.userAllow.size} always-allow rules") { editList(block = false) }
        note("Tip: add a parent domain (ads.example.com) and every subdomain is covered.")
    }

    private fun customRemotes(): List<Blocklists.Remote> =
        p.customLists.mapNotNull { Blocklists.customRemote(it) }

    private fun listRow(list: Blocklists.Remote, removable: Boolean) {
        val enabled = p.enabledLists.contains(list.id)
        val n = Blocklists.sizeOnDisk(this, list.id)
        val stamp = Blocklists.lastUpdate(this, list.id)
        val sub = buildString {
            append(list.desc)
            if (n > 0) {
                append(" · $n domains")
                append(" · updated ${DateFormat.getDateInstance().format(Date(stamp))}")
            } else if (enabled && list.id in fetching) append(" · downloading…")
            else if (enabled) append(" · not downloaded — use Update all lists")
        }
        row(list.name, sub, if (enabled) "ON" else "OFF") {
            val ids = p.enabledLists.toMutableSet()
            if (enabled) ids.remove(list.id) else {
                ids.add(list.id)
                if (n == 0) download(list)
            }
            p.enabledLists = ids
            restartIfRunning()
            recreate()
        }.setOnLongClickListener {
            if (removable) confirmRemove(list)
            true
        }
    }

    private fun confirmRemove(list: Blocklists.Remote) {
        openDialog = AlertDialog.Builder(this)
            .setTitle("Remove ${list.name}?")
            .setMessage("The downloaded list will be deleted from this device.")
            .setPositiveButton("Remove") { _, _ ->
                p.customLists = p.customLists.filterNot {
                    Blocklists.customRemote(it)?.id == list.id
                }.toSet()
                p.enabledLists = p.enabledLists - list.id
                Blocklists.delete(this, list.id)
                restartIfRunning()
                recreate()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addCustom() {
        val ctx = this
        val nameIn = EditText(ctx).apply { hint = "List name" }
        val urlIn = EditText(ctx).apply {
            hint = "https://example.com/hosts.txt"
            inputType = EditorInfo.TYPE_TEXT_VARIATION_URI
        }
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 20, 60, 0)
            addView(nameIn); addView(urlIn)
        }
        openDialog = AlertDialog.Builder(ctx)
            .setTitle("Add custom filter list")
            .setView(box)
            .setPositiveButton("Add & enable") { _, _ ->
                val name = nameIn.text.toString().trim()
                val url = urlIn.text.toString().trim()
                if (name.isEmpty() || !url.startsWith("https://") && !url.startsWith("http://")) {
                    Toast.makeText(ctx, "Need a name and an http(s) URL", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val entry = "$name|$url"
                val remote = Blocklists.customRemote(entry) ?: return@setPositiveButton
                p.customLists = p.customLists + entry
                p.enabledLists = p.enabledLists + remote.id
                download(remote)
                restartIfRunning()
                recreate()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Recreate wipes open dialogs + scroll position — skip while one is up. */
    private var openDialog: AlertDialog? = null

    // Fetches run detached from the activity lifecycle: a recreate must not
    // cancel in-flight downloads (it would orphan the remaining "update all"
    // items and leave rows stuck on "downloading…").
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun refreshLive() {
        val a = live ?: return
        a.runOnUiThread {
            if (!a.isFinishing && !a.isDestroyed && a.openDialog?.isShowing != true) a.recreate()
        }
    }

    override fun onDestroy() {
        if (live == this) live = null
        super.onDestroy()
    }

    private fun download(list: Blocklists.Remote) {
        fetching.add(list.id)
        val ctx = applicationContext
        ioScope.launch {
            val ok = try { Blocklists.update(ctx, list); true } catch (_: Exception) { false }
            fetching.remove(list.id)
            live?.runOnUiThread {
                if (!ok) Toast.makeText(ctx, "Couldn't fetch ${list.name} — will retry on update", Toast.LENGTH_SHORT).show()
            }
            refreshLive()
        }
    }

    private fun updateAll() {
        val ctx = applicationContext
        val enabled = Blocklists.allLists(p).filter { p.enabledLists.contains(it.id) }
        enabled.forEach { fetching.add(it.id) }
        ioScope.launch {
            for (list in enabled) {
                try { Blocklists.update(ctx, list) } catch (_: Exception) {}
                fetching.remove(list.id)
            }
            restartIfRunning()
            refreshLive()
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
        openDialog = AlertDialog.Builder(ctx)
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

    companion object {
        /** List ids already auto-fetched this process, to avoid fetch→recreate loops. */
        private val autoFetch = mutableSetOf<String>()
        /** List ids with a fetch currently in flight. */
        private val fetching = mutableSetOf<String>()
        /** The live activity instance, if any — detached fetches refresh through it. */
        @Volatile private var live: FiltersActivity? = null
    }
}
