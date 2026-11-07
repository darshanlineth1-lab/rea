package com.regainfocus.app.domain.model

import android.graphics.drawable.Drawable
import kotlinx.serialization.Serializable

@Serializable
enum class TimerModeType(val displayName: String) {
    POMODORO("Pomodoro"),
    COUNTDOWN("Timer"),
    STOPWATCH("Stopwatch")
}

@Serializable
enum class TimerPhase(val label: String) {
    IDLE("Ready"),
    WORK("Deep Focus"),
    SHORT_BREAK("Short Break"),
    LONG_BREAK("Long Break"),
    COMPLETED("Completed")
}

@Serializable
enum class StrictEnforcementLevel(val title: String, val badge: String) {
    NONE("Flexible Mode", "Flexible"),
    STANDARD_STRICT("Mode A: Standard Strict", "Strict · Mode A"),
    DEVICE_OWNER_STRICT("Mode B: Device Owner", "Device Owner · Mode B")
}

@Serializable
enum class SessionOutcome {
    COMPLETED,
    STOPPED_EARLY,
    RECOVERED_COMPLETED
}

@Serializable
enum class SoundscapeType(val title: String, val subtitle: String) {
    NONE("Silent", "No background sound"),
    RAIN("Gentle Rain", "Soft rainfall & warm droplets"),
    OCEAN_WAVES("Ocean Surf", "Slow rhythmic coastal waves"),
    BROWN_NOISE("Deep Brown Noise", "Low-frequency focus masking"),
    WHITE_NOISE("Soft White Noise", "Balanced acoustic masking"),
    BINAURAL_ALPHA("10 Hz Alpha Focus", "200/210 Hz stereo study tone")
}

@Serializable
enum class ThemeMode(val label: String) {
    DARK_AURORA("Aurora Dark"),
    AMOLED_DARK("AMOLED Pitch Black"),
    LIGHT_SAGE("Light Sage"),
    SYSTEM("System Default")
}

@Serializable
data class UserSettings(
    val hasCompletedOnboarding: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.DARK_AURORA,
    val dailyFocusGoalMinutes: Int = 120,
    val dailyScreenTimeGoalMinutes: Int = 180,
    val pomodoroWorkMinutes: Int = 25,
    val pomodoroShortBreakMinutes: Int = 5,
    val pomodoroLongBreakMinutes: Int = 15,
    val pomodoroCyclesBeforeLongBreak: Int = 4,
    val customCountdownMinutes: Int = 45,
    val autoStartBreaks: Boolean = true,
    val autoStartNextWork: Boolean = true,
    val selectedSoundscape: SoundscapeType = SoundscapeType.NONE,
    val soundVolume: Float = 0.55f,
    val playSoundDuringBreaks: Boolean = false,
    val defaultStrictLevel: StrictEnforcementLevel = StrictEnforcementLevel.STANDARD_STRICT,
    val guardSettingsDuringStrict: Boolean = true,
    val blockSplitScreenDuringStrict: Boolean = true,
    val useLockTaskInDeviceOwner: Boolean = false,
    val enableSessionNotifications: Boolean = true,
    val enableBreakReminders: Boolean = true,
    val enableDailyGoalReminder: Boolean = true,
    val dailyGoalReminderHour: Int = 20,
    val dailyGoalReminderMinute: Int = 0,
    val enableScheduleReminders: Boolean = true
)

@Serializable
data class PersistedTimerSessionState(
    val isActive: Boolean = false,
    val sessionId: Long = 0L,
    val timerMode: TimerModeType = TimerModeType.POMODORO,
    val phase: TimerPhase = TimerPhase.IDLE,
    val isPaused: Boolean = false,
    val sessionStartWallMillis: Long = 0L,
    val phaseStartWallMillis: Long = 0L,
    val phaseEndWallMillis: Long = 0L,
    val phaseDurationMillis: Long = 0L,
    // Monotonic boot-session anti-clock-tamper anchor
    val bootAnchorId: String = "",
    val phaseEndElapsedRealtimeMillis: Long = 0L,
    val strictHardEndWallMillis: Long = 0L,
    val strictHardEndElapsedRealtimeMillis: Long = 0L,
    val pausedRemainingMillis: Long = 0L,
    val stopwatchBaseElapsedRealtimeMillis: Long = 0L,
    val stopwatchAccumulatedMillis: Long = 0L,
    val workDurationMillis: Long = 25 * 60_000L,
    val shortBreakDurationMillis: Long = 5 * 60_000L,
    val longBreakDurationMillis: Long = 15 * 60_000L,
    val currentCycle: Int = 1,
    val totalCycles: Int = 4,
    val accumulatedWorkMillis: Long = 0L,
    val accumulatedBreakMillis: Long = 0L,
    val strictLevel: StrictEnforcementLevel = StrictEnforcementLevel.NONE,
    val blockedPackages: Set<String> = emptySet(),
    val blockShortsAndReels: Boolean = false,
    val guardSettings: Boolean = true,
    val blockSplitScreen: Boolean = true,
    val useLockTaskInDeviceOwner: Boolean = false,
    val blockingGroupId: Long? = null,
    val blockingGroupName: String? = null,
    val sessionTag: String = "Study",
    val sessionNote: String = "",
    val interceptionCount: Int = 0
) {
    val isStrictActive: Boolean
        get() = isActive && strictLevel != StrictEnforcementLevel.NONE
}

data class InstalledAppInfo(
    val packageName: String,
    val label: String,
    val category: String,
    val isSystemApp: Boolean,
    val icon: Drawable? = null
)

data class AppUsageItem(
    val packageName: String,
    val appLabel: String,
    val usageMillis: Long,
    val launchCount: Int,
    val fractionOfTotal: Float,
    val dailyLimitMinutes: Int? = null,
    val isStrictLimitLocked: Boolean = false,
    val icon: Drawable? = null
)

data class DailyUsagePoint(
    val epochDay: Long,
    val dayLabel: String,
    val screenTimeMinutes: Int,
    val focusMinutes: Int,
    val goalMinutes: Int
)

data class ScreenTimeOverview(
    val hasUsagePermission: Boolean,
    val todayTotalMillis: Long,
    val weeklyTotalMillis: Long,
    val monthlyTotalMillis: Long,
    val dailyAverageWeekMillis: Long,
    val hourlyMinutesToday: List<Int>, // 24 buckets (0..23)
    val topAppsToday: List<AppUsageItem>,
    val topAppsWeek: List<AppUsageItem>,
    val topAppsMonth: List<AppUsageItem>,
    val last7DaysScreenMinutes: List<DailyUsagePoint>,
    val last30DaysScreenMinutes: List<DailyUsagePoint>
)

data class AchievementProgress(
    val id: String,
    val title: String,
    val description: String,
    val currentValue: Int,
    val targetValue: Int,
    val isUnlocked: Boolean,
    val badgeCategory: String
) {
    val progressFraction: Float
        get() = if (targetValue <= 0) 0f else (currentValue.toFloat() / targetValue.toFloat()).coerceIn(0f, 1f)
}

data class DeviceEnforcementStatus(
    val hasUsageStatsPermission: Boolean,
    val isAccessibilityServiceEnabled: Boolean,
    val canDrawOverlays: Boolean,
    val hasNotificationPermission: Boolean,
    val isDeviceAdminActive: Boolean,
    val isDeviceOwnerApp: Boolean,
    val isInLockTaskMode: Boolean
)

@Serializable
data class BackupSessionItem(
    val modeType: TimerModeType,
    val tag: String,
    val note: String,
    val blockingGroupName: String?,
    val strictEnforcementMode: StrictEnforcementLevel,
    val startTimeEpochMillis: Long,
    val endTimeEpochMillis: Long,
    val plannedWorkSeconds: Long,
    val plannedBreakSeconds: Long,
    val effectiveWorkSeconds: Long,
    val effectiveBreakSeconds: Long,
    val completedCycles: Int,
    val interceptionCount: Int,
    val outcome: SessionOutcome
)

@Serializable
data class BackupBlockingGroupItem(
    val name: String,
    val description: String,
    val colorHex: Long,
    val blockedPackages: List<String>,
    val defaultDurationMinutes: Int,
    val strictByDefault: Boolean,
    val blockShortsAndReels: Boolean
)

@Serializable
data class BackupHabitItem(
    val title: String,
    val category: String,
    val targetMinutesPerDay: Int,
    val colorHex: Long
)

@Serializable
data class RegainBackupPayload(
    val schemaVersion: Int = 1,
    val exportedAtMillis: Long,
    val settings: UserSettings,
    val sessions: List<BackupSessionItem>,
    val blockingGroups: List<BackupBlockingGroupItem>,
    val habits: List<BackupHabitItem>
)
