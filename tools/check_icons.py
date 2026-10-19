#!/usr/bin/env python3
"""Validates every launcher icon resource (host only, no device).

    python tools/check_icons.py [apk_path]

Checks the five densities, PNG decodability, the adaptive XML references, and
(if an APK is given) what aapt2 says the app actually ships.
"""
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
LEGACY = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
ADAPTIVE = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
fails = []


def check(cond, msg):
    print(("  OK: " if cond else "  FAIL: ") + msg)
    if not cond:
        fails.append(msg)
    return cond


def icons():
    for d, px in LEGACY.items():
        for name, size, mode in (
            ("ic_launcher.png", px, "RGBA"),
            ("ic_launcher_round.png", px, "RGBA"),
            ("ic_launcher_foreground.png", ADAPTIVE[d], "RGBA"),
        ):
            p = os.path.join(RES, f"mipmap-{d}", name)
            if not check(os.path.exists(p), f"{d}/{name} ada"):
                continue
            with Image.open(p) as im:
                check(im.size == (size, size), f"{d}/{name} = {im.size[0]}x{im.size[1]} (harus {size})")
                check(im.mode == mode, f"{d}/{name} mode {im.mode}")
                im.verify()

    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        p = os.path.join(RES, "mipmap-anydpi-v26", name)
        if not check(os.path.exists(p), f"anydpi-v26/{name} ada"):
            continue
        root = ET.parse(p).getroot()
        refs = [e.attrib.get("{http://schemas.android.com/apk/res/android}drawable") for e in root]
        for r in refs:
            kind, _, res = (r or "").lstrip("@").partition("/")
            base = os.path.join(RES, "drawable" if kind == "drawable" else "mipmap-anydpi-v26", res + ".xml")
            if kind == "mipmap":
                found = any(os.path.exists(os.path.join(RES, f"mipmap-{d}", res + ".png")) for d in LEGACY)
            else:
                found = os.path.exists(base)
            check(found, f"{name} -> {r} dapat di-resolve")
        check(len(refs) == 2, f"{name} punya background + foreground")


def apk(path):
    aapt = None
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or r"C:\AndroidSDK"
    bt = os.path.join(sdk, "build-tools")
    if os.path.isdir(bt):
        for v in sorted(os.listdir(bt), reverse=True):
            exe = os.path.join(bt, v, "aapt2.exe" if os.name == "nt" else "aapt2")
            if os.path.exists(exe):
                aapt = exe
                break
    if not aapt:
        print("  SKIP: aapt2 tidak ditemukan, badging APK tidak diperiksa")
        return
    out = subprocess.run([aapt, "dump", "badging", path], capture_output=True, text=True,
                         encoding="utf-8", errors="replace").stdout
    check("application-icon-640:" in out, "APK punya entry icon density 640")
    label = re.search(r"application-label:'([^']*)'", out)
    check(label and label.group(1) == "Faa GameCore", f"label aplikasi = {label.group(1) if label else '?'}")
    names = subprocess.run([aapt, "dump", "resources", path], capture_output=True, text=True,
                           encoding="utf-8", errors="replace").stdout
    if re.search(r"res/mipmap-mdpi(-v\d+)?/ic_launcher\.png", names) is None:
        # Release builds shorten resource file paths (res/BW.xml); the resource table
        # still carries the names, so check those plus the density entries in badging.
        print("  note: resource paths dipendekkan (release), cek lewat resource table")
        for n in ("ic_launcher", "ic_launcher_round", "ic_launcher_foreground"):
            check(re.search(rf"mipmap/{n}\b", names) is not None, f"APK membawa mipmap/{n}")
        densities = set(re.findall(r"application-icon-(\d+):", out)) - {"65534"}
        check(len(densities) >= 5, f"APK punya {len(densities)} density icon (>=5)")
        return
    for d in LEGACY:
        check(re.search(rf"res/mipmap-{d}(-v\d+)?/ic_launcher\.png", names) is not None,
              f"APK membawa mipmap-{d}/ic_launcher.png")
    check("mipmap-anydpi-v26/ic_launcher.xml" in names, "APK membawa adaptive ic_launcher.xml")
    for d in LEGACY:
        check(re.search(rf"res/mipmap-{d}(-v\d+)?/ic_launcher_round\.png", names) is not None,
              f"APK membawa mipmap-{d}/ic_launcher_round.png")


if __name__ == "__main__":
    print("[resources]")
    icons()
    if len(sys.argv) > 1:
        print("[apk]", sys.argv[1])
        apk(sys.argv[1])
    print("RESULT:", "PASS" if not fails else f"FAIL ({len(fails)})")
    sys.exit(1 if fails else 0)
