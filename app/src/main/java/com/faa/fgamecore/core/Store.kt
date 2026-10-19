package com.faa.fgamecore.core

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class Game(val pkg: String, val label: String, val profile: Int = 0, val enabled: Boolean = true)

/** Local configuration store. SharedPreferences is enough here: no cross-process writers. */
class Store(ctx: Context) {
    private val p: SharedPreferences = ctx.getSharedPreferences("fgc", Context.MODE_PRIVATE)

    var games: List<Game>
        get() = runCatching {
            val arr = JSONArray(p.getString("games", "[]"))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Game(o.getString("package"), o.optString("label", o.getString("package")),
                    o.optInt("profile", 0), o.optBoolean("enabled", true))
            }
        }.getOrElse { emptyList() }
        set(v) {
            val arr = JSONArray()
            v.forEach { g ->
                arr.put(JSONObject().put("package", g.pkg).put("label", g.label)
                    .put("profile", g.profile).put("enabled", g.enabled))
            }
            p.edit().putString("games", arr.toString()).apply()
        }

    var blacklist: Set<String>
        get() = p.getStringSet("blacklist", emptySet())!!.toSet()
        set(v) = p.edit().putStringSet("blacklist", v).apply()

    var whitelist: Set<String>
        get() = p.getStringSet("whitelist", defaultWhitelist)!!.toSet()
        set(v) = p.edit().putStringSet("whitelist", v).apply()

    /** Packages we froze and still must restore. Written BEFORE the disable command. */
    var frozen: Set<String>
        get() = p.getStringSet("frozen", emptySet())!!.toSet()
        set(v) = p.edit().putStringSet("frozen", v).apply()

    var defaultProfile: Int
        get() = p.getInt("profile", 1)
        set(v) = p.edit().putInt("profile", v).apply()

    var enabled: Boolean
        get() = p.getBoolean("enabled", true)
        set(v) = p.edit().putBoolean("enabled", v).apply()

    var autoStart: Boolean
        get() = p.getBoolean("autoStart", true)
        set(v) = p.edit().putBoolean("autoStart", v).apply()

    var dryRun: Boolean
        get() = p.getBoolean("dryRun", false)
        set(v) = p.edit().putBoolean("dryRun", v).apply()

    var pollSec: Int
        get() = p.getInt("poll", 3)
        set(v) = p.edit().putInt("poll", v.coerceIn(1, 30)).apply()

    var notifyGame: Boolean
        get() = p.getBoolean("notifyGame", true)
        set(v) = p.edit().putBoolean("notifyGame", v).apply()

    var notifyDaemon: Boolean
        get() = p.getBoolean("notifyDaemon", true)
        set(v) = p.edit().putBoolean("notifyDaemon", v).apply()

    var notifyFreeze: Boolean
        get() = p.getBoolean("notifyFreeze", true)
        set(v) = p.edit().putBoolean("notifyFreeze", v).apply()

    var notifyUpdate: Boolean
        get() = p.getBoolean("notifyUpdate", false)
        set(v) = p.edit().putBoolean("notifyUpdate", v).apply()

    var onboarded: Boolean
        get() = p.getBoolean("onboarded", false)
        set(v) = p.edit().putBoolean("onboarded", v).apply()

    // --- Game launcher ---
    var launchEnabled: Boolean
        get() = p.getBoolean("launchEnabled", true)
        set(v) = p.edit().putBoolean("launchEnabled", v).apply()

    // --- Floating overlay (off until the user enables it) ---
    var overlayEnabled: Boolean
        get() = p.getBoolean("overlayEnabled", false)
        set(v) = p.edit().putBoolean("overlayEnabled", v).apply()

    var overlayMinimized: Boolean
        get() = p.getBoolean("overlayMinimized", true)
        set(v) = p.edit().putBoolean("overlayMinimized", v).apply()

    var overlayAutoShow: Boolean
        get() = p.getBoolean("overlayAutoShow", false)
        set(v) = p.edit().putBoolean("overlayAutoShow", v).apply()

    var overlayShowProfile: Boolean
        get() = p.getBoolean("overlayShowProfile", true)
        set(v) = p.edit().putBoolean("overlayShowProfile", v).apply()

    var overlayShowShizuku: Boolean
        get() = p.getBoolean("overlayShowShizuku", true)
        set(v) = p.edit().putBoolean("overlayShowShizuku", v).apply()

    var overlayShowStatus: Boolean
        get() = p.getBoolean("overlayShowStatus", true)
        set(v) = p.edit().putBoolean("overlayShowStatus", v).apply()

    /** Last overlay window position, in pixels; -1 means "use the default corner". */
    var overlayX: Int
        get() = p.getInt("overlayX", -1)
        set(v) = p.edit().putInt("overlayX", v).apply()

    var overlayY: Int
        get() = p.getInt("overlayY", -1)
        set(v) = p.edit().putInt("overlayY", v).apply()

    /** Persisted session so a process death never loses the "must unfreeze" fact. */
    var sessionGame: String?
        get() = p.getString("sessionGame", null)
        set(v) = p.edit().putString("sessionGame", v).apply()

    var sessionProfile: Int
        get() = p.getInt("sessionProfile", 1)
        set(v) = p.edit().putInt("sessionProfile", v).apply()

    var sessionStarted: Long
        get() = p.getLong("sessionStarted", 0L)
        set(v) = p.edit().putLong("sessionStarted", v).apply()

    /** Last healthy daemon poll. Recovery treats an old value as a dead daemon. */
    var lastPollAt: Long
        get() = p.getLong("lastPollAt", 0L)
        set(v) = p.edit().putLong("lastPollAt", v).apply()

    fun game(pkg: String): Game? = games.firstOrNull { it.pkg == pkg }

    companion object {
        val defaultWhitelist = setOf("android", "com.android.systemui", "com.android.phone")
    }
}
