# SleepOff — Smart Bluetooth Sleep Timer

A premium Android app that stops your music at a scheduled time, disconnects / disables Bluetooth where Android allows, saves battery overnight, and works reliably while your screen is locked.

- **Language/Stack:** Kotlin, Jetpack Compose, Material 3, MVVM, Coroutines + Flow, DataStore, AlarmManager + Foreground Service
- **Min SDK:** Android 8.0 (API 26)
- **Target SDK:** Android 14 (API 34)
- **No ads. No tracking. No account. No internet permission.**

---

## Project Layout

```
sleepoff/
├── build.gradle.kts, settings.gradle.kts, gradle/libs.versions.toml
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── res/                   # strings, themes, vector icons, accessibility config
│       └── java/com/sleepoff/app/
│           ├── SleepOffApplication.kt        # DI-lite: singletons
│           ├── MainActivity.kt               # Single-activity Compose host + NavHost
│           ├── theme/                        # AMOLED dark Material 3 palette
│           ├── navigation/                   # Route constants
│           ├── data/                         # DataStore-backed preferences
│           ├── domain/                       # Models (SleepMode, TimerState, TimerSnapshot, …)
│           ├── bluetooth/                    # BluetoothController + optional accessibility toggle helper
│           ├── media/                        # MediaControllerHelper (MediaSession + KEYCODE_MEDIA_PAUSE) + optional NotificationListener
│           ├── timer/                        # TimerService (foreground), AlarmScheduler, AlarmReceiver, BootReceiver, NotificationHelper
│           ├── viewmodel/HomeViewModel.kt    # Single ViewModel for all screens
│           └── ui/
│               ├── components/               # CircularCountdown, PresetChip, SleepCard, ModeSelector, TopBar
│               └── screens/                  # Home, Configure, Schedule, History, Settings, Setup
```

---

## How the Timer Works

The timer is **deadline-based, not tick-based** — this is the key to reliability:

1. When you tap **Start**, the foreground service writes
   `endEpochMs = now + durationMs` to DataStore and schedules an **exact alarm**
   via `AlarmManager.setExactAndAllowWhileIdle(RTC_WAKEUP, endEpochMs, …)`.
2. A lightweight foreground service posts the ongoing notification and updates
   it once per second (every 5s in battery-saver mode). The service's lifecycle
   is independent of the UI.
3. The alarm fires even if the screen is off, the app is in the background, or
   the process was killed in Doze mode. The `TimerAlarmReceiver` then starts
   the foreground service which runs the **completion sequence**:
   1. (Gentle mode) fade STREAM_MUSIC down to 0 over the final minutes.
   2. Pause playback via the active `MediaSession` if notification-listener
      access was granted, otherwise dispatch `KEYCODE_MEDIA_PAUSE` through
      `AudioManager` (the same path Bluetooth headsets use).
   3. Wait the configured delay.
   4. Attempt Bluetooth off (see Bluetooth Restrictions below).
   5. Post the completion notification, append a history entry, and clear state.
4. **Boot receiver:** on `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED`, the app
   rehydrates from DataStore. If the deadline is still in the future it
   re-arms the exact alarm; if it already passed it fires completion now; if
   the timer was paused it shows a resume notification. Daily schedules are
   also re-registered.
5. **Doze mode:** `setExactAndAllowWhileIdle` bypasses Doze. The app also
   takes a short `PARTIAL_WAKE_LOCK` during the completion sequence so the
   media pause + Bluetooth actions complete before the device sleeps again.

---

## Bluetooth on Android 13+ — the honest explanation

Google deprecated `BluetoothAdapter.disable()` in API 33, and third-party apps
targeting Android 13 **cannot** turn Bluetooth off programmatically. The API
returns `false` / throws `SecurityException`. Many apps on the Play Store lie
about this. SleepOff does not.

SleepOff's Bluetooth behaviour:

| Android version      | Behaviour at timer end                                                                                           |
|----------------------|-------------------------------------------------------------------------------------------------------------------|
| Android 8 – 12 (≤32) | Calls `BluetoothAdapter.disable()`, then verifies the adapter state. Honest result shown in notification/history. |
| Android 13+ (≥33)    | Never calls the deprecated API. After pausing music, SleepOff either: (a) opens **Settings → Bluetooth** so you can tap the toggle, or (b) if you enable the **optional Bluetooth auto-toggle helper** (an AccessibilityService you explicitly enable in system Settings), SleepOff opens the Bluetooth panel and the helper taps the toggle for you — true user-authorized automation of the kind Tasker and Bixby Routines use. |

In every case the completion notification and the History screen record what
actually happened. If Bluetooth could not be turned off automatically, the
notification contains a one-tap **Bluetooth settings** action.

---

## Permissions

| Permission | Why | Required? |
|---|---|---|
| `BLUETOOTH`, `BLUETOOTH_ADMIN` | Detect adapter state; disable on Android ≤12. | Required |
| `BLUETOOTH_CONNECT` | Read connected device names (Android 12+); never used for scanning (flagged `neverForLocation`). | Required on 12+ |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Keep the countdown visible and accurate while the screen is off. | Required |
| `POST_NOTIFICATIONS` | Show countdown + completion notifications (Android 13+). | Required on 13+ |
| `SCHEDULE_EXACT_ALARM` | Fire the timer at the exact minute in Doze. Granted via the system exact-alarm consent screen. | Required for reliable timing (Setup screen walks you through it) |
| `RECEIVE_BOOT_COMPLETED` | Re-arm the alarm after reboot. | Required |
| `WAKE_LOCK` | Short lock while the completion sequence runs. | Required |
| `VIBRATE` | Haptic on completion. | Required |
| **Notification Listener** (optional) | Access active MediaSessions for more reliable pause. | Optional; KEYCODE_MEDIA_PAUSE works without it |
| **Accessibility Service** (optional) | Auto-tap the Bluetooth toggle in Settings on Android 13+. | Optional; never enabled silently |

`INTERNET` is **not** requested. The app works fully offline.

---

## Build & Install

### Prerequisites
- Android Studio **Hedgehog (2023.1)** or newer (Koala / Ladybug recommended).
- Android SDK 34 installed through SDK Manager.
- JDK 17 (Android Studio bundles it).

### Steps

1. **Open the project** in Android Studio:
   `File → Open…` and select the `sleepoff/` folder.
2. Wait for Gradle sync to finish (first sync downloads Compose BOM
   2024.09.00, Kotlin 2.0.20, AGP 8.5.2).
3. Either:
   - Connect a device / start an emulator and click **Run ▶**, or
   - Build a release APK from the command line:
     ```bash
     cd sleepoff
     ./gradlew assembleRelease
     # output: app/build/outputs/apk/release/app-release.apk
     ```
   (You can skip real-code signing for local testing by installing the
   debug build: `./gradlew installDebug`.)
4. On first launch, the **Setup** screen walks you through the permissions
   you need to grant for reliable overnight behaviour.

### Minimum device requirements
- Android 8.0 or later (API 26+).
- Bluetooth is optional — SleepOff works as a plain music-pause timer on
  devices without a Bluetooth adapter.

---

## Music App Compatibility

SleepOff's pause path works with **any player that publishes an Android
MediaSession**, which includes:
- Spotify
- YouTube Music / YouTube
- VLC
- Apple Music
- SoundCloud
- Pandora
- Tidal
- Google Play Music / local music apps

Two paths are used:
1. **MediaSession API** (when notification listener is granted): calls
   `transportControls.pause()` on the actively-playing controller.
2. **Media key event fallback**: sends `KEYCODE_MEDIA_PAUSE` through
   `AudioManager.dispatchMediaKeyEvent`, which is the same mechanism
   Bluetooth headsets use.

If a player does neither (rare), SleepOff records "Music could not be paused"
in the History — it never claims success.

---

## Shutdown Modes

| Mode            | Pauses music? | Fades volume? | Turns Bluetooth off? |
|-----------------|---------------|---------------|----------------------|
| Music Only      | Yes           | No            | No                   |
| Music + Bluetooth | Yes         | No            | Yes (where allowed)  |
| Bluetooth Only  | No            | No            | Yes (where allowed)  |
| Gentle Shutdown | Yes           | Yes (final N minutes) | Yes (where allowed) |

---

## Testing Instructions

These tests cover every edge case called out in the spec. You can run them
manually against a debug build.

### Core countdown (do these first)
1. **Start a short timer.** Select 15 minutes, tap Start. Verify:
   - Circular ring and countdown update every second.
   - Exact shutdown time is shown in HH:mm under the ring.
   - Persistent notification appears and shows remaining time + end time.
   - Notification has Pause and Cancel actions.
2. **Pause / resume.** While running, tap Pause from the UI or notification.
   Ring freezes. Resume. Timer continues from where it left off.
3. **Extend.** Tap +5 / +10 / +15 while running; remaining time and end
   time increase accordingly.
4. **Cancel.** Cancel, confirm dialog; notification is removed; state
   returns to idle.

### Background / lockscreen reliability
5. **Screen off.** Start a 2-minute timer, turn the screen off. Wait until
   the deadline. Confirm music is paused (and BT off if applicable) and the
   completion notification appears.
6. **App swiped away (process killed).** Start a 3-minute timer, swipe the
   app away from recents. The foreground service keeps the countdown
   notification alive; on deadline the completion sequence runs.
7. **Doze mode.** Start a 10-minute timer, leave the screen off and
   unplugged. The exact alarm must fire on time (you can verify by setting
   a short timer to be sure).
8. **Phone locked.** Start a timer, lock the phone; confirm the ongoing
   notification persists and can be acted on from the lock screen.

### Bluetooth
9. **Connected device.** With Bluetooth on and a headphone/speaker
   connected, verify the name appears on the Home card.
10. **Bluetooth off before timer.** If Bluetooth is off when you start in a
    BT-touching mode, SleepOff skips the BT step and notes this in the
    completion notification ("Bluetooth untouched").
11. **Android 13+ restriction.** On Android 13+, start a Music+Bluetooth
    timer with a very short duration. Expect music to pause, then either:
    (a) you're taken to Bluetooth settings where you tap the toggle, or
    (b) if you enabled the helper, the helper taps it for you. History
    must reflect the actual result.
12. **Unexpected disconnect.** Disconnect your BT device mid-timer. The
    Bluetooth card updates live. Timer still pauses music at deadline.

### Music players
13. **Spotify / YouTube Music / VLC.** For each, start playback, start a
    1-minute timer. Verify playback pauses at deadline. Try both with and
    without notification-listener access.
14. **Music player closed mid-timer.** Start a timer in Music Only mode,
    force-close the music app. Completion fires; pause is best-effort and
    history notes the result (no crash).

### Reboot / persistence
15. **Reboot while RUNNING.** Start a long timer (e.g. 60 min), reboot the
    phone. After boot the timer notification reappears; if you leave it
    running, completion fires at the original wall-clock time. (If the
    deadline already passed during reboot, the completion sequence runs
    immediately on boot.)
16. **Reboot while PAUSED.** Pause the timer, reboot. The resume
    notification appears; the timer does NOT auto-fire.
17. **Reboot + daily schedule.** Enable a daily schedule, reboot. The
    schedule fires the next day at the set time.

### Settings / history
18. **Battery saver mode.** Enable battery saver in Configure. Verify
    countdown notification updates less often. Timer still fires at
    deadline (may be a few seconds late in deep Doze — documented).
19. **Restore volume.** Enable restore volume, run a Gentle Shutdown.
    Volume fades to 0, media pauses, and volume returns to the previous
    level after ~1.5s.
20. **Custom duration.** Go to Configure, enter hours/minutes, save.
    Start uses the custom time.
21. **History.** After a few completions, open the History screen. Each
    entry shows mode, duration, and honest outcomes for music + BT.
    "Clear history" empties the list.
22. **Last-used memory.** Kill and relaunch the app. The last-used
    duration and mode are preselected.

### Edge cases
23. **Battery saver system-wide.** Start a 2-minute timer, enable
    system battery saver. Timer still fires.
24. **Exact-alarm permission revoked** (Android 12+). Revoke "Alarms &
    reminders" in system settings, start a timer. The Setup screen prompts
    you to re-grant; if you don't, the timer falls back to an inexact
    alarm (which fires close to the deadline).
25. **Bluetooth disabled mid-fade.** In Gentle mode, turn Bluetooth off
    manually during the fade; the timer still pauses music.
26. **Prevent accidental cancel.** The Cancel button shows a confirmation
    dialog; the notification Cancel action cancels immediately (since it's
    already a deliberate action from a persistent notification).

---

## Why AlarmManager and not WorkManager?

`WorkManager` is for deferrable tasks. A sleep timer is the opposite:
the user demands that it fires at an exact wall-clock time, even when the
device is in deep Doze. `AlarmManager.setExactAndAllowWhileIdle` is the
correct primitive, with a `BroadcastReceiver` that starts a foreground
service to do the actual work. This is the same pattern Google recommends
for clock alarms.

---

## License / Notes

SleepOff is provided as-is for personal use. It contains no tracking, no
ads, and no network code. The optional Notification Listener and
Accessibility services do nothing unless explicitly enabled by the user in
system Settings, and only act when a timer has just finished.
