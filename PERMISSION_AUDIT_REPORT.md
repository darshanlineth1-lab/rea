# RegainFocus — Security, Permission & Google Play Protect Audit Report (`v1.1.0`)

## 1. Executive Summary & Root Cause of the Google Play Protect Block

When `apk/app-release.apk` (`v1.0.0`, commit `f7c13b2`) was downloaded via a mobile browser or file manager and opened for sideload installation, **Google Play Protect Enhanced Fraud Protection (EFP)** blocked installation with:

> *"App blocked to protect your device. This app can request access to sensitive data. This can increase the risk of identity theft or financial fraud."*

### Concrete Root Cause (Verified from Decoded Binary `AndroidManifest.xml` & Official Google Documentation)
Per Google's official developer guidance ([Google Play Protect Warning Developer Guidance](https://developers.google.com/android/play-protect/warning-dev-guidance)), **Google Play Protect Enhanced Fraud Protection automatically blocks installation of any APK sideloaded from an internet-download source** (browsers, messaging apps, or file managers — particularly enforced in India and other EFP-enabled regions) if its merged `AndroidManifest.xml` declares **any** of these four high-risk privileges:

| EFP Trigger Permission / Service | Present in `v1.0.0` `app-release.apk`? | Present in `v1.1.0` `apk/app-release.apk` (`sideload`)? | Present in `v1.1.0` `apk/app-playstore-accessibility-release.apk` (`playstore`)? |
| :--- | :---: | :---: | :---: |
| `android.permission.READ_SMS` | **No** | **No** | **No** |
| `android.permission.RECEIVE_SMS` | **No** | **No** | **No** |
| `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE` | **No** | **No** | **No** |
| **`android.permission.BIND_ACCESSIBILITY_SERVICE`** | **YES — EXACT TRIGGER** (`StrictAppBlockerAccessibilityService`) | **NO (Removed)** | **Yes** *(Hardened for Google Play review / `adb install` only)* |

### Secondary Policy & Heuristic Triggers Also Found and Fixed
In addition to the deterministic `BIND_ACCESSIBILITY_SERVICE` EFP internet-sideloading block, our code and binary audit uncovered five secondary policy/heuristic issues in `v1.0.0`:
1. **Unapproved Device Admin / DPC Receiver (`BIND_DEVICE_ADMIN`)**: `FocusDeviceAdminReceiver` declared `android.permission.BIND_DEVICE_ADMIN`, `ACTION_PROFILE_PROVISIONING_COMPLETE`, and `<force-lock />`. Google's Play Protect documentation explicitly notes that unapproved Device Policy Controllers trigger the exact same *"App blocked to protect your device"* warning during provisioning and heightened PHA scrutiny.
2. **Over-Privileged Accessibility Configuration (`canRetrieveWindowContent="true"`)**: `accessibility_service_config.xml` requested `android:canRetrieveWindowContent="true"`, `flagReportViewIds`, and `flagRetrieveInteractiveWindows` — granting the capability to read on-screen text and UI view hierarchies across all apps even though RegainFocus only needs the foreground package name.
3. **Self-Protection / Settings Interception (`StrictBlockerPolicyEngine`)**: `StrictBlockerPolicyEngine.kt` previously blocked `com.android.settings` sub-screens (`AccessibilitySettings`, `InstalledAppDetails`, `UninstallAppProgress`, `DeviceAdminAdd`). Blocking users from revoking permissions or uninstalling an app violates Google's **Mobile Unwanted Software (MUwS)** and **Potentially Harmful Application (PHA)** policies.
4. **Missing Standalone Prominent Disclosure Dialog**: Clicking permission buttons in `OnboardingScreen`, `FocusScreen`, `HomeScreen`, `AppsScreen`, and `SettingsScreen` previously jumped straight to `Settings.ACTION_ACCESSIBILITY_SETTINGS` / `ACTION_USAGE_ACCESS_SETTINGS` without first presenting a standalone, two-button affirmative consent dialog.
5. **Broad Restricted Permissions (`QUERY_ALL_PACKAGES` & `SCHEDULE_EXACT_ALARM`) & Unminified Release Build**: `v1.0.0` declared `QUERY_ALL_PACKAGES`, `SCHEDULE_EXACT_ALARM`, unused `VIBRATE`, transitive `ACCESS_NETWORK_STATE` (injected by `androidx.work`), and shipped with `isMinifyEnabled = false` and `DebugProbesKt.bin`.

---

## 2. Complete Merged `AndroidManifest.xml` Permission Audit (`v1.0.0` vs `v1.1.0`)

Every `<uses-permission>`, `<permission>`, `<service>`, and `<receiver>` in the compiled APKs was inspected via `aapt2 dump permissions` and `aapt2 dump xmltree`:

| Permission / Component | Source | `v1.0.0` | `v1.1.0` (`sideload` `app-release.apk`) | `v1.1.0` (`playstore` edition) | Audit Action & Technical Justification |
| :--- | :--- | :---: | :---: | :---: | :--- |
| `BIND_ACCESSIBILITY_SERVICE` (`StrictAppBlockerAccessibilityService`) | App Manifest | Declared | **Removed** | Declared (`canRetrieveWindowContent="false"`) | **Primary EFP trigger.** Removed from `sideload` APK so browser/file-manager installs are not blocked by EFP; isolated to `playstore` flavor with strict minimization. |
| `BIND_DEVICE_ADMIN` (`FocusDeviceAdminReceiver`) | App Manifest | Declared | **Removed** | **Removed** | Unnecessary for consumer app blocking; triggers unapproved DPC / PHA flags. Deleted `FocusDeviceAdminReceiver.kt` & `device_admin_policies.xml`. |
| `QUERY_ALL_PACKAGES` | App Manifest | Declared | **Removed** | **Removed** | Replaced with narrow Android 11+ `<queries>` (`ACTION_MAIN` + `CATEGORY_LAUNCHER` & `CATEGORY_HOME`), which suffices to list user-launchable apps and launchers. |
| `SCHEDULE_EXACT_ALARM` | App Manifest | Declared | **Removed** | **Removed** | Unnecessary. `FocusTimerService` runs as an active foreground service and uses `AlarmManager.setAndAllowWhileIdle()` (which requires zero exact-alarm permission). |
| `ACCESS_NETWORK_STATE` | Transitive (`androidx.work:work-runtime`) | Injected | **Removed** (`tools:node="remove"`) | **Removed** (`tools:node="remove"`) | RegainFocus has **zero `INTERNET` permission** and runs 100% offline; stripped via `tools:node="remove"`. |
| `VIBRATE` | App Manifest | Declared | **Removed** | **Removed** | Unused in application code; removed. |
| `SYSTEM_ALERT_WINDOW` | Flavor (`sideload` only) | Declared | **Retained (`sideload` only)** | **Removed** | Required in `sideload` flavor so Android 10–15 Background Activity Launch (BAL) rules permit `FocusTimerService` to launch `StrictBlockShieldActivity` over blocked apps without `AccessibilityService`. Omitted in `playstore` flavor. |
| `PACKAGE_USAGE_STATS` | App Manifest | Declared | **Retained** | **Retained** | Essential for real per-app screen-time analytics and `UsageStatsManager` active-package detection. Protected by standalone Prominent Disclosure dialog. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | App Manifest | Declared | **Retained** | **Retained** | Required on Android 9–15 (`targetSdk 35`) to run the user-initiated Focus Timer countdown and synthesized ambient focus audio (`AudioTrack`). |
| `POST_NOTIFICATIONS` | App Manifest | Declared | **Retained** | **Retained** | Standard Android 13+ runtime permission for live countdown notifications and optional study goal reminders. |
| `RECEIVE_BOOT_COMPLETED` | App Manifest | Declared | **Retained** | **Retained** | Restores active focus timers and `WorkManager` daily study reminders after device reboot. |
| `WAKE_LOCK` | Transitive (`androidx.work:work-runtime`) | Declared | Transitive (Normal) | Transitive (Normal) | Normal-level (`protectionLevel=normal`) permission merged by `androidx.work` for `SystemAlarmService` background constraint execution; removed explicit app-level declaration. |
| `com.regainfocus.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | Transitive (`androidx.core:core-ktx`) | Injected | Signature | Signature | AndroidX `signature`-level internal broadcast isolation for Android 13+. Safe and standard. |
| `INTERNET` | None | **Absent** | **Absent** | **Absent** | RegainFocus cannot open network sockets or transmit any user data off-device. |

---

## 3. How Essential App-Blocking Functionality Was Preserved & Architectural Tradeoffs

Per requirement #6 (*"Do NOT remove essential app-blocking functionality without explaining the consequences"*), `RegainFocus v1.1.0` uses two Gradle product flavors (`sideload` and `playstore`) so **both** internet-sideloading users and Play Store / ADB users retain working distraction blocking, with full transparency about the technical differences:

### A. Primary Sideload Release (`apk/app-release.apk` — `sideloadRelease`)
* **How it blocks distracting apps without `AccessibilityService`**:
  1. The user explicitly reviews two standalone Prominent Disclosure dialogs and grants **Usage Access** (`PACKAGE_USAGE_STATS`) and **Display Over Other Apps** (`SYSTEM_ALERT_WINDOW`).
  2. During an active Focus or Strict Focus Session, `FocusTimerService` runs a lightweight **350ms foreground watchdog** querying `UsageStatsManager.queryEvents()` (`ACTIVITY_RESUMED` transitions).
  3. When a user-selected distracting app comes to the foreground, `StrictBlockerPolicyEngine` evaluates the package and launches `StrictBlockShieldActivity` (`FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP`). On Android 10–15, holding `SYSTEM_ALERT_WINDOW` grants the official Android OS Background Activity Launch (BAL) exemption required for a foreground service to bring `StrictBlockShieldActivity` to the top.
* **Consequences & Tradeoffs of Removing `AccessibilityService` in `sideload`**:
  * **Advantage**: Eliminates `BIND_ACCESSIBILITY_SERVICE` from `AndroidManifest.xml`, removing the exact trigger of Google Play Protect Enhanced Fraud Protection's automatic internet-sideloading block, and eliminates any OS-level capability to inspect window hierarchies.
  * **Tradeoffs**:
    1. **Detection Latency (~350ms vs ~50ms)**: `UsageStatsManager` polls every 350ms during active sessions rather than receiving instant `TYPE_WINDOW_STATE_CHANGED` pushes, so a blocked app's splash frame may briefly appear for ~0.1–0.35 seconds before the Focus Shield covers it.
    2. **Session-Scoped Enforcement**: In `sideload`, real-time blocking runs while `FocusTimerService` is active in the foreground; background 24/7 per-app daily-limit blocking when no focus timer is running requires either starting a Focus Session or using the `playstore` Accessibility edition.
    3. **No Sub-Screen Shorts/Reels View Scraping**: Because neither flavor reads internal UI view IDs (`canRetrieveWindowContent="false"`), blocking operates cleanly at the **whole-app package level** (e.g., blocking YouTube or Instagram as a whole during study sessions rather than scraping internal tab nodes).

### B. Play Store / ADB Accessibility Edition (`apk/app-playstore-accessibility-release.apk` — `playstoreRelease`)
* Retains `StrictAppBlockerAccessibilityService` for instant event-driven blocking (`TYPE_WINDOW_STATE_CHANGED`) and passive Daily App Limit enforcement, while **omitting** `SYSTEM_ALERT_WINDOW`.
* **Warning for Direct Browser Downloads**: Because this flavor declares `BIND_ACCESSIBILITY_SERVICE`, downloading it directly via Chrome/WhatsApp/Files on an EFP-enabled Android device **will still trigger** Google Play Protect's Enhanced Fraud Protection internet-sideloading block unless installed via **Google Play** or developer **`adb install`**.

---

## 4. `AccessibilityService` Policy Hardening & Explicit User Consent (`v1.1.0`)

1. **`canRetrieveWindowContent="false"`**: Verified in `app/src/playstore/res/xml/accessibility_service_config.xml`. The service cannot inspect `AccessibilityNodeInfo` trees, screen text, OTPs, passwords, or financial UI.
2. **Minimal Event & Flag Scope**: `android:accessibilityEventTypes="typeWindowStateChanged"` and `android:accessibilityFlags="flagDefault"` (removed `typeWindowContentChanged`, `flagReportViewIds`, and `flagRetrieveInteractiveWindows`).
3. **No Deceptive `isAccessibilityTool` Attribute**: Google's Play Protect documentation warns that setting `android:isAccessibilityTool="true"` on apps not primarily designed for users with disabilities triggers the *Deceptive Accessibility Tool Warning*. `isAccessibilityTool` is strictly omitted.
4. **Zero Interference with System Settings or Uninstallation**: `StrictBlockerPolicyEngine` permanently allowlists `com.android.settings`, `com.android.vending`, `com.google.android.gms`, `com.google.android.permissioncontroller`, `com.android.permissioncontroller`, `com.google.android.packageinstaller`, emergency dialers, keyboards, and home launchers.
5. **Mandatory Standalone Two-Button Prominent Disclosure (`PermissionDisclosureDialogs.kt`)**:
   * Before **any** screen (`OnboardingScreen`, `HomeScreen`, `FocusScreen`, `AppsScreen`, `SettingsScreen`) opens `ACTION_USAGE_ACCESS_SETTINGS`, `ACTION_MANAGE_OVERLAY_PERMISSION`, or `ACTION_ACCESSIBILITY_SETTINGS`, `MainActivity` displays `ProminentPermissionDisclosureDialog` stating:
     1. Exact data accessed (foreground app package names / timestamps only),
     2. Exact purpose (local screen-time analytics and Focus Session shield),
     3. Explicit privacy guarantee (zero `INTERNET` permission; zero off-device transmission),
     4. Two explicit user choices: **"I Agree — Open Settings"** vs **"Not Now"**, persisted in `UserSettings` (`hasAcceptedUsageAccessDisclosure`, `hasAcceptedOverlayDisclosure`, `hasAcceptedAccessibilityDisclosure`). Additionally, `StrictAppBlockerAccessibilityService` checks `settings.hasAcceptedAccessibilityDisclosure` before enforcing any block.

---

## 5. Third-Party Library & SDK Audit

Every dependency in `gradle/libs.versions.toml`, `app/build.gradle.kts`, and `META-INF/*.version` inside the compiled release APK was audited:

| Library Group | Packaged Artifacts | Network / Tracking / Sensitive Permissions | Audit Verdict |
| :--- | :--- | :--- | :--- |
| `androidx.compose.*` & `material3` | Compose BOM `2024.02.00` UI, Graphics, Material3, Navigation | None | Clean official Jetpack UI toolkit |
| `androidx.room` | `room-runtime:2.6.1`, `room-ktx:2.6.1` | None (local SQLite only) | Clean local persistence |
| `androidx.datastore` | `datastore:1.0.0` | None (local files only) | Clean local settings store |
| `androidx.work` | `work-runtime-ktx:2.9.0` | Injects `WAKE_LOCK` (retained, normal) and `ACCESS_NETWORK_STATE` (**stripped** via `tools:node="remove"`) | Clean Jetpack background scheduler |
| `androidx.core` / `lifecycle` / `activity` | `core-ktx:1.12.0`, `lifecycle-*:2.6.2`, `activity-compose:1.8.2` | Injects `signature`-level `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | Clean official AndroidX core |
| `org.jetbrains.kotlinx` | `kotlinx-coroutines-android:1.7.3`, `kotlinx-serialization-json:1.6.3` | None (`DebugProbesKt.bin` excluded in packaging) | Clean Kotlin runtime libraries |
| **Ad / Analytics / Crash / Telemetry SDKs** | **None (0 SDKs)** | **Zero** | **Zero third-party trackers, Firebase, AdMob, Sentry, or network clients** |

---

## 6. Release Signing, R8 Hardening & Important Google Play Protect Caveats

### Cryptographic Verification (`apksigner verify --verbose --print-certs`)
Both `apk/app-release.apk` (`1.4 MB`, SHA-256 `6daa1f20b5b047a4d45eb7b115d0cb4eb0b6f823150a8b2d61feb2bd100183f8`) and `apk/app-playstore-accessibility-release.apk` (`1.4 MB`, SHA-256 `d4840e4c5b091082a40fbb89b3975029a498ac38c643c7d4a023957dcc497893`) were built with R8 code shrinking (`isMinifyEnabled = true`), resource shrinking (`isShrinkResources = true`), and signed with RSA-2048 (`enableV1Signing = true`, `enableV2Signing = true`, `enableV3Signing = true`):
* **v2 (APK Signature Scheme v2)**: `true`
* **v3 (APK Signature Scheme v3)**: `true`
* **Signer Certificate DN**: `CN=RegainFocus Open Source, OU=Productivity Engineering, O=RegainFocus, L=Krishnagiri, ST=Tamil Nadu, C=IN`
* **Certificate SHA-256**: `c33be3cc770ab29ba2709caae1f03c17f1b6872968113df5e2843aac968fefb3`

### Honest Disclosure Regarding Google Play Protect & Distribution Requirements
1. **Why Rebuilding/Signing Alone Never Removes EFP Blocks**: Signing an APK with a release keystore is required by Android to install a release build, **but signing alone never exempts an internet-sideloaded APK from Google Play Protect Enhanced Fraud Protection** if `BIND_ACCESSIBILITY_SERVICE`, `BIND_NOTIFICATION_LISTENER_SERVICE`, `READ_SMS`, or `RECEIVE_SMS` is present. Removing `BIND_ACCESSIBILITY_SERVICE` in `apk/app-release.apk` (`sideloadRelease`) is what eliminates the EFP internet-sideloading trigger.
2. **Standard "Unrecognized App Developer / Scan App" Prompt on Self-Signed APKs**: Even when an APK contains zero EFP-restricted permissions (`apk/app-release.apk`), Google Play Protect evaluates signing-key reputation on newly generated self-signed developer certificates. On first sideload, Play Protect may offer *"Scan app with Play Protect"* or *"More details -> Install anyway"* for an unknown developer key until the app establishes reputation. Users should **keep Google Play Protect enabled** and allow Play Protect to scan the APK so Google's cloud scanner can verify the clean binary directly.
3. **Requirements for Publishing the `playstore` Edition on Google Play**:
   * **Play App Signing & Developer Verification**: Enroll the app in Google Play Console with Play App Signing and verified developer identity; apps installed from the Google Play Store are not treated as internet-sideloaded sources by EFP.
   * **Accessibility API Declaration & Video Walkthrough**: In **Play Console -> App content -> Sensitive permissions and APIs -> Accessibility API**, declare the core productivity/focus-blocking purpose, confirm `canRetrieveWindowContent="false"`, link a public Privacy Policy, and upload a short unedited screen-recording showing the `ProminentPermissionDisclosureDialog` appearing before `Settings.ACTION_ACCESSIBILITY_SETTINGS` opens.
   * **Official Play Protect Appeal Channel**: If a sideloaded build is ever flagged by a heuristic classification, submit the signed APK via Google's official [Play Protect Appeal Form](https://developers.google.com/android/play-protect/warning-dev-guidance) rather than instructing users to disable Play Protect.
