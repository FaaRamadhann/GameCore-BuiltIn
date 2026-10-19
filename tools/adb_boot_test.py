#!/usr/bin/env python3
"""Boot recovery test without a physical reboot.

    python tools/adb_boot_test.py

A full reboot is not usable on the Redmi 6A (it never reaches boot_completed;
only "soft restart" works), so this delivers BOOT_COMPLETED to the receiver via
`am broadcast` instead. That exercises the same BootReceiver -> bootRecovery path:
freeze survives process death, boot closes the session and re-enables the package.

Needs the debug build: src/debug/AndroidManifest.xml exports the receiver.

Report (stdout + stderr + exit code): build/boot-test-last.log
"""
import os
import sys
import time
import traceback

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import adb_smoke as S  # noqa: E402
import adb_freeze_test as F  # noqa: E402

REPORT = os.path.join(S.ROOT, "build", "boot-test-last.log")
BOOT_WAIT_S = 300
fails = []


def check(cond, msg):
    print(("  OK: " if cond else "  FAIL: ") + msg)
    if not cond:
        fails.append(msg)
    return cond


def wait_device(timeout=60):
    end = time.time() + timeout
    while time.time() < end:
        if S.sh("get-state").strip() == "device":
            return True
        time.sleep(2)
    return False


def boot_broadcast():
    """Runs the boot path. BOOT_COMPLETED itself is protected (adb cannot send it on
    Android 12+), so the debug manifest registers this test action instead.
    FLAG_INCLUDE_STOPPED_PACKAGES (0x20): right after force-stop the package is in
    the stopped state and the broadcast would otherwise be dropped."""
    return S.sh("shell", "am", "broadcast", "-a", "com.faa.fgamecore.action.DEBUG_BOOT",
                "-f", "0x20", "-n", f"{S.PKG}/.core.BootReceiver")


def main():
    print("[1/5] guards")
    F.guards()
    before_disabled = S.disabled_packages()
    saved, before_xml = S.backup_state()
    try:
        if not F.configure():
            return 1
        F.play_and_freeze()
        check(F.DISPOSABLE in S.disabled_packages(), "paket disabled sebelum boot")

        print("[2/5] simulasi BOOT_COMPLETED (tanpa reboot fisik)")
        S.sh("shell", "am", "force-stop", S.PKG)
        # A broadcast sent while the old process is still dying is dropped, which
        # looks like "boot did nothing". Wait for the process to be gone first.
        end = time.time() + 30
        while time.time() < end and S.sh("shell", "pidof", S.PKG).strip():
            time.sleep(1)
        log_before = len(F.log_tail(100_000))  # assert only on this run's lines

        print("[3/5] boot recovery")
        # Retry the broadcast: delivery to a just-stopped package is not guaranteed
        # even with INCLUDE_STOPPED_PACKAGES, and a dropped one looks like a hang.
        for attempt in range(3):
            out = boot_broadcast()
            if attempt == 0:
                check("Broadcast completed" in out, "sinyal boot terkirim ke receiver")
            end = time.time() + 30
            while time.time() < end:
                if not S.pref_str(S.read_prefs(), "sessionGame"):
                    break
                time.sleep(3)
            if not S.pref_str(S.read_prefs(), "sessionGame"):
                break
            print(f"  retry broadcast ({attempt + 2}/3)")
        end = time.time() + 90
        while time.time() < end and (
                S.pref_str(S.read_prefs(), "sessionGame")
                or F.DISPOSABLE in S.pref_set(S.read_prefs(), "frozen")):
            time.sleep(5)
        xml = S.read_prefs()
        check(not S.pref_str(xml, "sessionGame"), "sesi basi ditutup saat boot")
        check(F.DISPOSABLE not in S.pref_set(xml, "frozen"), "jurnal dikosongkan saat boot")
        check(F.DISPOSABLE not in S.disabled_packages(), "paket enabled kembali setelah boot")
        log = F.log_tail(100_000)[log_before:]  # this run only, not older lines
        check("Boot: menutup sesi lama" in log, "log boot tercatat")
        check("unfreeze selesai" in log, "log unfreeze tercatat")

        print("[4/5] auto-start daemon")
        check("Auto start" in log, "auto start daemon dicoba setelah boot")
    finally:
        print("[5/5] cleanup")
        # Keep the device log for evidence: restore_state overwrites the on-device file.
        with open(os.path.join(S.ROOT, "build", "boot-run-device.log"), "w", encoding="utf-8") as f:
            f.write(S.sh("shell", "run-as", S.PKG, "cat", "files/fgc-runtime.log"))
        S.restore_state(saved, before_xml)
        if F.DISPOSABLE in S.disabled_packages():
            S.sh("shell", "pm", "enable", "--user", "0", F.DISPOSABLE)
        check(S.disabled_packages() == before_disabled, "tidak ada package lain berubah state")
        check(not F.journal(), "jurnal bersih setelah restore")
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
