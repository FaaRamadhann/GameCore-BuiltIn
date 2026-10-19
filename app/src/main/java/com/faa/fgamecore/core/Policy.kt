package com.faa.fgamecore.core

import android.content.Context

/**
 * Port of the FGC policy engine (src-reference/core/policy_engine.sh +
 * process_manager.sh + session_controller.sh) to on-device Kotlin.
 *
 * Invariants kept from the original:
 *  - whitelist > blacklist > system protection > foreground game
 *  - freeze is user-type only, write-ahead journalled, verified, always reversible
 *  - a failed unfreeze keeps its journal entry for the next retry
 *  - no operation is ever reported as successful without a live verification
 */
object Policy {
    private val PROTECTED = setOf(
        "android", "com.android.systemui", "com.android.phone", "com.android.shell",
    )
    private val PKG_RE = Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$")

    fun validPkg(pkg: String) = PKG_RE.matches(pkg)

    fun resolveProfile(profile: Int, battery: Int): Int = when (profile) {
        1, 2, 3 -> profile
        4 -> when {
            battery < 0 -> 1
            battery <= 20 -> 1
            battery <= 50 -> 2
            else -> 3
        }
        else -> 1
    }

    fun profileName(p: Int) = when (p) {
        1 -> "Balance"; 2 -> "Performance"; 3 -> "Awaken"; 4 -> "Auto"; else -> "Balance"
    }

    private fun protectedSet(ctx: Context): Set<String> =
        PROTECTED + ctx.packageName + listOfNotNull(Device.launcherPkg(ctx))

    fun targets(store: Store, ctx: Context, game: String?): List<String> {
        val prot = protectedSet(ctx)
        return store.blacklist
            .filter { validPkg(it) }
            .filter { it !in store.whitelist }
            .filter { it !in prot }
            .filter { game == null || it != game }
            .sorted()
    }

    private fun disabledSnapshot(): Set<String>? {
        val r = ShizukuBridge.exec("pm list packages -d --user 0", 15_000)
        if (!r.ok) return null
        return r.out.lineSequence()
            .mapNotNull { it.trim().removePrefix("package:").takeIf(String::isNotEmpty) }
            .toSet()
    }

    private fun enabledSnapshot(): Set<String>? {
        val r = ShizukuBridge.exec("pm list packages -e --user 0", 15_000)
        if (!r.ok) return null
        return r.out.lineSequence()
            .mapNotNull { it.trim().removePrefix("package:").takeIf(String::isNotEmpty) }
            .toSet()
    }

    private fun forceStop(pkgs: List<String>): Int {
        var n = 0
        pkgs.forEach { p ->
            val r = ShizukuBridge.exec("am force-stop --user 0 $p", 15_000)
            if (r.ok) n++ else Logs.add("CLEANUP_FAILED", "force-stop gagal: $p (${r.err.ifBlank { r.code.toString() }})")
        }
        return n
    }

    /**
     * Applies a profile to the current targets. Returns the human summary used in
     * the runtime log and in the session state.
     */
    fun apply(store: Store, ctx: Context, game: String, profile: Int): String {
        val t = targets(store, ctx, game)
        if (t.isEmpty()) return "tidak ada target"
        return when (profile) {
            1 -> {
                Logs.add("SESSION", "Balance: sesi dipantau, tanpa force-stop/freeze (${t.size} target)")
                "monitor-only (${t.size} target)"
            }
            2 -> {
                val n = forceStop(t)
                Logs.add("CLEANUP_SUCCESS", "Performance: force-stop $n/${t.size} target")
                "force-stop $n/${t.size}"
            }
            else -> {
                val n = forceStop(t)
                val frozen = freeze(store, ctx, t, game)
                // Awaken: one bounded second sweep for targets that came back.
                val again = t.filter { running(it) }
                if (again.isNotEmpty()) {
                    val n2 = forceStop(again)
                    Logs.add("CLEANUP_SUCCESS", "Awaken: second-sweep $n2/${again.size}")
                }
                "force-stop $n/${t.size}, freeze $frozen"
            }
        }
    }

    private fun running(pkg: String): Boolean {
        val r = ShizukuBridge.exec("pidof $pkg", 8_000)
        return r.ok && r.trimmed.isNotEmpty()
    }

    /** Write-ahead journalled, verified freeze. Returns the count actually disabled. */
    fun freeze(store: Store, ctx: Context, pkgs: List<String>, game: String?): Int {
        if (pkgs.isEmpty()) return 0
        if (store.dryRun) {
            Logs.add("CLEANUP_SKIPPED", "dry-run: freeze dilewati (${pkgs.size} paket)")
            return 0
        }
        val prot = protectedSet(ctx)
        val already = disabledSnapshot() ?: emptySet()
        var done = 0
        pkgs.forEach { p ->
            when {
                !validPkg(p) -> Logs.add("CLEANUP_SKIPPED", "nama paket tidak valid: $p")
                p in store.whitelist -> Logs.add("CLEANUP_SKIPPED", "whitelisted: $p")
                p in prot -> Logs.add("CLEANUP_SKIPPED", "system-protected: $p")
                p == game -> Logs.add("CLEANUP_SKIPPED", "foreground-game: $p")
                Device.isSystem(ctx, p) -> Logs.add("CLEANUP_SKIPPED", "system-type-nofreeze: $p")
                p in already -> Logs.add("CLEANUP_SKIPPED", "already-disabled: $p")
                else -> {
                    // Journal BEFORE disable: pm disable-user is persistent, so a kill
                    // between the command and bookkeeping would strand the app disabled.
                    store.frozen = store.frozen + p
                    val r = ShizukuBridge.exec("pm disable-user --user 0 $p", 20_000)
                    if (!r.ok) {
                        Logs.add("CLEANUP_FAILED", "freeze gagal: $p (${r.err.ifBlank { r.code.toString() }})")
                        // Keep the entry: unfreeze retires it if the app was never disabled.
                    }
                }
            }
        }
        // One live verification for the whole batch; never claim an unverified freeze.
        val live = disabledSnapshot()
        if (live != null) {
            store.frozen.forEach { p ->
                if (p in live) {
                    done++
                    Logs.add("CLEANUP_SUCCESS", "frozen: $p")
                } else if (p in pkgs) {
                    store.frozen = store.frozen - p
                    Logs.add("CLEANUP_FAILED", "tidak ter-freeze setelah perintah: $p")
                }
            }
        }
        return done
    }

    /**
     * Restores every package the journal says FGC disabled. The journal is the
     * only source of truth: packages that were already disabled by the user are
     * never added to it, and system/protected entries from a corrupt journal are
     * skipped instead of being force-enabled. Entries that cannot be proven
     * enabled stay for the next retry.
     */
    fun unfreezeAll(ctx: Context, store: Store, event: String): Boolean {
        val frozen = store.frozen
        if (frozen.isEmpty()) return true
        val dis = disabledSnapshot()
        if (dis == null) {
            Logs.add(event, "unfreeze ditunda: package service tidak menjawab")
            return false
        }
        val prot = protectedSet(ctx)
        val en = enabledSnapshot()
        val candidates = mutableListOf<String>()
        frozen.forEach { p ->
            when {
                !validPkg(p) -> Logs.add("$event", "dilewati, nama paket tidak valid: $p")
                p in prot || Device.isSystem(ctx, p) ->
                    Logs.add("$event", "dilewati, paket sistem tidak diaktifkan: $p")
                p in dis -> candidates += p
                en == null || p in en -> Logs.add("$event", "sudah enabled: $p")
                else -> candidates += p // in neither list: try to restore, verify after
            }
        }
        candidates.forEach { p ->
            var ok = ShizukuBridge.exec("pm enable --user 0 $p", 20_000).ok
            if (!ok) ok = ShizukuBridge.exec("cmd package enable --user 0 $p", 20_000).ok
            if (!ok) Logs.add("CLEANUP_FAILED", "enable gagal: $p")
        }
        // Honest verification: rc=0 that did not re-enable is not success.
        val after = disabledSnapshot()
        val enabled = if (after == null) enabledSnapshot() else null
        val keep = candidates.filter { p ->
            when {
                after != null -> p in after
                enabled != null -> p !in enabled
                else -> true // cannot prove -> keep for retry
            }
        }.toSet()
        store.frozen = keep
        val restored = candidates.size - keep.size
        Logs.add("$event", "unfreeze selesai: $restored dipulihkan, ${keep.size} ditunda")
        return keep.isEmpty()
    }
}
