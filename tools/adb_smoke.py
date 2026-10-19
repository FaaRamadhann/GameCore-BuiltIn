#!/usr/bin/env python3
"""Non-destructive ADB smoke test for Faa GameCore.

Installs with -r (data kept), backs up prefs + runtime log, walks every screen,
screenshots each one, and fails on a crash. No `pm clear`, no freeze, no
`pm disable-user`: the freeze journal and the user's configuration survive.

The launcher test only runs when the saved profile is Balance (1) and the
blacklist is empty, so the test can never freeze anything.

    python tools/adb_smoke.py [apk_path]

Report (stdout + stderr + exit code): build/smoke-last.log
"""
import os
import re
import subprocess
import sys
import time
import traceback

PKG = "com.faa.fgamecore"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "build", "shots")
BACKUP = os.path.join(ROOT, "build", "smoke-backup")
REPORT = os.path.join(ROOT, "build", "smoke-last.log")
DEFAULT_APK = os.path.join(ROOT, "app", "build", "outputs", "apk", "debug", "app-debug.apk")
PNG = b"\x89PNG\r\n\x1a\n"
FILES = {"fgc.xml": "shared_prefs/fgc.xml", "fgc-runtime.log": "files/fgc-runtime.log"}
failures = []


class Tee:
    def __init__(self, path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        self.f = open(path, "w", encoding="utf-8")

    def write(self, s):
        self.f.write(s)
        sys.__stdout__.write(s)

    def flush(self):
        self.f.flush()
        sys.__stdout__.flush()

    def close(self):
        self.f.close()


def sh(*args):
    r = subprocess.run(["adb", *args], capture_output=True)
    return r.stdout.decode("utf-8", "replace")


def shb(*args):
    return subprocess.run(["adb", *args], capture_output=True).stdout


def shot(name):
    os.makedirs(OUT, exist_ok=True)
    data = shb("exec-out", "screencap", "-p")
    if not data.startswith(PNG):
        failures.append(f"screenshot tidak valid: {name}")
        print("  BAD SHOT:", name)
        return
    with open(os.path.join(OUT, name + ".png"), "wb") as f:
        f.write(data)
    print("  shot:", name)


def ui():
    # uiautomator can return the previous dump; delete first so a stale file never
    # masquerades as the current screen.
    for _ in range(4):
        sh("shell", "rm", "-f", "/sdcard/ui.xml")
        r = sh("shell", "uiautomator", "dump", "/sdcard/ui.xml")
        xml = sh("shell", "cat", "/sdcard/ui.xml")
        if "<hierarchy" in xml and ("dumped to" in r or xml):
            return xml
        time.sleep(0.8)
    return ""


def find(text):
    for node in re.findall(r"<node[^>]*>", ui()):
        if f'text="{text}"' in node or f'content-desc="{text}"' in node:
            b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
            if b:
                x1, y1, x2, y2 = map(int, b.groups())
                return (x1 + x2) // 2, (y1 + y2) // 2
    return None


def tap_text(text, wait=1.2, required=True):
    pos = find(text)
    if not pos:
        if required:
            failures.append(f"tombol tidak ditemukan: {text}")
            print("  MISSING:", text)
        return False
    sh("shell", "input", "tap", str(pos[0]), str(pos[1]))
    time.sleep(wait)
    return True


def tap_switch(label):
    """Taps the Switch on the same row as [label]; the row text itself is not clickable."""
    nodes = re.findall(r"<node[^>]*>", ui())
    anchor = None
    for n in nodes:
        if f'text="{label}"' in n:
            b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
            if b:
                _, y1, _, y2 = map(int, b.groups())
                anchor = (y1 + y2) // 2
                break
    if anchor is None:
        failures.append(f"baris toggle tidak ditemukan: {label}")
        print("  MISSING SWITCH ROW:", label)
        return False
    for n in nodes:
        # Compose renders Switch as android.view.View with checkable="true".
        if 'checkable="true"' not in n:
            continue
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        if not b:
            continue
        x1, y1, x2, y2 = map(int, b.groups())
        if abs((y1 + y2) // 2 - anchor) < 40:
            sh("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
            time.sleep(1.5)
            return True
    failures.append(f"switch tidak ditemukan: {label}")
    print("  MISSING SWITCH:", label)
    return False


def tap_edit(index=0):
    """Tap the Nth EditText by its real bounds; placeholder text is not a hit target."""
    nodes = [n for n in re.findall(r"<node[^>]*>", ui()) if 'class="android.widget.EditText"' in n]
    if index >= len(nodes):
        failures.append(f"field input #{index} tidak ditemukan")
        return False
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', nodes[index])
    if not b:
        failures.append(f"field input #{index} tanpa bounds")
        return False
    x1, y1, x2, y2 = map(int, b.groups())
    sh("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(0.6)
    return True


def scroll_until(text, tries=6):
    for _ in range(tries):
        if has(text):
            return True
        swipe_up(320)
    return has(text)


def hide_keyboard():
    """A visible IME covers buttons below the field; Back closes it."""
    sh("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(0.8)


def type_text(text):
    """`input text` drops characters around dots on this ROM; send segment by segment."""
    for i, part in enumerate(text.split(".")):
        if i:
            sh("shell", "input", "keyevent", "KEYCODE_PERIOD")
        if part:
            sh("shell", "input", "text", part)
        time.sleep(0.15)


def swipe_up(pixels=900):
    sh("shell", "input", "swipe", "360", "1150", "360", str(1150 - pixels), "300")
    time.sleep(0.8)


def has(text):
    return f'text="{text}"' in ui()


def wait_for(text, timeout=20):
    for _ in range(int(timeout / 0.5)):
        if has(text):
            return True
        time.sleep(0.5)
    failures.append(f"layar tidak muncul: {text}")
    print("  TIMEOUT:", text)
    return False


def settle(*texts, timeout=25):
    """Waits for the first of several possible screens (fresh vs onboarded)."""
    for _ in range(int(timeout / 0.5)):
        xml = ui()
        for t in texts:
            if f'text="{t}"' in xml:
                return t
        time.sleep(0.5)
    return None


def foreground():
    out = sh("shell", "dumpsys", "activity", "activities")
    m = re.search(r"(?:topResumedActivity|mResumedActivity).*?([A-Za-z0-9_.]+)/", out)
    return m.group(1) if m else None


def disabled_packages():
    return set(re.findall(r"package:(\S+)", sh("shell", "pm", "list", "packages", "-d", "--user", "0")))


def overlay_allowed():
    return "SYSTEM_ALERT_WINDOW: allow" in sh("shell", "appops", "get", PKG, "SYSTEM_ALERT_WINDOW")


def overlay_window():
    out = sh("shell", "dumpsys", "window", "windows")
    for block in out.split("Window #"):
        if PKG in block and "APPLICATION_OVERLAY" in block:
            return True
    return False


# --- user state -------------------------------------------------------------

def read_prefs():
    return sh("shell", "run-as", PKG, "cat", "shared_prefs/fgc.xml")


def pref_bool(xml, key, default=False):
    m = re.search(rf'<boolean name="{key}" value="(true|false)"', xml)
    return default if not m else m.group(1) == "true"


def pref_int(xml, key, default=0):
    m = re.search(rf'<int name="{key}" value="(-?\d+)"', xml)
    return default if not m else int(m.group(1))


def pref_str(xml, key, default=""):
    m = re.search(rf'<string name="{key}">(.*?)</string>', xml) or \
        re.search(rf'<string name="{key}" value="(.*?)"', xml)
    return default if not m else m.group(1)


def pref_set(xml, key):
    m = re.search(rf'<set name="{key}">(.*?)</set>', xml, re.S)
    return set(re.findall(r"<string>(.*?)</string>", m.group(1))) if m else set()


def backup_state():
    """Returns (bytes per file, prefs text). Aborts before touching anything if run-as fails."""
    if "uid=" not in sh("shell", "run-as", PKG, "id"):
        print("run-as gagal: butuh build debug dan app terpasang")
        sys.exit(1)
    os.makedirs(BACKUP, exist_ok=True)
    saved = {}
    for name, rel in FILES.items():
        data = shb("exec-out", "run-as", PKG, "cat", rel)
        with open(os.path.join(BACKUP, name), "wb") as f:
            f.write(data)
        saved[name] = (rel, len(data))
    xml = read_prefs()
    print("  backup:", {k: v[1] for k, v in saved.items()})
    return saved, xml


def restore_state(saved, before_xml):
    """Stops the app first so SharedPreferences cannot overwrite the restore."""
    sh("shell", "am", "force-stop", PKG)
    time.sleep(1)
    for name, (rel, size) in saved.items():
        tmp = "/data/local/tmp/fgc-smoke-restore"
        if size == 0:
            sh("shell", "run-as", PKG, "rm", "-f", rel)
            continue
        sh("push", os.path.join(BACKUP, name), tmp)
        r = subprocess.run(
            ["adb", "shell", "run-as", PKG, "cp", tmp, rel], capture_output=True
        )
        sh("shell", "rm", "-f", tmp)
        if r.returncode != 0:
            failures.append(f"gagal restore {rel}: {r.stderr.decode('utf-8', 'replace').strip()}")
    after = read_prefs()
    if after != before_xml:
        failures.append("prefs tidak kembali persis seperti sebelum test")
        print("  RESTORE MISMATCH")
    else:
        print("  prefs restored byte-identical")


def launch_is_safe():
    xml = read_prefs()
    profile = pref_int(xml, "profile", 1)
    black = pref_set(xml, "blacklist")
    games = re.search(r'<string name="games">(.*?)</string>', xml, re.S)
    if games and "com.android.settings" in games.group(1):
        return False, "com.android.settings sudah ada di Game List pengguna"
    if profile != 1 or black:
        return False, f"profil={profile}, blacklist={sorted(black)}"
    return True, "profil Balance, blacklist kosong"


# --- tests ------------------------------------------------------------------

def launch_test():
    """Adds a real installed app, launches it from Game List, then removes it."""
    print("[5c/7] game launcher")
    safe, why = launch_is_safe()
    if not safe:
        print("  SKIP: test launch tidak aman tanpa freeze ->", why)
        return
    tap_text("Home")
    tap_text("Game List")
    tap_text("Tambah Game")
    # Placeholder text is not a hit target; tap the real EditText bounds.
    if not tap_edit(1):
        return
    type_text("com.android.settings")
    time.sleep(0.8)
    hide_keyboard()
    tap_text("Simpan")
    time.sleep(2)
    # onSaved returns to the dashboard, so navigate back to the list before asserting.
    tap_text("Game List")
    time.sleep(1.5)
    if not has("com.android.settings"):
        failures.append("game tidak tersimpan lewat form manual")
        print("  ADD FAILED")
        return
    shot("19-launcher-row")
    if not has("PLAY"):
        failures.append("tombol PLAY tidak muncul untuk game terdaftar")
        print("  MISSING: PLAY")
    tap_text("PLAY")
    time.sleep(4)
    fg = foreground()
    shot("20-after-launch")
    print("  foreground:", fg)
    if fg != "com.android.settings":
        failures.append(f"launch tidak membuka aplikasi (foreground={fg})")
    sh("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
    time.sleep(2)
    tap_text("Game List")
    time.sleep(1.5)
    tap_text("com.android.settings")
    time.sleep(1.2)
    shot("21-launcher-menu")
    for label in ("Buka Game", "Profil khusus game", "Whitelist (tidak pernah dibekukan)", "Hapus dari Game List"):
        if not has(label):
            failures.append(f"menu game hilang: {label}")
            print("  MISSING:", label)
    tap_text("Hapus dari Game List")
    time.sleep(1.5)


def overlay_test():
    print("[5b/7] overlay settings")
    scroll_until("FLOATING OVERLAY")
    scroll_until("Auto-show on game launch")
    shot("17-overlay-settings")
    for label in ("FLOATING OVERLAY", "Enable overlay", "Start minimized", "Auto-show on game launch"):
        if not has(label):
            failures.append(f"baris overlay hilang: {label}")
            print("  MISSING:", label)
    allowed = overlay_allowed()
    print("  overlay permission:", allowed)
    if not allowed:
        tap_text("Izin overlay", required=False)
        if has("Buka Pengaturan"):
            shot("18-overlay-permission-dialog")
            tap_text("Batal")
        else:
            failures.append("dialog penjelasan izin overlay tidak muncul")
        return
    if pref_bool(read_prefs(), "overlayEnabled"):
        print("  overlay sudah aktif milik pengguna; tidak diubah")
        return
    # Permission exists, so tapping the toggle must start the overlay, not show a dialog.
    if tap_switch("Enable overlay"):
        time.sleep(2.5)
        if overlay_window():
            shot("18-overlay-active")
        else:
            failures.append("overlay tidak muncul walau izin sudah diberikan")
        tap_text("Sembunyikan", required=False)
        time.sleep(1)


def main(apk):
    if not os.path.exists(apk):
        print("APK tidak ada: " + apk)
        sys.exit(1)

    print("[1/7] install (data dipertahankan)")
    print(" ", sh("install", "-r", "-g", apk).strip().splitlines()[-1])

    saved, before_xml = backup_state()
    before_disabled = disabled_packages()

    try:
        print("[2/7] cold start")
        sh("logcat", "-c")
        sh("shell", "am", "force-stop", PKG)
        sh("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
        time.sleep(3)
        shot("01-splash")

        print("[3/7] onboarding")
        first = settle("Cara Menggunakan", "Game List")
        if first == "Cara Menggunakan":
            shot("02-welcome")
            tap_text("Cara Menggunakan")
            wait_for("Panduan Shizuku")
            shot("03-guide")
            tap_text("Kembali")
            wait_for("Cara Menggunakan")
            tap_text("Lanjutkan", required=False) or tap_text("Lanjutkan tanpa Shizuku")
            wait_for("Izin Akses")
            shot("04-permission")
            tap_text("Lanjutkan")
        else:
            print("  sudah onboarded, langkah onboarding dilewati")
        wait_for("Game List")
        shot("05-dashboard")

        print("[4/7] dashboard tiles")
        tap_text("Game List"); shot("06-games")
        tap_text("Tambah Game"); shot("07-add")
        tap_text("Kembali")
        tap_text("Home")
        tap_text("Profiles"); shot("08-profiles"); tap_text("Kembali")
        tap_text("Home")
        tap_text("Blacklist / Whitelist"); shot("09-lists"); tap_text("Kembali")
        tap_text("Home")

        print("[5/7] settings + sub-screens")
        tap_text("Menu"); shot("10-settings")
        if not has("Launch game langsung"):
            failures.append("bagian Game Launcher tidak ada di Settings")
        tap_text("Profiles"); shot("11-profiles-settings"); tap_text("Kembali")
        tap_text("Daemon"); shot("12-daemon"); tap_text("Kembali")
        tap_text("Runtime Logs"); shot("13-logs"); tap_text("Kembali")
        tap_text("Notifikasi"); shot("14-notifications"); tap_text("Kembali")
        tap_text("Tentang"); shot("15-about"); tap_text("Kembali")
        tap_text("Shizuku"); shot("16-guide-settings"); tap_text("Kembali")

        overlay_test()
        launch_test()

        print("[6/7] crash scan")
        log = sh("logcat", "-d")
        idx = [i for i, l in enumerate(log.splitlines()) if "FATAL EXCEPTION" in l or "Force finishing" in l]
        crashes = [log.splitlines()[i] for i in idx if PKG in log[max(0, i - 3000):]]
        print("  crashes:", crashes if crashes else "none")
        if crashes:
            failures.append(f"{len(crashes)} crash(es)")

        print("[7/7] result")
        alive = bool(sh("shell", "pidof", PKG).strip())
        print("  process alive:", alive)
        if not alive:
            failures.append("proses mati")
    finally:
        print("[cleanup] restore prefs + log")
        restore_state(saved, before_xml)
        after_disabled = disabled_packages()
        new_disabled = sorted(after_disabled - before_disabled)
        print("  package disabled baru:", new_disabled if new_disabled else "tidak ada")
        if new_disabled:
            # Never auto-enable: a new disabled package needs a human decision.
            failures.append(f"package disabled baru (tidak diaktifkan otomatis): {new_disabled}")

    print("  failures:", failures if failures else "none")
    return 1 if failures else 0


if __name__ == "__main__":
    tee = Tee(REPORT)
    sys.stdout = tee
    sys.stderr = tee
    code = 2
    try:
        code = main(sys.argv[1] if len(sys.argv) > 1 else DEFAULT_APK)
    except SystemExit as e:
        code = int(e.code or 0)
    except Exception:
        traceback.print_exc()
    finally:
        print(f"EXIT_CODE={code}")
        print(f"REPORT={REPORT}")
        sys.stdout = sys.__stdout__
        sys.stderr = sys.__stderr__
        tee.close()
    sys.exit(code)
