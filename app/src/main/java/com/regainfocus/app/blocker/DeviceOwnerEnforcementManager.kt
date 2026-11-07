package com.regainfocus.app.blocker

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.regainfocus.app.BuildConfig
import com.regainfocus.app.data.repository.InstalledAppsAndUsageRepository
import com.regainfocus.app.domain.model.DeviceEnforcementStatus

/**
 * Audits active user-granted permissions across both distribution flavors:
 * - `sideload` flavor (`HAS_ACCESSIBILITY_BLOCKER == false`): Checks Usage Access + Overlay Shield.
 * - `playstore` flavor (`HAS_ACCESSIBILITY_BLOCKER == true`): Checks AccessibilityService + Usage Access.
 *
 * Uses 100% standard public Android SDK APIs — zero DeviceAdmin/DPC enrollment, zero hidden APIs.
 */
class DeviceOwnerEnforcementManager(
    context: Context,
    private val usageRepository: InstalledAppsAndUsageRepository
) {
    private val appContext = context.applicationContext

    fun isAccessibilityEnabled(): Boolean {
        if (!BuildConfig.HAS_ACCESSIBILITY_BLOCKER) return false
        val expected = ComponentName(
            appContext.packageName,
            "com.regainfocus.app.service.StrictAppBlockerAccessibilityService"
        )
        val enabledServices = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        return enabledServices.split(':')
            .mapNotNull { ComponentName.unflattenFromString(it) }
            .any { it == expected }
    }

    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(appContext)

    fun getEnforcementStatus(hasNotificationPermission: Boolean): DeviceEnforcementStatus {
        return DeviceEnforcementStatus(
            supportsAccessibilityBlocker = BuildConfig.HAS_ACCESSIBILITY_BLOCKER,
            hasUsageStatsPermission = usageRepository.hasUsageStatsPermission(),
            isAccessibilityServiceEnabled = isAccessibilityEnabled(),
            canDrawOverlays = canDrawOverlays(),
            hasNotificationPermission = hasNotificationPermission
        )
    }
}
