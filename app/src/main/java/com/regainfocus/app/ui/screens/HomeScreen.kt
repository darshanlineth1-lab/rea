package com.regainfocus.app.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.regainfocus.app.data.local.BlockingGroupEntity
import com.regainfocus.app.data.local.FocusSessionEntity
import com.regainfocus.app.data.local.HabitCompletionEntity
import com.regainfocus.app.data.local.HabitEntity
import com.regainfocus.app.domain.engine.StreakAndAchievementEngine
import com.regainfocus.app.domain.model.PersistedTimerSessionState
import com.regainfocus.app.domain.model.ScreenTimeOverview
import com.regainfocus.app.domain.model.SessionOutcome
import com.regainfocus.app.domain.model.StrictEnforcementLevel
import com.regainfocus.app.domain.model.TimerModeType
import com.regainfocus.app.domain.model.UserSettings
import com.regainfocus.app.ui.components.AuroraGoalRing
import com.regainfocus.app.ui.components.BarChartPoint
import com.regainfocus.app.ui.components.WeeklyBarChart
import com.regainfocus.app.ui.theme.AuroraCyan
import com.regainfocus.app.ui.theme.AuroraEmerald
import com.regainfocus.app.ui.theme.StrictCrimson
import com.regainfocus.app.ui.theme.WarmAmber
import com.regainfocus.app.util.TimeFormatters
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    settings: UserSettings,
    activeSession: PersistedTimerSessionState,
    liveDisplayMillis: Long,
    sessions: List<FocusSessionEntity>,
    blockingGroups: List<BlockingGroupEntity>,
    screenTimeOverview: ScreenTimeOverview,
    streakSummary: StreakAndAchievementEngine.StreakSummary,
    habits: List<HabitEntity>,
    habitCompletions: List<HabitCompletionEntity>,
    onNavigateToFocus: () -> Unit,
    onNavigateToApps: () -> Unit,
    onQuickStartFocus: (minutes: Int, group: BlockingGroupEntity?) -> Unit,
    onAddHabit: (title: String, category: String, targetMinutes: Int) -> Unit,
    onToggleHabitToday: (HabitEntity) -> Unit,
    onArchiveHabit: (Long) -> Unit
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val todayEpoch = today.toEpochDay()

    // Compute 100% real metrics from stored FocusSessionEntity records
    val todaySessions = remember(sessions, todayEpoch) {
        sessions.filter {
            Instant.ofEpochMilli(it.startTimeEpochMillis).atZone(zone).toLocalDate().toEpochDay() == todayEpoch
        }
    }
    val todayFocusMinutes = remember(todaySessions) {
        (todaySessions.sumOf { it.effectiveWorkSeconds } / 60L).toInt()
    }
    val completedTodayCount = remember(todaySessions) {
        todaySessions.count {
            it.outcome == SessionOutcome.COMPLETED || it.outcome == SessionOutcome.RECOVERED_COMPLETED
        }
    }
    val remainingTargetMinutes = (settings.dailyFocusGoalMinutes - todayFocusMinutes).coerceAtLeast(0)
    val goalProgress = if (settings.dailyFocusGoalMinutes > 0) {
        todayFocusMinutes.toFloat() / settings.dailyFocusGoalMinutes.toFloat()
    } else 0f

    val weeklyChartPoints = remember(sessions, today) {
        val byDay = HashMap<Long, Int>()
        for (s in sessions) {
            val d = Instant.ofEpochMilli(s.startTimeEpochMillis).atZone(zone).toLocalDate().toEpochDay()
            byDay[d] = (byDay[d] ?: 0) + (s.effectiveWorkSeconds / 60L).toInt()
        }
        (6 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            BarChartPoint(
                label = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                primaryMinutes = byDay[date.toEpochDay()] ?: 0
            )
        }
    }

    var showAddHabitDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header + Streak Pill
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = today.format(DateTimeFormatter.ofPattern("EEEE, MMM d")),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "RegainFocus",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Surface(
                shape = RoundedCornerShape(50),
                color = WarmAmber.copy(alpha = 0.15f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        tint = WarmAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${streakSummary.currentStreakDays}d Streak",
                        style = MaterialTheme.typography.labelLarge,
                        color = WarmAmber,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Active Session Live Banner
        if (activeSession.isActive) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (activeSession.isStrictActive) {
                        StrictCrimson.copy(alpha = 0.18f)
                    } else {
                        AuroraEmerald.copy(alpha = 0.16f)
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToFocus() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = if (activeSession.isStrictActive) Icons.Default.Lock else Icons.Default.Bolt,
                            contentDescription = null,
                            tint = if (activeSession.isStrictActive) StrictCrimson else AuroraEmerald
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "${activeSession.phase.label} · ${activeSession.sessionTag}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (activeSession.isStrictActive) {
                                    "${activeSession.strictLevel.badge} · ${activeSession.blockedPackages.size} apps locked"
                                } else {
                                    "Tap to open Focus Timer"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = TimeFormatters.formatMillisClock(liveDisplayMillis),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (activeSession.isStrictActive) StrictCrimson else AuroraEmerald
                    )
                }
            }
        }

        // Hero Daily Focus Progress Card
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AuroraGoalRing(
                        progress = goalProgress,
                        centerTitle = TimeFormatters.formatMinutesCompact(todayFocusMinutes),
                        centerSubtitle = "of ${TimeFormatters.formatMinutesCompact(settings.dailyFocusGoalMinutes)}"
                    )
                    Spacer(Modifier.width(18.dp))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        MetricLine(
                            label = "Remaining Target",
                            value = if (remainingTargetMinutes == 0) "Goal Reached! 🎉" else TimeFormatters.formatMinutesCompact(remainingTargetMinutes),
                            accent = AuroraEmerald
                        )
                        MetricLine(
                            label = "Completed Today",
                            value = "$completedTodayCount ${if (completedTodayCount == 1) "session" else "sessions"}",
                            accent = AuroraCyan
                        )
                        MetricLine(
                            label = "Best Streak",
                            value = "${streakSummary.longestStreakDays} days",
                            accent = WarmAmber
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                // Quick-Start Focus Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val primaryGroup = blockingGroups.firstOrNull()
                    Button(
                        onClick = {
                            if (activeSession.isActive) {
                                onNavigateToFocus()
                            } else {
                                onQuickStartFocus(settings.pomodoroWorkMinutes, primaryGroup)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AuroraEmerald,
                            contentColor = Color(0xFF032518)
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (activeSession.isActive) "View Timer" else "Quick ${settings.pomodoroWorkMinutes}m",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = onNavigateToFocus,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Strict Setup")
                    }
                }
            }
        }

        // Today's Screen Time Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.clickable { onNavigateToApps() }
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = AuroraCyan
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Today's Screen Time",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (screenTimeOverview.hasUsagePermission) {
                        Text(
                            text = TimeFormatters.formatDurationMillisCompact(screenTimeOverview.todayTotalMillis),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = AuroraCyan
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                if (!screenTimeOverview.hasUsagePermission) {
                    Text(
                        text = "Grant Usage Access to display real per-app and daily screen time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Grant Usage Access")
                    }
                } else {
                    val usedMins = (screenTimeOverview.todayTotalMillis / 60_000L).toInt()
                    val budgetMins = settings.dailyScreenTimeGoalMinutes.coerceAtLeast(1)
                    val ratio = (usedMins.toFloat() / budgetMins.toFloat()).coerceIn(0f, 1f)
                    val overBudget = usedMins > budgetMins

                    LinearProgressIndicator(
                        progress = { ratio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50)),
                        color = if (overBudget) StrictCrimson else AuroraCyan,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (overBudget) {
                            "${TimeFormatters.formatMinutesCompact(usedMins - budgetMins)} over your ${TimeFormatters.formatMinutesCompact(budgetMins)} daily budget · Tap for app limits"
                        } else {
                            "${TimeFormatters.formatMinutesCompact(budgetMins - usedMins)} left in your ${TimeFormatters.formatMinutesCompact(budgetMins)} daily budget"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (overBudget) StrictCrimson else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Weekly Productivity Overview Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                val weekFocusTotal = weeklyChartPoints.sumOf { it.primaryMinutes }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Weekly Focus Overview",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${TimeFormatters.formatMinutesCompact(weekFocusTotal)} this week",
                        style = MaterialTheme.typography.labelLarge,
                        color = AuroraEmerald
                    )
                }
                Spacer(Modifier.height(14.dp))
                WeeklyBarChart(
                    points = weeklyChartPoints,
                    primaryColor = AuroraEmerald,
                    goalLineMinutes = settings.dailyFocusGoalMinutes
                )
                if (weekFocusTotal == 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Complete your first focus session to populate your 7-day productivity bars.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Study Goals & Daily Habits Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Daily Study Habits",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Track recurring study rituals alongside your focus timer",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showAddHabitDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Habit", tint = AuroraEmerald)
                    }
                }

                Spacer(Modifier.height(8.dp))

                if (habits.isEmpty()) {
                    OutlinedButton(
                        onClick = { showAddHabitDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Create Your First Study Habit")
                    }
                } else {
                    val todayCompletionMap = habitCompletions
                        .filter { it.dateEpochDay == todayEpoch && it.isCompleted }
                        .associateBy { it.habitId }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        habits.forEach { habit ->
                            val done = todayCompletionMap.containsKey(habit.id)
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleHabitToday(habit) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (done) AuroraEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = habit.title,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = "${habit.category} · ${habit.targetMinutesPerDay}m daily target",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { onArchiveHabit(habit.id) }) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Remove habit",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
    }

    if (showAddHabitDialog) {
        var title by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("Study") }
        var mins by remember { mutableFloatStateOf(30f) }

        AlertDialog(
            onDismissRequest = { showAddHabitDialog = false },
            title = { Text("New Daily Study Habit") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Habit Title (e.g., Active Recall Flashcards)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Subject / Category") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Daily Target: ${mins.roundToInt()} minutes", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = mins,
                        onValueChange = { mins = (it / 5f).roundToInt() * 5f },
                        valueRange = 10f..180f
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            onAddHabit(title, category, mins.roundToInt())
                            showAddHabitDialog = false
                        }
                    }
                ) {
                    Text("Save Habit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddHabitDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun MetricLine(label: String, value: String, accent: Color) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = accent
        )
    }
}
