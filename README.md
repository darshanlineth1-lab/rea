# RegainFocus — Open-Source Deep Focus, Screen-Time & Strict App Blocker (`v1.1.0`)

`RegainFocus` is a **100% local-first, zero-network, minimum-privilege Android productivity & focus application** built with **Kotlin, Jetpack Compose, Material 3, Room, DataStore, and WorkManager**, grounded in the MIT-licensed [`Focus-Modes-App`](https://github.com/chirag-singh-07/Focus-Modes-App) architecture and hardened for **Google Play Protect Enhanced Fraud Protection (EFP)** and **Google Play User Data / Sensitive Permissions policy compliance**.

See **[`PERMISSION_AUDIT_REPORT.md`](./PERMISSION_AUDIT_REPORT.md)** for the full binary manifest inspection, root-cause investigation of the Google Play Protect EFP block, third-party dependency audit, and Play Store review checklist.

---

## Direct APK Downloads (`v1.1.0` — Branch `arena/00693eaa-rea`)

| Edition | APK Path | Size | Purpose & Permission Profile |
| :--- | :--- | :---: | :--- |
| **1. Direct Sideload Release (Recommended for Browser / File-Manager Install)** | [`apk/app-release.apk`](https://raw.githubusercontent.com/darshanlineth1-lab/rea/arena/00693eaa-rea/apk/app-release.apk) | `1.4 MB` | **Zero `BIND_ACCESSIBILITY_SERVICE`**, zero `BIND_DEVICE_ADMIN`, zero `SMS`, zero `NOTIFICATION_LISTENER`, zero `INTERNET`. Uses `UsageStatsManager` (350ms foreground watchdog) + `SYSTEM_ALERT_WINDOW` shield during Focus Sessions so internet sideloading does not trigger Play Protect EFP. |
| **2. Play Store / ADB Accessibility Release** | [`apk/app-playstore-accessibility-release.apk`](https://raw.githubusercontent.com/darshanlineth1-lab/rea/arena/00693eaa-rea/apk/app-playstore-accessibility-release.apk) | `1.4 MB` | Designed for **Google Play Store distribution** or `adb install`. Uses a policy-hardened `AccessibilityService` (`canRetrieveWindowContent="false"`, `typeWindowStateChanged` only, standalone two-button Prominent Disclosure) without `SYSTEM_ALERT_WINDOW`. *(Note: Direct browser downloads of any APK declaring `BIND_ACCESSIBILITY_SERVICE` are blocked by Play Protect EFP).* |
| **3. Debug Developer Build (`sideloadDebug`)** | [`apk/app-debug.apk`](https://raw.githubusercontent.com/darshanlineth1-lab/rea/arena/00693eaa-rea/apk/app-debug.apk) | `16 MB` | Unminified debug build of the `sideload` flavor for local development and inspection. |

### SHA-256 Checksums
* `apk/app-release.apk`: `6daa1f20b5b047a4d45eb7b115d0cb4eb0b6f823150a8b2d61feb2bd100183f8`
* `apk/app-playstore-accessibility-release.apk`: `d4840e4c5b091082a40fbb89b3975029a498ac38c643c7d4a023957dcc497893`
* `apk/app-debug.apk`: `001d2dd39477ea4dcd3c91cc521dabddd5bf08c75742584e72152dd49658f639`

---

## Core Features

1. **Multi-Mode Focus Timer (`FocusTimerService`)**:
   * **Pomodoro** (configurable Work / Short Break / Long Break / Rounds), **Countdown Timer**, and **Stopwatch**.
   * Dual-clock persistence (`SystemClock.elapsedRealtime()` + wall-clock epoch + `BOOT_COUNT` anchor) resists manual system-clock jumps and recovers cleanly across reboots.
2. **Flexible & Strict App Blocking (`StrictBlockerPolicyEngine` + `StrictBlockShieldActivity`)**:
   * Custom Blocking Groups, per-session app selection, Per-App Daily Screen-Time Budgets, and Recurring Focus Schedules.
   * **Strict Focus Mode** locks in-app Pause, Skip, Stop, and Group edits until `00:00` while permanently allowlisting Android System Settings, Emergency Dialers, Permission Controller, and Google Play / Play Protect.
3. **Standalone Prominent Permission Disclosures (`PermissionDisclosureDialogs.kt`)**:
   * Every sensitive permission (`PACKAGE_USAGE_STATS`, `SYSTEM_ALERT_WINDOW`, or `BIND_ACCESSIBILITY_SERVICE`) is gated behind a standalone two-button disclosure dialog (**"I Agree — Open Settings"** / **"Not Now"**) explaining exact data use and on-device privacy before opening system settings.
4. **Real Screen-Time Analytics, Study Streaks, Daily Habits & Procedural Soundscapes**:
   * Live 24-hour / 7-day / 30-day screen-time analytics via `UsageStatsManager`.
   * Real-time procedural `AudioTrack` synthesis (Gentle Rain, Ocean Surf, Brown Noise, White Noise, 10 Hz Alpha Binaural Tone).
   * 100% local Room + DataStore persistence with JSON backup export/import and **zero `INTERNET` permission**.

---

## Building & Testing from Source

Requires JDK 17 and Android SDK (`compileSdk 35`, `buildToolsVersion 36.1.0`):

```bash
# Run unit tests across both distribution flavors
./gradlew testSideloadDebugUnitTest testPlaystoreDebugUnitTest

# Build R8-minified, signed release APKs
./gradlew assembleSideloadRelease assemblePlaystoreRelease
```
