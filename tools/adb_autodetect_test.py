#!/usr/bin/env python3
"""Auto-detect tab: installed apps are listed, searchable, system apps badged.

    python tools/adb_autodetect_test.py

Read-only UI walk: nothing is saved to the Game List.

Report: build/autodetect-test-last.log
"""
import os
import re
import sys
import time
import traceback

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import adb_smoke as S  # noqa: E402

REPORT = os.path.join(S.ROOT, "build", "autodetect-test-last.log")
GAME = "com.mobile.legends"
fails = []


def check(cond, msg):
    print(("  OK: " if cond else "  FAIL: ") + msg)
    if not cond:
        fails.append(msg)
    return cond


def main():
    print("[1/3] buka Auto Detect")
    if "uid=" not in S.sh("shell", "run-as", S.PKG, "id"):
        print("run-as gagal")
        return 1
    before = S.read_prefs()
    S.sh("shell", "am", "start", "-n", f"{S.PKG}/.MainActivity")
    time.sleep(2.5)
    S.wait_for("Game List", 20)
    S.tap_text("Game List")
    S.tap_text("Tambah Game")
    check(S.tap_text("Auto Detect", required=False), "tab Auto Detect ada")
    time.sleep(1)
    S.shot("60-autodetect-list")

    print("[2/3] cari aplikasi terpasang")
    xml = S.ui()
    # LazyColumn only renders visible rows; the list is alphabetical, so assert on
    # populated rows + SYS badges, then use the search field to reach one app.
    rows = re.findall(r'text="([^"]*)" resource-id="" class="android.widget.TextView"', xml)
    check(len(rows) >= 5, f"daftar aplikasi terisi ({len(rows)} baris)")
    check('text="SYS"' in xml, "badge SYS tampil untuk aplikasi sistem")
    S.tap_edit(0)
    S.type_text("Mobile")
    time.sleep(1.2)
    S.hide_keyboard()
    time.sleep(1)
    xml = S.ui()
    check(GAME in xml, "pencarian menyaring ke Mobile Legends")
    S.shot("61-autodetect-search")

    print("[3/3] kembali tanpa menyimpan")
    S.sh("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(1)
    S.sh("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(1)
    check(S.read_prefs() == before, "prefs tidak berubah (tidak ada game tersimpan)")
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
