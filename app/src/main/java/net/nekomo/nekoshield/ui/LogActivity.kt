package net.nekomo.nekoshield.ui

import android.app.AlertDialog
import net.nekomo.nekoshield.R
import net.nekomo.nekoshield.data.Prefs
import net.nekomo.nekoshield.data.QueryLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Recent DNS decisions - kept only in memory on this device, never uploaded. */
class LogActivity : BaseActivity() {

    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    override fun build() {
        title("Query log")
        subtitle("The last ${QueryLog.MAX} lookups answered while protection was on. " +
                 "Cleared when the app closes - nothing is stored or sent anywhere.")

        toggle("Record queries", "Keep an in-memory log on this device",
            get = { Prefs.get(this).logQueries },
            set = { v -> Prefs.get(this).logQueries = v; if (!v) QueryLog.clear() })

        val entries = QueryLog.list()
        section("${entries.size} recent")
        if (entries.isEmpty()) {
            note("Nothing yet - activate protection and browse around.")
        }
        for (e in entries.take(200)) {
            row(e.domain, "${fmt.format(Date(e.at))} · ${e.resolver}",
                if (e.blocked) "BLOCKED" else "OK") {}
        }
        if (entries.isNotEmpty()) {
            action("Clear log", danger = true) {
                AlertDialog.Builder(this)
                    .setMessage("Clear the in-memory query log?")
                    .setPositiveButton("Clear") { _, _ -> QueryLog.clear(); recreate() }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }
}
