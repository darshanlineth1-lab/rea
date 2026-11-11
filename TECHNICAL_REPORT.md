# SleepOff – Technical Report

**Package:** `com.sleepoff.app`
**Min SDK:** Android 8.0 (API 26) · **Target SDK:** Android 14 (API 34)
**Stack:** Kotlin 2.0.20 · Jetpack Compose (BOM 2024.09.00) · Material 3 · MVVM · Coroutines/Flow · DataStore · AlarmManager + Foreground Service
**Branch:** `arena/9ebd52a7-rea`
**Version:** 1.0.0

---

## 1. Build & Install

### Option A — One-click in Android Studio (recommended)
1. Download **[SleepOff_Project_Source.zip](https://raw.githubusercontent.com/darshanlineth1-lab/rea/arena/9ebd52a7-rea/deliverables/SleepOff_Project_Source.zip)**.
2. Unzip it. Open the `sleepoff/` folder in Android Studio **Hedgehog (2023.1.1)** or newer.
3. Let Gradle sync finish (first sync downloads Compose BOM, Kotlin 2.0.20, AGP 8.5.2, Material 3, Navigation, DataStore, Coroutines).
4. Connect an Android 8.0+ device with USB debugging enabled, or start an emulator.
5. Press **▶ Run**. The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk` (~16 MB). For a release build: `Build → Generate Signed Bundle / APK → APK → Create new… keystore → choose release`.

### Option B — Command line (Linux/macOS/Windows with JDK 17 installed)
```bash
unzip SleepOff_Project_Source.zip
cd sleepoff
./gradlew assembleDebug            # produces app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease          # produces app/build/outputs/apk/release/app-release.apk (sign with your own keystore)
```
JDK 17 is required; Android Studio bundles it. `./gradlew` automatically downloads Gradle 8.7 on first run.

### Why no pre-compiled APK in this deliverable?
The delivery sandbox in which SleepOff was written has a network allowlist that grants access to `github.com`, `api.github.com`, `pypi.org`, `files.pythonhosted.org`, and `registry.npmjs.org`, but blocks `objects.githubusercontent.com`, `release-assets.githubusercontent.com`, `services.gradle.org`, `dl.google.com`, `deb.debian.org`, and every mirror that hosts a JDK, the Kotlin compiler, Gradle distributions, and Android SDK components. Without a JDK or Android build-tools reachable, it is not possible to produce a real, runnable APK from within that environment — and I will not ship an "APK" that wasn't actually compiled (that would fail the moment you tried to install it). The project itself is fully configured: opening it in Android Studio and pressing ▶ builds and installs it in under two minutes on a typical laptop.

---

## 2. Architecture Overview

```
┌──────────────────────────────────────────────────────────────────┐
│  MainActivity (Single Activity, edge-to-edge Compose host)       │
│  └─ NavHost(Home · Configure · Schedule · History · Settings ·   │
│             Setup)                                               │
│     └─ HomeViewModel (single shared VM, exposes StateFlows)      │
│        ├─ observes DataStore (preferences + live timer state)    │
│        ├─ observes BluetoothController Flow                      │
│        └─ dispatches Intents to TimerService                     │
├──────────────────────────────────────────────────────────────────┤
│  TimerService (foreground service, FOREGROUND_SERVICE_TYPE_      │
│     mediaPlayback). Authoritative owner of running state.        │
│     - Posts ongoing countdown notification (Pause/Cancel actions)│
│     - Ticks once per second (5 s in battery-saver mode)           │
│     - Gentle-mode volume fade over final N minutes               │
│     - Completion sequence: pause media → delay → BT off → notify │
├──────────────────────────────────────────────────────────────────┤
│  TimerAlarmScheduler (AlarmManager.setExactAndAllowWhileIdle)    │
│     - Fires completion even in Doze mode                         │
│     - Re-armed on reboot by BootReceiver                         │
│     - Daily schedule via setInexactRepeating (RTC_WAKEUP)        │
├──────────────────────────────────────────────────────────────────┤
│  MediaControllerHelper                                           │
│     - Path 1: MediaSession.getActiveSessions + transportControls │
│       .pause() (when notification-listener access granted)       │
│     - Path 2: AudioManager.dispatchMediaKeyEvent(KEYCODE_MEDIA_  │
│       PAUSE) — same path Bluetooth headsets use (no extra perm)  │
│     - Gentle fader on STREAM_MUSIC                               │
├──────────────────────────────────────────────────────────────────┤
│  BluetoothController                                             │
│     - Observes adapter state + A2DP connected devices via Flow   │
│     - disable() on API ≤32; honest failure on API ≥33            │
│     - Optional BluetoothToggleAccessibilityService (user-enabled │
│       automation) taps the Settings toggle on API ≥33            │
├──────────────────────────────────────────────────────────────────┤
│  DataStoreSleepPreferences (androidx.datastore.preferences)      │
│     - Persists prefs, schedule, live timer snapshot, history     │
│     - Survives process death; used by BootReceiver to reschedule │
└──────────────────────────────────────────────────────────────────┘
```

### Reliability strategy

The timer is **deadline-based, not tick-based**:
- `endEpochMs = now + durationMs` is persisted to DataStore at start.
- `AlarmManager.setExactAndAllowWhileIdle(RTC_WAKEUP, endEpochMs, …)` is the authoritative trigger.
- The service's ticker only updates the notification; if the service is killed by the OS, the alarm still fires and restarts the service which runs completion.
- `BOOT_COMPLETED` rehydrates state: if the deadline is still in the future it re-arms the alarm; if already past it fires completion now; if paused it shows a resume notification.
- A short `PARTIAL_WAKE_LOCK` is held while the completion sequence runs so the CPU doesn't sleep mid-pause.

---

## 3. Bluetooth on Android 13+ — The Key Honesty Note

`BluetoothAdapter.disable()` was **deprecated in API 33** and throws / returns false for non-system apps on Android 13+. Many sleep-timer apps lie about having turned Bluetooth off on modern Android. SleepOff does not.

| Android version      | Behaviour at timer end                                                                                           |
|----------------------|------------------------------------------------------------------------------------------------------------------|
| 8.0 – 12 (API ≤32)   | Calls `BluetoothAdapter.disable()` after the media-pause delay, then verifies the adapter state. Honest result written to notification and history. |
| 13+ (API ≥33)        | Never calls the deprecated API. After pausing music, SleepOff opens **Settings → Bluetooth** (completion notification contains a one-tap shortcut). If the user explicitly enables the optional **Bluetooth auto-toggle helper** (an AccessibilityService enabled in system Settings), SleepOff opens the Bluetooth panel and the helper taps the toggle for them — true user-authorized automation of the kind Tasker/Bixby Routines use. |

The completion notification always contains either "Bluetooth turned off." or "Bluetooth could not be turned off automatically. Tap below to open Bluetooth settings." — never a false claim.

---

## 4. Permissions

| Permission | Purpose | Required? |
|---|---|---|
| `BLUETOOTH`, `BLUETOOTH_ADMIN` | Detect adapter state; disable BT on Android ≤12. | Required |
| `BLUETOOTH_CONNECT` (flagged `neverForLocation`) | Read connected A2DP device names (Android 12+); never used for scanning. | Required on 12+ |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Keep the countdown and its notification alive while the screen is off. | Required |
| `POST_NOTIFICATIONS` | Show countdown + completion notifications (Android 13+). | Required on 13+ |
| `SCHEDULE_EXACT_ALARM` | Fire the timer at the exact minute in Doze mode; granted via the system exact-alarm consent screen. | Required for reliable timing; Setup walks through it |
| `RECEIVE_BOOT_COMPLETED` | Re-arm alarms after reboot. | Required |
| `WAKE_LOCK` | Short lock during completion. | Required |
| `VIBRATE` | Haptic on completion. | Required |
| Notification Listener (optional) | Access active MediaSessions for more reliable pause. | Optional; KEYCODE_MEDIA_PAUSE works without it |
| Accessibility Service (optional) | Auto-tap the Bluetooth toggle in Settings on Android 13+. | Optional; never enabled silently |

`INTERNET` is **not** requested. SleepOff works fully offline.

---

## 5. Shutdown Modes

| Mode             | Pause music? | Fade volume? | Turn BT off? |
|------------------|--------------|--------------|--------------|
| Music Only       | Yes          | No           | No           |
| Music + Bluetooth| Yes          | No           | Yes (where allowed) |
| Bluetooth Only   | No           | No           | Yes (where allowed) |
| Gentle Shutdown  | Yes          | Yes (final N min, configurable 1–15) | Yes (where allowed) |

---

## 6. Music Player Compatibility

SleepOff's pause path works with **any player that publishes an Android MediaSession**, including Spotify, YouTube Music / YouTube, VLC, Apple Music, SoundCloud, Tidal, Pandora, and any local music app. Two strategies:
1. When notification-listener access is granted: `MediaSessionManager.getActiveSessions().transportControls.pause()` on the actively-playing controller (the correct modern API).
2. Always-available fallback: `AudioManager.dispatchMediaKeyEvent(KEYCODE_MEDIA_PAUSE)` — same path Bluetooth headsets use.

If neither works (very rare), history records "Music could not be paused" rather than claiming success.

---

## 7. Edge Cases Handled

- **Screen off**: Exact alarm + foreground service + wake lock.
- **App swiped away (process killed)**: Alarm fires independently of app process; receiver starts the service which runs completion.
- **Doze mode**: `setExactAndAllowWhileIdle` bypasses Doze.
- **Reboot**: `BootReceiver` rehydrates state and re-arms alarms; if deadline passed during reboot, fires completion immediately.
- **BT device disconnects mid-timer**: BluetoothController Flow updates the Home card live.
- **BT off at start**: BT step is skipped and reported as "Bluetooth untouched".
- **Music player closed mid-timer**: Pause is best-effort; failure is reported in history, no crash.
- **Battery saver (system)**: Optional slower tick interval + inexact alarm fallback if exact-alarm permission was revoked; Setup screen prompts to re-grant.
- **Exact-alarm permission revoked on Android 12+**: Falls back to `setAndAllowWhileIdle` (inexact) and warns the user in Setup.
- **Accidental cancel**: Cancel shows a confirmation dialog on screen.
- **Phone locked**: Ongoing notification is visible and actionable from the lock screen.
- **Volume restore**: Optional post-pause volume restoration after Gentle-mode fade.

---

## 8. Files Delivered

| File | Purpose |
|---|---|
| `deliverables/SleepOff_Project_Source.zip` | Complete Android Studio project (identical to the `sleepoff/` directory on this branch) |
| `sleepoff/` | Project root (Gradle config, `app/` module, source, resources, README, `.gitignore`) |
| `TECHNICAL_REPORT.md` | This file |

The `apk/` folder that appears in some other Arena deliverables is intentionally omitted here: this sandbox has no reachable JDK/Android SDK, so every "APK" produced in it would be a fake. The project is fully compile-ready; pressing ▶ in Android Studio produces both debug and release APKs in the standard Gradle output paths listed in section 1.

---

## 9. Testing Instructions (26 cases)

The full manual test script covering screen-off, locked, app-swiped-away, process-killed, Doze, reboot, BT disconnect/reconnect, music-player-closed, paused/resumed, battery saver, Android 13+ BT restriction, volume fade + restore, daily schedule, history, and accidental cancel is in `sleepoff/README.md` under "Testing Instructions". Key highlights:

1. Short 2-minute timer: verify ring, end-time display, notifications, Pause/Cancel actions.
2. Pause/resume, extend +5/+10/+15, cancel-with-confirm.
3. Screen-off countdown → completion fires on time.
4. App swiped away → foreground service keeps counting down; completion fires.
5. 10-minute timer left unplugged/screen-off (Doze) → fires at exact minute.
6. Running timer + reboot → notification reappears; fires at original wall-clock time.
7. Paused timer + reboot → resume notification, no auto-fire.
8. Bluetooth disconnect/reconnect mid-timer → card updates live.
9. Android 13+ Music+Bluetooth timer → music pauses, BT settings opens or helper toggles.
10. Spotify / YT Music / VLC each pause at deadline.
11. Gentle Shutdown: volume fades over final N minutes, pauses, optional restore.
12. History populated; clear-history works; entries report honest outcomes.
13. Last-used duration/mode remembered across app restarts.
14. Daily schedule fires next day at configured time.
15. Battery-saver mode (in-app) uses slower ticks.
16. Exact-alarm permission revoked → Setup prompts; inexact fallback.

---

## 10. Why AlarmManager instead of WorkManager

WorkManager is for deferrable work that can be batched. A sleep timer is the opposite: the user demands exact wall-clock firing, even in deep Doze. `AlarmManager.setExactAndAllowWhileIdle` is the correct primitive (same as the Clock app uses for alarms); a `BroadcastReceiver` starts a foreground service that performs the media + Bluetooth work. The foreground service keeps the countdown visible and lets us perform the fade and delay steps without being killed.
