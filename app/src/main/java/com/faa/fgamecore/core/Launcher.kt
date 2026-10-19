package com.faa.fgamecore.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

sealed class LaunchResult {
    /** The launcher intent was handed to the system. [verified] is null when it cannot be checked. */
    data class Started(val pkg: String, val verified: Boolean?) : LaunchResult()
    data object NotInstalled : LaunchResult()
    data object NoLauncher : LaunchResult()
    data class Failed(val reason: String) : LaunchResult()
}

/**
 * Opens an installed app through its own launcher intent. No hardcoded activity
 * names and no shell command: this is plain Android.
 */
object Launcher {
    fun installed(ctx: Context, pkg: String): Boolean = runCatching {
        ctx.packageManager.getApplicationInfo(pkg, 0); true
    }.getOrDefault(false)

    fun canLaunch(ctx: Context, pkg: String): Boolean = runCatching {
        ctx.packageManager.getLaunchIntentForPackage(pkg) != null
    }.getOrDefault(false)

    fun start(ctx: Context, pkg: String): LaunchResult {
        if (!Policy.validPkg(pkg)) return LaunchResult.Failed("nama paket tidak valid")
        if (!installed(ctx, pkg)) return LaunchResult.NotInstalled
        val intent = runCatching { ctx.packageManager.getLaunchIntentForPackage(pkg) }.getOrNull()
            ?: return LaunchResult.NoLauncher
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        val resolved = runCatching {
            ctx.packageManager.resolveActivity(
                Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PackageManager.MATCH_DEFAULT_ONLY,
            )
        }.getOrNull()
        if (resolved == null) return LaunchResult.NoLauncher
        return runCatching { ctx.startActivity(intent); LaunchResult.Started(pkg, null) }
            .getOrElse { e -> LaunchResult.Failed(e.message ?: "startActivity gagal") }
    }

    /**
     * Best-effort confirmation. Without Shizuku the foreground app is not readable
     * (getRunningTasks is restricted), so this returns null instead of guessing.
     */
    suspend fun verifyForeground(pkg: String, waitMs: Long = 2500): Boolean? {
        if (ShizukuBridge.status(Fgc.app) != ShizukuStatus.Granted) return null
        return withTimeoutOrNull(waitMs + 4000) {
            delay(waitMs)
            Fgc.detectForeground() == pkg
        }
    }
}
