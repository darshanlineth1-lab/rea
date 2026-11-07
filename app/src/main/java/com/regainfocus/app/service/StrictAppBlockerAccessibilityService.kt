package com.regainfocus.app.service

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.regainfocus.app.RegainFocusApplication
import com.regainfocus.app.data.preferences.ActiveStrictSessionStore
import com.regainfocus.app.domain.engine.StrictBlockerPolicyEngine
import com.regainfocus.app.domain.engine.StrictRemainingTimeCalculator
import com.regainfocus.app.ui.blocker.StrictBlockShieldActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Primary real-time window & multi-window blocker service (Mode A + Mode B).
 *
 * Privacy guarantee:
 * - Reads ONLY foreground package names, activity class names (for Settings tamper guard), and
 *   specific short-form feed container resource IDs when Shorts/Reels blocking is enabled.
 * - Never reads or stores text content, messages, keystrokes, or personal data.
 * - Never clears active session state in `onDestroy()`, ensuring session survival across restarts.
 */
class StrictAppBlockerAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lastInterceptPackage: String = ""
    private var lastInterceptRealtimeMillis: Long = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val app = application as? RegainFocusApplication ?: return
        val container = app.container
        val sessionState = container.activeSessionStore.readSnapshotSync()
        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val bootId = ActiveStrictSessionStore.currentBootCountId(this)

        // If an active strict session just reached 00:00, trigger clean release immediately
        if (sessionState.isStrictActive &&
            StrictRemainingTimeCalculator.isStrictSessionExpired(sessionState, nowWall, nowElapsed, bootId)
        ) {
            FocusTimerService.triggerExpiryCheck(this)
            return
        }

        val eventPkg = event?.packageName?.toString().orEmpty()
        val eventCls = event?.className?.toString()

        val launchers = container.usageRepository.getLauncherPackages()
        val imes = container.usageRepository.getImePackages()

        // Evaluate primary foreground event
        if (eventPkg.isNotBlank()) {
            evaluateAndEnforce(
                targetPackage = eventPkg,
                targetClass = eventCls,
                launchers = launchers,
                imes = imes
            )
        }

        // Multi-window / Split-screen / Floating-window inspection during Strict Mode
        if (sessionState.isActive && sessionState.blockSplitScreen) {
            val interactiveWindows = runCatching { windows }.getOrNull().orEmpty()
            for (win in interactiveWindows) {
                if (win.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                    val winPkg = win.root?.packageName?.toString().orEmpty()
                    if (winPkg.isNotBlank() && winPkg != eventPkg) {
                        evaluateAndEnforce(
                            targetPackage = winPkg,
                            targetClass = null,
                            launchers = launchers,
                            imes = imes
                        )
                    }
                }
            }
        }

        // Optional Short-Form Video (YouTube Shorts / Instagram Reels) Feed Interception
        if (sessionState.isActive && sessionState.blockShortsAndReels && eventPkg.isNotBlank()) {
            if (eventPkg in SHORT_FORM_HOST_PACKAGES && eventPkg !in sessionState.blockedPackages) {
                val root = runCatching { rootInActiveWindow }.getOrNull()
                if (root != null && containsShortFormFeedNode(root, depth = 0)) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
            }
        }
    }

    private fun evaluateAndEnforce(
        targetPackage: String,
        targetClass: String?,
        launchers: Set<String>,
        imes: Set<String>
    ) {
        val app = application as? RegainFocusApplication ?: return
        val container = app.container
        val sessionState = container.activeSessionStore.readSnapshotSync()
        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val bootId = ActiveStrictSessionStore.currentBootCountId(this)

        scope.launch(Dispatchers.Default) {
            val enabledLimits = container.database.appLimitDao().getEnabledLimitsOnce()
            val exceededPackages = container.usageRepository.getExceededDailyLimitPackages(enabledLimits)

            val decision = StrictBlockerPolicyEngine.evaluateForegroundEvent(
                selfPackageName = packageName,
                foregroundPackage = targetPackage,
                foregroundClassName = targetClass,
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

        // Deduplicate rapid window state events within 1.4s for interception counter accuracy
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
                "Locked by active Strict Focus Session (${sessionState.strictLevel.badge})."
            StrictBlockerPolicyEngine.BlockReason.FLEXIBLE_FOCUS_SESSION ->
                "Paused during your active Focus Session."
            StrictBlockerPolicyEngine.BlockReason.SETTINGS_SELF_GUARD ->
                "System Settings tampering is guarded while Strict Mode is active."
            StrictBlockerPolicyEngine.BlockReason.DAILY_APP_LIMIT_EXCEEDED ->
                "Daily screen-time limit reached for today."
        }

        // Immediately push blocked app out of foreground and display Focus Shield
        performGlobalAction(GLOBAL_ACTION_HOME)
        StrictBlockShieldActivity.launch(
            context = this@StrictAppBlockerAccessibilityService,
            blockedPackage = decision.packageName,
            reasonLabel = reasonText
        )
    }

    private fun containsShortFormFeedNode(node: AccessibilityNodeInfo?, depth: Int): Boolean {
        if (node == null || depth > 7) return false
        if (StrictBlockerPolicyEngine.isShortFormVideoNodeId(node.viewIdResourceName)) {
            return true
        }
        for (i in 0 until node.childCount) {
            if (containsShortFormFeedNode(node.getChild(i), depth + 1)) {
                return true
            }
        }
        return false
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private val SHORT_FORM_HOST_PACKAGES = setOf(
            "com.google.android.youtube",
            "com.instagram.android",
            "com.snapchat.android"
        )
    }
}
