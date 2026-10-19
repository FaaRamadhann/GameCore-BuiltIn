# Shizuku setup

Faa GameCore runs **without root**. Every privileged action (`pm disable-user`,
`pm enable`, `am force-stop`, `dumpsys activity`) goes through Shizuku, which runs
those commands as the `shell` uid (2000) — the same uid `adb shell` uses.

## 1. Install Shizuku

**Recommended: Shizuku Next** — versi 14.x dengan perbaikan start via wireless
debugging dan reliabilitas yang lebih baik di Android 12+:

- Rilis: <https://github.com/rushiranpise/Shizuku-Next/releases/latest>
- APK: `shizuku-v14.0.27-next.apk` (versi yang diuji di proyek ini: `14.0.26-next`)

Alternatif: Shizuku resmi dari
<https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api>
(atau <https://shizuku.rikka.app/download/>). Faa GameCore tetap kompatibel,
tapi start-on-boot dan wireless debugging di Android 12+ lebih stabil di Next.

## 2. Start Shizuku

Shizuku needs one of:

- **Wireless debugging** (Android 11+, recommended, survives reboot with "start on boot"): pair
  `adb pair <ip:port>` from developer options, then Shizuku → *Start via wireless debugging*.
- **ADB from a PC**: `adb shell sh /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh`
- **Root** if the device happens to have it (not required, not used by Faa GameCore).

## 3. Grant Faa GameCore permission

Open Faa GameCore → Welcome screen → **Buka Shizuku** → in Shizuku open *Authorized
applications* → enable **Faa GameCore**. Or press **Minta Izin Shizuku** in Faa GameCore; the
Shizuku dialog appears and the app refreshes automatically.

## 4. Start the daemon

Dashboard → **Start Daemon**. The foreground notification appears; the daemon polls the
foreground app every N seconds (Settings → poll).

## What works with Shizuku (shell uid)

| Operation | Command | Notes |
|---|---|---|
| Read foreground app | `dumpsys activity activities` | works on all tested ROMs |
| Force-stop app | `am force-stop --user 0 <pkg>` | user apps and system apps |
| Freeze app | `pm disable-user --user 0 <pkg>` | user-type apps only, journalled |
| Unfreeze app | `pm enable --user 0 <pkg>` / `cmd package enable` | verified against live state |
| List disabled | `pm list packages -d --user 0` | used as the honesty check |

## What does NOT work (non-root limits)

- No Zygisk/LSPosed hooks, no in-process game hooks.
- No kernel-level process kill; `am force-stop` is the strongest available action.
- Cannot read `/data/adb`, cannot install Magisk modules.
- Cannot change SELinux mode; Faa GameCore never tries.
- If a ROM blocks `pm disable-user` from the shell uid, Faa GameCore reports the failure and
  keeps the journal entry for retry — it never claims a freeze that did not happen.

## Recovery guarantees

- The freeze journal (`frozen`) is written **before** the disable command.
- Unfreeze runs at session end, on app start, and on boot when no session is active.
- Any package that cannot be proven enabled stays in the journal for the next retry.
