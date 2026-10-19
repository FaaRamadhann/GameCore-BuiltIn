# Faa GameCore — Progress

Terakhir diperbarui: 2026-10-19 (rebranding Faa GameCore + launcher icon dari artwork;
seluruh uji device selesai: freeze, recovery, boot, overlay, dry-run, auto-detect,
APK release signed — semua exit 0)
Workspace: `D:\GitHub-Project\FGC-Standalone-BuiltIn` (bukan git repo; `src-reference/` = modul Faa GameCore asli, tidak disentuh)

Legenda status:
- **VERIFIED** — dibuktikan langsung (build log, adb, screenshot, file log/prefs).
- **UNVERIFIED** — ada di source code tapi belum ada bukti eksekusi.
- **PARTIAL** — sebagian jalur terbukti, sebagian belum.
- **FAIL / RISK** — ada bukti kegagalan atau celah yang berpotensi merusak state.

---

## 0. Audit package disabled (2026-10-19, read-only)

Bukti lengkap: `build/audit/audit.txt`, `build/audit/disabled-before.txt`,
`build/audit/disabled-state.csv`. Tidak ada `pm clear`, `pm disable-user`, freeze,
atau perubahan state perangkat pada audit ini.

### Daftar aktual (19 package, `pm list packages -d --user 0`)

| Package | enabled | Arti |
|---|---|---|
| com.github.anrimian.musicplayer | 2 | DISABLED |
| com.rhmsoft.edit | 2 | DISABLED |
| com.android.messaging | 2 | DISABLED |
| com.discord | 2 | DISABLED |
| com.android.contacts | 2 | DISABLED |
| com.google.android.gm | 2 | DISABLED |
| com.NGLFACTORY.KIZUNAPLAYER | 2 | DISABLED |
| com.faa.faaccapp | 2 | DISABLED |
| com.faa.faadlmod | 2 | DISABLED |
| com.google.android.apps.docs | 2 | DISABLED |
| com.google.android.apps.maps | 2 | DISABLED |
| com.faa.faf | 2 | DISABLED |
| com.android.inputmethod.latin | 4 | DISABLED_UNTIL_USED |
| com.gojek.gopay | 2 | DISABLED |
| com.termux | 2 | DISABLED |
| com.android.calculator2 | 2 | DISABLED |
| io.github.vvb2060.keyattestation | 2 | DISABLED |
| id.dana | 2 | DISABLED |
| com.drhowdydoo.diskinfo | 2 | DISABLED |

### Bukti jurnal & log Faa GameCore

- `shared_prefs/fgc.xml`: **tidak ada key `frozen`**, tidak ada key `blacklist`,
  `games=[]`. Hanya ada `onboarded=true`, `overlayAutoShow=true`,
  `overlayEnabled=false`, `sessionGame=com.android.settings` (sesi basi),
  `sessionProfile=1`, `sessionStarted=1792336433848`.
- `files/fgc-runtime.log` (6 baris): hanya `GAME_DETECTED`, `SESSION_STARTED`,
  `Cleanup: tidak ada target`, `LAUNCH`, dua baris `OVERLAY`. **Tidak ada**
  `CLEANUP_SUCCESS`, `CLEANUP_FAILED`, atau perintah disable.
- `com.android.settings` — satu-satunya game yang pernah ditambahkan — `enabled=0`
  (DEFAULT/enabled), bukan disabled.
- Satu user saja (`UserInfo{0:ItsFaa_}`), tidak ada work profile.

### Atribusi

- Satu-satunya jalur freeze Faa GameCore adalah `pm disable-user --user 0 <pkg>`
  (`Policy.kt:134`; modul asli juga `pm disable-user`, `process_manager.sh:96`).
  Perintah itu menghasilkan state **3 = DISABLED_USER**.
- Tidak ada satu pun dari 19 package ber-state 3: 18 ber-state 2 (DISABLED) dan
  1 ber-state 4 (DISABLED_UNTIL_USED).
- Journal kosong + blacklist kosong + log tanpa freeze ⇒ **tidak ada bukti Faa GameCore
  menyebabkan 19 package itu disabled**. Pola state 2/4 konsisten dengan debloat
  manual/OEM, tetapi asal pastinya **tidak dapat dibuktikan** dari device non-root
  (`/data/system/users/0/package-restrictions.xml` = permission denied).
- **Tidak ada package yang diaktifkan.** Menunggu konfirmasi pemilik device
  sebelum menyentuh state ini.

---

## 1. Fitur yang sudah diimplementasikan

| Area | Status | Bukti / catatan |
|---|---|---|
| Launcher icon (adaptive + legacy) dari artwork | VERIFIED | `build/icon-on-device.png`; `check_icons.py` PASS pada resource, APK build, dan APK terpasang |
| Onboarding + panduan Shizuku + layar izin | VERIFIED | `build/shots/02-welcome.png` … `04-permission.png` |
| Dashboard (status Shizuku/Daemon, baterai, foreground) | VERIFIED | `05-dashboard.png` (Shizuku: Running, Daemon: Stopped, Baterai 100%) |
| Game List manual (validasi paket, harus terpasang) | VERIFIED | `19-launcher-row.png`; `com.android.settings` tersimpan via form |
| Auto-detect aplikasi terpasang + pencarian + badge sistem | VERIFIED | `build/autodetect-test-last.log` (exit 0): 31 baris + badge SYS, pencarian menyaring ke Mobile Legends |
| Profil 1 Balance / 2 Performance / 3 Awaken / 4 Auto | PARTIAL (eksekusi) | profil 3 (Awaken) dipakai di uji freeze & dry run; 1/2/4 belum dieksekusi |
| Profil khusus per game (0–4) | VERIFIED (UI) | `21-launcher-menu.png` |
| Blacklist / Whitelist (whitelist menang, paket sistem dilindungi) | VERIFIED (UI + host check) | `09-lists.png`; `check_policy.py` PASS |
| Freeze reversibel + jurnal write-ahead + verifikasi `pm list packages -d` | VERIFIED | `build/freeze-test-last.log` (2026-10-19 05:55): paket uji benar-benar disabled, jurnal ditulis sebelum disable |
| Unfreeze + fallback `cmd package enable` + retry | VERIFIED | paket uji kembali enabled; jalur `cmd package enable` belum pernah dibutuhkan |
| **Recovery sesi basi (process death / daemon mati)** | VERIFIED | `force-stop` → jurnal + freeze bertahan → watchdog meng-enable paket + `RECOVERY` di log; `check_recovery.py` PASS |
| Deteksi game foreground (`dumpsys activity`, fallback `dumpsys window`) | PARTIAL | terverifikasi saat launch manual; deteksi otomatis belum |
| Daemon foreground service (poll interval, start/stop/restart) | PARTIAL | auto-start saat sesi terbukti (`FgcService isForeground=true`, notifikasi tampil); start/stop manual belum diuji |
| Notifikasi (daemon, overlay, deteksi game) | UNVERIFIED | channel & builder ada di `Notifier.kt`; belum ada screenshot notifikasi |
| Runtime log (ring + file, tab Runtime/Daemon, hapus) | VERIFIED | `13-logs.png` + `files/fgc-runtime.log` terisi setelah uji launch |
| Boot auto-start + recovery | VERIFIED | `build/boot-test-last.log` (2026-10-19): sinyal boot → sesi ditutup, paket enabled, daemon auto-start. Redmi 6A tidak mencapai `boot_completed` pada reboot biasa, jadi dipakai action debug (jalur kode sama) |
| Game Launcher (PLAY → launcher intent, tanpa shell/hardcoded activity) | VERIFIED | `19-launcher-row.png`, `20-after-launch.png`, log `LAUNCH Setelan berjalan` |
| Umpan balik launch jujur (verified / "belum terlihat di foreground") | VERIFIED | `Launcher.verifyForeground()`; teks "berjalan" hanya saat terbukti |
| Entri game tidak terpasang → dialog hapus dari Game List | UNVERIFIED | ada di `Games.kt`; tidak ada screenshot dialog |
| Floating Overlay (panel + bubble, draggable, foreground service, default off) | VERIFIED | `40/41/42-overlay-*.png`; bubble terlihat di atas app lain, tap → panel, minimize → bubble, disable → service berhenti |
| Overlay: start minimized, expand via tap bubble | VERIFIED | `42-overlay-minimized.png`; `overlayMinimized` berpindah sesuai aksi |
| Overlay: auto-show saat game dibuka | PARTIAL | `overlayAutoShow=true`; belum diuji (butuh sesi game) |
| Overlay: ganti profil / LOGS / HOME / minimize / posisi / rotasi | PARTIAL | minimize terbukti; ganti profil, LOGS, HOME, drag, rotasi belum |
| Overlay: cek ulang izin saat resume / tiap render | UNVERIFIED (jalur cabut izin) | kode ada; belum dieksekusi |
| Overlay di atas Setelan | TIDAK MUNGKIN (platform) | Android 12 `mForceHideNonSystemOverlayWindow=true` pada app yang meminta `HIDE_OVERLAY_WINDOWS`; bukan bug app |
| Dry run | VERIFIED | `build/dryrun-test-last.log` (exit 0): sesi Awaken jalan, jurnal kosong, paket tidak di-disable, log `dry-run: profil tidak diterapkan` |
| Shizuku bridge (`IShizukuService.newProcess` → `sh -c`) | VERIFIED (status) | Dashboard "Shizuku: Running"; `shizuku_server` aktif |
| Smoke test ADB non-destruktif | VERIFIED | `build/smoke-last.log` (2026-10-19 05:23): exit 0, 21 screenshot, crash none, prefs byte-identik, tidak ada package disabled baru |
| APK release signed (non-debuggable) | VERIFIED | `build/release-check-last.log` (exit 0): install bersih, onboarding, dashboard/Game List/Settings, cold start tanpa crash |

---

## 2. File penting yang ditambahkan / diubah

Rebranding + ikon (2026-10-19):
- `tools/make_icon.py` — generate seluruh ukuran dari artwork sumber.
- `mipmap-*/ic_launcher.png` (48/72/96/144/192), `ic_launcher_round.png`
  (alpha circle), `ic_launcher_foreground.png` (108–432, full-bleed 108dp).
- `mipmap-anydpi-v26/ic_launcher{,_round}.xml` — foreground menunjuk bitmap
  artwork (sebelumnya vector placeholder yang tidak pernah dipakai).
- `drawable/ic_launcher_foreground.xml` **dihapus** (placeholder F/F lama).
- `tools/check_icons.py` **(baru)** — validasi 5 densitas (ukuran/mode/decodable),
  resolusi referensi adaptive XML, dan isi APK via `aapt2` (termasuk label).
- Bukti device: `build/icon-on-device.png` (ikon bulat di launcher) dan
  `build/icon-preview.png` (mask adaptive vs legacy 48px).
- `AndroidManifest.xml` — `roundIcon` ke `@mipmap/ic_launcher_round`;
  `LOCKED_BOOT_COMPLETED` dibuang (prefs ada di credential-encrypted storage).
- `MainActivity.kt` + `ui/AppRoot.kt` — LOGS dari overlay tetap membuka Runtime
  Logs walau app sudah hidup (`onNewIntent`).
- Nama tampilan: `Faa GameCore` di onboarding, dashboard, settings, about,
  notifikasi, channel, overlay panel/bubble, docs, dan tool.
- `tools/adb_freeze_test.py` **(baru)** — uji freeze/unfreeze terkontrol.
  Diperbaiki 2026-10-19: pencarian picker memakai fragmen tanpa titik (Gboard
  menyisipkan spasi setelah `.`), `pref_str()` untuk prefs berbentuk elemen
  `<string>`, retry tap profil sampai `games[].profile == 3`, `wait_for("Menu")`
  setelah cold start.

Perbaikan 2026-10-19 (audit + recovery + smoke test):
- `core/Recovery.kt` **(baru)** — decision table murni: kapan sesi dianggap basi
  (foreground, heartbeat, umur sesi) tanpa panggilan Android.
- `core/Fgc.kt` — `reconcile()`, `watchdog()` 60 dtk, `bootRecovery()`; heartbeat
  `lastPollAt` di `poll()`; daemon auto-start saat sesi dimulai.
- `core/Policy.kt` — `unfreezeAll(ctx, …)`: hanya package di jurnal, lewati
  paket sistem/protected, verifikasi live sebelum menghapus jurnal.
- `core/Store.kt` — `lastPollAt` (heartbeat).
- `core/BootReceiver.kt` — boot selalu menutup sesi lama lalu recovery.
  Diperbaiki 2026-10-19: `goAsync()` supaya proses tidak dibunuh sebelum restore
  selesai, dan `Fgc.bootRecovery()` kini retry 45 dtk (interval 5 dtk) karena
  Shizuku belum tentu siap saat `BOOT_COMPLETED`. Sebelumnya paket bisa tetap
  ter-freeze setelah reboot bila enable gagal.
- `tools/check_recovery.py` **(baru)** — 13 skenario: process death, daemon mati,
  jurnal kosong/valid, sesi basi, clock mundur, zombie daemon, idempotensi.
- `tools/check_policy.py` — tambah self-check keamanan jurnal unfreeze.
- `tools/adb_smoke.py` — tanpa `pm clear`; backup/restore prefs + log; onboarding
  kondisional; cabang izin overlay; gate profil Balance; screenshot divalidasi;
  laporan stdout/stderr/exit code; deteksi package disabled baru.
- `docs/TEST-REPORT.md`, `docs/FEATURES.md`, `docs/BUILD.md`, `PROGRESS.md`.

Fitur sebelumnya (tidak berubah pada audit ini):
- `core/Launcher.kt` — launcher intent + verifikasi foreground.
- `core/OverlayService.kt` — panel/bubble, drag, clamp, izin, auto-close.
- `ui/screens/Games.kt` — PLAY, dialog game, auto-show overlay.
- `ui/screens/Settings.kt` — Game Launcher + Floating Overlay + dialog izin.
- `AndroidManifest.xml` — `SYSTEM_ALERT_WINDOW`, service overlay, boot receiver,
  `ShizukuProvider`.

---

## 3. Status build & APK

- Perintah: `set ANDROID_HOME=C:\AndroidSDK && gradlew.bat :app:assembleDebug`
- Build terakhir (2026-10-19, incremental setelah patch): **BUILD SUCCESSFUL**,
  21s, `35 actionable tasks: 35 up-to-date` (tidak ada source berubah sejak
  build 17:17:07; APK debug 17.841.549 byte).
- APK: `app\build\outputs\apk\debug\app-debug.apk`
  - 17.841.549 byte, 2026-10-19 17:17:07
  - SHA-256 `DFAD80BCDA4F0F576779B63F10C866BA9C6FD09649B56596247D1C85AFA473CA`
  - APK terpasang di device (di-pull saat pemeriksaan) **byte-identik** dengan APK
    build: 17.841.549 byte, SHA-256 sama.
- `check_icons.py` PASS untuk resource, APK build, dan APK terpasang:
  5 densitas legacy/round/foreground, adaptive XML resolve, label `Faa GameCore`.
- Warning (kosmetik, bukan error): `TYPE_PHONE` deprecated (`OverlayService.kt:416`),
  `Icons.Rounded.OpenInNew` (`Onboarding.kt:151`, `:287`),
  `LocalLifecycleOwner` (`Settings.kt:195`).
- Release APK sudah ada (2026-10-19): `app\build\outputs\apk\release\app-release.apk`
  11.718.715 byte, SHA-256 `78312B040C6C1BE87DB8D328713A03F4DC6F0E331EF8861FEF201E7776858D0B`,
  signed `CN=Faa GameCore` (cert SHA-256 `55c1fca4…8dd7`).
  Keystore lokal di `keystore/` (tidak untuk dibagikan). Belum dipublikasikan.
- `tools/check_icons.py` PASS untuk APK debug **dan** release (release memendekkan
  path resource menjadi `res/*.xml`, jadi validasi memakai resource table).
- `git status` → **fatal: not a git repository**; tidak ada commit/push.

---

## 4. Hasil pengujian

### 4a. Self-test host (2026-10-19, dijalankan ulang setelah patch)

```
python tools\check_policy.py    -> policy self-check: PASS
python tools\check_launcher.py  -> launcher self-check: PASS
python tools\check_recovery.py  -> recovery self-check: PASS
python tools\check_icons.py <apk> -> icon self-check: PASS (resource + APK build + APK terpasang)
python -m py_compile tools\*.py -> OK
```

`check_recovery.py` membuktikan: jurnal kosong → tidak ada aksi; jurnal orphan →
restore; game di foreground → sesi dipertahankan; game keluar foreground → restore;
heartbeat basi / foreground tak terbaca → restore; sesi > 12 jam → restore; jam
mundur tidak dipercaya; keputusan idempotent.

Simulasi dengan data device asli (sesi basi `com.android.settings`, jurnal kosong,
heartbeat 0) → **restore** (sesi hantu akan dibersihkan saat app dibuka).

### 4b. Pemeriksaan ADB read-only (2026-10-19)

- Device: Redmi 6A, Android 12 (SDK 32), 720x1440, USB `08c1da027d2a`.
- App: `com.faa.fgamecore` 1.3.1 (131), minSdk 26 / targetSdk 34.
- `SYSTEM_ALERT_WINDOW`: appop `allow`; izin FGS/BOOT/NOTIFICATIONS granted.
- `shizuku_server` berjalan; `logcat -b crash` kosong.
- `dumpsys activity services com.faa.fgamecore` → **(nothing)** (daemon/overlay mati).
- Sesi basi di prefs: `sessionGame=com.android.settings`, `sessionStarted` lama,
  `frozen` tidak ada. Ini akan ditutup oleh recovery baru saat app dijalankan.
- Ikon: `check_icons.py` PASS pada APK terpasang; `build/icon-on-device.png`
  menunjukkan ikon artwork ter-mask bulat di launcher (bukan placeholder F/F).
- Smoke test baru **dijalankan** 2026-10-19 05:23 (`build/smoke-last.log`):
  `EXIT_CODE=0`, 21 screenshot, prefs/log direstore byte-identik,
  `package disabled baru: tidak ada`, crash: none.

### 4b2. Uji freeze/unfreeze + recovery di device (2026-10-19 05:55)

`python tools\adb_freeze_test.py` → **EXIT_CODE=0, failures: none**
(`build/freeze-test-last.log`, screenshot `build/shots/30-freeze-log.png`,
`31-recovery-log.png`). Paket uji: `de.szalkowski.activitylauncher` (disposable).

| Langkah | Bukti |
|---|---|
| Blacklist + profil Awaken lewat UI | `games[].profile == 3`; blacklist tersimpan |
| PLAY → sesi | `sessionGame=com.android.settings`, `sessionProfile=3`, foreground = Setelan |
| Daemon | `FgcService … isForeground=true`; notifikasi "Faa GameCore Daemon aktif" |
| Freeze | jurnal berisi paket, `pm list packages -d` memuatnya, log `CLEANUP_SUCCESS frozen` |
| Process death | `force-stop` → jurnal + freeze tetap ada |
| Recovery | watchdog mengosongkan jurnal, paket enabled kembali, log `RECOVERY` |
| Cleanup | prefs byte-identik, 19 package disabled **tidak berubah**, jurnal bersih |

Catatan: dua run sebelumnya gagal karena bug harness (bukan app) — query picker
`de.szalkowski` jadi `de. szalkowski` (Gboard), assert `sessionGame` memakai
`value=` padahal prefs memakai elemen `<string>`, dan tap profil kena dump UI basi.
Ketiganya sudah diperbaiki di `tools/`.

### 4b3. Uji boot recovery (2026-10-19 17:35)

`python tools\adb_boot_test.py` → **EXIT_CODE=0, failures: none**
(`build/boot-test-last.log`).

- Redmi 6A ini **tidak mencapai `sys.boot_completed=1`** setelah reboot normal
  (hanya "Mulai Ulang Lunak"), dan `BOOT_COMPLETED` adalah protected broadcast
  sehingga `adb shell am broadcast` ditolak. Uji memakai action debug
  `com.faa.fgamecore.action.DEBUG_BOOT` yang hanya teregistrasi di build debug
  (`app/src/debug/AndroidManifest.xml`); release tetap unexported + boot-only.
- Hasil: freeze sebelum boot → sinyal boot → sesi ditutup, jurnal dikosongkan,
  paket enabled, log `Boot: menutup sesi lama` + `unfreeze selesai`, daemon
  auto-start.
- Bug nyata yang ditemukan & diperbaiki: `BootReceiver` mengembalikan hasil
  sebelum restore selesai (proses bisa dibunuh) dan `bootRecovery` tidak retry
  saat Shizuku belum siap → paket bisa tetap ter-freeze setelah reboot. Kini
  `goAsync()` + retry 45 dtk (5 dtk/iterasi).

### 4b4. Uji floating overlay di atas app lain (2026-10-19 18:44)

`python tools\adb_overlay_test.py` → **EXIT_CODE=0, failures: none**
(`build/overlay-test-last.log`).

| Langkah | Bukti |
|---|---|
| Enable dari Settings | `overlayEnabled=true`, log `Overlay tampil` |
| Bubble di atas `de.szalkowski.activitylauncher` | window `isVisible=true`, `40-overlay-other-app.png` |
| Tap bubble | panel terbuka (`overlayMinimized=false`), `41-overlay-panel-other-app.png` |
| Minimize | kembali ke bubble (`overlayMinimized=true`), `42-overlay-minimized.png` |
| Disable | `overlayEnabled=false`, service berhenti |
| Cleanup | prefs byte-identik, 19 package disabled tidak berubah |

Catatan platform: overlay **tidak bisa** tampil di atas `com.android.settings` —
Android 12 memasang `mForceHideNonSystemOverlayWindow=true` untuk app yang meminta
`HIDE_OVERLAY_WINDOWS` (anti-tapjacking). Bukan bug app; uji memakai app biasa.

Dua bug harness diperbaiki: overlay sisa run sebelumnya membuat tap toggle
mematikan (kini state dinormalkan di guard), dan assert memakai log padahal
`expand()`/`minimize()` tidak menulis log (kini memakai pref `overlayMinimized`).

### 4b5. Uji dry run (2026-10-19 18:47)

`python tools\adb_dryrun_test.py` → **EXIT_CODE=0, failures: none**
(`build/dryrun-test-last.log`, `50-dryrun-log.png`).

- Sesi Awaken berjalan (`sessionProfile=3`), tapi `frozen` kosong dan paket uji
  tetap enabled; log `dry-run: profil tidak diterapkan untuk …`.
- Tidak ada package berubah state; prefs direstore byte-identik.
- Harness: toggle "Dry run" perlu scroll (`reveal()`), dan uji kini berhenti keras
  kalau `dryRun` gagal aktif supaya tidak mem-freeze sungguhan.

### 4b6. Uji auto-detect (2026-10-19 18:49)

`python tools\adb_autodetect_test.py` → **EXIT_CODE=0, failures: none**
(`build/autodetect-test-last.log`, `60/61-autodetect-*.png`).

- Tab Auto Detect menampilkan 31 baris aplikasi terpasang + badge `SYS`.
- Pencarian "Mobile" menyaring ke Mobile Legends.
- Kembali tanpa menyimpan: prefs tidak berubah.

### 4b7. APK release di device (2026-10-19 18:50)

`python tools\adb_release_check.py` → **EXIT_CODE=0, failures: none**
(`build/release-check-last.log`, `70/71/72-release-*.png`).

- Uninstall debug → install `app-release.apk` (signing berbeda, wajib uninstall).
- `versionName=1.3.1`, **tidak** `DEBUGGABLE`.
- Onboarding 3 langkah (gate Shizuku → Izin Akses → Oke) + dialog Shizuku
  "Always allow" dilewati; dashboard, Game List, Settings terbuka.
- Cold start ulang: tidak ada crash; proses tetap hidup.
- Device dikembalikan ke APK debug + prefs baseline (373 B) + 19 package disabled.

### 4c. Bukti historis (2026-10-18, sebelum patch)

21 screenshot di `build/shots` (22:09–22:14) + log device:
onboarding → dashboard → game list → add → profiles → lists → settings →
sub-screens → overlay settings → dialog izin → launcher row → after launch →
menu game. Exit code smoke test lama **tidak tersimpan**.

### 4d. Tidak dijalankan (sengaja)

- Reboot fisik penuh: Redmi 6A tidak mencapai `sys.boot_completed=1`; jalur boot
  diuji lewat action debug `DEBUG_BOOT` (lihat 4b3).
- Tidak ada uji cabut izin overlay saat overlay aktif, drag/simpan posisi bubble,
  atau rotasi layar (kode ada, belum dieksekusi).
- Tidak ada package yang di-enable/disable selama audit read-only; uji freeze
  memakai satu paket disposable dan mengembalikannya.

---

## 5. Bukti Game Launcher, Floating Overlay & ikon

### Launcher icon — VERIFIED (2026-10-19)
1. Sumber tunggal: artwork JPG di root workspace (736×736); `tools/make_icon.py`
   menghasilkan 5 densitas legacy, round (alpha circle), dan foreground adaptive.
2. `tools/check_icons.py` PASS: ukuran 48/72/96/144/192 dan 108/162/216/324/432,
   mode RGBA, PNG decodable, kedua adaptive XML me-resolve background + foreground.
3. APK build dan APK terpasang sama byte-per-byte; `aapt2` melihat label
   `Faa GameCore`, `application-icon-640`, kelima `ic_launcher{,_round}.png`,
   dan `mipmap-anydpi-v26/ic_launcher.xml`.
4. `build/icon-on-device.png` — ikon artwork ter-mask bulat di launcher device.

### Game Launcher — VERIFIED (2026-10-18)
1. `com.android.settings` ditambahkan manual, tampil dengan badge `Default` dan
   tombol `PLAY` — `build/shots/19-launcher-row.png`.
2. PLAY ditekan → UI Setelan Android terbuka di foreground —
   `build/shots/20-after-launch.png` (22:13).
3. Log: `GAME_DETECTED … [manual]` → `SESSION_STARTED Profil Balance` →
   `SESSION Cleanup: tidak ada target` → `LAUNCH Setelan berjalan`.
   "berjalan" hanya muncul bila `Launcher.verifyForeground()` = true.
4. Menu game lengkap — `21-launcher-menu.png`.
5. `Settings → GAME LAUNCHER → Launch game langsung` aktif — `10-settings.png`.

### Floating Overlay — VERIFIED (2026-10-19)
`python tools\adb_overlay_test.py` → **EXIT_CODE=0, failures: none**
(`build/overlay-test-last.log`).

1. Overlay diaktifkan dari Settings → `overlayEnabled=true`, log `Overlay tampil`.
2. Di atas `de.szalkowski.activitylauncher`: window overlay `isVisible=true`
   (bukan hanya terdaftar) — `build/shots/40-overlay-other-app.png`.
3. Tap bubble → panel (`overlayMinimized=false`) — `41-overlay-panel-other-app.png`.
4. Tombol minimize → bubble (`overlayMinimized=true`) — `42-overlay-minimized.png`.
5. Disable → `overlayEnabled=false`, service berhenti, prefs direstore byte-identik.

Catatan platform: overlay **tidak bisa** tampil di atas Setelan (`com.android.settings`)
karena app itu meminta `HIDE_OVERLAY_WINDOWS`; Android 12 lalu memasang
`mForceHideNonSystemOverlayWindow=true` dan overlay `isVisible=false`. Ini perilaku
OS (anti-tapjacking), bukan bug Faa GameCore.

### Floating Overlay — bukti lama (2026-10-18)
1. Toggle aktif → `Panel aktif • bubble`; bubble "Faa GameCore" melayang —
   `build/shots/ov1.png` (22:25:46).
2. Tap bubble → panel `Game Setelan / Profile Balance / Shizuku Connected /
   Faa GameCore Overlay only` + `PROFILE / LOGS / HOME` — `ov2.png` (22:26:07).
3. Log: `Overlay diminta tampil` → `Overlay tampil (bubble)`; tanpa
   `OVERLAY_ERROR`.
4. Dialog izin overlay tampil — `18-overlay-permission-dialog.png`.
5. Overlay dimatikan lagi; prefs `overlayEnabled=false`; tidak ada bubble di
   home — `ov4.png` (22:32:54).

Artefak rusak: `build/shots/ov-1-bubble.png` dan `ov-2-panel.png` (UTF-16, hash
identik `198C8497…`) — bukan bukti valid.

---

## 6. Error / bug — status

1. **Sesi basi meninggalkan freeze — DIPERBAIKI DAN TERBUKTI DI DEVICE.**
   `Fgc.reconcile()` berjalan saat process start, boot, dan tiap 60 dtk; memakai
   foreground + heartbeat + umur sesi. Uji 2026-10-19: `force-stop` saat jurnal
   terisi → freeze bertahan → recovery meng-enable paket dan mencatat `RECOVERY`
   tanpa intervensi. `BootReceiver` tidak lagi bergantung pada `sessionGame == null`.
2. **`pm clear` di smoke test — DIPERBAIKI.** Tidak ada lagi `pm clear`; prefs +
   runtime log dibackup dan direstore byte-identik; app di-force-stop sebelum
   restore. Sudah dijalankan di device (05:23, exit 0).
3. **False failure izin overlay — DIPERBAIKI.** Smoke test membaca appop
   `SYSTEM_ALERT_WINDOW`; bila izin ada, ia mengharapkan overlay muncul, bukan
   dialog. Bila overlay sudah aktif milik pengguna, tidak diubah.
4. **19 package disabled — AUDIT SELESAI, asal tidak dapat dipastikan.**
   Bukti kuat bukan dari Faa GameCore (state 2/4, bukan 3; jurnal & blacklist kosong; log
   tanpa freeze). **Belum ada yang diaktifkan**; menunggu konfirmasi.
5. **Dokumentasi** — `TEST-REPORT.md` dan `FEATURES.md` diperbarui dengan bukti
   nyata; klaim "OK" tanpa bukti diturunkan ke PARTIAL/UNVERIFIED.
6. Artefak rusak `ov-1-bubble.png` / `ov-2-panel.png`; `build/ui*.xml` dump lama.
7. Warning deprecation (4 baris) — kosmetik.
8. ~~Belum ada release signing / APK release~~ **SELESAI 2026-10-19** — APK release
   signed lokal (`keystore/`, tidak dibagikan), terpasang & lolos uji device
   (`build/release-check-last.log`).

---

## 7. Pekerjaan berikutnya (prioritas)

1. **Konfirmasi audit 19 package** sebelum menyentuh state perangkat. Jika ada
   yang ingin diaktifkan, lakukan satu per satu dengan `pm enable --user 0 <pkg>`
   dan verifikasi; jangan massal.
2. **Verifikasi recovery di device tanpa freeze**: buka app dengan prefs sesi basi
   saat ini, pastikan log menampilkan `Sesi basi (APP_START)` dan
   `sessionGame` terhapus, tanpa package berubah.
3. ~~Uji freeze/unfreeze terkontrol~~ **SELESAI 2026-10-19 05:55** — `adb_freeze_test.py`
   exit 0 (freeze, process death, recovery, cleanup). Berikutnya: uji freeze pada
   profil Awaken untuk lebih dari satu target, dan jalur `cmd package enable`.
4. ~~Jalankan smoke test baru~~ **SELESAI 2026-10-19 05:23** — exit 0, tanpa package
   berubah. Berikutnya: review screenshot hasil run ini (`build/shots/01…44`).
5. **Daemon end-to-end** + notifikasi. Auto-start + notifikasi sudah terbukti;
   sisa: stop/restart manual, interval poll.
6. ~~Overlay di atas aplikasi lain~~ **SELESAI 2026-10-19 18:44** — `adb_overlay_test.py`
   exit 0. Sisa: ganti profil / LOGS / HOME dari panel, drag + simpan posisi,
   clamp rotasi, cabut izin saat aktif, auto-show saat game dibuka.
7. ~~Uji boot auto-start + recovery dan dry-run~~ **SELESAI 2026-10-19** —
   `adb_boot_test.py` dan `adb_dryrun_test.py` exit 0. Auto-detect dan APK release
   di device juga sudah PASS. Sisa yang belum dieksekusi ada di §4d.
