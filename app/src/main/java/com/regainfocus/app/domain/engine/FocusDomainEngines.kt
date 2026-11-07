package com.regainfocus.app.domain.engine

import com.regainfocus.app.data.local.FocusSessionEntity
import com.regainfocus.app.domain.model.AchievementProgress
import com.regainfocus.app.domain.model.PersistedTimerSessionState
import com.regainfocus.app.domain.model.SessionOutcome
import com.regainfocus.app.domain.model.StrictEnforcementLevel
import com.regainfocus.app.domain.model.TimerModeType
import com.regainfocus.app.domain.model.TimerPhase
import java.time.Instant
import java.time.ZoneId
import kotlin.math.max

/**
 * Pure, deterministic calculation of remaining time & anti-clock-tamper deadlines.
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
                        PhaseTransitionResult(TimerPhase.LONG_BREAK, currentCycle, longBreakDurationMillis, false)
                    } else {
                        PhaseTransitionResult(TimerPhase.COMPLETED, currentCycle, 0L, true)
                    }
                } else {
                    if (shortBreakDurationMillis > 0L) {
                        PhaseTransitionResult(TimerPhase.SHORT_BREAK, currentCycle, shortBreakDurationMillis, false)
                    } else {
                        PhaseTransitionResult(TimerPhase.WORK, currentCycle + 1, workDurationMillis, false)
                    }
                }
            }
            TimerPhase.SHORT_BREAK -> {
                PhaseTransitionResult(TimerPhase.WORK, currentCycle + 1, workDurationMillis, false)
            }
            TimerPhase.LONG_BREAK, TimerPhase.COMPLETED, TimerPhase.IDLE -> {
                PhaseTransitionResult(TimerPhase.COMPLETED, currentCycle, 0L, true)
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
            TimerModeType.STOPWATCH -> workDurationMillis
        }
    }
}

/**
 * Policy-compliant foreground package evaluator.
 *
 * Security & Google Play Policy Invariants:
 * - Permanently allowlists emergency dialers, telecom, System UI, System Settings, Package Installer,
 *   Permission Controller, Google Play Store / Play Protect, active keyboards, and Home Launchers.
 * - NEVER interferes with System Settings, Permission revocation, Play Protect, or app uninstallation.
 */
object StrictBlockerPolicyEngine {

    private val ESSENTIAL_SYSTEM_ALLOWLIST = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.incallui",
        "com.android.server.telecom",
        "com.google.android.dialer",
        "com.android.dialer",
        "com.samsung.android.incallui",
        "com.samsung.android.dialer",
        "com.android.phone",
        "com.android.emergency",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.vending",
        "com.google.android.gms"
    )

    sealed class InterceptionDecision {
        data object Allow : InterceptionDecision()
        data class BlockApp(val packageName: String, val reason: BlockReason) : InterceptionDecision()
    }

    enum class BlockReason {
        STRICT_FOCUS_SESSION,
        FLEXIBLE_FOCUS_SESSION,
        DAILY_APP_LIMIT_EXCEEDED
    }

    fun evaluateForegroundEvent(
        selfPackageName: String,
        foregroundPackage: String,
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

        // 1. Active Focus Session check
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

            if (stillWithinSession && foregroundPackage in sessionState.blockedPackages) {
                return InterceptionDecision.BlockApp(
                    packageName = foregroundPackage,
                    reason = if (isStrict) BlockReason.STRICT_FOCUS_SESSION else BlockReason.FLEXIBLE_FOCUS_SESSION
                )
            }
        }

        // 2. Per-App Daily Screen-Time Limit check
        if (foregroundPackage in exceededDailyLimitPackages) {
            return InterceptionDecision.BlockApp(
                packageName = foregroundPackage,
                reason = BlockReason.DAILY_APP_LIMIT_EXCEEDED
            )
        }

        return InterceptionDecision.Allow
    }
}

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
            AchievementProgress("first_focus", "First Spark", "Complete your first focus session", completedSessions, 1, completedSessions >= 1, "Milestone"),
            AchievementProgress("focus_1h", "1 Hour Reclaimed", "Accumulate 60 minutes of deep focus", totalFocusMinutes, 60, totalFocusMinutes >= 60, "Hours"),
            AchievementProgress("focus_10h", "Deep Scholar (10h)", "Accumulate 10 hours of verified study time", totalFocusHours, 10, totalFocusHours >= 10, "Hours"),
            AchievementProgress("focus_50h", "Focus Architect (50h)", "Accumulate 50 hours of focused work", totalFocusHours, 50, totalFocusHours >= 50, "Hours"),
            AchievementProgress("focus_100h", "Centurion (100h)", "Reach 100 total hours of distraction-free focus", totalFocusHours, 100, totalFocusHours >= 100, "Hours"),
            AchievementProgress("streak_3", "3-Day Momentum", "Maintain a 3-day focus streak", bestStreak, 3, bestStreak >= 3, "Streak"),
            AchievementProgress("streak_7", "Full Week Discipline", "Maintain a 7-day focus streak", bestStreak, 7, bestStreak >= 7, "Streak"),
            AchievementProgress("streak_30", "30-Day Unstoppable", "Maintain a 30-day focus streak", bestStreak, 30, bestStreak >= 30, "Streak"),
            AchievementProgress("strict_5", "Iron Resolve", "Finish 5 Strict Mode sessions with zero early exits", strictCompleted, 5, strictCompleted >= 5, "Strict"),
            AchievementProgress("shield_25", "Distraction Slayer", "Block 25 distracting app launch attempts", totalInterceptions, 25, totalInterceptions >= 25, "Shield")
        )
    }
}
