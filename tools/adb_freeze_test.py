#!/usr/bin/env python3
"""Controlled freeze/unfreeze test on ONE disposable user app.

Proves end-to-end, through the real UI:
  1. profile Awaken freezes a blacklisted app (journal + `pm list packages -d`)
  2. process death keeps the journal (the freeze survives the crash)
  3. recovery re-enables the app once the game is gone, without a human
  4. no other package changes state, and prefs return byte-identical

Refuses to start if the app already has a session or a journal. Only the
package passed on the command line is ever frozen.

    python tools/adb_freeze_test.py [package]

Report (stdout + stderr + exit code): build/freeze-test-last.log
"""
import html
import json
import os
import re
import sys
import time
import traceback

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import adb_smoke as S  # noqa: E402  (same adb/UI helpers as the smoke test)

PKG = S.PKG
GAME = "com.android.settings"
DISPOSABLE = "de.szalkowski.activitylauncher"
REPORT = os.path.join(S.ROOT, "build", "freeze-test-last.log")
RECOVERY_WAIT_S = 480  # recovery grace is 5 min; the watchdog ticks every 60 s
fails = []


def check(cond, msg):
    print(("  OK: " if cond else "  FAIL: ") + msg)
    if not cond:
        fails.append(msg)
    return cond


def journal():
    return S.pref_set(S.read_prefs(), "frozen")


def log_tail(n=400):
    return S.sh("shell", "run-as", PKG, "cat", "files/fgc-runtime.log")[-n:]


def home():
    S.sh("shell", "input", "keyevent", "KEYCODE_HOME")
    time.sleep(1.2)


def open_app():
    S.sh("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
    time.sleep(2.5)


def wait_journal(predicate, timeout):
    """Polls prefs; keeps the screen awake so the app process is not suspended."""
    end = time.time() + timeout
    while time.time() < end:
        S.sh("shell", "input", "keyevent", "KEYCODE_WAKEUP")
        if predicate(journal()):
            return True
        time.sleep(10)
    return predicate(journal())


def guards():
    if "uid=" not in S.sh("shell", "run-as", PKG, "id"):
        print("run-as gagal: butuh build debug dan app terpasang")
        sys.exit(1)
    xml = S.read_prefs()
    if S.pref_set(xml, "frozen") or re.search(r'name="sessionGame"', xml):
        print("app punya sesi/jurnal aktif; selesaikan dulu sebelum uji freeze")
        sys.exit(1)
    if S.pref_bool(xml, "dryRun") or not S.pref_bool(xml, "enabled", True):
        print("dryRun/enabled tidak siap untuk uji freeze")
        sys.exit(1)
    if DISPOSABLE in S.pref_set(xml, "whitelist"):
        print(f"{DISPOSABLE} ada di whitelist; tidak akan pernah dibekukan")
        sys.exit(1)
    if not S.sh("shell", "pm", "path", DISPOSABLE).strip():
        print(f"{DISPOSABLE} tidak terpasang")
        sys.exit(1)
    if "shizuku_server" not in S.sh("shell", "ps", "-A"):
        print("shizuku_server tidak berjalan")
        sys.exit(1)


def profile_of(pkg):
    """Per-game profile from the `games` JSON pref (0 = follow default)."""
    m = re.search(r'<string name="games">(.*?)</string>', S.read_prefs(), re.S)
    if not m:
        return 0
    for g in json.loads(html.unescape(m.group(1))):
        if g.get("package") == pkg:
            return int(g.get("profile", 0))
    return 0


def configure():
    """Adds the game with profile Awaken and blacklists the disposable app."""
    print("[2/6] configure game + blacklist")
    open_app()
    if not S.wait_for("Game List", 25):
        return False
    S.tap_text("Game List")
    S.tap_text("Tambah Game")
    if not S.tap_edit(1):
        return False
    S.type_text(GAME)
    time.sleep(0.6)
    S.hide_keyboard()
    S.tap_text("Simpan")
    S.tap_text("Game List")
    # uiautomator occasionally serves the previous screen; re-open and re-tap once
    # rather than silently starting the session on the default profile.
    for _ in range(2):
        S.tap_text(GAME)
        if not S.wait_for("Profil khusus game", 15):
            return False
        S.tap_text("Awaken (3)")
        S.sh("shell", "input", "keyevent", "KEYCODE_BACK")  # dismiss the game dialog
        time.sleep(1)
        if profile_of(GAME) == 3:
            break
    check(profile_of(GAME) == 3, "profil game = Awaken lewat UI")
    S.tap_text("Home")
    S.tap_text("Blacklist / Whitelist")
    S.tap_text("Blacklist")
    S.tap_text("Tambah Aplikasi")
    if not S.tap_edit(0):
        return False
    # Dots are typed as a keyevent and Gboard appends a space after each one, so a
    # full package name becomes "de. szalkowski…" and matches nothing. Search a
    # dot-free fragment instead; the full package text appears as a row.
    S.type_text(DISPOSABLE.split(".")[1])
    time.sleep(0.8)
    S.hide_keyboard()
    S.tap_text(DISPOSABLE)
    time.sleep(1)
    S.tap_text("Kembali")
    ok = DISPOSABLE in S.pref_set(S.read_prefs(), "blacklist")
    return check(ok, "blacklist tersimpan lewat UI")


def play_and_freeze():
    print("[3/6] PLAY -> Awaken freeze")
    S.tap_text("Game List")
    S.tap_text("PLAY")
    time.sleep(5)
    fg = S.foreground()
    print("  foreground:", fg)
    xml = S.read_prefs()
    check(S.pref_str(xml, "sessionGame") == GAME, "sesi tercatat untuk game")
    check(S.pref_int(xml, "sessionProfile") == 3, "profil sesi = 3 (Awaken)")
    services = S.sh("shell", "dumpsys", "activity", "services", PKG)
    check("FgcService" in services and "isForeground=true" in services,
          "daemon foreground service jalan untuk sesi")
    notes = S.sh("shell", "dumpsys", "notification", "--noredact")
    check("Faa GameCore Daemon aktif" in notes, "notifikasi daemon tampil")
    frozen = journal()
    check(DISPOSABLE in frozen, "jurnal mencatat paket sebelum/sesudah disable")
    check(DISPOSABLE in S.disabled_packages(), "paket benar-benar disabled (pm list -d)")
    check(("frozen: " + DISPOSABLE) in log_tail(), "log CLEANUP_SUCCESS frozen")
    return fg


def prove_log():
    print("[4/6] evidence screenshot")
    open_app()
    S.wait_for("Menu", 20)
    S.tap_text("Menu")
    S.tap_text("Runtime Logs")
    time.sleep(1)
    S.shot("30-freeze-log")
    S.tap_text("Kembali")


def crash_and_recover():
    print("[5/6] process death -> recovery")
    S.sh("shell", "am", "force-stop", PKG)
    time.sleep(1)
    check(DISPOSABLE in journal(), "jurnal bertahan setelah proses FGC dibunuh")
    check(DISPOSABLE in S.disabled_packages(), "paket masih disabled setelah crash")
    home()
    open_app()
    # The watchdog restores once the 5 min heartbeat grace expires.
    restored = wait_journal(lambda j: DISPOSABLE not in j, RECOVERY_WAIT_S)
    check(restored, "jurnal dikosongkan oleh recovery otomatis")
    check(DISPOSABLE not in S.disabled_packages(), "paket kembali enabled")
    check("RECOVERY" in log_tail(), "log RECOVERY tercatat")
    S.wait_for("Menu", 20)
    S.tap_text("Menu")
    S.tap_text("Runtime Logs")
    time.sleep(1)
    S.shot("31-recovery-log")


def main():
    global DISPOSABLE
    if len(sys.argv) > 1:
        DISPOSABLE = sys.argv[1]
    print("[1/6] guards")
    guards()
    before_disabled = S.disabled_packages()
    saved, before_xml = S.backup_state()
    try:
        if not configure():
            return 1
        play_and_freeze()
        prove_log()
        crash_and_recover()
    finally:
        print("[6/6] cleanup")
        S.restore_state(saved, before_xml)
        after = S.disabled_packages()
        check(after == before_disabled, "tidak ada package lain berubah state")
        check(not journal(), "jurnal bersih setelah restore")
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
