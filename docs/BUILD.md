# Build & install

## Prerequisites (verified on this machine)

| Tool | Version used |
|---|---|
| JDK | 21.0.10 (Temurin/Oracle) |
| Gradle | wrapper 8.11.1 |
| Android SDK | `C:\AndroidSDK` (platform 34, build-tools 35.0.0) |
| AGP / Kotlin | 8.7.3 / 2.0.21 |
| adb | 37.0.1 |

`ANDROID_HOME` must point at the SDK. `local.properties` is optional if the env var is set.

## Build

```bat
set ANDROID_HOME=C:\AndroidSDK
gradlew.bat :app:assembleDebug
```

Output: `app\build\outputs\apk\debug\app-debug.apk`

## Release build

Signing material is local-only and never committed:

```bat
keytool -genkeypair -keystore keystore\fgc-release.jks -alias fgc -keyalg RSA ^
        -keysize 2048 -validity 10000 -dname "CN=Faa GameCore, O=Faa, C=ID"
```

`keystore\keystore.properties` (also local-only):

```properties
storePassword=...
keyPassword=...
keyAlias=fgc
storeFile=keystore/fgc-release.jks
```

```bat
set ANDROID_HOME=C:\AndroidSDK
gradlew.bat :app:assembleRelease
```

Output: `app\build\outputs\apk\release\app-release.apk`. Without
`keystore/keystore.properties` the release task fails on purpose; debug is unaffected.

> `gradle.properties` sets `kotlin.compiler.execution.strategy=in-process` because the Kotlin
> compile daemon cannot bind on this host. Remove it on a normal machine for faster builds.

## Install

```bat
adb install -r -g app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.faa.fgamecore/.MainActivity
```

Release install (uninstall first: different signature from debug):

```bat
adb uninstall com.faa.fgamecore
adb install -g app\build\outputs\apk\release\app-release.apk
```

## Checks

```bat
python tools\check_policy.py       :: policy invariants (host, no device)
python tools\check_launcher.py     :: launch decision order (host, no device)
python tools\check_recovery.py     :: stale-session / journal recovery (host)
python tools\check_icons.py [apk]  :: launcher icon resources + APK badging
python tools\adb_smoke.py          :: install + walk every screen + crash scan
python tools\adb_freeze_test.py    :: controlled freeze / process death / recovery
python tools\adb_boot_test.py      :: BOOT_COMPLETED -> boot recovery (debug build)
python tools\adb_overlay_test.py   :: overlay over another app
python tools\adb_dryrun_test.py    :: dry-run touches no package
python tools\adb_autodetect_test.py:: installed-app picker (read-only)
python tools\adb_release_check.py  :: signed release APK on device (non-debuggable, screens, crash scan)
```

`adb_smoke.py` is non-destructive: it backs up `shared_prefs/fgc.xml` and
`files/fgc-runtime.log`, never runs `pm clear`, never freezes, and restores the
backup before exiting. It writes stdout/stderr/exit code to
`build\smoke-last.log` and only runs the launcher test when the saved profile is
Balance (1) and the blacklist is empty.

The freeze/boot tests only ever touch `de.szalkowski.activitylauncher` (disposable
user app) and refuse to start if a session or journal is already active.

`adb_boot_test.py` delivers the boot signal with a debug-only action
(`com.faa.fgamecore.action.DEBUG_BOOT`) instead of a real reboot: this Redmi 6A
does not reach `sys.boot_completed=1` after a normal reboot (only "soft restart"
works), and `BOOT_COMPLETED` is a protected broadcast that `adb shell am broadcast`
cannot send. It needs the debug build, whose manifest exports the receiver
(`app/src/debug/AndroidManifest.xml`); release keeps it unexported.
