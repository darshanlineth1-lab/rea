package com.regainfocus.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.regainfocus.app.domain.model.SessionOutcome
import com.regainfocus.app.domain.model.StrictEnforcementLevel
import com.regainfocus.app.domain.model.TimerModeType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(
    tableName = "focus_sessions",
    indices = [Index(value = ["startTimeEpochMillis"])]
)
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val modeType: TimerModeType,
    val tag: String,
    val note: String,
    val blockingGroupId: Long?,
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

@Entity(tableName = "blocking_groups")
data class BlockingGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val description: String,
    val colorHex: Long,
    val blockedPackages: List<String>,
    val defaultDurationMinutes: Int,
    val strictByDefault: Boolean,
    val blockShortsAndReels: Boolean,
    val createdAtMillis: Long
)

@Entity(tableName = "app_daily_limits")
data class AppDailyLimitEntity(
    @PrimaryKey val packageName: String,
    val appLabel: String,
    val dailyLimitMinutes: Int,
    val isEnabled: Boolean,
    val strictLockedUntilMillis: Long = 0L
)

@Entity(tableName = "focus_schedules")
data class FocusScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val daysOfWeekMask: Int, // Bit 0 = Monday .. Bit 6 = Sunday
    val blockingGroupId: Long?,
    val strictMode: Boolean,
    val isEnabled: Boolean,
    val lastTriggeredEpochDay: Long = -1L
)

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val category: String,
    val targetMinutesPerDay: Int,
    val colorHex: Long,
    val createdAtMillis: Long,
    val isArchived: Boolean = false
)

@Entity(
    tableName = "habit_completions",
    indices = [Index(value = ["habitId", "dateEpochDay"], unique = true)]
)
data class HabitCompletionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val habitId: Long,
    val dateEpochDay: Long,
    val completedMinutes: Int,
    val isCompleted: Boolean,
    val updatedAtMillis: Long
)

@Entity(
    tableName = "interception_events",
    indices = [Index(value = ["timestampMillis"])]
)
data class InterceptionEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: Long?,
    val packageName: String,
    val appLabel: String,
    val timestampMillis: Long,
    val enforcementLevel: StrictEnforcementLevel
)

class RegainRoomConverters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromStringList(list: List<String>): String = json.encodeToString(list)

    @TypeConverter
    fun toStringList(raw: String): List<String> =
        if (raw.isBlank()) emptyList() else runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())

    @TypeConverter
    fun fromTimerMode(mode: TimerModeType): String = mode.name

    @TypeConverter
    fun toTimerMode(raw: String): TimerModeType =
        runCatching { TimerModeType.valueOf(raw) }.getOrDefault(TimerModeType.POMODORO)

    @TypeConverter
    fun fromStrictLevel(level: StrictEnforcementLevel): String = level.name

    @TypeConverter
    fun toStrictLevel(raw: String): StrictEnforcementLevel =
        runCatching { StrictEnforcementLevel.valueOf(raw) }.getOrDefault(StrictEnforcementLevel.NONE)

    @TypeConverter
    fun fromOutcome(outcome: SessionOutcome): String = outcome.name

    @TypeConverter
    fun toOutcome(raw: String): SessionOutcome =
        runCatching { SessionOutcome.valueOf(raw) }.getOrDefault(SessionOutcome.COMPLETED)
}
