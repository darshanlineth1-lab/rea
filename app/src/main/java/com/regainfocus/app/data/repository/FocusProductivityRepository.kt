package com.regainfocus.app.data.repository

import com.regainfocus.app.data.local.AppDailyLimitEntity
import com.regainfocus.app.data.local.BlockingGroupEntity
import com.regainfocus.app.data.local.FocusScheduleEntity
import com.regainfocus.app.data.local.FocusSessionEntity
import com.regainfocus.app.data.local.HabitCompletionEntity
import com.regainfocus.app.data.local.HabitEntity
import com.regainfocus.app.data.local.InterceptionEventEntity
import com.regainfocus.app.data.local.RegainFocusDatabase
import com.regainfocus.app.data.preferences.ActiveStrictSessionStore
import com.regainfocus.app.data.preferences.AppPreferencesStore
import com.regainfocus.app.domain.model.BackupBlockingGroupItem
import com.regainfocus.app.domain.model.BackupHabitItem
import com.regainfocus.app.domain.model.BackupSessionItem
import com.regainfocus.app.domain.model.RegainBackupPayload
import com.regainfocus.app.domain.model.StrictEnforcementLevel
import com.regainfocus.app.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.time.LocalDate

class StrictSessionMutationLockedException(message: String) : IllegalStateException(message)

class FocusProductivityRepository(
    private val database: RegainFocusDatabase,
    private val preferencesStore: AppPreferencesStore,
    private val activeSessionStore: ActiveStrictSessionStore
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    val settingsFlow: Flow<UserSettings> = preferencesStore.settingsFlow
    val sessionsFlow: Flow<List<FocusSessionEntity>> = database.sessionDao().observeAllSessions()
    val blockingGroupsFlow: Flow<List<BlockingGroupEntity>> = database.blockingGroupDao().observeAllGroups()
    val appLimitsFlow: Flow<List<AppDailyLimitEntity>> = database.appLimitDao().observeAllLimits()
    val schedulesFlow: Flow<List<FocusScheduleEntity>> = database.scheduleDao().observeAllSchedules()
    val habitsFlow: Flow<List<HabitEntity>> = database.habitDao().observeActiveHabits()
    val habitCompletionsFlow: Flow<List<HabitCompletionEntity>> = database.habitDao().observeAllCompletions()
    val totalInterceptionsFlow: Flow<Int> = database.interceptionDao().observeTotalInterceptionCount()
    val recentInterceptionsFlow: Flow<List<InterceptionEventEntity>> = database.interceptionDao().observeRecentInterceptions()

    private fun ensureNotStrictLocked(operationDescription: String) {
        val active = activeSessionStore.readSnapshotSync()
        if (active.isStrictActive) {
            throw StrictSessionMutationLockedException(
                "Cannot $operationDescription while an active Strict Focus Session is running."
            )
        }
    }

    suspend fun ensureStarterBlockingGroupsSeeded() {
        if (database.blockingGroupDao().countGroups() > 0) return
        val now = System.currentTimeMillis()
        val templates = listOf(
            BlockingGroupEntity(
                name = "Social Media Shield",
                description = "Blocks high-dopamine social feeds & messaging distractions during study.",
                colorHex = 0xFF2EE59D,
                blockedPackages = listOf(
                    "com.instagram.android",
                    "com.zhiliaoapp.musically",
                    "com.ss.android.ugc.trill",
                    "com.snapchat.android",
                    "com.facebook.katana",
                    "com.twitter.android",
                    "com.reddit.frontpage"
                ),
                defaultDurationMinutes = 25,
                strictByDefault = true,
                blockShortsAndReels = true,
                createdAtMillis = now
            ),
            BlockingGroupEntity(
                name = "Video & Reels Detox",
                description = "Blocks video streaming and entertainment apps during focus sessions.",
                colorHex = 0xFF38BDF8,
                blockedPackages = listOf(
                    "com.google.android.youtube",
                    "com.netflix.mediaclient",
                    "com.amazon.avod.thirdpartyclient",
                    "tv.twitch.android.app",
                    "in.startv.hotstar"
                ),
                defaultDurationMinutes = 45,
                strictByDefault = true,
                blockShortsAndReels = true,
                createdAtMillis = now
            ),
            BlockingGroupEntity(
                name = "Deep Exam Lock",
                description = "Strict exam-prep shield for JEE / NEET / UPSC / University study sessions.",
                colorHex = 0xFF818CF8,
                blockedPackages = listOf(
                    "com.instagram.android",
                    "com.google.android.youtube",
                    "com.zhiliaoapp.musically",
                    "com.snapchat.android",
                    "com.reddit.frontpage",
                    "com.discord"
                ),
                defaultDurationMinutes = 60,
                strictByDefault = true,
                blockShortsAndReels = true,
                createdAtMillis = now
            )
        )
        templates.forEach { database.blockingGroupDao().insertGroup(it) }
    }

    suspend fun updateSettings(
        requiresUnlockedStrictState: Boolean = false,
        transform: (UserSettings) -> UserSettings
    ) {
        if (requiresUnlockedStrictState) {
            ensureNotStrictLocked("modify strict blocking settings")
        }
        preferencesStore.updateSettings(transform)
    }

    suspend fun saveBlockingGroup(group: BlockingGroupEntity): Long {
        val active = activeSessionStore.readSnapshotSync()
        if (active.isStrictActive && active.blockingGroupId != null && active.blockingGroupId == group.id) {
            throw StrictSessionMutationLockedException("Cannot edit the active Blocking Group during a Strict Session.")
        }
        return if (group.id == 0L) {
            database.blockingGroupDao().insertGroup(group)
        } else {
            database.blockingGroupDao().updateGroup(group)
            group.id
        }
    }

    suspend fun deleteBlockingGroup(group: BlockingGroupEntity) {
        val active = activeSessionStore.readSnapshotSync()
        if (active.isStrictActive && active.blockingGroupId == group.id) {
            throw StrictSessionMutationLockedException("Cannot delete the active Blocking Group during a Strict Session.")
        }
        database.blockingGroupDao().deleteGroup(group)
    }

    suspend fun upsertAppLimit(
        packageName: String,
        appLabel: String,
        dailyLimitMinutes: Int,
        isEnabled: Boolean,
        lockStrictForHours: Int = 0
    ) {
        val existing = database.appLimitDao().getLimitForPackage(packageName)
        val now = System.currentTimeMillis()
        if (existing != null && existing.strictLockedUntilMillis > now) {
            if (!isEnabled || dailyLimitMinutes > existing.dailyLimitMinutes) {
                throw StrictSessionMutationLockedException(
                    "This app limit is Strict-Locked until its lock timer expires."
                )
            }
        }
        val lockUntil = if (lockStrictForHours > 0) {
            maxOf(existing?.strictLockedUntilMillis ?: 0L, now + lockStrictForHours * 3_600_000L)
        } else {
            existing?.strictLockedUntilMillis ?: 0L
        }
        database.appLimitDao().upsertLimit(
            AppDailyLimitEntity(
                packageName = packageName,
                appLabel = appLabel,
                dailyLimitMinutes = dailyLimitMinutes.coerceIn(1, 720),
                isEnabled = isEnabled,
                strictLockedUntilMillis = lockUntil
            )
        )
    }

    suspend fun removeAppLimit(packageName: String) {
        ensureNotStrictLocked("remove app limits")
        val existing = database.appLimitDao().getLimitForPackage(packageName)
        if (existing != null && existing.strictLockedUntilMillis > System.currentTimeMillis()) {
            throw StrictSessionMutationLockedException("Cannot delete a Strict-Locked app limit before it expires.")
        }
        database.appLimitDao().deleteLimit(packageName)
    }

    suspend fun saveSchedule(schedule: FocusScheduleEntity): Long {
        return database.scheduleDao().upsertSchedule(schedule)
    }

    suspend fun deleteSchedule(schedule: FocusScheduleEntity) {
        ensureNotStrictLocked("delete focus schedules")
        database.scheduleDao().deleteSchedule(schedule)
    }

    suspend fun saveHabit(habit: HabitEntity): Long {
        return database.habitDao().upsertHabit(habit)
    }

    suspend fun archiveHabit(habitId: Long) {
        database.habitDao().archiveHabit(habitId)
    }

    suspend fun toggleHabitToday(habit: HabitEntity, epochDay: Long = LocalDate.now().toEpochDay()) {
        val current = database.habitDao().getCompletion(habit.id, epochDay)
        val nextCompleted = !(current?.isCompleted ?: false)
        val nextMinutes = if (nextCompleted) habit.targetMinutesPerDay else 0
        database.habitDao().upsertCompletion(
            HabitCompletionEntity(
                id = current?.id ?: 0L,
                habitId = habit.id,
                dateEpochDay = epochDay,
                completedMinutes = nextMinutes,
                isCompleted = nextCompleted,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun recordSession(session: FocusSessionEntity): Long {
        return database.sessionDao().insertSession(session)
    }

    suspend fun recordInterception(
        sessionId: Long?,
        packageName: String,
        appLabel: String,
        level: StrictEnforcementLevel
    ) {
        database.interceptionDao().insertInterception(
            InterceptionEventEntity(
                sessionId = sessionId,
                packageName = packageName,
                appLabel = appLabel,
                timestampMillis = System.currentTimeMillis(),
                enforcementLevel = level
            )
        )
    }

    suspend fun exportBackupJson(): String {
        val settings = settingsFlow.first()
        val sessions = database.sessionDao().getAllSessionsOnce()
        val groups = database.blockingGroupDao().getAllGroupsOnce()
        val habits = database.habitDao().getActiveHabitsOnce()

        val payload = RegainBackupPayload(
            exportedAtMillis = System.currentTimeMillis(),
            settings = settings,
            sessions = sessions.map {
                BackupSessionItem(
                    modeType = it.modeType,
                    tag = it.tag,
                    note = it.note,
                    blockingGroupName = it.blockingGroupName,
                    strictEnforcementMode = it.strictEnforcementMode,
                    startTimeEpochMillis = it.startTimeEpochMillis,
                    endTimeEpochMillis = it.endTimeEpochMillis,
                    plannedWorkSeconds = it.plannedWorkSeconds,
                    plannedBreakSeconds = it.plannedBreakSeconds,
                    effectiveWorkSeconds = it.effectiveWorkSeconds,
                    effectiveBreakSeconds = it.effectiveBreakSeconds,
                    completedCycles = it.completedCycles,
                    interceptionCount = it.interceptionCount,
                    outcome = it.outcome
                )
            },
            blockingGroups = groups.map {
                BackupBlockingGroupItem(
                    name = it.name,
                    description = it.description,
                    colorHex = it.colorHex,
                    blockedPackages = it.blockedPackages,
                    defaultDurationMinutes = it.defaultDurationMinutes,
                    strictByDefault = it.strictByDefault,
                    blockShortsAndReels = it.blockShortsAndReels
                )
            },
            habits = habits.map {
                BackupHabitItem(
                    title = it.title,
                    category = it.category,
                    targetMinutesPerDay = it.targetMinutesPerDay,
                    colorHex = it.colorHex
                )
            }
        )
        return json.encodeToString(RegainBackupPayload.serializer(), payload)
    }

    suspend fun importBackupJson(rawJson: String) {
        ensureNotStrictLocked("import backup data")
        val payload = json.decodeFromString(RegainBackupPayload.serializer(), rawJson)
        preferencesStore.updateSettings { payload.settings.copy(hasCompletedOnboarding = true) }

        val now = System.currentTimeMillis()
        for (g in payload.blockingGroups) {
            database.blockingGroupDao().insertGroup(
                BlockingGroupEntity(
                    name = g.name,
                    description = g.description,
                    colorHex = g.colorHex,
                    blockedPackages = g.blockedPackages,
                    defaultDurationMinutes = g.defaultDurationMinutes,
                    strictByDefault = g.strictByDefault,
                    blockShortsAndReels = g.blockShortsAndReels,
                    createdAtMillis = now
                )
            )
        }
        for (h in payload.habits) {
            database.habitDao().upsertHabit(
                HabitEntity(
                    title = h.title,
                    category = h.category,
                    targetMinutesPerDay = h.targetMinutesPerDay,
                    colorHex = h.colorHex,
                    createdAtMillis = now
                )
            )
        }
        for (s in payload.sessions) {
            database.sessionDao().insertSession(
                FocusSessionEntity(
                    modeType = s.modeType,
                    tag = s.tag,
                    note = s.note,
                    blockingGroupId = null,
                    blockingGroupName = s.blockingGroupName,
                    strictEnforcementMode = s.strictEnforcementMode,
                    startTimeEpochMillis = s.startTimeEpochMillis,
                    endTimeEpochMillis = s.endTimeEpochMillis,
                    plannedWorkSeconds = s.plannedWorkSeconds,
                    plannedBreakSeconds = s.plannedBreakSeconds,
                    effectiveWorkSeconds = s.effectiveWorkSeconds,
                    effectiveBreakSeconds = s.effectiveBreakSeconds,
                    completedCycles = s.completedCycles,
                    interceptionCount = s.interceptionCount,
                    outcome = s.outcome
                )
            )
        }
    }

    suspend fun clearAllHistory() {
        ensureNotStrictLocked("clear focus history")
        database.sessionDao().deleteAllSessions()
        database.interceptionDao().deleteAllInterceptions()
        database.habitDao().deleteAllCompletions()
    }
}
