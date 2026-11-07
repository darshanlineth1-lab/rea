# RegainFocus — Complete Technical Research, Architecture & Verification Report

## 1. Executive Summary

**RegainFocus** (`com.regainfocus.app`, v1.0.0) is a complete, offline-first, installable native Android productivity and digital-wellbeing application inspired by **Regain: Focus, Study, Screen Time** (`ai.regainapp`). Built with **Kotlin**, **Jetpack Compose**, **Material 3**, **Room Database**, **Jetpack DataStore**, **WorkManager**, **`UsageStatsManager`**, **`AccessibilityService`**, **`DevicePolicyManager`**, and **Real-Time PCM DSP Audio Synthesis (`AudioTrack`)**, RegainFocus delivers verified focus timers, real screen-time analytics, study goals/habits/schedules, and a two-tier **Strict App Blocker** (`Mode A: Standard Strict` for personal phones and `Mode B: Device Owner` for dedicated managed study devices).

### Verified Compiled Deliverables

| Artifact | Workspace Path | Snapshot-Persisted Copy | Size | Signing | SHA-256 |
|---|---|---|---|---|---|
| **Debug APK** | `app/build/outputs/apk/debug/app-debug.apk` | `apk/app-debug.apk` | 16 MB | APK Signature Scheme v2 (`CN=Android Debug`) | `4bc1fc50bd750f061b2d34447cb4461115efc4ab05b71d3d32dc06ff8a5257a1` |
| **Release APK** | `app/build/outputs/apk/release/app-release.apk` | `apk/app-release.apk` | 11 MB | APK Signature Scheme v2 (`CN=RegainFocus Open Source`) | `506386f216e249e4dfac1f344935b0cd861acba8fe94946d73c55d7afe81eeb2` |

---

## 2. Phase 1 — Research & Analysis of Regain (`ai.regainapp`)

Public sources investigated:
- Official Google Play Store listing (`https://play.google.com/store/apps/details?id=ai.regainapp&hl=en`, publisher **EpowerX Labs**, 5M+ downloads, ~4.5–4.6★).
- Official product site (`https://regainapp.ai/`) and published privacy documentation (`https://regainapp.ai/legal/privacy-policy-ios/`).

### 2.1 Publicly Verifiable Regain Features

1. **Focus Timer & Study Modes**:
   - Supports **Countdown Timer**, **Stopwatch**, and **Pomodoro** cycles with customizable focus, short-break, and long-break intervals plus pause/resume in non-strict sessions.
   - Supports session **Tags** (e.g., Study, Coding, Exam Prep) and notes to categorize productive hours.
   - Includes ambient study audio (rain, ocean waves, white noise, brown noise) to mask background noise.
2. **App Limits, Strict Mode & Block Protection**:
   - Lets users block selected distracting apps during focus sessions and configure **Daily App Limits**.
   - **Strict Mode** prevents early exits and prevents editing or disabling limits while a session is active.
   - **Reels & Shorts Blocker** uses Android's on-device `AccessibilityService` to detect and exit short-form feeds (YouTube Shorts, Instagram Reels, Snapchat Spotlight) without blocking the entire host app when desired.
   - **Block Protection** addresses common circumvention paths including split-screen/floating windows, system clock jumps, and accidental uninstallation.
3. **Screen-Time & Productivity Insights**:
   - Displays daily and weekly screen-time reports, per-app foreground time, daily focus goal ring, and tag-level focus breakdowns using Android's `UsageStatsManager`.
4. **Schedule Planner, Streaks & Achievements**:
   - Recurring study/detox schedules by day-of-week and time window, daily focus streaks, and milestone badges (from 1 hour upward).
5. **Social & Monetization Model in Regain**:
   - Regain includes cloud-backed Multiplayer Focus Rooms / weekly leaderboards, a mascot ("Rega"), and a paid **Regain Pro** tier unlocking advanced strict options, unlimited schedules, and extended history views.

### 2.2 Explicit Separation: Proposed Original Improvements in RegainFocus

Without copying any proprietary Regain code, logos, mascot artwork, or paid paywalls, **RegainFocus** introduces the following transparent engineering improvements:
1. **100% Free, Paywall-Free, Zero-Internet Architecture**:
   - Declares **zero `android.permission.INTERNET`** in `AndroidManifest.xml`. Every strict option, 30-day chart, schedule slot, theme, and JSON backup feature is unlocked locally with zero cloud telemetry.
2. **Two-Tier Legitimate Strict Enforcement (`Mode A` + `Mode B`)**:
   - Clearly separates **Mode A (Standard Strict Mode)** for personal unmanaged phones (with an upfront pre-activation transparency notice explaining Android OS boundaries) from **Mode B (Dedicated Device Owner Mode)** utilizing official `DevicePolicyManager` package suspension (`setPackagesSuspended`), `setUninstallBlocked`, `UserManager` restrictions (`DISALLOW_APPS_CONTROL`, `DISALLOW_UNINSTALL_APPS`, `DISALLOW_SAFE_BOOT`), and optional Lock Task Kiosk pinning.
3. **Procedural Real-Time 16-bit PCM DSP Soundscape Engine**:
   - Generates **Gentle Rain**, **Ocean Surf**, **Deep Brown Noise**, **Soft White Noise**, and **10 Hz Alpha Binaural Focus** mathematically on-device via `AudioTrack` — zero copyrighted audio assets and tiny APK footprint.
4. **Monotonic + Direct-Boot Dual Session Persistence**:
   - Combines `SystemClock.elapsedRealtime()` within a boot session (`Settings.Global.BOOT_COUNT`) with `createDeviceProtectedStorageContext()` persistence so manual system clock forward-jumping cannot bypass active Strict Mode AND active sessions survive `LOCKED_BOOT_COMPLETED` / `BOOT_COMPLETED` reboots.

---

## 3. Phase 2 — Evaluation of Open-Source Android Repositories

Eight relevant open-source Android repositories on GitHub were inspected before writing code:

| # | Repository | Stack & Architecture | Target / Min SDK | Key Features | License | Evaluation Decision |
|---|---|---|---|---|---|---|
| **1** | **`adnanrangrej/Focus-Modes-App`** | Kotlin, Jetpack Compose, Material 3, MVVM, Room, Services | `compileSdk 36`, `minSdk 26` | Pomodoro foreground service, Room entities, `UsageStatsManager`, `AccessibilityService` blocker, Compose overlay (`OverlayLifecycleOwner`), onboarding & charts | **MIT** | **SELECTED AS PRIMARY FOUNDATION** — Cleanest modern Kotlin + Compose + Room structure and permissive MIT license. |
| **2** | `1372Slash/Zenith` | Kotlin, Jetpack Compose, Material 3, Room | Android 14 | Focus timer, ambient white noise, screen-time insights, app blocker | MIT | Strong UI inspiration; less modular service separation than #1. |
| **3** | `ChristopherLaughlin/warden` | Kotlin, Jetpack Compose, MVVM, Hilt | Android 14 | Clean `UsageStatsManager` daily/weekly screen-time aggregation and app blocker | MIT | Informed our exact `UsageEvents` interval + `queryUsageStats` fallback design. |
| **4** | `eddiegulay/tempo` | Kotlin, Jetpack Compose, Room | Android 14 | Schedule-driven blocking profiles and usage limit tracking | GPL-3.0 | Informed recurring schedule bitmask modeling; copyleft license avoided for core reuse. |
| **5** | `yogeswar142/Zenlock` | Kotlin, Jetpack Compose | Android 14 | Focus lock screen and app launch interception patterns | Custom / All Rights Reserved | Not license-permissive for code reuse; evaluated for UX comparison only. |
| **6** | `curbox-app/curbox-android` | Kotlin, Compose, Accessibility | Android 14 | Mindful app limits and short-form reel/shorts container detection | GPL-3.0 | Informed public view-resource-ID matching concepts for Reels/Shorts. |
| **7** | `astraedus/nudge` | Kotlin, Compose, Material 3 | Android 14 | Minimalist habit & screen-time nudges | MIT | Evaluated for habit-card ergonomics; lacks strict focus timer & DPM support. |
| **8** | `warleysr/dechainer` | Kotlin, `DevicePolicyManager` | Android 13 | Device Owner app locker | GPL-3.0 | **REJECTED FOR REUSE** — Depends on `org.lsposed.hiddenapibypass:hiddenapibypass` (prohibited hidden API bypass). |

### Foundation Reuse & Major Upgrades Over `adnanrangrej/Focus-Modes-App`

We attributed `adnanrangrej/Focus-Modes-App` in `NOTICE_FOUNDATION.md`, adapted its `OverlayLifecycleOwner` lifecycle bridge and manifest service patterns, and re-engineered the core runtime to fix critical architectural gaps discovered during code audit:
1. **Fixed Session Destruction on Unbind**: Upstream `PomodoroTimerService` cancelled timers when UI unbound and lacked reboot survival. Our `FocusTimerService` persists deadlines to Direct-Boot storage, schedules `AlarmManager.setExactAndAllowWhileIdle` wakeups, and recovers seamlessly across process death and `BOOT_COMPLETED`.
2. **Upgraded Soft Overlay to True Strict Mode (`Mode A` + `Mode B`)**: Upstream `BlockingOverlay` included an instant *"Exit to Home"* dismissal path and lacked Settings tamper guards, split-screen window scanning, monotonic clock-tamper immunity, or `DevicePolicyManager` integration.
3. **Eliminated Hardcoded/Simulated Fallbacks**: Every dashboard card, streak counter, achievement badge, and chart in RegainFocus reads exclusively from Room Database (`regain_focus.db`) and `UsageStatsManager`.

---

## 4. System Architecture & Key Modules

```text
com.regainfocus.app
├── RegainFocusApplication.kt       # AppContainer DI, starter group seeding, boot sync, WorkManager init
├── MainActivity.kt                 # Edge-to-edge Compose host, 5-tab NavigationBar, LockTask lifecycle release
├── blocker/
│   └── DeviceOwnerEnforcementManager.kt # Mode B DevicePolicyManager suspension, UserManager restrictions & LockTask
├── domain/
│   ├── model/FocusModels.kt        # Domain models, UserSettings, PersistedTimerSessionState, Backup schemas
│   └── engine/FocusDomainEngines.kt # Pure unit-tested calculators: StrictRemainingTime, Pomodoro, BlockerPolicy, Streaks
├── data/
│   ├── local/                      # Room DB (7 entities, 6 DAOs, kotlinx-serialization converters)
│   ├── preferences/                # Typed DataStore<UserSettings> + Direct-Boot ActiveStrictSessionStore
│   └── repository/                 # InstalledAppsAndUsageRepository (UsageStatsManager) & FocusProductivityRepository
├── service/
│   ├── FocusTimerService.kt        # Foreground timer, AlarmManager fallback, UsageStats secondary watchdog
│   ├── StrictAppBlockerAccessibilityService.kt # Primary window/multi-window/Shorts/Settings blocker
│   └── AmbientSoundSynthesizer.kt  # 16-bit stereo PCM AudioTrack DSP (Rain, Ocean, Brown, White, Alpha 10Hz)
├── receiver/
│   ├── BootRecoveryReceiver.kt     # Direct-Boot LOCKED_BOOT_COMPLETED & BOOT_COMPLETED session recovery
│   ├── FocusAlarmReceiver.kt       # Exact AlarmManager timer deadline wake receiver
│   └── FocusDeviceAdminReceiver.kt # DeviceAdminReceiver guarding active strict sessions
├── notification/
│   └── NotificationHelper.kt       # Ongoing timer channel (omits pause/stop in Strict Mode) & alert channels
├── worker/
│   └── ReminderWorkers.kt          # Periodic WorkManager daily goal & schedule reminder evaluator
└── ui/
    ├── blocker/                    # StrictBlockShieldActivity (zero unlock/skip buttons, Back -> Home Launcher)
    ├── components/                 # Custom Canvas AuroraGoalRing, WeeklyBarChart, SmoothTrendLineChart, Heatmap
    ├── screens/                    # OnboardingScreen, HomeScreen, FocusScreen, AppsScreen, StatisticsScreen, SettingsScreen
    ├── theme/                      # Color.kt, Type.kt, Shape.kt, Theme.kt (Aurora Dark, AMOLED, Light Sage, System)
    └── viewmodel/                  # RegainMainViewModel (reactive StateFlows + strict mutation lock enforcement)
```

---

## 5. Strict App Blocker — Security & Platform Enforcement Analysis

### 5.1 Core Strict Invariants (Enforced in Both Mode A & Mode B)
- **Zero In-App Escape Hatches**: When `strictLevel != NONE`, `FocusScreen`, `StrictBlockShieldActivity`, and `NotificationHelper` render **zero** Pause, Skip, Cancel, Snooze, or Early-Unlock actions.
- **Mutation Lock**: `FocusProductivityRepository` throws `StrictSessionMutationLockedException` if any caller attempts to edit/delete the active Blocking Group, weaken strict guards, import backups, or clear history during an active Strict Session.
- **Anti-Clock-Tamper**: `StrictRemainingTimeCalculator` anchors active sessions to `SystemClock.elapsedRealtime()` tagged with `Settings.Global.BOOT_COUNT`. Jumping the system wall clock ahead by hours in Android Settings does **not** end the session early.
- **Launcher, Multi-Window & Reboot Persistence**:
  - `StrictBlockShieldActivity` intercepts hardware/gesture Back navigation and routes exclusively to `CATEGORY_HOME` while keeping blocking 100% active; re-tapping any blocked app icon immediately re-triggers `performGlobalAction(GLOBAL_ACTION_HOME)` + `StrictBlockShieldActivity`.
  - `StrictAppBlockerAccessibilityService` inspects interactive `AccessibilityWindowInfo` layers to catch split-screen and floating pop-up windows.
  - `BootRecoveryReceiver` (`directBootAware="true"`) restores timers and enforcement immediately on `LOCKED_BOOT_COMPLETED` / `BOOT_COMPLETED`.
- **Automatic Restoration**: The instant `remainingMillis <= 0`, `FocusTimerService` unsuspends packages, clears restrictions, logs the verified `FocusSessionEntity`, clears `ActiveStrictSessionStore`, and dismisses `StrictBlockShieldActivity`.
- **Emergency Safety Allowlist**: `StrictBlockerPolicyEngine` permanently allowlists `com.android.dialer`, `com.google.android.dialer`, `com.android.incallui`, `com.android.emergency`, `com.android.systemui`, active IME keyboards, and the Home Launcher so emergency calls and basic phone safety are never obstructed.

### 5.2 Mode A (Standard Strict Mode) vs. Mode B (Device Owner Mode)

| Capability | Mode A: Standard Strict (Personal Devices) | Mode B: Device Owner (Provisioned Study Devices) |
|---|---|---|
| **Target Device** | Any ordinary Android 8.0–15 personal smartphone | Dedicated study phone/tablet provisioned via ADB |
| **Blocking Mechanism** | `AccessibilityService` window/multi-window interception + `UsageStatsManager` 750ms watchdog + `StrictBlockShieldActivity` | Official `DevicePolicyManager.setPackagesSuspended(..., true)` + Mode A shield |
| **Settings Tamper Guard** | Intercepts `InstalledAppDetails`, `AccessibilitySettings`, `DeviceAdminAdd`, `DateTimeSettings` | OS-enforced `UserManager.DISALLOW_APPS_CONTROL` (disables Force-Stop & Clear Data buttons in Settings) |
| **Uninstall Protection** | Optional `DeviceAdminReceiver` warning + interception | OS-enforced `dpm.setUninstallBlocked(..., true)` + `DISALLOW_UNINSTALL_APPS` |
| **Safe Mode Reboot Bypass** | Standard Android allows Safe Mode on unmanaged phones | OS-enforced `UserManager.DISALLOW_SAFE_BOOT` |
| **Kiosk Pinning** | Not enforced by OS | Optional `dpm.setLockTaskPackages` + `Activity.startLockTask()` |
| **Known Honest Limitations** | Displayed to user before every activation: Android intentionally allows owners of unmanaged personal devices to Force-Stop apps via ADB, Safe Mode, or OEM task killers if determined. | Requires one-time `adb shell dpm set-device-owner com.regainfocus.app/.receiver.FocusDeviceAdminReceiver` on a device without pre-existing accounts (or before re-adding accounts). |

---

## 6. Permissions Reference

| Permission / Declaration | Purpose | Required For |
|---|---|---|
| `PACKAGE_USAGE_STATS` | Reads real daily/weekly/monthly per-app foreground durations & secondary strict watchdog | Screen-Time Tracker & Daily App Limits |
| `BIND_ACCESSIBILITY_SERVICE` | Detects foreground package launches, split-screen windows & Shorts/Reels view containers | Mode A Strict App Blocker & Reels/Shorts Shield |
| `FOREGROUND_SERVICE` / `SPECIAL_USE` / `MEDIA_PLAYBACK` | Keeps focus timer & ambient DSP soundscape alive when minimized | Accurate Background Focus Timer & Soundscapes |
| `RECEIVE_BOOT_COMPLETED` (`directBootAware`) | Resumes active strict timers & unsuspends expired sessions after reboot | Reboot Persistence |
| `SCHEDULE_EXACT_ALARM` | Wakes `FocusTimerService` at exact phase/session completion timestamp under Doze | Zero-drift Background Timer |
| `POST_NOTIFICATIONS` | Displays live countdown notification, break alerts & evening goal reminders | Notifications (Android 13+) |
| `QUERY_ALL_PACKAGES` | Lists installed launchable user apps inside the Blocking Group & App Limit pickers | App Selection UI |
| `SYSTEM_ALERT_WINDOW` | Optional overlay permission for instant shield transitions | Enhanced Shield Responsiveness |
| `BIND_DEVICE_ADMIN` | Enables optional Device Admin guard (Mode A) or full DevicePolicyManager control (Mode B) | Mode B Device Owner / Tamper Resistance |

*(Note: `android.permission.INTERNET` is intentionally **absent** — 100% offline privacy.)*

---

## 7. Automated Testing & Build Verification

Executed command:
```bash
./gradlew testDebugUnitTest assembleDebug assembleRelease --no-daemon
```

- **Unit Test Suite (`FocusEngineAndPolicyTest.kt`) — 7/7 Passed (0 failures, 0 errors)**:
  1. `strict timer resists manual system clock forward jump within same boot session`
  2. `strict timer recovers remaining time accurately across device reboot`
  3. `blocker policy blocks target apps across strict breaks and guards system settings`
  4. `shorts and reels detector matches short form feed containers`
  5. `pomodoro phase engine cycles accurately through work short breaks and long break`
  6. `streak and achievement engine calculates real streaks and unlocks badges`
  7. `datastore serializers and room converters round trip cleanly`
- **APK Packaging & Cryptographic Verification**:
  - Verified with Android Build-Tools `36.1.0` `aapt2 dump badging`, `aapt2 dump permissions`, and `apksigner verify --verbose` (APK Signature Scheme v2 `Verifies` on both Debug and Release APKs).

---

## 8. Installation & Usage Instructions

### Standard Installation (Personal Android Device — Mode A)
1. Transfer `app/build/outputs/apk/debug/app-debug.apk` (or `apk/app-release.apk`) to an Android 8.0+ (API 26–35) phone, or install via ADB:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
2. Open **RegainFocus** and complete the 2-step Onboarding screen:
   - Choose your Daily Focus Target and Daily Screen-Time Budget.
   - Grant **Usage Access**, **Accessibility Focus Blocker**, and **Notifications**.
3. Open **Apps** -> **Block Groups** to customize which installed apps belong to your focus shields, or set **Per-App Daily Budgets** under **Screen Time**.
4. Open **Focus**, choose **Pomodoro / Timer / Stopwatch**, select **Mode A: Standard Strict**, review the transparent confirmation dialog, and tap **I Understand — Lock & Start**.

### Optional Dedicated Study Device Provisioning (Mode B: Device Owner)
1. On a newly reset Android study device (or after temporarily removing Google/OEM accounts in Settings -> Accounts), install the APK and run:
   ```bash
   adb shell dpm set-device-owner com.regainfocus.app/.receiver.FocusDeviceAdminReceiver
   ```
2. Open **RegainFocus** -> **Settings** -> **Mode B: Dedicated Device Owner** to confirm the green `ACTIVE` status badge, then select **Mode B: Device Owner** when starting a Strict Focus Session.
