package com.faa.fgamecore.core

import android.content.Context
import android.content.pm.PackageManager
import android.os.BatteryManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ShizukuStatus { NotInstalled, NotRunning, PermissionDenied, Granted }

data class Session(val game: String, val profile: Int, val startedAt: Long)

data class AppEntry(val pkg: String, val label: String, val system: Boolean)

data class UiSnapshot(
    val shizuku: ShizukuStatus = ShizukuStatus.NotRunning,
    val shizukuVersion: Int = 0,
    val shizukuUid: Int = 0,
    val daemon: Boolean = false,
    val daemonPid: Int = 0,
    val daemonSince: Long = 0L,
    val session: Session? = null,
    val games: List<Game> = emptyList(),
    val blacklist: Set<String> = emptySet(),
    val whitelist: Set<String> = emptySet(),
    val frozen: Set<String> = emptySet(),
    val foreground: String? = null,
    val battery: Int = -1,
    val dryRun: Boolean = false,
    val enabled: Boolean = true,
    val overlay: Boolean = false,
    val busy: Boolean = false,
    val lastError: String? = null,
)

object Device {
    fun batteryPct(ctx: Context): Int = runCatching {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }.getOrDefault(-1)

    fun installedApps(ctx: Context): List<AppEntry> = runCatching {
        val pm = ctx.packageManager
        pm.getInstalledApplications(0)
            .filter { it.packageName != ctx.packageName }
            .map {
                AppEntry(
                    it.packageName,
                    runCatching { pm.getApplicationLabel(it).toString() }.getOrDefault(it.packageName),
                    (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0,
                )
            }
            .sortedBy { it.label.lowercase(Locale.ROOT) }
    }.getOrElse { emptyList() }

    fun label(ctx: Context, pkg: String): String = runCatching {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    fun isSystem(ctx: Context, pkg: String): Boolean = runCatching {
        val pm = ctx.packageManager
        (pm.getApplicationInfo(pkg, 0).flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
    }.getOrDefault(true) // fail-closed: unknown type is treated as system (never frozen)

    fun launcherPkg(ctx: Context): String? = runCatching {
        val i = android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_HOME)
        ctx.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }.getOrNull()

    fun fmtDuration(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    }

    fun fmtTime(ms: Long): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(ms))
}
