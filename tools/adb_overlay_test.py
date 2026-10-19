#!/usr/bin/env python3
"""Overlay over a foreign app (no session, no freeze).

    python tools/adb_overlay_test.py [target_package]

Target must NOT request HIDE_OVERLAY_WINDOWS: Android 12 hides every non-system
overlay over such apps (com.android.settings does), and the overlay window then
reports mForceHideNonSystemOverlayWindow=true / isVisible=false. That is platform
behaviour, not a bug in Faa GameCore.

Proves: enable -> bubble visible over another app -> tap expands panel ->
minimize returns to bubble -> disable stops the service.

Report: build/overlay-test-last.log
"""
import os
import re
import sys
import time
import traceback

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import adb_smoke as S  # noqa: E402

REPORT = os.path.join(S.ROOT, "build", "overlay-test-last.log")
TARGET = "de.szalkowski.activitylauncher"
DENSITY = 1.6125  # 258 dpi on the Redmi 6A
fails = []


def check(cond, msg):
    print(("  OK: " if cond else "  FAIL: ") + msg)
    if not cond:
        fails.append(msg)
    return cond


def dp(v):
    return int(v * DENSITY)


def log():
    return S.sh("shell", "run-as", S.PKG, "cat", "files/fgc-runtime.log")


def overlay_window():
    """The overlay window block from dumpsys (one per attached view)."""
    for block in S.sh("shell", "dumpsys", "window", "windows").split("Window #"):
        if "package=" + S.PKG in block and "appop=SYSTEM_ALERT_WINDOW" in block:
            return block
    return ""


def reveal(text, max_swipes=6):
    """Scrolls until `find(text)` succeeds. wait_for() can miss a row while the
    list is still flinging; find() after a settle is reliable."""
    for _ in range(max_swipes):
        if S.find(text):
            return True
        S.swipe_up(250)
        time.sleep(1.2)
    return S.find(text) is not None


def open_settings_screen():
    # Fresh process: a resumed app keeps its scroll position, so the toggle could
    # already be scrolled past and never appear in the dump.
    S.sh("shell", "am", "force-stop", S.PKG)
    time.sleep(1)
    S.sh("shell", "am", "start", "-n", f"{S.PKG}/.MainActivity")
    time.sleep(2.5)
    S.wait_for("Settings", 20)
    S.tap_text("Settings")
    time.sleep(1.5)
    return reveal("Enable overlay")


def main():
    global TARGET
    if len(sys.argv) > 1:
        TARGET = sys.argv[1]
    print("[1/6] guards")
    if "uid=" not in S.sh("shell", "run-as", S.PKG, "id"):
        print("run-as gagal")
        return 1
    if "allow" not in S.sh("shell", "appops", "get", S.PKG, "SYSTEM_ALERT_WINDOW"):
        print("izin overlay tidak ada")
        return 1
    if S.pref_str(S.read_prefs(), "sessionGame"):
        print("ada sesi aktif; overlay test butuh tanpa sesi")
        return 1
    saved, before = S.backup_state()
    try:
        # Normalize: a leftover ON from an earlier run would make the first toggle
        # turn the overlay OFF and every later assert would be inverted.
        if S.pref_bool(S.read_prefs(), "overlayEnabled"):
            if not open_settings_screen():
                return 1
            S.tap_switch("Enable overlay")
            time.sleep(2)
            if S.pref_bool(S.read_prefs(), "overlayEnabled"):
                print("gagal mematikan overlay sisa run sebelumnya")
                return 1
        print("  state awal: overlay mati")

        print("[2/6] enable overlay")
        if not open_settings_screen():
            return 1
        mark = len(log())
        check(S.tap_switch("Enable overlay"), "toggle Enable overlay ditekan")
        time.sleep(2.5)
        check(S.pref_bool(S.read_prefs(), "overlayEnabled"), "overlayEnabled=true")
        check("Overlay tampil" in log()[mark:], "log overlay tampil")

        print("[3/6] bubble di atas app lain")
        S.sh("shell", "monkey", "-p", TARGET, "-c", "android.intent.category.LAUNCHER", "1")
        time.sleep(3)
        check(TARGET in S.foreground(), f"foreground = {S.foreground()}")
        w = overlay_window()
        check("isVisible=true" in w, "window overlay benar-benar terlihat")
        check("mForceHideNonSystemOverlayWindow=true" not in w, "target tidak menyembunyikan overlay")
        S.shot("40-overlay-other-app")

        print("[4/6] tap bubble -> panel")
        x = S.pref_int(S.read_prefs(), "overlayX", 19) + dp(28)
        y = S.pref_int(S.read_prefs(), "overlayY", 193) + dp(28)
        S.sh("shell", "input", "tap", str(x), str(y))
        time.sleep(2)
        # expand()/minimize() do not log; the pref is the observable state.
        check(not S.pref_bool(S.read_prefs(), "overlayMinimized"), "panel terbuka setelah tap bubble")
        check(re.search(r"Requested w=\d+", overlay_window()) is not None, "window overlay masih ada")
        S.shot("41-overlay-panel-other-app")

        print("[5/6] minimize -> bubble")
        px = S.pref_int(S.read_prefs(), "overlayX", 19)
        py = S.pref_int(S.read_prefs(), "overlayY", 193)
        # Header: 232dp wide, 6dp right padding, 34dp buttons; minimize sits before ×.
        S.sh("shell", "input", "tap", str(px + dp(175)), str(py + dp(25)))
        time.sleep(2)
        check(S.pref_bool(S.read_prefs(), "overlayMinimized"), "kembali ke bubble")
        S.shot("42-overlay-minimized")

        print("[6/6] disable overlay")
        check(open_settings_screen(), "layar Settings dengan toggle overlay")
        check(S.tap_switch("Enable overlay"), "toggle Enable overlay ditekan")
        time.sleep(2.5)
        check(not S.pref_bool(S.read_prefs(), "overlayEnabled"), "overlayEnabled=false")
        check("OverlayService" not in S.sh("shell", "dumpsys", "activity", "services", S.PKG),
              "service overlay berhenti")
    finally:
        S.restore_state(saved, before)
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
