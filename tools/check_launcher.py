#!/usr/bin/env python3
"""Self-check for the game-launcher rules (host, no device).

Mirrors Launcher.kt: a package name must be valid before it is handed to the
system, and a missing app must never be treated as a successful launch.

    python tools/check_launcher.py
"""
import re
import sys

PKG_RE = re.compile(r"^[A-Za-z0-9_]+(\.[A-Za-z0-9_]+)+$")


def result(installed, has_launcher, valid=True):
    """Same decision order as Launcher.start()."""
    if not valid:
        return "failed"
    if not installed:
        return "not-installed"
    if not has_launcher:
        return "no-launcher"
    return "started"


def check():
    assert result(False, False) == "not-installed"
    assert result(True, False) == "no-launcher"
    assert result(True, True) == "started"
    assert result(True, True, valid=False) == "failed"
    # Only "started" may be reported as a launch; everything else must surface.
    assert result(False, True) != "started"
    assert result(True, False) != "started"
    # Package validation matches Policy.validPkg
    assert PKG_RE.match("com.mobile.legends")
    assert not PKG_RE.match("com/mobile")
    assert not PKG_RE.match("")
    assert not PKG_RE.match("com.mobile.legends; rm -rf /")
    print("launcher self-check: PASS")


if __name__ == "__main__":
    try:
        check()
    except AssertionError as e:
        print("launcher self-check: FAIL", e)
        sys.exit(1)
