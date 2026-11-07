package com.regainfocus.app

import com.regainfocus.app.data.local.FocusSessionEntity
import com.regainfocus.app.data.local.RegainRoomConverters
import com.regainfocus.app.data.preferences.TimerSessionStateSerializer
import com.regainfocus.app.data.preferences.UserSettingsSerializer
import com.regainfocus.app.domain.engine.PomodoroPhaseEngine
import com.regainfocus.app.domain.engine.StrictBlockerPolicyEngine
import com.regainfocus.app.domain.engine.StrictRemainingTimeCalculator
import com.regainfocus.app.domain.engine.StreakAndAchievementEngine
import com.regainfocus.app.domain.model.PersistedTimerSessionState
import com.regainfocus.app.domain.model.SessionOutcome
import com.regainfocus.app.domain.model.SoundscapeType
import com.regainfocus.app.domain.model.StrictEnforcementLevel
import com.regainfocus.app.domain.model.ThemeMode
import com.regainfocus.app.domain.model.TimerModeType
import com.regainfocus.app.domain.model.TimerPhase
import com.regainfocus.app.domain.model.UserSettings
import com.regainfocus.app.util.TimeFormatters
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneOffset

class FocusEngineAndPolicyTest {

    @Test
    fun `strict timer resists manual system clock forward jump within same boot session`() {
        val bootId = "boot_42"
        val startWall = 1_700_000_000_000L
        val startElapsed = 100_000L
        val thirtyMin = 30 * 60_000L

        val strictState = PersistedTimerSessionState(
            isActive = true,
            timerMode = TimerModeType.COUNTDOWN,
            phase = TimerPhase.WORK,
            bootAnchorId = bootId,
            sessionStartWallMillis = startWall,
            phaseStartWallMillis = startWall,
            phaseDurationMillis = thirtyMin,
            phaseEndWallMillis = startWall + thirtyMin,
            phaseEndElapsedRealtimeMillis = startElapsed + thirtyMin,
            strictHardEndWallMillis = startWall + thirtyMin,
            strictHardEndElapsedRealtimeMillis = startElapsed + thirtyMin,
            strictLevel = StrictEnforcementLevel.STANDARD_STRICT,
            blockedPackages = setOf("com.instagram.android")
        )

        val tamperedWallClock = startWall + (5 * 3_600_000L)
        val realElapsedAfter2Min = startElapsed + (2 * 60_000L)

        val remaining = StrictRemainingTimeCalculator.computeStrictTotalRemainingMillis(
            state = strictState,
            currentWallMillis = tamperedWallClock,
            currentElapsedRealtimeMillis = realElapsedAfter2Min,
            currentBootId = bootId
        )

        assertEquals(28 * 60_000L, remaining)
        assertFalse(
            StrictRemainingTimeCalculator.isStrictSessionExpired(
                strictState,
                tamperedWallClock,
                realElapsedAfter2Min,
                bootId
            )
        )
    }

    @Test
    fun `strict timer recovers remaining time accurately across device reboot`() {
        val preRebootId = "boot_42"
        val postRebootId = "boot_43"
        val startWall = 1_700_000_000_000L
        val fortyFiveMin = 45 * 60_000L

        val strictState = PersistedTimerSessionState(
            isActive = true,
            timerMode = TimerModeType.COUNTDOWN,
            phase = TimerPhase.WORK,
            bootAnchorId = preRebootId,
            phaseEndWallMillis = startWall + fortyFiveMin,
            phaseEndElapsedRealtimeMillis = 5_000_000L,
            strictHardEndWallMillis = startWall + fortyFiveMin,
            strictHardEndElapsedRealtimeMillis = 5_000_000L,
            strictLevel = StrictEnforcementLevel.STANDARD_STRICT,
            blockedPackages = setOf("com.google.android.youtube")
        )

        val wallAfterReboot = startWall + (15 * 60_000L)
        val freshBootElapsed = 25_000L

        val remaining = StrictRemainingTimeCalculator.computeStrictTotalRemainingMillis(
            state = strictState,
            currentWallMillis = wallAfterReboot,
            currentElapsedRealtimeMillis = freshBootElapsed,
            currentBootId = postRebootId
        )

        assertEquals(30 * 60_000L, remaining)
        assertFalse(
            StrictRemainingTimeCalculator.isStrictSessionExpired(
                strictState,
                wallAfterReboot,
                freshBootElapsed,
                postRebootId
            )
        )

        val wallExpired = startWall + (46 * 60_000L)
        assertTrue(
            StrictRemainingTimeCalculator.isStrictSessionExpired(
                strictState,
                wallExpired,
                freshBootElapsed + 31 * 60_000L,
                postRebootId
            )
        )
    }

    @Test
    fun `blocker policy blocks selected apps across strict breaks and never blocks system settings or play protect`() {
        val bootId = "boot_1"
        val nowWall = 1_700_000_000_000L
        val nowElapsed = 50_000L

        val strictDuringBreak = PersistedTimerSessionState(
            isActive = true,
            timerMode = TimerModeType.POMODORO,
            phase = TimerPhase.SHORT_BREAK,
            bootAnchorId = bootId,
            phaseEndWallMillis = nowWall + 300_000L,
            phaseEndElapsedRealtimeMillis = nowElapsed + 300_000L,
            strictHardEndWallMillis = nowWall + 1_800_000L,
            strictHardEndElapsedRealtimeMillis = nowElapsed + 1_800_000L,
            strictLevel = StrictEnforcementLevel.STANDARD_STRICT,
            blockedPackages = setOf("com.instagram.android", "com.android.settings", "com.android.vending")
        )

        // 1. Distracting target app is blocked during Strict Mode Short Break
        val igDecision = StrictBlockerPolicyEngine.evaluateForegroundEvent(
            selfPackageName = "com.regainfocus.app",
            foregroundPackage = "com.instagram.android",
            launcherPackages = setOf("com.google.android.apps.nexuslauncher"),
            imePackages = setOf("com.google.android.inputmethod.latin"),
            sessionState = strictDuringBreak,
            currentWallMillis = nowWall,
            currentElapsedRealtimeMillis = nowElapsed,
            currentBootId = bootId,
            exceededDailyLimitPackages = emptySet()
        )
        assertTrue(igDecision is StrictBlockerPolicyEngine.InterceptionDecision.BlockApp)

        // 2. System Settings, Google Play / Play Protect, Permission Controller & Emergency Dialer are NEVER blocked
        for (protectedPkg in listOf(
            "com.android.settings",
            "com.android.vending",
            "com.google.android.permissioncontroller",
            "com.google.android.dialer"
        )) {
            val decision = StrictBlockerPolicyEngine.evaluateForegroundEvent(
                selfPackageName = "com.regainfocus.app",
                foregroundPackage = protectedPkg,
                launcherPackages = emptySet(),
                imePackages = emptySet(),
                sessionState = strictDuringBreak,
                currentWallMillis = nowWall,
                currentElapsedRealtimeMillis = nowElapsed,
                currentBootId = bootId,
                exceededDailyLimitPackages = setOf(protectedPkg)
            )
            assertEquals(StrictBlockerPolicyEngine.InterceptionDecision.Allow, decision)
        }
    }

    @Test
    fun `pomodoro phase engine cycles accurately through work short breaks and long break`() {
        val work = 25 * 60_000L
        val shortB = 5 * 60_000L
        val longB = 15 * 60_000L

        val totalStrict = PomodoroPhaseEngine.calculateTotalStrictDurationMillis(
            mode = TimerModeType.POMODORO,
            workDurationMillis = work,
            shortBreakDurationMillis = shortB,
            longBreakDurationMillis = longB,
            totalCycles = 4
        )
        assertEquals(130 * 60_000L, totalStrict)

        val step1 = PomodoroPhaseEngine.nextPomodoroPhase(TimerPhase.WORK, 1, 4, work, shortB, longB)
        assertEquals(TimerPhase.SHORT_BREAK, step1.nextPhase)
        assertEquals(1, step1.nextCycle)
        assertFalse(step1.isSessionCompleted)

        val step2 = PomodoroPhaseEngine.nextPomodoroPhase(step1.nextPhase, step1.nextCycle, 4, work, shortB, longB)
        assertEquals(TimerPhase.WORK, step2.nextPhase)
        assertEquals(2, step2.nextCycle)

        val finalWork = PomodoroPhaseEngine.nextPomodoroPhase(TimerPhase.WORK, 4, 4, work, shortB, longB)
        assertEquals(TimerPhase.LONG_BREAK, finalWork.nextPhase)
        assertFalse(finalWork.isSessionCompleted)

        val afterLong = PomodoroPhaseEngine.nextPomodoroPhase(TimerPhase.LONG_BREAK, 4, 4, work, shortB, longB)
        assertEquals(TimerPhase.COMPLETED, afterLong.nextPhase)
        assertTrue(afterLong.isSessionCompleted)
    }

    @Test
    fun `streak and achievement engine calculates real streaks and unlocks badges`() {
        val zone = ZoneOffset.UTC
        val baseDate = LocalDate.of(2026, 11, 6)
        val sessions = (0 until 7).map { offset ->
            val dayStart = baseDate.minusDays(offset.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
            FocusSessionEntity(
                id = (offset + 1).toLong(),
                modeType = TimerModeType.POMODORO,
                tag = "Study",
                note = "",
                blockingGroupId = 1L,
                blockingGroupName = "Deep Exam Lock",
                strictEnforcementMode = StrictEnforcementLevel.STANDARD_STRICT,
                startTimeEpochMillis = dayStart + 3_600_000L,
                endTimeEpochMillis = dayStart + 9_000_000L,
                plannedWorkSeconds = 5_400L,
                plannedBreakSeconds = 900L,
                effectiveWorkSeconds = 5_400L,
                effectiveBreakSeconds = 900L,
                completedCycles = 3,
                interceptionCount = 4,
                outcome = SessionOutcome.COMPLETED
            )
        }

        val streaks = StreakAndAchievementEngine.computeStreaks(
            sessions = sessions,
            todayEpochDay = baseDate.toEpochDay(),
            dailyGoalMinutes = 60,
            zoneId = zone
        )
        assertEquals(7, streaks.currentStreakDays)
        assertEquals(7, streaks.longestStreakDays)
        assertEquals(7, streaks.goalMetEpochDays.size)

        val achievements = StreakAndAchievementEngine.computeAchievements(
            sessions = sessions,
            streakSummary = streaks,
            totalInterceptions = 28
        ).associateBy { it.id }

        assertTrue(achievements.getValue("first_focus").isUnlocked)
        assertTrue(achievements.getValue("focus_1h").isUnlocked)
        assertTrue(achievements.getValue("focus_10h").isUnlocked)
        assertTrue(achievements.getValue("streak_7").isUnlocked)
        assertTrue(achievements.getValue("strict_5").isUnlocked)
        assertTrue(achievements.getValue("shield_25").isUnlocked)
        assertFalse(achievements.getValue("focus_50h").isUnlocked)
    }

    @Test
    fun `datastore serializers and room converters round trip cleanly`() = runBlocking {
        val settings = UserSettings(
            hasCompletedOnboarding = true,
            hasAcceptedUsageAccessDisclosure = true,
            themeMode = ThemeMode.AMOLED_DARK,
            dailyFocusGoalMinutes = 180,
            selectedSoundscape = SoundscapeType.RAIN
        )
        val out = ByteArrayOutputStream()
        UserSettingsSerializer.writeTo(settings, out)
        val restoredSettings = UserSettingsSerializer.readFrom(ByteArrayInputStream(out.toByteArray()))
        assertEquals(settings, restoredSettings)

        val session = PersistedTimerSessionState(
            isActive = true,
            strictLevel = StrictEnforcementLevel.STANDARD_STRICT,
            blockedPackages = setOf("a.b.c", "x.y.z")
        )
        val outSession = ByteArrayOutputStream()
        TimerSessionStateSerializer.writeTo(session, outSession)
        val restoredSession = TimerSessionStateSerializer.readFrom(ByteArrayInputStream(outSession.toByteArray()))
        assertEquals(session, restoredSession)

        val conv = RegainRoomConverters()
        val list = listOf("com.instagram.android", "com.google.android.youtube")
        assertEquals(list, conv.toStringList(conv.fromStringList(list)))
        assertEquals("01:05:09", TimeFormatters.formatMillisClock(3_909_000L))
    }
}
