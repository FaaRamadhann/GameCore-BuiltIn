#!/usr/bin/env python3
"""Self-check for the Faa GameCore recovery rules (host, no device).

Mirrors app/src/main/java/com/faa/fgamecore/core/Recovery.kt. Keep the two in
sync. Covers process death, dead daemon, empty/valid journal and stale sessions.

    python tools/check_recovery.py
"""
import sys

GRACE = 5 * 60_000
MAX_SESSION = 12 * 60 * 60_000
OWN = "com.faa.fgamecore"


def heartbeat_fresh(last, now, grace=GRACE):
    return last > 0 and now >= last and now - last <= grace


def too_old(started, now, max_age=MAX_SESSION):
    return started > 0 and now >= started and now - started > max_age


def decide(journal, game, started, last, now, fg, own=OWN, grace=GRACE, max_age=MAX_SESSION):
    if game is None:
        return "restore" if journal > 0 else "nothing"
    if too_old(started, now, max_age):
        return "restore"
    if fg == game:
        return "nothing"
    if fg is not None and fg != own:
        return "restore"
    return "nothing" if heartbeat_fresh(last, now, grace) else "restore"


def check():
    t = 1_000_000_000_000
    game = "com.mobile.legends"

    # Empty journal + no session: nothing to do.
    assert decide(0, None, 0, 0, t, None) == "nothing"

    # Orphan journal after process death: restore even with no sessionGame.
    assert decide(2, None, 0, 0, t, None) == "restore"

    # Live session: game in foreground keeps its journal, fresh or not.
    assert decide(2, game, t - 60_000, t - 3_000, t, game) == "nothing"
    assert decide(2, game, t - 60_000, 0, t, game) == "nothing"

    # Dead daemon (stale heartbeat) and the game left the foreground.
    assert decide(2, game, t - 60_000, t - GRACE - 1, t, "com.miui.home") == "restore"

    # Foreground unreadable (no Shizuku): heartbeat decides.
    assert decide(2, game, t - 60_000, t - 3_000, t, None) == "nothing"
    assert decide(2, game, t - 60_000, t - GRACE - 1, t, None) == "restore"
    assert decide(2, game, t - 60_000, 0, t, None) == "restore"

    # User is inside FGC: heartbeat decides, so a dead daemon is still caught.
    assert decide(2, game, t - 60_000, t - 3_000, t, OWN) == "nothing"
    assert decide(2, game, t - 60_000, t - GRACE - 1, t, OWN) == "restore"

    # Stale session with an empty journal is cleared too.
    assert decide(0, game, t - 60_000, t - GRACE - 1, t, None) == "restore"

    # Grace boundary is still fresh; one ms past it is stale.
    assert decide(2, game, t - 60_000, t - GRACE, t, None) == "nothing"
    assert decide(2, game, t - 60_000, t - GRACE - 1, t, None) == "restore"

    # Clock moved backwards: never trust a future heartbeat.
    assert decide(2, game, t - 60_000, t + 60_000, t, None) == "restore"

    # Zombie daemon: a session older than 12h is stale even with heartbeats.
    assert decide(2, game, t - MAX_SESSION - 1, t - 3_000, t, None) == "restore"
    assert decide(2, game, t - MAX_SESSION, t - 3_000, t, None) == "nothing"

    # Idempotent: the same facts always produce the same action.
    facts = (2, game, t - 60_000, t - GRACE - 1, t, None)
    assert decide(*facts) == decide(*facts) == "restore"

    print("recovery self-check: PASS")


if __name__ == "__main__":
    try:
        check()
    except AssertionError as e:
        print("recovery self-check: FAIL", e)
        sys.exit(1)
