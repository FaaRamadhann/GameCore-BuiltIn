# Faa GameCore

Aplikasi Android native **non-root** untuk mengatur aplikasi latar belakang saat bermain game.
Diturunkan dari logika inti [Faa GameCore](https://github.com/FaaRamadhann/Faa-GameCore) (modul Magisk),
dijalankan ulang sebagai aplikasi mandiri di atas **Shizuku**.

- Nama: `Faa GameCore` (Standalone)
- Package: `com.faa.fgamecore`
- Versi: 1.3.1 (versionCode 131)
- minSdk 26 / targetSdk 34
- Tanpa root, tanpa Magisk, tanpa `su`

## Arsitektur

```
UI (Jetpack Compose, dark "Night Blue Sky")
  └── Fgc (state flow + engine, satu pintu)
        ├── Policy      ← port policy_engine.sh + process_manager.sh + session_controller.sh
        ├── ShizukuBridge → IShizukuService.newProcess("sh -c …")  (uid shell)
        ├── Store       → SharedPreferences (games, black/whitelist, frozen journal)
        ├── Logs        → ring buffer + file (fgc-runtime.log)
        └── FgcService  → foreground service, poll tiap N detik (dumpsys foreground)
```

Keputusan penting yang dipertahankan dari sumber asli:

| Aturan | Implementasi |
|---|---|
| whitelist > blacklist | `Policy.targets()` memfilter whitelist lebih dulu |
| paket sistem tidak pernah dibekukan | `Policy.freeze()` + `Device.isSystem()` (fail-closed) |
| freeze reversibel | `pm disable-user` saat sesi, `pm enable` / `cmd package enable` saat selesai |
| jurnal write-ahead | `Store.frozen` ditulis **sebelum** perintah disable |
| verifikasi jujur | snapshot `pm list packages -d/-e`; gagal verifikasi → entry disimpan untuk retry |
| game foreground tidak dibekukan | target difilter terhadap paket game |
| dry-run | `Policy.freeze()` melewati perintah, hanya log |

## Ikon

Artwork sumber `роза_Rozaletta____деревня друзей фиксая.jpg` (736×736) diproses oleh
`python tools/make_icon.py` menjadi:

| Resource | Ukuran | Peran |
|---|---|---|
| `mipmap-*/ic_launcher_foreground.png` | 108/162/216/324/432 | foreground adaptive (full-bleed 108dp) |
| `mipmap-*/ic_launcher.png` | 48/72/96/144/192 | legacy launcher icon |
| `mipmap-*/ic_launcher_round.png` | 48/72/96/144/192 | legacy round icon (alpha circle) |
| `mipmap-anydpi-v26/ic_launcher{,_round}.xml` | — | adaptive icon (API 26+) |

Foreground memakai crop yang membuat wajah tetap berada di area aman 72dp, jadi
mask lingkaran/squircle tidak memotong bagian penting.

Verifikasi: `python tools/check_icons.py app\build\outputs\apk\debug\app-debug.apk`
(resource + isi APK). Bukti device: `build/icon-on-device.png`.

## Build

Butuh JDK 17+ dan Android SDK (platform 34, build-tools 34+).

```bat
set ANDROID_HOME=C:\AndroidSDK
gradlew.bat :app:assembleDebug
```

APK: `app\build\outputs\apk\debug\app-debug.apk`

## Instal & setup Shizuku

1. Install Shizuku dari Play Store.
2. Jalankan Shizuku (mode ADB / wireless debugging).
3. `adb install -r app\build\outputs\apk\debug\app-debug.apk`
4. Buka Faa GameCore → Welcome → **Buka Shizuku** → izinkan Faa GameCore.
5. Kembali ke Faa GameCore → **Lanjutkan** → **Start Daemon**.

## Fitur

Berfungsi: Game List (manual + auto-detect aplikasi terpasang), profil 1/2/3/4 (Auto dari baterai),
blacklist/whitelist, freeze/unfreeze dengan jurnal + verifikasi, daemon foreground, deteksi game
via `dumpsys activity`, runtime log, notifikasi, boot auto-start, recovery saat proses mati.

Tidak didukung (batasan non-root/Shizuku): hook Zygisk/LSPosed, kill proses level kernel,
`setenforce`, akses `/data/adb`.

## Lisensi

GPL-3.0-or-later, mengikuti proyek sumber.
