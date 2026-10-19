package com.faa.fgamecore.core

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Application-wide engine: one snapshot flow shared by UI and the daemon service. */
object Fgc {
    lateinit var app: Context
        private set
    lateinit var store: Store
        private set

    private val _state = MutableStateFlow(UiSnapshot())
    val state: StateFlow<UiSnapshot> = _state
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private var daemonSince = 0L
    private var missCount = 0
    // Broadcast receivers get ~60 s; stay well inside it.
    private const val BOOT_RETRY_MS = 45_000L
    private const val BOOT_RETRY_STEP_MS = 5_000L

    fun init(ctx: Context) {
        if (::app.isInitialized) return
        app = ctx.applicationContext
        store = Store(app)
        Logs.load(app)
        refresh()
        scope.launch {
            // Never leave a package frozen with a session that cannot be alive.
            reconcile("APP_START")
            watchdog()
        }
    }

    /**
     * Ends a session that can no longer be alive and restores its journal.
     * Idempotent: safe on process start, boot, and every watchdog tick.
     */
    suspend fun reconcile(reason: String) {
        if (store.sessionGame == null && store.frozen.isEmpty()) return
        val now = System.currentTimeMillis()
        // A healthy daemon already enforces the session; step in only when it is silent.
        val hb = store.lastPollAt
        if (_state.value.daemon && hb > 0 && now >= hb && now - hb <= Recovery.DEFAULT_GRACE_MS) return
        val fg = if (ShizukuBridge.status(app) == ShizukuStatus.Granted) {
            withContext(Dispatchers.IO) { detectForeground() }
        } else null
        val facts = Recovery.Facts(
            journalSize = store.frozen.size,
            sessionGame = store.sessionGame,
            sessionStartedAt = store.sessionStarted,
            lastHeartbeatAt = store.lastPollAt,
            now = now,
            foreground = fg,
            ownPackage = app.packageName,
        )
        if (Recovery.decide(facts) == Recovery.Action.NOTHING) {
            if (store.frozen.isNotEmpty())
                Logs.add("RECOVERY", "Sesi masih hidup, jurnal dipertahankan (${store.frozen.size})")
            return
        }
        Logs.add("RECOVERY", "Sesi basi ($reason), memulihkan ${store.frozen.size} paket…")
        val ok = endSession("recovery: $reason")
        Logs.add("RECOVERY", if (ok) "Jurnal bersih, semua paket terverifikasi" else "Sebagian paket belum pulih, disimpan untuk retry")
    }

    /** A daemon that stops heartbeating must not strand a freeze. */
    private suspend fun watchdog() {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            runCatching { reconcile("WATCHDOG") }
                .onFailure { Logs.add("CORE_ERROR", "Recovery gagal: ${it.message}") }
        }
    }

    /** Boot always invalidates the session: no game survives a reboot. */
    suspend fun bootRecovery() {
        if (store.sessionGame != null || store.frozen.isNotEmpty()) {
            Logs.add("RECOVERY", "Boot: menutup sesi lama (${store.frozen.size} paket di jurnal)")
            // Shizuku often comes up seconds after BOOT_COMPLETED; a failed enable
            // keeps the journal, so retry instead of stranding the freeze.
            var ok = endSession("boot")
            var waited = 0L
            while (!ok && waited < BOOT_RETRY_MS) {
                kotlinx.coroutines.delay(BOOT_RETRY_STEP_MS)
                waited += BOOT_RETRY_STEP_MS
                ok = endSession("boot retry")
            }
            if (!ok) Logs.add("RECOVERY", "Boot: paket belum pulih, jurnal disimpan")
        }
        if (store.autoStart && store.enabled && store.onboarded) {
            val err = startDaemon()
            Logs.add("DAEMON", if (err == null) "Auto start setelah boot" else "Auto start dilewati: $err")
        }
        refresh()
    }

    /** Safe from any thread: probes Shizuku off the main thread. */
    fun refresh() {
        scope.launch { refreshNow() }
    }

    /** Blocking refresh; only from a background/IO context. */
    fun refreshNow() {
        val session = store.sessionGame?.let { Session(it, store.sessionProfile, store.sessionStarted) }
        _state.value = _state.value.copy(
            shizuku = ShizukuBridge.status(app),
            shizukuVersion = ShizukuBridge.version(),
            shizukuUid = ShizukuBridge.uid(),
            games = store.games,
            blacklist = store.blacklist,
            whitelist = store.whitelist,
            frozen = store.frozen,
            session = session,
            battery = Device.batteryPct(app),
            dryRun = store.dryRun,
            enabled = store.enabled,
        )
    }

    fun markDaemon(on: Boolean, pid: Int = 0) {
        daemonSince = if (on) (daemonSince.takeIf { it > 0 } ?: System.currentTimeMillis()) else 0L
        _state.value = _state.value.copy(
            daemon = on,
            daemonPid = if (on) pid else 0,
            daemonSince = daemonSince,
        )
    }

    fun startDaemon(): String? {
        if (ShizukuBridge.status(app) != ShizukuStatus.Granted)
            return "Shizuku belum aktif atau izin belum diberikan"
        return runCatching {
            ContextCompat.startForegroundService(app, Intent(app, FgcService::class.java))
            Logs.add("DAEMON", "Daemon diminta start")
            null
        }.getOrElse { e ->
            Logs.add("CORE_ERROR", "Gagal start daemon: ${e.message}")
            e.message ?: "gagal start daemon"
        }
    }

    fun stopDaemon() {
        app.stopService(Intent(app, FgcService::class.java))
        Logs.add("DAEMON", "Daemon dihentikan oleh pengguna")
    }

    /** Applies the game profile before the launcher intent, when Shizuku can actually do it. */
    suspend fun prepareLaunch(pkg: String): String? {
        if (ShizukuBridge.status(app) != ShizukuStatus.Granted) return "Shizuku belum aktif, profil tidak diterapkan"
        if (!store.enabled) return "Faa GameCore dinonaktifkan, profil tidak diterapkan"
        if (store.dryRun) {
            Logs.add("LAUNCH", "dry-run: profil tidak diterapkan untuk $pkg")
            return null
        }
        return startSession(pkg, manual = true)
    }

    /** A launch that never started must not leave a session (and frozen apps) behind. */
    suspend fun abortLaunch(pkg: String) {
        if (store.sessionGame == pkg) endSession("peluncuran gagal")
    }

    /** Changes the profile: applies to the live session, otherwise sets the default. */
    suspend fun changeProfile(p: Int): String? = lock.withLock {
        val g = store.sessionGame
        if (g == null) {
            store.defaultProfile = p
            refresh()
            return@withLock null
        }
        if (ShizukuBridge.status(app) != ShizukuStatus.Granted) return@withLock "Shizuku tidak aktif"
        store.sessionProfile = p
        val summary = withContext(Dispatchers.IO) { Policy.apply(store, app, g, p) }
        Logs.add("SESSION", "Profil diganti ke ${Policy.profileName(p)}: $summary")
        refresh()
        null
    }

    // --- Floating overlay lifecycle ---

    fun overlayAllowed(): Boolean = OverlayService.allowed(app)

    fun startOverlay(): String? {
        if (!overlayAllowed()) return "Izin overlay belum diberikan"
        return runCatching {
            ContextCompat.startForegroundService(app, Intent(app, OverlayService::class.java))
            Logs.add("OVERLAY", "Overlay diminta tampil")
            null
        }.getOrElse { e ->
            Logs.add("OVERLAY_ERROR", "Gagal start overlay: ${e.message}")
            e.message ?: "gagal menampilkan overlay"
        }
    }

    fun stopOverlay() {
        app.stopService(Intent(app, OverlayService::class.java))
    }

    fun markOverlay(on: Boolean) {
        _state.value = _state.value.copy(overlay = on)
    }

    fun restartDaemon() {
        stopDaemon()
        scope.launch {
            kotlinx.coroutines.delay(600)
            startDaemon()
        }
    }

    suspend fun startSession(pkg: String, manual: Boolean): String? = lock.withLock {
        val game = store.game(pkg) ?: return@withLock "Aplikasi bukan game terdaftar"
        if (!game.enabled) return@withLock "Game dinonaktifkan di Game List"
        if (store.sessionGame == pkg) return@withLock null
        if (store.sessionGame != null) endSessionLocked("ganti game")

        val rawProfile = if (game.profile > 0) game.profile else store.defaultProfile
        val profile = Policy.resolveProfile(rawProfile, Device.batteryPct(app))
        store.sessionGame = pkg
        store.sessionProfile = profile
        store.sessionStarted = System.currentTimeMillis()
        Logs.add("GAME_DETECTED", "Game terdeteksi: ${Device.label(app, pkg)} ($pkg)${if (manual) " [manual]" else ""}")
        Logs.add("SESSION_STARTED", "Profil ${Policy.profileName(profile)} (${if (rawProfile == 4) "auto dari baterai ${Device.batteryPct(app)}%" else "profil $profile"})")
        // A session without a daemon can never end on its own; start one now.
        if (!_state.value.daemon) {
            val err = startDaemon()
            Logs.add("DAEMON", if (err == null) "Daemon dijalankan untuk sesi" else "Daemon tidak bisa jalan: $err")
        }
        val summary = withContext(Dispatchers.IO) { Policy.apply(store, app, pkg, profile) }
        Logs.add("SESSION", "Cleanup: $summary")
        refresh()
        if (store.notifyGame) Notifier.alert(app, 10, "Sesi game dimulai", "${Device.label(app, pkg)} • ${Policy.profileName(profile)}")
        null
    }

    suspend fun endSession(reason: String): Boolean = lock.withLock {
        if (store.sessionGame == null && store.frozen.isEmpty()) return@withLock true
        endSessionLocked(reason)
    }

    private suspend fun endSessionLocked(reason: String): Boolean {
        val g = store.sessionGame
        val ok = withContext(Dispatchers.IO) { Policy.unfreezeAll(app, store, "SESSION_ENDED") }
        store.sessionGame = null
        store.sessionStarted = 0L
        missCount = 0
        Logs.add("SESSION_ENDED", "Sesi berakhir ($reason)${if (g != null) " • $g" else ""}")
        refresh()
        return ok
    }

    /** One daemon tick. Safe to call when no daemon is running (manual refresh). */
    suspend fun poll() {
        val st = ShizukuBridge.status(app)
        if (st != ShizukuStatus.Granted) {
            _state.value = _state.value.copy(shizuku = st)
            return
        }
        val fg = detectForeground()
        val now = System.currentTimeMillis()
        // Heartbeat: recovery treats an old value as a dead daemon.
        if (now - store.lastPollAt > 10_000) store.lastPollAt = now
        _state.value = _state.value.copy(shizuku = st, foreground = fg, battery = Device.batteryPct(app))
        if (!store.enabled) {
            if (store.sessionGame != null) endSession("modul dinonaktifkan")
            return
        }
        val game = store.sessionGame
        if (game == null) {
            if (fg != null && store.game(fg)?.enabled == true) startSession(fg, manual = false)
            return
        }
        if (fg == game) { missCount = 0; return }
        if (fg == app.packageName) return // user is checking the app; do not end the session
        missCount++
        if (missCount >= 2) endSession("game tidak lagi di foreground")
    }

    fun detectForeground(): String? {
        val r = ShizukuBridge.exec(
            "dumpsys activity activities 2>/dev/null | grep -m1 -E 'topResumedActivity|mResumedActivity|ResumedActivity'",
            15_000,
        )
        val fromActivity = Regex("([A-Za-z0-9_.]+)/[A-Za-z0-9_.$]+").find(r.out)?.groupValues?.get(1)
        if (!fromActivity.isNullOrBlank()) return fromActivity
        val w = ShizukuBridge.exec("dumpsys window 2>/dev/null | grep -m1 mCurrentFocus", 15_000)
        return Regex("([A-Za-z0-9_.]+)/[A-Za-z0-9_.$]+").find(w.out)?.groupValues?.get(1)
    }
}
