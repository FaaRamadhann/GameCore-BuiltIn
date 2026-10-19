# Faa GameCore — feature matrix

Legend: **OK** = implemented + at least one real check · **PARTIAL** = implemented,
only partly verified · **NO** = not possible non-root.

`Device` column: **yes** = exercised on the Redmi 6A · **host** = only host
self-check · **no** = no execution evidence.

| Area | Status | Device | Notes / evidence |
|---|---|---|---|
| Launcher icon (adaptive + legacy) | OK | yes | `build/icon-on-device.png`; `check_icons.py` PASS pada resource, APK build, dan APK terpasang (label `Faa GameCore`) |
| Game List (manual add) | OK | yes | `19-launcher-row.png`; package validated + must be installed |
| Game List (pick from installed apps) | OK | yes | `build/autodetect-test-last.log`: 31 baris + badge SYS, pencarian menyaring ke Mobile Legends |
| Per-game profile override | OK | yes (UI) | `21-launcher-menu.png` (0–4) |
| Profiles 1 Balance | PARTIAL | yes | log `SESSION_STARTED Profil Balance`; `Cleanup: tidak ada target` |
| Profiles 2 Performance | PARTIAL | no | `am force-stop` path; never exercised |
| Profiles 3 Awaken | OK | yes | dipakai di `adb_freeze_test.py` dan `adb_dryrun_test.py` (force-stop + `pm disable-user`) |
| Profiles 4 Auto | PARTIAL | no | battery mapping covered by `check_policy.py`; not on device |
| Blacklist / Whitelist | OK | yes (UI) | `09-lists.png`; whitelist wins; `check_policy.py` PASS |
| Freeze (reversible) | OK | yes | `build/freeze-test-last.log`: jurnal write-ahead + live `pm list packages -d` verification |
| Unfreeze | OK | yes | paket uji kembali enabled setelah sesi/recovery; `cmd package enable` fallback belum pernah dipakai |
| Stale-session recovery | OK | yes | `force-stop` → freeze bertahan → watchdog restore + `RECOVERY`; `check_recovery.py` PASS |
| Game detection | PARTIAL | yes (manual) | `dumpsys activity` / `dumpsys window`; auto-detect not exercised |
| Daemon (foreground service) | PARTIAL | no | `12-daemon.png` shows Stopped; start/stop not verified |
| Boot auto-start + recovery | OK | yes | `build/boot-test-last.log` (action debug `DEBUG_BOOT`; Redmi 6A tidak mencapai `boot_completed`) |
| Runtime logs | OK | yes | ring + file; `files/fgc-runtime.log` has real entries |
| Notifications | PARTIAL | yes | notifikasi daemon tampil saat sesi; channel overlay belum di-screenshot |
| Launch game from Game List | OK | yes | `20-after-launch.png`; no shell, no hardcoded activity |
| Launch feedback | OK | yes | `Launcher.verifyForeground()`; "berjalan" only when proven |
| Invalid game entry | PARTIAL | no | dialog offering removal from Game List; not exercised |
| Floating overlay | OK | yes | `build/overlay-test-last.log`: bubble `isVisible=true` di atas app lain, tap → panel, minimize → bubble, disable → service berhenti |
| Overlay behavior | OK | yes | start minimized + bubble→panel verified (`40/41/42-overlay-*.png`); auto-show belum |
| Overlay controls | PARTIAL | no | profile switch / LOGS / HOME / drag / clamp in code only (minimize sudah terbukti) |
| Overlay permission loss | PARTIAL | no | re-check on resume + each render; not exercised |
| Overlay over Settings | NO | — | Android 12 `HIDE_OVERLAY_WINDOWS` (`mForceHideNonSystemOverlayWindow=true`); platform, bukan bug |
| Dry run | OK | yes | `build/dryrun-test-last.log`: sesi jalan, jurnal kosong, tidak ada paket di-disable |
| Shizuku integration | OK | yes | `IShizukuService.newProcess` → `sh -c`; status "Running" |
| Package-state audit | OK | yes | read-only; `build/audit/`; 19 disabled packages not attributable to Faa GameCore |
| Screen capture / in-app overlay | PARTIAL | no | status panel only; no capture, no game hook |
| Accessibility service | NO | — | not used; overlay needs only `SYSTEM_ALERT_WINDOW` |
| Foreground app detection without Shizuku | NO | — | `getRunningTasks` restricted; falls back to unknown |
| Zygisk / LSPosed hooks | NO | — | requires root/zygote |
| Kernel-level kill | NO | — | `am force-stop` is the ceiling |
| SELinux mode change | NO | — | never attempted by design |
| `/data/adb` state (module) | NO | — | replaced by app-private storage |
| WebUI | NO | — | replaced by the native UI |
| Release signing | OK | yes | `keystore/` lokal (tidak dibagikan); `app-release.apk` signed, non-debuggable, lolos `build/release-check-last.log` |
