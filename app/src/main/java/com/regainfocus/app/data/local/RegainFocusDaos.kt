package com.regainfocus.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM focus_sessions ORDER BY startTimeEpochMillis DESC")
    fun observeAllSessions(): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions ORDER BY startTimeEpochMillis DESC")
    suspend fun getAllSessionsOnce(): List<FocusSessionEntity>

    @Query("SELECT * FROM focus_sessions WHERE startTimeEpochMillis >= :sinceMillis ORDER BY startTimeEpochMillis DESC")
    fun observeSessionsSince(sinceMillis: Long): Flow<List<FocusSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: FocusSessionEntity): Long

    @Update
    suspend fun updateSession(session: FocusSessionEntity)

    @Query("SELECT * FROM focus_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: Long): FocusSessionEntity?

    @Query("DELETE FROM focus_sessions")
    suspend fun deleteAllSessions()
}

@Dao
interface BlockingGroupDao {
    @Query("SELECT * FROM blocking_groups ORDER BY id ASC")
    fun observeAllGroups(): Flow<List<BlockingGroupEntity>>

    @Query("SELECT * FROM blocking_groups ORDER BY id ASC")
    suspend fun getAllGroupsOnce(): List<BlockingGroupEntity>

    @Query("SELECT * FROM blocking_groups WHERE id = :id LIMIT 1")
    suspend fun getGroupById(id: Long): BlockingGroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: BlockingGroupEntity): Long

    @Update
    suspend fun updateGroup(group: BlockingGroupEntity)

    @Delete
    suspend fun deleteGroup(group: BlockingGroupEntity)

    @Query("SELECT COUNT(*) FROM blocking_groups")
    suspend fun countGroups(): Int
}

@Dao
interface AppLimitDao {
    @Query("SELECT * FROM app_daily_limits ORDER BY appLabel ASC")
    fun observeAllLimits(): Flow<List<AppDailyLimitEntity>>

    @Query("SELECT * FROM app_daily_limits WHERE isEnabled = 1")
    suspend fun getEnabledLimitsOnce(): List<AppDailyLimitEntity>

    @Query("SELECT * FROM app_daily_limits WHERE packageName = :pkg LIMIT 1")
    suspend fun getLimitForPackage(pkg: String): AppDailyLimitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLimit(limit: AppDailyLimitEntity)

    @Query("DELETE FROM app_daily_limits WHERE packageName = :pkg")
    suspend fun deleteLimit(pkg: String)

    @Query("DELETE FROM app_daily_limits")
    suspend fun deleteAllLimits()
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM focus_schedules ORDER BY startHour ASC, startMinute ASC")
    fun observeAllSchedules(): Flow<List<FocusScheduleEntity>>

    @Query("SELECT * FROM focus_schedules WHERE isEnabled = 1")
    suspend fun getEnabledSchedulesOnce(): List<FocusScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSchedule(schedule: FocusScheduleEntity): Long

    @Delete
    suspend fun deleteSchedule(schedule: FocusScheduleEntity)

    @Query("UPDATE focus_schedules SET lastTriggeredEpochDay = :epochDay WHERE id = :id")
    suspend fun markTriggered(id: Long, epochDay: Long)
}

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits WHERE isArchived = 0 ORDER BY id ASC")
    fun observeActiveHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE isArchived = 0 ORDER BY id ASC")
    suspend fun getActiveHabitsOnce(): List<HabitEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHabit(habit: HabitEntity): Long

    @Query("UPDATE habits SET isArchived = 1 WHERE id = :habitId")
    suspend fun archiveHabit(habitId: Long)

    @Query("SELECT * FROM habit_completions ORDER BY dateEpochDay DESC")
    fun observeAllCompletions(): Flow<List<HabitCompletionEntity>>

    @Query("SELECT * FROM habit_completions WHERE habitId = :habitId AND dateEpochDay = :epochDay LIMIT 1")
    suspend fun getCompletion(habitId: Long, epochDay: Long): HabitCompletionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCompletion(completion: HabitCompletionEntity)

    @Query("DELETE FROM habits")
    suspend fun deleteAllHabits()

    @Query("DELETE FROM habit_completions")
    suspend fun deleteAllCompletions()
}

@Dao
interface InterceptionDao {
    @Query("SELECT * FROM interception_events ORDER BY timestampMillis DESC LIMIT 200")
    fun observeRecentInterceptions(): Flow<List<InterceptionEventEntity>>

    @Query("SELECT COUNT(*) FROM interception_events")
    fun observeTotalInterceptionCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM interception_events")
    suspend fun getTotalInterceptionCountOnce(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInterception(event: InterceptionEventEntity)

    @Query("DELETE FROM interception_events")
    suspend fun deleteAllInterceptions()
}
