package net.nekomo.nekoshield.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import net.nekomo.nekoshield.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.util.concurrent.TimeUnit

/** GitHub-releases in-app updater — check, download, install. */
object Updater {

    private const val REPO = "Nekomo-App/NekoShield"
    private const val API = "https://api.github.com/repos/$REPO/releases"

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    data class Release(
        val tag: String,
        val title: String,
        val notes: String,
        val apkUrl: String,
        val prerelease: Boolean,
    )

    /** Newest release whose tag is numerically newer than the installed version.
     * null = checked and you're current; throws when GitHub can't be reached. */
    fun fetchUpdate(): Release? {
        val req = Request.Builder().url(API).header("Accept", "application/vnd.github+json").build()
        http.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw java.io.IOException("github ${res.code}")
            val arr = JSONArray(res.body?.string() ?: return null)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val tag = o.optString("tag_name")
                if (!isNewer(tag, BuildConfig.VERSION_NAME)) continue
                val assets = o.optJSONArray("assets") ?: continue
                var apk: String? = null
                for (j in 0 until assets.length()) {
                    val a = assets.getJSONObject(j)
                    if (a.optString("name").endsWith(".apk")) {
                        apk = a.optString("browser_download_url"); break
                    }
                }
                if (apk != null) return Release(
                    tag = tag,
                    title = o.optString("name").ifEmpty { tag },
                    notes = o.optString("body"),
                    apkUrl = apk,
                    prerelease = o.optBoolean("prerelease"))
            }
            return null
        }
    }

    /** "v0.1.1-alpha" > "0.1.0" — compares numeric dot-segments, ignores suffixes. */
    fun isNewer(tag: String, current: String): Boolean {
        fun nums(v: String) = v.trim().removePrefix("v")
            .split('.', '-', '+')
            .mapNotNull { it.takeWhile(Char::isDigit).toIntOrNull() }
        val a = nums(tag); val b = nums(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Streams the APK to cache and returns the file, or null on failure. */
    fun download(ctx: Context, url: String, onProgress: (Int) -> Unit = {}): File? {
        val out = File(ctx.cacheDir, "nekoshield-update.apk")
        try {
            http.newCall(Request.Builder().url(url).build()).execute().use { res ->
                if (!res.isSuccessful) return null
                val body = res.body ?: return null
                val total = body.contentLength()
                body.byteStream().use { ins ->
                    out.outputStream().use { fos ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = ins.read(buf); if (n < 0) break
                            fos.write(buf, 0, n); done += n
                            if (total > 0) onProgress(((done * 100) / total).toInt())
                        }
                    }
                }
            }
            return if (out.length() > 0) out else null
        } catch (_: Exception) {
            out.delete(); return null
        }
    }

    /** True when installs from this app are already permitted, or we've asked. */
    fun canInstall(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Hands the downloaded APK to the system package installer. */
    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // Third-party APK handlers (SAI, Acode, …) also claim this intent and
        // trigger a chooser — pin it to the ROM's own package installer.
        listOf("com.google.android.packageinstaller", "com.android.packageinstaller",
            "com.samsung.android.packageinstaller", "com.miui.packageinstaller",
            "com.oplus.packageinstaller", "com.vivo.packageinstaller")
            .firstOrNull { pkg -> runCatching { ctx.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false) }
            ?.let { i.setPackage(it) }
        ctx.startActivity(i)
    }
}
