#!/usr/bin/env python3
"""Dry-run test: a session with dry-run on must touch no package.

    python tools/adb_dryrun_test.py

Report: build/dryrun-test-last.log
"""
import os
import sys
import time
import traceback

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import adb_smoke as S  # noqa: E402
import adb_freeze_test as F  # noqa: E402

REPORT = os.path.join(S.ROOT, "build", "dryrun-test-last.log")
fails = []


def check(cond, msg):
    print(("  OK: " if cond else "  FAIL: ") + msg)
    if not cond:
        fails.append(msg)
    return cond


def reveal(text, max_swipes=6):
    """Scrolls until `find(text)` succeeds; wait_for() can miss a row mid-fling."""
    for _ in range(max_swipes):
        if S.find(text):
            return True
        S.swipe_up(250)
        time.sleep(1.2)
    return S.find(text) is not None


def set_dry_run(on):
    S.sh("shell", "am", "force-stop", S.PKG)  # resumed scroll position hides the row
    time.sleep(1)
    S.sh("shell", "am", "start", "-n", f"{S.PKG}/.MainActivity")
    time.sleep(2.5)
    S.wait_for("Settings", 20)
    S.tap_text("Settings")
    time.sleep(1.5)
    if not reveal("Dry run"):
        return False
    S.tap_switch("Dry run")
    time.sleep(1.5)
    return S.pref_bool(S.read_prefs(), "dryRun") == on


def main():
    print("[1/5] guards")
    if "uid=" not in S.sh("shell", "run-as", S.PKG, "id"):
        print("run-as gagal")
        return 1
    if not S.sh("shell", "pm", "path", F.DISPOSABLE).strip():
        print(f"{F.DISPOSABLE} tidak terpasang")
        return 1
    saved, before = S.backup_state()
    before_disabled = S.disabled_packages()
    try:
        print("[2/5] aktifkan dry run")
        # Hard gate: without dry run the session would freeze for real.
        if not check(set_dry_run(True), "dryRun=true"):
            return 1
        check(F.configure(), "game + blacklist siap")

        print("[3/5] PLAY dengan dry run")
        S.tap_text("Game List")
        S.tap_text("PLAY")
        time.sleep(6)
        xml = S.read_prefs()
        check(S.pref_int(xml, "sessionProfile") == 3, "profil sesi = 3 (Awaken)")
        check(not S.pref_set(xml, "frozen"), "jurnal tetap kosong")
        check(F.DISPOSABLE not in S.disabled_packages(), "paket tidak di-disable")
        check("dry-run: profil tidak diterapkan" in F.log_tail(4000), "log dry-run tercatat")
        check(S.disabled_packages() == before_disabled, "tidak ada package berubah state")
        S.sh("shell", "am", "force-stop", S.PKG)
        time.sleep(1)
        S.sh("shell", "am", "start", "-n", f"{S.PKG}/.MainActivity")
        time.sleep(2.5)
        S.wait_for("Menu", 20)
        S.tap_text("Menu")
        S.tap_text("Runtime Logs")
        time.sleep(1)
        S.shot("50-dryrun-log")

        print("[4/5] sesi ditutup")
    finally:
        print("[5/5] cleanup")
        # A dry-run failure must never leave the disposable app frozen.
        if F.DISPOSABLE in S.disabled_packages():
            S.sh("shell", "pm", "enable", "--user", "0", F.DISPOSABLE)
        S.restore_state(saved, before)
        check(S.disabled_packages() == before_disabled, "state akhir tidak berubah")
    return 1 if (fails or S.failures) else 0


if __name__ == "__main__":
    tee = S.Tee(REPORT)
    sys.stdout = tee
    sys.stderr = tee
    code = 2
    try:
        code = main()
    except SystemExit as e:
        code = int(e.code or 0)
    except Exception:
        traceback.print_exc()
    finally:
        print("failures:", fails + S.failures if (fails or S.failures) else "none")
        print(f"EXIT_CODE={code}")
        print(f"REPORT={REPORT}")
        sys.stdout = sys.__stdout__
        sys.stderr = sys.__stderr__
        tee.close()
    sys.exit(code)
