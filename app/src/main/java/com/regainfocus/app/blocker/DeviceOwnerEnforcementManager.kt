package com.regainfocus.app.blocker

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import com.regainfocus.app.data.repository.InstalledAppsAndUsageRepository
import com.regainfocus.app.domain.model.DeviceEnforcementStatus
import com.regainfocus.app.receiver.FocusDeviceAdminReceiver
import com.regainfocus.app.service.StrictAppBlockerAccessibilityService

/**
 * Legitimate Android enforcement manager supporting:
 * - Mode A (Standard Strict Mode): Explicit user-consented AccessibilityService + UsageStatsManager + Overlay Shield + optional DeviceAdmin.
 * - Mode B (Device Owner Mode): Official Android DevicePolicyManager APIs (`setPackagesSuspended`,
 *   `setUninstallBlocked`, `addUserRestriction` for `DISALLOW_APPS_CONTROL` / `DISALLOW_UNINSTALL_APPS` /
 *   `DISALLOW_SAFE_BOOT`, and `setLockTaskPackages` / Lock Task Kiosk Mode).
 *
 * Strictly uses 100% public Android SDK APIs — zero hidden APIs, zero root, zero exploits.
 */
class DeviceOwnerEnforcementManager(
    context: Context,
    private val usageRepository: InstalledAppsAndUsageRepository
) {
    private val appContext = context.applicationContext
    private val deviceProtectedContext = appContext.createDeviceProtectedStorageContext() ?: appContext
    private val prefs = deviceProtectedContext.getSharedPreferences(
        "regain_dpm_enforced_state",
        Context.MODE_PRIVATE
    )

    val adminComponent = ComponentName(appContext, FocusDeviceAdminReceiver::class.java)

    private val dpm: DevicePolicyManager?
        get() = appContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager

    fun isDeviceOwner(): Boolean {
        return runCatching { dpm?.isDeviceOwnerApp(appContext.packageName) == true }.getOrDefault(false)
    }

    fun isDeviceAdminActive(): Boolean {
        return runCatching { dpm?.isAdminActive(adminComponent) == true }.getOrDefault(false)
    }

    fun isAccessibilityEnabled(): Boolean {
        val expected = ComponentName(appContext, StrictAppBlockerAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        return enabledServices.split(':')
            .mapNotNull { ComponentName.unflattenFromString(it) }
            .any { it == expected }
    }

    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(appContext)

    fun isInLockTaskMode(): Boolean {
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        return am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }

    fun getEnforcementStatus(hasNotificationPermission: Boolean): DeviceEnforcementStatus {
        return DeviceEnforcementStatus(
            hasUsageStatsPermission = usageRepository.hasUsageStatsPermission(),
            isAccessibilityServiceEnabled = isAccessibilityEnabled(),
            canDrawOverlays = canDrawOverlays(),
            hasNotificationPermission = hasNotificationPermission,
            isDeviceAdminActive = isDeviceAdminActive(),
            isDeviceOwnerApp = isDeviceOwner(),
            isInLockTaskMode = isInLockTaskMode()
        )
    }

    /**
     * Applies Mode B (Device Owner) OS-level restrictions for the duration of a Strict Focus Session.
     * Returns true if Device Owner policies were applied.
     */
    fun applyDeviceOwnerStrictSession(
        blockedPackages: Set<String>,
        enableLockTask: Boolean
    ): Boolean {
        val manager = dpm ?: return false
        if (!isDeviceOwner()) return false

        val launchers = usageRepository.getLauncherPackages()
        val imes = usageRepository.getImePackages()
        val safeTargets = blockedPackages.filter { pkg ->
            pkg.isNotBlank() &&
                pkg != appContext.packageName &&
                pkg !in launchers &&
                pkg !in imes &&
                pkg != "com.android.systemui" &&
                pkg != "com.android.settings" &&
                pkg != "com.android.dialer" &&
                pkg != "com.google.android.dialer"
        }.toTypedArray()

        return runCatching {
            // 1. OS-level package suspension: blocked apps cannot open from launcher, recents, or intents
            if (safeTargets.isNotEmpty()) {
                manager.setPackagesSuspended(adminComponent, safeTargets, true)
                prefs.edit()
                    .putStringSet(KEY_SUSPENDED_PACKAGES, safeTargets.toSet())
                    .putBoolean(KEY_RESTRICTIONS_ACTIVE, true)
                    .commit()
            } else {
                prefs.edit().putBoolean(KEY_RESTRICTIONS_ACTIVE, true).commit()
            }

            // 2. Prevent uninstalling RegainFocus while strict timer is active
            manager.setUninstallBlocked(adminComponent, appContext.packageName, true)

            // 3. Prevent Force-Stop / Clear-Data / Uninstall / Safe-Boot bypasses during active session
            manager.addUserRestriction(adminComponent, UserManager.DISALLOW_APPS_CONTROL)
            manager.addUserRestriction(adminComponent, UserManager.DISALLOW_UNINSTALL_APPS)
            manager.addUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)

            // 4. Configure Lock Task allowlist if dedicated kiosk focus pinning was requested
            if (enableLockTask) {
                val allowedInLockTask = buildSet {
                    add(appContext.packageName)
                    add("com.android.dialer")
                    add("com.google.android.dialer")
                    add("com.android.incallui")
                }.toTypedArray()
                manager.setLockTaskPackages(adminComponent, allowedInLockTask)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    manager.setLockTaskFeatures(
                        adminComponent,
                        DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO or
                            DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD
                    )
                }
            }
            true
        }.getOrDefault(false)
    }

    fun startLockTaskIfPermitted(activity: Activity) {
        val manager = dpm ?: return
        if (isDeviceOwner() && manager.isLockTaskPermitted(appContext.packageName)) {
            runCatching { activity.startLockTask() }
        }
    }

    fun stopLockTaskIfActive(activity: Activity) {
        if (isInLockTaskMode()) {
            runCatching { activity.stopLockTask() }
        }
    }

    /**
     * Automatically and cleanly releases all Mode B (Device Owner) restrictions when the strict
     * session finishes or expires across a reboot.
     */
    fun releaseAllDeviceOwnerRestrictions(extraPackagesToUnsuspend: Set<String> = emptySet()) {
        val manager = dpm ?: return
        if (!isDeviceOwner()) {
            prefs.edit().clear().commit()
            return
        }

        val recorded = prefs.getStringSet(KEY_SUSPENDED_PACKAGES, emptySet()).orEmpty()
        val allToUnsuspend = (recorded + extraPackagesToUnsuspend)
            .filter { it.isNotBlank() && it != appContext.packageName }
            .toTypedArray()

        runCatching {
            if (allToUnsuspend.isNotEmpty()) {
                manager.setPackagesSuspended(adminComponent, allToUnsuspend, false)
            }
            manager.setUninstallBlocked(adminComponent, appContext.packageName, false)
            manager.clearUserRestriction(adminComponent, UserManager.DISALLOW_APPS_CONTROL)
            manager.clearUserRestriction(adminComponent, UserManager.DISALLOW_UNINSTALL_APPS)
            manager.clearUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)
            manager.setLockTaskPackages(adminComponent, emptyArray())
        }

        prefs.edit().clear().commit()
    }

    companion object {
        private const val KEY_SUSPENDED_PACKAGES = "suspended_packages"
        private const val KEY_RESTRICTIONS_ACTIVE = "restrictions_active"

        const val ADB_PROVISION_COMMAND =
            "adb shell dpm set-device-owner com.regainfocus.app/.receiver.FocusDeviceAdminReceiver"
    }
}
