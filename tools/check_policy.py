#!/usr/bin/env python3
"""Self-check for the Faa GameCore policy rules that must never regress.

Runs on the host (no Android needed) and mirrors the Kotlin implementation in
app/src/main/java/com/faa/fgamecore/core/Policy.kt. Keep the two in sync.

    python tools/check_policy.py
"""
import sys

PROTECTED = {"android", "com.android.systemui", "com.android.phone", "com.android.shell"}


def resolve_profile(profile, battery):
    if profile in (1, 2, 3):
        return profile
    if profile == 4:
        if battery < 0:
            return 1
        if battery <= 20:
            return 1
        if battery <= 50:
            return 2
        return 3
    return 1


def targets(blacklist, whitelist, game, launcher="com.miui.home"):
    prot = PROTECTED | {launcher}
    return sorted(
        p for p in blacklist
        if p not in whitelist and p not in prot and p != game
    )


def unfreeze_candidates(journal, disabled, protected=PROTECTED, system=()):
    """Mirrors Policy.unfreezeAll: only journalled, currently disabled, non-system."""
    return sorted(
        p for p in journal
        if p not in protected and p not in system and p in disabled
    )


def check():
    # whitelist wins over blacklist
    assert targets({"com.a", "com.b"}, {"com.a"}, "com.game") == ["com.b"]
    # system packages are never targets, even if blacklisted
    assert targets({"android", "com.android.systemui"}, set(), None) == []
    # detected launcher is protected dynamically
    assert targets({"com.miui.home"}, set(), None) == []
    # foreground game is never a target
    assert targets({"com.game"}, set(), "com.game") == []
    # profile resolution: Auto follows battery, unknown battery -> Balance
    assert [resolve_profile(4, b) for b in (-1, 5, 20, 21, 50, 51, 100)] == [1, 1, 1, 2, 2, 3, 3]
    # explicit profiles pass through; unknown values fall back to Balance
    assert resolve_profile(3, 4) == 3
    assert resolve_profile(9, 99) == 1

    # Journal safety: only packages FGC recorded are ever re-enabled.
    assert unfreeze_candidates({"com.a"}, {"com.a"}) == ["com.a"]
    # A package the user disabled outside FGC is not in the journal -> untouched.
    assert unfreeze_candidates({"com.a"}, {"com.a", "com.user.disabled"}) == ["com.a"]
    # Corrupt journal entries for system/protected packages are skipped.
    assert unfreeze_candidates({"com.android.systemui", "android"}, {"com.android.systemui", "android"}) == []
    assert unfreeze_candidates({"com.oem.app"}, {"com.oem.app"}, system={"com.oem.app"}) == []
    # Already-enabled entries need no command.
    assert unfreeze_candidates({"com.a"}, set()) == []
    # Idempotent: after a verified restore the journal is empty, so is the work.
    assert unfreeze_candidates(set(), {"com.a"}) == []

    print("policy self-check: PASS")


if __name__ == "__main__":
    try:
        check()
    except AssertionError as e:
        print("policy self-check: FAIL", e)
        sys.exit(1)
