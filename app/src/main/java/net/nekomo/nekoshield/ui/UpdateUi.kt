package net.nekomo.nekoshield.ui

import android.app.AlertDialog
import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.nekomo.nekoshield.data.Prefs
import net.nekomo.nekoshield.data.Updater

/** Shared "new release available" flow — offer, download, hand to the installer. */
object UpdateUi {

    fun offer(ctx: AppCompatActivity, rel: Updater.Release) {
        val notes = rel.notes.lines().take(8).joinToString("\n").trim()
        AlertDialog.Builder(ctx)
            .setTitle("Update available — ${rel.tag}")
            .setMessage(notes.ifEmpty { rel.title })
            .setPositiveButton("Download & install") { _, _ -> downloadAndInstall(ctx, rel) }
            .setNegativeButton("Later", null)
            .show()
    }

    fun downloadAndInstall(ctx: AppCompatActivity, rel: Updater.Release) {
        if (!Updater.canInstall(ctx)) {
            AlertDialog.Builder(ctx)
                .setTitle("Allow app installs")
                .setMessage("Android needs permission to install updates from NekoShield.")
                .setPositiveButton("Open settings") { _, _ ->
                    ctx.startActivity(Updater.installPermissionIntent(ctx))
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        val toast = Toast.makeText(ctx, "Downloading ${rel.tag}…", Toast.LENGTH_SHORT)
        toast.show()
        ctx.lifecycleScope.launch {
            var last = -1
            val apk = withContext(Dispatchers.IO) {
                Updater.download(ctx, rel.apkUrl) { pct ->
                    if (pct / 10 != last / 10) { last = pct }
                }
            }
            if (apk == null) {
                Toast.makeText(ctx, "Download failed — try again later", Toast.LENGTH_LONG).show()
            } else {
                runCatching { Updater.install(ctx, apk) }
                    .onFailure {
                        Toast.makeText(ctx, "Couldn't open the installer", Toast.LENGTH_LONG).show()
                    }
            }
        }
    }

    /** Once-a-day silent check — only prompts when a newer release exists. */
    fun autoCheck(ctx: AppCompatActivity) {
        val p = Prefs.get(ctx)
        if (!p.autoUpdate) return
        if (System.currentTimeMillis() - p.lastUpdateCheck < 24L * 3600 * 1000) return
        ctx.lifecycleScope.launch {
            var ok = false
            val rel = try {
                withContext(Dispatchers.IO) { Updater.fetchUpdate() }.also { ok = true }
            } catch (_: Exception) { null }
            if (ok) p.lastUpdateCheck = System.currentTimeMillis() // don't burn the daily slot on a network failure
            if (rel != null) offer(ctx, rel)
        }
    }
}
