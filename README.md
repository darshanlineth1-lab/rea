# RegainFocus — Focus Timer, Strict App Blocker & Screen-Time Tracker for Android

**RegainFocus** (`com.regainfocus.app`) is a complete, 100% local-first, privacy-respecting Android digital-wellbeing and deep-study application built with **Kotlin**, **Jetpack Compose**, **Material 3**, **Room**, **DataStore**, **WorkManager**, **`UsageStatsManager`**, **`AccessibilityService`**, and **`DevicePolicyManager`**.

Inspired by public productivity workflows in Regain (`ai.regainapp`) and built atop the MIT-licensed open-source foundation [`adnanrangrej/Focus-Modes-App`](https://github.com/adnanrangrej/Focus-Modes-App) (see [`NOTICE_FOUNDATION.md`](NOTICE_FOUNDATION.md)), RegainFocus contains **zero ads, zero cloud accounts, zero paid paywalls, and zero `INTERNET` permission**.

---

## Compiled Installable APKs

- **Debug APK (Primary Deliverable)**:
  - Build output path: `app/build/outputs/apk/debug/app-debug.apk`
  - Snapshot-persisted path: `apk/app-debug.apk`
  - SHA-256: `4bc1fc50bd750f061b2d34447cb4461115efc4ab05b71d3d32dc06ff8a5257a1`
- **Signed Release APK (R8 Optimized)**:
  - Build output path: `app/build/outputs/apk/release/app-release.apk`
  - Snapshot-persisted path: `apk/app-release.apk`
  - SHA-256: `506386f216e249e4dfac1f344935b0cd861acba8fe94946d73c55d7afe81eeb2`

Full research, open-source repository comparison, architecture, security analysis, and limitations are documented in **[`TECHNICAL_REPORT.md`](TECHNICAL_REPORT.md)**.

---

## Implemented Features

1. **Home Dashboard (`HomeScreen.kt`)**:
   - Live Daily Focus Goal ring, Today's Verified Focus Time, Remaining Target, Current & Best Streak, Completed Sessions count, Today's Real Screen Time vs. Budget bar, One-Tap Quick Focus button, Weekly Productivity Bar Chart, and Interactive Daily Study Habits checklist.
2. **Focus Timer & DSP Soundscapes (`FocusScreen.kt`, `FocusTimerService.kt`, `AmbientSoundSynthesizer.kt`)**:
   - **Pomodoro** (custom work/short break/long break/cycles), **Custom Countdown**, and **Stopwatch**.
   - Monotonic + wall-clock foreground timer with `AlarmManager` exact wakeup fallback — zero drift when minimized or under Doze.
   - Real-time procedural 16-bit stereo PCM audio synthesizer: **Gentle Rain**, **Ocean Surf**, **Deep Brown Noise**, **Soft White Noise**, and **10 Hz Alpha Binaural Focus**.
   - Session tags (`Study`, `Coding`, `Exam Prep`, `Reading`, `Writing`, `Deep Work`), notes, and full persistent session history.
3. **Two-Tier Strict App Blocker (`StrictAppBlockerAccessibilityService.kt`, `StrictBlockShieldActivity.kt`, `DeviceOwnerEnforcementManager.kt`)**:
   - Custom **Blocking Groups** & individual installed-app multi-select picker, plus **YouTube Shorts / Instagram Reels** short-form feed interception.
   - **Zero early-exit / pause / skip / unlock controls** during active Strict Mode; mutation locks prevent editing/deleting active groups or settings until `00:00`.
   - **Anti-Clock-Tamper** monotonic `SystemClock.elapsedRealtime()` tracking within boot sessions + Direct-Boot (`LOCKED_BOOT_COMPLETED` / `BOOT_COMPLETED`) reboot recovery.
   - **Mode A (Standard Strict Mode)**: Explicit user-consented Accessibility + Foreground Watchdog + Settings Self-Guard + Split-Screen scanning, with transparent pre-activation limitation notice.
   - **Mode B (Dedicated Device Owner Mode)**: Official `DevicePolicyManager.setPackagesSuspended`, `setUninstallBlocked`, `DISALLOW_APPS_CONTROL`, `DISALLOW_UNINSTALL_APPS`, `DISALLOW_SAFE_BOOT`, and optional Lock Task Kiosk Mode, with automatic restoration when the timer reaches `00:00`.
4. **Screen-Time Tracker & Per-App Daily Budgets (`AppsScreen.kt`, `InstalledAppsAndUsageRepository.kt`)**:
   - Real `UsageStatsManager` daily, 7-day, and 30-day totals, 24-hour today activity spark bars, most-used apps list with open counts, Per-App Daily Limits (with optional 24-hour Strict Lock), and Recurring Study Schedules.
5. **Statistics, Streaks & Achievements (`StatisticsScreen.kt`)**:
   - 7D/30D Focus Trend charts, Focus vs. Screen-Time dual-bar comparison, Tag distribution bars, 28-Day Streak & Goal Heatmap, and 10 real unlockable Achievements.
6. **Settings & Local JSON Backup (`SettingsScreen.kt`)**:
   - 4 Theme modes (**Aurora Dark**, **AMOLED Pitch Black**, **Light Sage**, **System Default**), timer & goal customization, permission status shortcuts, Mode B ADB provisioning helper, notification/reminder controls, and full local JSON Export/Import.

---

## Quick Installation

```bash
# Install Debug APK over ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk

# (Optional) Provision Mode B Device Owner on a dedicated account-free study device
adb shell dpm set-device-owner com.regainfocus.app/.receiver.FocusDeviceAdminReceiver
```

## Building & Running Tests

```bash
./gradlew testDebugUnitTest assembleDebug assembleRelease
```
