package com.regainfocus.app.domain.engine

import com.regainfocus.app.data.local.FocusSessionEntity
import com.regainfocus.app.domain.model.AchievementProgress
import com.regainfocus.app.domain.model.PersistedTimerSessionState
import com.regainfocus.app.domain.model.SessionOutcome
import com.regainfocus.app.domain.model.StrictEnforcementLevel
import com.regainfocus.app.domain.model.TimerModeType
import com.regainfocus.app.domain.model.TimerPhase
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max

/**
 * Pure, deterministic, unit-tested calculation of remaining time & anti-clock-tamper deadlines.
 *
 * Within the same device boot session (`currentBootId == state.bootAnchorId`), monotonic
 * `elapsedRealtimeMillis` is authoritative so advancing the system wall clock in Android Settings
 * CANNOT prematurely end an active Strict Mode session. Across a device reboot (`bootAnchorId`
 * changed), `phaseEndWallMillis - currentWallMillis` recovers remaining session time accurately.
 */
object StrictRemainingTimeCalculator {

    fun computePhaseRemainingMillis(
        state: PersistedTimerSessionState,
        currentWallMillis: Long,
        currentElapsedRealtimeMillis: Long,
        currentBootId: String
    ): Long {
        if (!state.isActive) return 0L
        if (state.timerMode == TimerModeType.STOPWATCH) {
            return if (state.isPaused) {
                state.stopwatchAccumulatedMillis
            } else if (state.bootAnchorId == currentBootId && state.stopwatchBaseElapsedRealtimeMillis > 0L) {
                state.stopwatchAccumulatedMillis + max(0L, currentElapsedRealtimeMillis - state.stopwatchBaseElapsedRealtimeMillis)
            } else {
                max(0L, currentWallMillis - state.phaseStartWallMillis) + state.stopwatchAccumulatedMillis
            }
        }
        if (state.isPaused && state.strictLevel == StrictEnforcementLevel.NONE) {
            return max(0L, state.pausedRemainingMillis)
        }
        return if (state.bootAnchorId.isNotEmpty() && state.bootAnchorId == currentBootId && state.phaseEndElapsedRealtimeMillis > 0L) {
            max(0L, state.phaseEndElapsedRealtimeMillis - currentElapsedRealtimeMillis)
        } else {
            max(0L, state.phaseEndWallMillis - currentWallMillis)
        }
    }

    fun computeStrictTotalRemainingMillis(
        state: PersistedTimerSessionState,
        currentWallMillis: Long,
        currentElapsedRealtimeMillis: Long,
        currentBootId: String
    ): Long {
        if (!state.isStrictActive) return 0L
        return if (state.bootAnchorId.isNotEmpty() && state.bootAnchorId == currentBootId && state.strictHardEndElapsedRealtimeMillis > 0L) {
            max(0L, state.strictHardEndElapsedRealtimeMillis - currentElapsedRealtimeMillis)
        } else {
            max(0L, state.strictHardEndWallMillis - currentWallMillis)
        }
    }

    fun isStrictSessionExpired(
        state: PersistedTimerSessionState,
        currentWallMillis: Long,
        currentElapsedRealtimeMillis: Long,
        currentBootId: String
    ): Boolean {
        if (!state.isStrictActive) return true
        return computeStrictTotalRemainingMillis(
            state = state,
            currentWallMillis = currentWallMillis,
            currentElapsedRealtimeMillis = currentElapsedRealtimeMillis,
            currentBootId = currentBootId
        ) <= 0L
    }
}

/**
 * Deterministic state transitions for Pomodoro cycles, Custom Countdown, and Stopwatch.
 */
object PomodoroPhaseEngine {

    data class PhaseTransitionResult(
        val nextPhase: TimerPhase,
        val nextCycle: Int,
        val nextDurationMillis: Long,
        val isSessionCompleted: Boolean
    )

    fun nextPomodoroPhase(
        currentPhase: TimerPhase,
        currentCycle: Int,
        totalCycles: Int,
        workDurationMillis: Long,
        shortBreakDurationMillis: Long,
        longBreakDurationMillis: Long
    ): PhaseTransitionResult {
        return when (currentPhase) {
            TimerPhase.WORK -> {
                if (currentCycle >= totalCycles) {
                    if (longBreakDurationMillis > 0L) {
                        PhaseTransitionResult(
                            nextPhase = TimerPhase.LONG_BREAK,
                            nextCycle = currentCycle,
                            nextDurationMillis = longBreakDurationMillis,
                            isSessionCompleted = false
                        )
                    } else {
                        PhaseTransitionResult(
                            nextPhase = TimerPhase.COMPLETED,
                            nextCycle = currentCycle,
                            nextDurationMillis = 0L,
                            isSessionCompleted = true
                        )
                    }
                } else {
                    if (shortBreakDurationMillis > 0L) {
                        PhaseTransitionResult(
                            nextPhase = TimerPhase.SHORT_BREAK,
                            nextCycle = currentCycle,
                            nextDurationMillis = shortBreakDurationMillis,
                            isSessionCompleted = false
                        )
                    } else {
                        PhaseTransitionResult(
                            nextPhase = TimerPhase.WORK,
                            nextCycle = currentCycle + 1,
                            nextDurationMillis = workDurationMillis,
                            isSessionCompleted = false
                        )
                    }
                }
            }
            TimerPhase.SHORT_BREAK -> {
                PhaseTransitionResult(
                    nextPhase = TimerPhase.WORK,
                    nextCycle = currentCycle + 1,
                    nextDurationMillis = workDurationMillis,
                    isSessionCompleted = false
                )
            }
            TimerPhase.LONG_BREAK, TimerPhase.COMPLETED, TimerPhase.IDLE -> {
                PhaseTransitionResult(
                    nextPhase = TimerPhase.COMPLETED,
                    nextCycle = currentCycle,
                    nextDurationMillis = 0L,
                    isSessionCompleted = true
                )
            }
        }
    }

    fun calculateTotalStrictDurationMillis(
        mode: TimerModeType,
        workDurationMillis: Long,
        shortBreakDurationMillis: Long,
        longBreakDurationMillis: Long,
        totalCycles: Int
    ): Long {
        return when (mode) {
            TimerModeType.COUNTDOWN -> workDurationMillis
            TimerModeType.POMODORO -> {
                val cycles = totalCycles.coerceAtLeast(1)
                val totalWork = workDurationMillis * cycles
                val totalShortBreaks = shortBreakDurationMillis * (cycles - 1).coerceAtLeast(0)
                totalWork + totalShortBreaks + longBreakDurationMillis
            }
            TimerModeType.STOPWATCH -> workDurationMillis // Minimum strict commitment window if enabled on stopwatch
        }
    }
}

/**
 * Pure policy engine deciding whether a foreground window/package/view must be intercepted.
 * Never blocks emergency dialer, telecom, system UI, or RegainFocus itself.
 */
object StrictBlockerPolicyEngine {

    private val ESSENTIAL_SYSTEM_ALLOWLIST = setOf(
        "com.android.systemui",
        "com.android.incallui",
        "com.android.server.telecom",
        "com.google.android.dialer",
        "com.android.dialer",
        "com.samsung.android.incallui",
        "com.samsung.android.dialer",
        "com.android.phone",
        "com.android.emergency",
        "android"
    )

    private val SETTINGS_PACKAGES = setOf(
        "com.android.settings",
        "com.samsung.accessibility",
        "com.miui.securitycenter",
        "com.coloros.safecenter"
    )

    private val DANGEROUS_SETTINGS_CLASS_HINTS = listOf(
        "InstalledAppDetails",
        "SpaActivity",
        "ManageApplications",
        "AccessibilitySettings",
        "ToggleAccessibilityService",
        "DeviceAdminAdd",
        "DeviceAdminSettings",
        "UninstallAppProgress",
        "DateTimeSettings"
    )

    private val SHORTS_AND_REELS_VIEW_IDS = listOf(
        "com.google.android.youtube:id/reel_player_page_container",
        "com.google.android.youtube:id/reel_recycler",
        "com.instagram.android:id/clips_viewer_view_pager",
        "com.instagram.android:id/reel_viewer_root",
        "com.snapchat.android:id/spotlight_view"
    )

    sealed class InterceptionDecision {
        data object Allow : InterceptionDecision()
        data class BlockApp(val packageName: String, val reason: BlockReason) : InterceptionDecision()
        data class BlockInAppShortFeed(val packageName: String) : InterceptionDecision()
    }

    enum class BlockReason {
        STRICT_FOCUS_SESSION,
        FLEXIBLE_FOCUS_SESSION,
        SETTINGS_SELF_GUARD,
        DAILY_APP_LIMIT_EXCEEDED
    }

    fun evaluateForegroundEvent(
        selfPackageName: String,
        foregroundPackage: String,
        foregroundClassName: String?,
        launcherPackages: Set<String>,
        imePackages: Set<String>,
        sessionState: PersistedTimerSessionState,
        currentWallMillis: Long,
        currentElapsedRealtimeMillis: Long,
        currentBootId: String,
        exceededDailyLimitPackages: Set<String>
    ): InterceptionDecision {
        if (foregroundPackage.isBlank() || foregroundPackage == selfPackageName) {
            return InterceptionDecision.Allow
        }
        if (foregroundPackage in ESSENTIAL_SYSTEM_ALLOWLIST ||
            foregroundPackage in launcherPackages ||
            foregroundPackage in imePackages
        ) {
            return InterceptionDecision.Allow
        }

        // 1. Check active Focus Session blocking (Strict Mode keeps blocking across entire session, including breaks)
        if (sessionState.isActive) {
            val isStrict = sessionState.strictLevel != StrictEnforcementLevel.NONE
            val stillWithinSession = if (isStrict) {
                !StrictRemainingTimeCalculator.isStrictSessionExpired(
                    sessionState,
                    currentWallMillis,
                    currentElapsedRealtimeMillis,
                    currentBootId
                )
            } else {
                !sessionState.isPaused && sessionState.phase == TimerPhase.WORK
            }

            if (stillWithinSession) {
                // Guard Settings pages against impulsive Force-Stop / Accessibility revocation / Clock tamper during Strict Mode
                if (isStrict && sessionState.guardSettings && foregroundPackage in SETTINGS_PACKAGES) {
                    val cls = foregroundClassName.orEmpty()
                    if (cls.isEmpty() || DANGEROUS_SETTINGS_CLASS_HINTS.any { cls.contains(it, ignoreCase = true) }) {
                        return InterceptionDecision.BlockApp(
                            packageName = foregroundPackage,
                            reason = BlockReason.SETTINGS_SELF_GUARD
                        )
                    }
                }

                if (foregroundPackage in sessionState.blockedPackages) {
                    return InterceptionDecision.BlockApp(
                        packageName = foregroundPackage,
                        reason = if (isStrict) BlockReason.STRICT_FOCUS_SESSION else BlockReason.FLEXIBLE_FOCUS_SESSION
                    )
                }
            }
        }

        // 2. Check Daily App Limits
        if (foregroundPackage in exceededDailyLimitPackages) {
            return InterceptionDecision.BlockApp(
                packageName = foregroundPackage,
                reason = BlockReason.DAILY_APP_LIMIT_EXCEEDED
            )
        }

        return InterceptionDecision.Allow
    }

    fun isShortFormVideoNodeId(viewResourceId: String?): Boolean {
        if (viewResourceId.isNullOrBlank()) return false
        return SHORTS_AND_REELS_VIEW_IDS.any { viewResourceId.contains(it, ignoreCase = true) }
    }
}

/**
 * Pure analytics engine computing streaks, goal completion, and achievements from real FocusSessionEntity records.
 */
object StreakAndAchievementEngine {

    data class StreakSummary(
        val currentStreakDays: Int,
        val longestStreakDays: Int,
        val activeEpochDays: Set<Long>,
        val goalMetEpochDays: Set<Long>
    )

    fun computeStreaks(
        sessions: List<FocusSessionEntity>,
        todayEpochDay: Long,
        dailyGoalMinutes: Int,
        zoneId: ZoneId = ZoneId.systemDefault(),
        minimumMinutesForStreakDay: Int = 10
    ): StreakSummary {
        val minutesByDay = mutableMapOf<Long, Long>()
        for (s in sessions) {
            if (s.effectiveWorkSeconds <= 0L) continue
            val day = Instant.ofEpochMilli(s.startTimeEpochMillis)
                .atZone(zoneId)
                .toLocalDate()
                .toEpochDay()
            minutesByDay[day] = (minutesByDay[day] ?: 0L) + (s.effectiveWorkSeconds / 60L)
        }

        val activeDays = minutesByDay.filterValues { it >= minimumMinutesForStreakDay }.keys
        val goalDays = minutesByDay.filterValues { it >= dailyGoalMinutes.coerceAtLeast(1) }.keys

        if (activeDays.isEmpty()) {
            return StreakSummary(0, 0, emptySet(), goalDays)
        }

        val sorted = activeDays.sorted()
        var longest = 1
        var run = 1
        for (i in 1 until sorted.size) {
            if (sorted[i] == sorted[i - 1] + 1L) {
                run++
                if (run > longest) longest = run
            } else {
                run = 1
            }
        }

        // Current streak counts backwards from today (or yesterday if today isn't logged yet)
        var cursor = when {
            todayEpochDay in activeDays -> todayEpochDay
            (todayEpochDay - 1L) in activeDays -> todayEpochDay - 1L
            else -> Long.MIN_VALUE
        }
        var current = 0
        while (cursor != Long.MIN_VALUE && cursor in activeDays) {
            current++
            cursor -= 1L
        }

        return StreakSummary(
            currentStreakDays = current,
            longestStreakDays = longest,
            activeEpochDays = activeDays,
            goalMetEpochDays = goalDays
        )
    }

    fun computeAchievements(
        sessions: List<FocusSessionEntity>,
        streakSummary: StreakSummary,
        totalInterceptions: Int
    ): List<AchievementProgress> {
        val completedSessions = sessions.count {
            it.outcome == SessionOutcome.COMPLETED || it.outcome == SessionOutcome.RECOVERED_COMPLETED
        }
        val strictCompleted = sessions.count {
            (it.outcome == SessionOutcome.COMPLETED || it.outcome == SessionOutcome.RECOVERED_COMPLETED) &&
                it.strictEnforcementMode != StrictEnforcementLevel.NONE
        }
        val totalFocusMinutes = (sessions.sumOf { it.effectiveWorkSeconds } / 60L).toInt()
        val totalFocusHours = totalFocusMinutes / 60
        val bestStreak = streakSummary.longestStreakDays

        return listOf(
            AchievementProgress(
                id = "first_focus",
                title = "First Spark",
                description = "Complete your first focus session",
                currentValue = completedSessions,
                targetValue = 1,
                isUnlocked = completedSessions >= 1,
                badgeCategory = "Milestone"
            ),
            AchievementProgress(
                id = "focus_1h",
                title = "1 Hour Reclaimed",
                description = "Accumulate 60 minutes of deep focus",
                currentValue = totalFocusMinutes,
                targetValue = 60,
                isUnlocked = totalFocusMinutes >= 60,
                badgeCategory = "Hours"
            ),
            AchievementProgress(
                id = "focus_10h",
                title = "Deep Scholar (10h)",
                description = "Accumulate 10 hours of verified study time",
                currentValue = totalFocusHours,
                targetValue = 10,
                isUnlocked = totalFocusHours >= 10,
                badgeCategory = "Hours"
            ),
            AchievementProgress(
                id = "focus_50h",
                title = "Focus Architect (50h)",
                description = "Accumulate 50 hours of focused work",
                currentValue = totalFocusHours,
                targetValue = 50,
                isUnlocked = totalFocusHours >= 50,
                badgeCategory = "Hours"
            ),
            AchievementProgress(
                id = "focus_100h",
                title = "Centurion (100h)",
                description = "Reach 100 total hours of distraction-free focus",
                currentValue = totalFocusHours,
                targetValue = 100,
                isUnlocked = totalFocusHours >= 100,
                badgeCategory = "Hours"
            ),
            AchievementProgress(
                id = "streak_3",
                title = "3-Day Momentum",
                description = "Maintain a 3-day focus streak",
                currentValue = bestStreak,
                targetValue = 3,
                isUnlocked = bestStreak >= 3,
                badgeCategory = "Streak"
            ),
            AchievementProgress(
                id = "streak_7",
                title = "Full Week Discipline",
                description = "Maintain a 7-day focus streak",
                currentValue = bestStreak,
                targetValue = 7,
                isUnlocked = bestStreak >= 7,
                badgeCategory = "Streak"
            ),
            AchievementProgress(
                id = "streak_30",
                title = "30-Day Unstoppable",
                description = "Maintain a 30-day focus streak",
                currentValue = bestStreak,
                targetValue = 30,
                isUnlocked = bestStreak >= 30,
                badgeCategory = "Streak"
            ),
            AchievementProgress(
                id = "strict_5",
                title = "Iron Resolve",
                description = "Finish 5 Strict Mode sessions with zero early exits",
                currentValue = strictCompleted,
                targetValue = 5,
                isUnlocked = strictCompleted >= 5,
                badgeCategory = "Strict"
            ),
            AchievementProgress(
                id = "shield_25",
                title = "Distraction Slayer",
                description = "Block 25 distracting app launch attempts",
                currentValue = totalInterceptions,
                targetValue = 25,
                isUnlocked = totalInterceptions >= 25,
                badgeCategory = "Shield"
            )
        )
    }
}
