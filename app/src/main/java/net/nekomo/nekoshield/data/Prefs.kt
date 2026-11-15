package net.nekomo.nekoshield.data

import android.content.Context
import android.content.SharedPreferences

/** Central settings — single SharedPreferences file, no secrets. */
class Prefs private constructor(ctx: Context) {

    private val sp: SharedPreferences = ctx.getSharedPreferences("nekoshield", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean("enabled", false)
        set(v) = sp.edit().putBoolean("enabled", v).apply()

    var providerId: String
        get() = sp.getString("provider", "cloudflare") ?: "cloudflare"
        set(v) = sp.edit().putString("provider", v).apply()

    /** Custom upstream profile (proto|host|port|name) or empty for preset. */
    var customUpstream: String
        get() = sp.getString("custom_upstream", "") ?: ""
        set(v) = sp.edit().putString("custom_upstream", v).apply()

    var useCustom: Boolean
        get() = sp.getBoolean("use_custom", false)
        set(v) = sp.edit().putBoolean("use_custom", v).apply()

    var blocklistsEnabled: Boolean
        get() = sp.getBoolean("bl_enabled", true)
        set(v) = sp.edit().putBoolean("bl_enabled", v).apply()

    /** Enabled remote list ids. */
    var enabledLists: Set<String>
        get() = sp.getStringSet("bl_ids", setOf("steven-black")) ?: setOf("steven-black")
        set(v) = sp.edit().putStringSet("bl_ids", v).apply()

    var userBlock: Set<String>
        get() = sp.getStringSet("user_block", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("user_block", v).apply()

    var userAllow: Set<String>
        get() = sp.getStringSet("user_allow", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("user_allow", v).apply()

    /** Packages routed OUTSIDE the tunnel. */
    var excludedApps: Set<String>
        get() = sp.getStringSet("excluded_apps", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("excluded_apps", v).apply()

    var logQueries: Boolean
        get() = sp.getBoolean("log_queries", true)
        set(v) = sp.edit().putBoolean("log_queries", v).apply()

    var startOnBoot: Boolean
        get() = sp.getBoolean("start_on_boot", false)
        set(v) = sp.edit().putBoolean("start_on_boot", v).apply()

    var autoUpdate: Boolean
        get() = sp.getBoolean("auto_update", true)
        set(v) = sp.edit().putBoolean("auto_update", v).apply()

    var lastUpdateCheck: Long
        get() = sp.getLong("last_update_check", 0)
        set(v) = sp.edit().putLong("last_update_check", v).apply()

    var blockedCount: Long
        get() = sp.getLong("stat_blocked", 0)
        set(v) = sp.edit().putLong("stat_blocked", v).apply()

    var queryCount: Long
        get() = sp.getLong("stat_queries", 0)
        set(v) = sp.edit().putLong("stat_queries", v).apply()

    fun resetStats() {
        sp.edit().putLong("stat_blocked", 0).putLong("stat_queries", 0).apply()
    }

    companion object {
        @Volatile private var inst: Prefs? = null
        fun get(ctx: Context): Prefs = inst ?: synchronized(this) {
            inst ?: Prefs(ctx.applicationContext).also { inst = it }
        }
    }
}
