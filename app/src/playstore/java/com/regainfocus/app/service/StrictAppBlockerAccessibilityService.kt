package com.regainfocus.app.service

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.regainfocus.app.RegainFocusApplication
import com.regainfocus.app.data.preferences.ActiveStrictSessionStore
import com.regainfocus.app.domain.engine.StrictBlockerPolicyEngine
import com.regainfocus.app.domain.engine.StrictRemainingTimeCalculator
import com.regainfocus.app.ui.blocker.StrictBlockShieldActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Policy-hardened AccessibilityService for the `playstore` distribution flavor.
 *
 * Google Play Accessibility API Policy Compliance:
 * 1. `android:canRetrieveWindowContent="false"` — Cannot read screen text, UI hierarchy nodes,
 *    passwords, messages, or financial information.
 * 2. `android:accessibilityEventTypes="typeWindowStateChanged"` — Receives ONLY top-level
 *    foreground package transitions (`event.packageName`).
 * 3. Only acts after the user has explicitly accepted the standalone Prominent In-App Disclosure
 *    (`hasAcceptedAccessibilityDisclosure == true`) and enabled the service in Android Settings.
 * 4. Never interferes with Android System Settings, Accessibility Settings, or app uninstallation.
 */
class StrictAppBlockerAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lastInterceptPackage: String = ""
    private var lastInterceptRealtimeMillis: Long = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val eventPkg = event.packageName?.toString().orEmpty()
        if (eventPkg.isBlank() || eventPkg == packageName) return

        val app = application as? RegainFocusApplication ?: return
        val container = app.container
        val sessionState = container.activeSessionStore.readSnapshotSync()
        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val bootId = ActiveStrictSessionStore.currentBootCountId(this)

        if (sessionState.isStrictActive &&
            StrictRemainingTimeCalculator.isStrictSessionExpired(sessionState, nowWall, nowElapsed, bootId)
        ) {
            FocusTimerService.triggerExpiryCheck(this)
            return
        }

        val launchers = container.usageRepository.getLauncherPackages()
        val imes = container.usageRepository.getImePackages()

        scope.launch(Dispatchers.Default) {
            val settings = container.productivityRepository.settingsFlow.first()
            if (!settings.hasAcceptedAccessibilityDisclosure) return@launch

            val enabledLimits = container.database.appLimitDao().getEnabledLimitsOnce()
            val exceededPackages = container.usageRepository.getExceededDailyLimitPackages(enabledLimits)

            val decision = StrictBlockerPolicyEngine.evaluateForegroundEvent(
                selfPackageName = packageName,
                foregroundPackage = eventPkg,
                launcherPackages = launchers,
                imePackages = imes,
                sessionState = sessionState,
                currentWallMillis = nowWall,
                currentElapsedRealtimeMillis = nowElapsed,
                currentBootId = bootId,
                exceededDailyLimitPackages = exceededPackages
            )

            if (decision is StrictBlockerPolicyEngine.InterceptionDecision.BlockApp) {
                enforceBlock(decision, sessionState)
            }
        }
    }

    private suspend fun enforceBlock(
        decision: StrictBlockerPolicyEngine.InterceptionDecision.BlockApp,
        sessionState: com.regainfocus.app.domain.model.PersistedTimerSessionState
    ) {
        val app = application as? RegainFocusApplication ?: return
        val container = app.container
        val nowElapsed = SystemClock.elapsedRealtime()

        val shouldRecordMetric = decision.packageName != lastInterceptPackage ||
            (nowElapsed - lastInterceptRealtimeMillis) > 1_400L

        if (shouldRecordMetric) {
            lastInterceptPackage = decision.packageName
            lastInterceptRealtimeMillis = nowElapsed

            if (sessionState.isActive) {
                val updated = sessionState.copy(interceptionCount = sessionState.interceptionCount + 1)
                container.activeSessionStore.saveState(updated)
            }
            val label = container.usageRepository.getAppLabel(decision.packageName)
            container.productivityRepository.recordInterception(
                sessionId = sessionState.sessionId.takeIf { it > 0L },
                packageName = decision.packageName,
                appLabel = label,
                level = sessionState.strictLevel
            )
        }

        val reasonText = when (decision.reason) {
            StrictBlockerPolicyEngine.BlockReason.STRICT_FOCUS_SESSION ->
                "Restricted during your active Strict Focus Session."
            StrictBlockerPolicyEngine.BlockReason.FLEXIBLE_FOCUS_SESSION ->
                "Paused during your active Focus Session."
            StrictBlockerPolicyEngine.BlockReason.DAILY_APP_LIMIT_EXCEEDED ->
                "Daily screen-time budget reached for today."
        }

        performGlobalAction(GLOBAL_ACTION_HOME)
        StrictBlockShieldActivity.launch(
            context = this@StrictAppBlockerAccessibilityService,
            blockedPackage = decision.packageName,
            reasonLabel = reasonText
        )
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
