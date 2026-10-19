#!/usr/bin/env python3
"""Verifies an installed release APK with no run-as (release is not debuggable).

    python tools/adb_release_check.py

Checks signature/flags, cold start, one screenshot per main screen, crash scan.

Report: build/release-check-last.log
"""
import os
import re
import sys
import time
import traceback

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import adb_smoke as S  # noqa: E402

REPORT = os.path.join(S.ROOT, "build", "release-check-last.log")
fails = []


def check(cond, msg):
    print(("  OK: " if cond else "  FAIL: ") + msg)
    if not cond:
        fails.append(msg)
    return cond


def main():
    print("[1/5] paket terpasang")
    path = S.sh("shell", "pm", "path", S.PKG).strip()
    check(path.startswith("package:"), "APK terpasang")
    dump = S.sh("shell", "dumpsys", "package", S.PKG)
    check("versionName=1.3.1" in dump, "versionName 1.3.1")
    check("flags=[" in dump and "DEBUGGABLE" not in dump.split("flags=[")[1].split("]")[0],
          "release tidak debuggable")

    print("[2/5] cold start")
    S.sh("shell", "am", "force-stop", S.PKG)
    time.sleep(1)
    S.sh("shell", "am", "start", "-n", f"{S.PKG}/.MainActivity")
    time.sleep(3)
    check(S.PKG in S.foreground(), f"foreground = {S.foreground()}")
    # Fresh install has no data: the Shizuku gate + permission screens come first.
    # Later runs are already onboarded, so every step here is optional.
    S.tap_text("Lanjutkan tanpa Shizuku", required=False)
    time.sleep(1.5)
    S.tap_text("Lanjutkan", required=False)
    time.sleep(2)
    S.tap_text("Oke", required=False)  # optional success screen
    time.sleep(1.5)
    # "Lanjutkan" triggers ShizukuBridge.requestPermission(): grant it so the system
    # dialog does not sit on top of every later tap.
    S.tap_text("Always allow", required=False)
    time.sleep(2)
    S.shot("70-release-dashboard")

    print("[3/5] layar utama")
    for label, shot in (("Game List", "71-release-games"), ("Settings", "72-release-settings")):
        check(S.tap_text(label, required=False), f"tombol {label} ada")
        time.sleep(1)
        S.shot(shot)
        S.sh("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(1)
    check("Home" in S.ui() or "Game List" in S.ui(), "kembali ke dashboard")

    print("[4/5] crash scan")
    S.sh("logcat", "-b", "crash", "-c")
    S.sh("shell", "am", "force-stop", S.PKG)
    time.sleep(1)
    S.sh("shell", "am", "start", "-n", f"{S.PKG}/.MainActivity")
    time.sleep(3)
    crashes = S.sh("logcat", "-b", "crash", "-d")
    check(S.PKG not in crashes, "tidak ada crash setelah cold start ulang")

    print("[5/5] proses hidup")
    check(bool(S.sh("shell", "pidof", S.PKG).strip()), "proses masih hidup")
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
