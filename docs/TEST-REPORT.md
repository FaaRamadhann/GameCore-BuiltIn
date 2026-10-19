# ADB test report

Device: Redmi 6A, Android 12 (SDK 32), 720x1440, single user.
Connection: USB ADB (`08c1da027d2a`).
Shizuku: `shizuku_server` running, app `moe.shizuku.privileged.api` installed.
App: `com.faa.fgamecore` 1.3.1 (131), minSdk 26 / targetSdk 34.

Hasil di bawah dipisahkan antara **bukti tersimpan** (screenshot/log/prefs),
**self-test host**, dan **belum diuji**. Tidak ada hasil yang diisi hanya karena
operasi "diminta".

## Test matrix

| # | Test | Result | Evidence |
|---|---|---|---|
| 1 | APK installs (`adb install -r -g`) | VERIFIED (2026-10-18) | `firstInstallTime=2026-10-18 20:12:12`, `lastUpdateTime=2026-10-18 22:30:49` |
| 2 | Cold start, splash → dashboard | VERIFIED | `build/shots/01-splash.png`, `05-dashboard.png` |
| 3 | Shizuku status detected as Running | VERIFIED | `05-dashboard.png`; `adb shell ps -A` → `shizuku_server` |
| 4 | Permission request flow | VERIFIED (UI) | `04-permission.png`; izin FGS/BOOT/NOTIFICATIONS granted di `dumpsys package` |
| 5 | All 16 screens render without crash | VERIFIED (2026-10-19) | `build/smoke-last.log` exit 0, 21 screenshot, crash none, proses hidup |
| 5b | Launcher icon (adaptive + legacy) | VERIFIED (2026-10-19) | `build/icon-on-device.png`; `check_icons.py` PASS pada APK build + APK terpasang |
| 6 | Add game (manual, invalid package rejected) | PARTIAL | `19-launcher-row.png` membuktikan penambahan valid; penolakan paket invalid belum diuji |
| 7 | Add game (auto-detect from installed apps) | VERIFIED (2026-10-19) | `build/autodetect-test-last.log`: 31 baris + badge SYS, pencarian menyaring ke Mobile Legends |
| 8 | Toggle game on/off persists after restart | UNVERIFIED | belum diuji |
| 9 | Profile selection persists | VERIFIED (2026-10-19) | `games[].profile == 3` setelah UI flow di `adb_freeze_test.py` |
| 10 | Blacklist add/remove | PARTIAL | `09-lists.png` (UI); penambahan lewat UI terbukti di `adb_freeze_test.py` |
| 11 | Whitelist add/remove | PARTIAL | `09-lists.png`; 3 whitelist default |
| 12 | Daemon start → foreground notification | VERIFIED (2026-10-19) | `FgcService isForeground=true`, notifikasi "Faa GameCore Daemon aktif" saat sesi |
| 13 | Daemon stop / restart | UNVERIFIED | belum diuji |
| 14 | Foreground game detection (real game) | PARTIAL | manual launch `com.android.settings` terverifikasi; deteksi otomatis belum |
| 15 | Freeze on Awaken profile | VERIFIED (2026-10-19) | `build/freeze-test-last.log`: paket disposable disabled, jurnal + `CLEANUP_SUCCESS` |
| 16 | Unfreeze on session end | VERIFIED (2026-10-19) | paket kembali enabled; 19 package lain tidak berubah |
| 17 | Unfreeze after force-stop of Faa GameCore (recovery) | VERIFIED (2026-10-19) | `force-stop` → jurnal + freeze bertahan → watchdog restore + `RECOVERY`; `check_recovery.py` PASS |
| 18 | Boot auto-start + recovery | VERIFIED (2026-10-19) | `build/boot-test-last.log`: action debug `DEBUG_BOOT` (Redmi 6A tidak mencapai `boot_completed`) → sesi ditutup, paket enabled, daemon start |
| 19 | Runtime log content | VERIFIED | `files/fgc-runtime.log` (GAME_DETECTED, SESSION_STARTED, Cleanup, LAUNCH, OVERLAY) |
| 20 | Rotation / small screen layout | UNVERIFIED | belum diuji |
| 21 | Floating overlay over another app | VERIFIED (2026-10-19) | `build/overlay-test-last.log`: `isVisible=true`, tap → panel, minimize → bubble, disable → stop |
| 22 | Dry run leaves packages untouched | VERIFIED (2026-10-19) | `build/dryrun-test-last.log`: jurnal kosong, tidak ada paket disabled |
| 23 | Signed release APK installs and runs | VERIFIED (2026-10-19) | `build/release-check-last.log`: non-debuggable, onboarding, 3 layar, cold start tanpa crash |
| 24 | Overlay over Settings | N/A (platform) | Android 12 `mForceHideNonSystemOverlayWindow=true`; bukan bug app |

## Game Launcher (2026-10-18) — VERIFIED

- `build/shots/19-launcher-row.png`: `com.android.settings` tersimpan, badge
  `Default`, tombol `PLAY`.
- `build/shots/20-after-launch.png`: UI Setelan Android terbuka setelah PLAY.
- Log device:
  ```
  22:13:53 [GAME_DETECTED] Game terdeteksi: Setelan (com.android.settings) [manual]
  22:13:53 [SESSION_STARTED] Profil Balance (profil 1)
  22:13:53 [SESSION] Cleanup: tidak ada target
  22:13:56 [LAUNCH] Setelan berjalan
  ```
- `build/shots/21-launcher-menu.png`: menu Buka Game / Profil khusus / Whitelist /
  Blacklist / Hapus.
- `Settings → GAME LAUNCHER` tampil dan aktif: `10-settings.png`.

## Floating Overlay (2026-10-19) — VERIFIED

`python tools\adb_overlay_test.py` → **EXIT_CODE=0, failures: none**
(`build/overlay-test-last.log`).

- Overlay diaktifkan dari Settings → `overlayEnabled=true`, log `Overlay tampil`.
- Di atas `de.szalkowski.activitylauncher`: window overlay `isVisible=true` —
  `build/shots/40-overlay-other-app.png` (bukan sekadar terdaftar di dumpsys).
- Tap bubble → panel (`overlayMinimized=false`) — `41-overlay-panel-other-app.png`.
- Tombol minimize → bubble (`overlayMinimized=true`) — `42-overlay-minimized.png`.
- Disable → `overlayEnabled=false`, service berhenti; prefs direstore byte-identik.
- Platform: overlay tidak bisa tampil di atas `com.android.settings` karena
  Android 12 memasang `mForceHideNonSystemOverlayWindow=true` untuk app dengan
  `HIDE_OVERLAY_WINDOWS`. Bukan bug app.
- Belum: auto-show saat game dibuka, ganti profil/LOGS/HOME dari panel,
  drag + simpan posisi, clamp rotasi, cabut izin saat aktif.
- Bukti lama 2026-10-18 (`ov1.png`/`ov2.png`) tetap valid untuk panel sendiri;
  `ov-1-bubble.png` / `ov-2-panel.png` rusak (UTF-16) — tidak dipakai.

## Dry run (2026-10-19) — VERIFIED

`python tools\adb_dryrun_test.py` → **EXIT_CODE=0, failures: none**
(`build/dryrun-test-last.log`, `50-dryrun-log.png`): sesi Awaken berjalan,
`frozen` kosong, paket uji tetap enabled, log `dry-run: profil tidak diterapkan`,
tidak ada package berubah state.

## Auto-detect (2026-10-19) — VERIFIED

`python tools\adb_autodetect_test.py` → **EXIT_CODE=0, failures: none**
(`build/autodetect-test-last.log`, `60/61-autodetect-*.png`): 31 baris aplikasi
terpasang + badge `SYS`, pencarian menyaring ke Mobile Legends, prefs tidak berubah.

## Signed release APK on device (2026-10-19) — VERIFIED

`python tools\adb_release_check.py` → **EXIT_CODE=0, failures: none**
(`build/release-check-last.log`, `70/71/72-release-*.png`).

- Uninstall debug → install `app-release.apk` (signing beda; wajib uninstall).
- `versionName=1.3.1`, **tidak** `DEBUGGABLE`.
- Onboarding 3 langkah + dialog Shizuku "Always allow"; dashboard, Game List,
  Settings terbuka; cold start ulang tanpa crash; proses tetap hidup.
- Device dikembalikan ke APK debug + prefs baseline + 19 package disabled.

## Audit package disabled (2026-10-19) — read-only

- 19 package disabled; 18 × `enabled=2` (DISABLED), 1 × `enabled=4`
  (DISABLED_UNTIL_USED: `com.android.inputmethod.latin`).
- Jurnal Faa GameCore kosong (tidak ada key `frozen`), blacklist kosong, log tanpa baris
  freeze. Faa GameCore hanya memakai `pm disable-user` (state 3); tidak ada package
  ber-state 3.
- Kesimpulan: **tidak ada bukti Faa GameCore menyebabkan 19 package itu disabled**; asal
  pasti tidak dapat dibuktikan tanpa akses root. Tidak ada package yang diubah.
- Detail: `build/audit/audit.txt`, `disabled-before.txt`, `disabled-state.csv`.

## Self-test host (2026-10-19)

```
python tools\check_policy.py    -> policy self-check: PASS
python tools\check_launcher.py  -> launcher self-check: PASS
python tools\check_recovery.py  -> recovery self-check: PASS
```

`check_recovery.py` mencakup process death, daemon mati, jurnal kosong, jurnal
valid, sesi basi, clock mundur, zombie daemon, dan idempotensi.

## Build (2026-10-19)

```
gradlew.bat :app:assembleDebug   -> BUILD SUCCESSFUL in 21s (35 up-to-date)
gradlew.bat :app:assembleRelease -> BUILD SUCCESSFUL
APK debug:   app\build\outputs\apk\debug\app-debug.apk (17.841.549 byte)
SHA-256:     9E2D43A05FC5EDC749F899D0A753043FD6EADEE83F1C0E896FD641CC54D27E07
APK release: app\build\outputs\apk\release\app-release.apk (11.718.715 byte)
Signer:      CN=Faa GameCore, O=Faa, C=ID
Cert SHA-256: 55c1fca45521b259c019c7ce5bada7fdc4b4acdc16bce78310fe80c8c41e8dd7
```

APK debug terpasang di device identik byte-per-byte dengan APK build.
`python tools\check_icons.py <apk>` → PASS untuk resource, APK debug, APK release,
dan APK terpasang. Keystore release lokal di `keystore/` (tidak dibagikan).

## Tidak dijalankan (sengaja)

- Smoke test destruktif lama tidak diulang. Versi baru (`tools/adb_smoke.py`,
  tanpa `pm clear`, backup/restore prefs + log) **sudah dijalankan 2026-10-19
  05:23**: `EXIT_CODE=0`, crash none, prefs restored byte-identical, tidak ada
  package disabled baru (`build/smoke-last.log`).
- Reboot fisik penuh: Redmi 6A tidak mencapai `sys.boot_completed=1`; jalur boot
  diuji lewat action debug `DEBUG_BOOT` (jalur kode sama).
- Tidak ada `pm enable` massal untuk 19 package di atas.
- Belum: rotasi layar, drag/simpan posisi bubble, cabut izin overlay saat aktif,
  auto-show overlay saat game dibuka, daemon stop/restart manual.

## Freeze + recovery di device (2026-10-19 05:55)

`python tools\adb_freeze_test.py` → **EXIT_CODE=0, failures: none**
(`build/freeze-test-last.log`; screenshot `30-freeze-log.png`, `31-recovery-log.png`).

- Paket uji `de.szalkowski.activitylauncher`: blacklist + profil Awaken lewat UI,
  PLAY → `sessionProfile=3`, daemon foreground + notifikasi, paket disabled,
  log `CLEANUP_SUCCESS frozen`.
- `am force-stop` Faa GameCore → jurnal + freeze bertahan; watchdog recovery
  meng-enable paket dan menulis `RECOVERY`.
- Cleanup: prefs byte-identik, 19 package disabled tidak berubah, jurnal bersih.

Tiga kegagalan awal murni bug harness (query picker kena autospace Gboard,
assert `sessionGame` memakai `value=` padahal prefs memakai elemen `<string>`,
tap profil kena dump UI basi); sudah diperbaiki di `tools/`.

## Boot recovery (2026-10-19 17:35)

`python tools\adb_boot_test.py` → **EXIT_CODE=0, failures: none**
(`build/boot-test-last.log`).

- Freeze sebelum boot → sinyal `com.faa.fgamecore.action.DEBUG_BOOT` (debug-only;
  release tetap unexported) → sesi ditutup, jurnal dikosongkan, paket enabled,
  log `Boot: menutup sesi lama` + `unfreeze selesai`, daemon auto-start.
- Bug nyata yang diperbaiki: `BootReceiver` mengembalikan hasil sebelum restore
  selesai (proses bisa dibunuh) → `goAsync()`; `bootRecovery()` tidak retry saat
  Shizuku belum siap → `suspend` + retry 45 dtk (5 dtk/iterasi).
