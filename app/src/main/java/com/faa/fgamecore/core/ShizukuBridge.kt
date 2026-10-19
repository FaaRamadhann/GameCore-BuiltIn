package com.faa.fgamecore.core

import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.io.ByteArrayOutputStream

data class Exec(val code: Int, val out: String, val err: String) {
    val ok: Boolean get() = code == 0
    val trimmed: String get() = out.trim()
}

/**
 * Single door to privileged operations. Everything runs as the Shizuku-provided
 * shell uid (2000) through IShizukuService.newProcess; no root, no su.
 */
object ShizukuBridge {
    const val REQ_PERMISSION = 4242
    private const val PKG = "moe.shizuku.privileged.api"

    fun installed(ctx: Context): Boolean = runCatching {
        ctx.packageManager.getPackageInfo(PKG, 0); true
    }.getOrDefault(false)

    fun status(ctx: Context): ShizukuStatus {
        if (!installed(ctx)) return ShizukuStatus.NotInstalled
        val alive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!alive) return ShizukuStatus.NotRunning
        val granted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        return if (granted) ShizukuStatus.Granted else ShizukuStatus.PermissionDenied
    }

    fun version(): Int = runCatching { Shizuku.getVersion() }.getOrDefault(0)
    fun uid(): Int = runCatching { Shizuku.getUid() }.getOrDefault(-1)

    /** Must be called from the UI process; the result arrives via the permission listener. */
    fun requestPermission() {
        runCatching { Shizuku.requestPermission(REQ_PERMISSION) }
    }

    fun exec(cmd: String, timeoutMs: Long = 20_000): Exec {
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false))
            return Exec(-1, "", "shizuku-not-running")
        val svc = runCatching {
            IShizukuService.Stub.asInterface(Shizuku.getBinder())
        }.getOrNull() ?: return Exec(-1, "", "shizuku-binder-null")
        return runCatching {
            val p = svc.newProcess(arrayOf("sh", "-c", cmd), null, null)
            val out = ByteArrayOutputStream()
            val err = ByteArrayOutputStream()
            val tOut = Thread { drain(p.inputStream, out) }.apply { isDaemon = true; start() }
            val tErr = Thread { drain(p.errorStream, err) }.apply { isDaemon = true; start() }
            val finished = runCatching { p.waitForTimeout(timeoutMs, "MILLISECONDS") }.getOrDefault(false)
            if (!finished) runCatching { p.destroy() }
            tOut.join(1500)
            tErr.join(1500)
            val rc = if (finished) runCatching { p.exitValue() }.getOrDefault(-1) else -1
            Exec(rc, out.toString("UTF-8"), err.toString("UTF-8"))
        }.getOrElse { e -> Exec(-1, "", e.message ?: "exec-failed") }
    }

    private fun drain(pfd: ParcelFileDescriptor?, sink: ByteArrayOutputStream) {
        runCatching {
            ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.copyTo(sink) }
        }
    }
}
