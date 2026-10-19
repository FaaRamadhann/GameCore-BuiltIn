package com.faa.fgamecore.core

/**
 * Pure recovery decision table (no Android calls) so the same rules can be
 * mirrored by tools/check_recovery.py.
 *
 * The freeze journal is the only record of what FGC disabled. A session is
 * considered alive only while the daemon keeps writing heartbeats; once the
 * heartbeat is older than [Facts.graceMs] the session is stale even if the
 * process (and the journal) survived. A session that outlives [MAX_SESSION_MS]
 * is stale too, which covers a zombie daemon.
 */
object Recovery {
    enum class Action { NOTHING, RESTORE }

    data class Facts(
        val journalSize: Int,
        val sessionGame: String?,
        val sessionStartedAt: Long,
        val lastHeartbeatAt: Long,
        val now: Long,
        /** Foreground package, or null when it cannot be read (no Shizuku). */
        val foreground: String?,
        val ownPackage: String,
        val graceMs: Long = DEFAULT_GRACE_MS,
        val maxSessionMs: Long = MAX_SESSION_MS,
    )

    const val DEFAULT_GRACE_MS = 5 * 60_000L
    const val MAX_SESSION_MS = 12 * 60 * 60_000L

    fun heartbeatFresh(f: Facts): Boolean =
        f.lastHeartbeatAt > 0 && f.now >= f.lastHeartbeatAt && f.now - f.lastHeartbeatAt <= f.graceMs

    fun tooOld(f: Facts): Boolean =
        f.sessionStartedAt > 0 && f.now >= f.sessionStartedAt && f.now - f.sessionStartedAt > f.maxSessionMs

    fun decide(f: Facts): Action {
        val game = f.sessionGame
        if (game == null) return if (f.journalSize > 0) Action.RESTORE else Action.NOTHING
        if (tooOld(f)) return Action.RESTORE
        if (f.foreground == game) return Action.NOTHING
        // Another app is in front, so no game session can be alive.
        if (f.foreground != null && f.foreground != f.ownPackage) return Action.RESTORE
        // Foreground unknown or FGC itself: trust the daemon heartbeat.
        return if (heartbeatFresh(f)) Action.NOTHING else Action.RESTORE
    }
}
