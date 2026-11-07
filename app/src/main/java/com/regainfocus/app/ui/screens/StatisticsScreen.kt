package com.regainfocus.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.regainfocus.app.data.local.FocusSessionEntity
import com.regainfocus.app.domain.engine.StreakAndAchievementEngine
import com.regainfocus.app.domain.model.AchievementProgress
import com.regainfocus.app.domain.model.ScreenTimeOverview
import com.regainfocus.app.domain.model.SessionOutcome
import com.regainfocus.app.domain.model.UserSettings
import com.regainfocus.app.ui.components.BarChartPoint
import com.regainfocus.app.ui.components.SmoothTrendLineChart
import com.regainfocus.app.ui.components.StreakHeatmapGrid
import com.regainfocus.app.ui.components.WeeklyBarChart
import com.regainfocus.app.ui.theme.AuroraCyan
import com.regainfocus.app.ui.theme.AuroraEmerald
import com.regainfocus.app.ui.theme.AuroraIndigo
import com.regainfocus.app.ui.theme.WarmAmber
import com.regainfocus.app.util.TimeFormatters
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun StatisticsScreen(
    settings: UserSettings,
    sessions: List<FocusSessionEntity>,
    screenTimeOverview: ScreenTimeOverview,
    streakSummary: StreakAndAchievementEngine.StreakSummary,
    achievements: List<AchievementProgress>,
    totalInterceptions: Int
) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    var periodDays by rememberSaveable { mutableIntStateOf(7) } // 7 or 30

    val totalFocusMinutes = remember(sessions) {
        (sessions.sumOf { it.effectiveWorkSeconds } / 60L).toInt()
    }
    val completedCount = remember(sessions) {
        sessions.count {
            it.outcome == SessionOutcome.COMPLETED || it.outcome == SessionOutcome.RECOVERED_COMPLETED
        }
    }
    val completionRate = remember(sessions, completedCount) {
        if (sessions.isEmpty()) 100 else ((completedCount * 100f) / sessions.size.toFloat()).toInt()
    }

    val focusByDay = remember(sessions) {
        val m = HashMap<Long, Int>()
        for (s in sessions) {
            val d = Instant.ofEpochMilli(s.startTimeEpochMillis).atZone(zone).toLocalDate().toEpochDay()
            m[d] = (m[d] ?: 0) + (s.effectiveWorkSeconds / 60L).toInt()
        }
        m
    }

    val tagBreakdown = remember(sessions) {
        sessions.groupBy { it.tag.ifBlank { "Study" } }
            .mapValues { (_, list) -> (list.sumOf { it.effectiveWorkSeconds } / 60L).toInt() }
            .entries
            .filter { it.value > 0 }
            .sortedByDescending { it.value }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Productivity & Insights",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        // Top 4 Real KPI Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatKpiCard(
                title = "Total Focus",
                value = TimeFormatters.formatMinutesCompact(totalFocusMinutes),
                accent = AuroraEmerald,
                modifier = Modifier.weight(1f)
            )
            StatKpiCard(
                title = "Completion",
                value = "$completionRate% ($completedCount)",
                accent = AuroraCyan,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatKpiCard(
                title = "Current / Best",
                value = "${streakSummary.currentStreakDays}d / ${streakSummary.longestStreakDays}d",
                accent = WarmAmber,
                modifier = Modifier.weight(1f)
            )
            StatKpiCard(
                title = "Blocked Opens",
                value = "$totalInterceptions",
                accent = AuroraIndigo,
                modifier = Modifier.weight(1f)
            )
        }

        // Period Switcher + Focus Time Chart
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
                    Text(
                        text = "Focus Time Trend",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = periodDays == 7,
                            onClick = { periodDays = 7 },
                            label = { Text("7D") }
                        )
                        FilterChip(
                            selected = periodDays == 30,
                            onClick = { periodDays = 30 },
                            label = { Text("30D") }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (periodDays == 7) {
                    val pts = (6 downTo 0).map { offset ->
                        val d = today.minusDays(offset.toLong())
                        BarChartPoint(
                            label = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                            primaryMinutes = focusByDay[d.toEpochDay()] ?: 0
                        )
                    }
                    WeeklyBarChart(
                        points = pts,
                        primaryColor = AuroraEmerald,
                        goalLineMinutes = settings.dailyFocusGoalMinutes
                    )
                } else {
                    val values30 = (29 downTo 0).map { offset ->
                        focusByDay[today.minusDays(offset.toLong()).toEpochDay()] ?: 0
                    }
                    SmoothTrendLineChart(values = values30, lineColor = AuroraEmerald)
                }
            }
        }

        // Focus vs Screen-Time Comparison Chart
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "7-Day Focus vs Screen Time",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Emerald = Verified Focus · Cyan = Device Screen Time",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))

                val dualPoints = if (screenTimeOverview.last7DaysScreenMinutes.isNotEmpty()) {
                    screenTimeOverview.last7DaysScreenMinutes.map { pt ->
                        BarChartPoint(
                            label = pt.dayLabel,
                            primaryMinutes = focusByDay[pt.epochDay] ?: pt.focusMinutes,
                            secondaryMinutes = pt.screenTimeMinutes
                        )
                    }
                } else {
                    (6 downTo 0).map { offset ->
                        val d = today.minusDays(offset.toLong())
                        BarChartPoint(
                            label = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                            primaryMinutes = focusByDay[d.toEpochDay()] ?: 0,
                            secondaryMinutes = 0
                        )
                    }
                }

                WeeklyBarChart(
                    points = dualPoints,
                    primaryColor = AuroraEmerald,
                    secondaryColor = AuroraCyan
                )
            }
        }

        // Tag Distribution Breakdown
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Focus by Study Tag",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (tagBreakdown.isEmpty()) {
                    Text(
                        text = "Complete focus sessions with Study, Coding, or Exam Prep tags to see your subject breakdown.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val sum = tagBreakdown.sumOf { it.value }.coerceAtLeast(1)
                    tagBreakdown.forEach { (tag, mins) ->
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(tag, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    TimeFormatters.formatMinutesCompact(mins),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = AuroraEmerald
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { (mins.toFloat() / sum.toFloat()).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(50)),
                                color = AuroraEmerald,
                                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            )
                        }
                    }
                }
            }
        }

        // 28-Day Streak & Goal Heatmap
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "28-Day Consistency Heatmap",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${streakSummary.goalMetEpochDays.size} goal days",
                        style = MaterialTheme.typography.labelLarge,
                        color = AuroraEmerald
                    )
                }
                StreakHeatmapGrid(
                    activeEpochDays = streakSummary.activeEpochDays,
                    goalEpochDays = streakSummary.goalMetEpochDays,
                    todayEpochDay = today.toEpochDay()
                )
            }
        }

        // Achievements Showcase
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val unlockedCount = achievements.count { it.isUnlocked }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = WarmAmber)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Achievements",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "$unlockedCount / ${achievements.size} Unlocked",
                        style = MaterialTheme.typography.labelLarge,
                        color = WarmAmber
                    )
                }

                achievements.forEach { ach ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (ach.isUnlocked) {
                            AuroraEmerald.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
                        }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = if (ach.isUnlocked) Icons.Default.EmojiEvents else Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = if (ach.isUnlocked) WarmAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(ach.title, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            ach.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text(
                                    text = "${ach.currentValue}/${ach.targetValue}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (ach.isUnlocked) AuroraEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { ach.progressFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(50)),
                                color = if (ach.isUnlocked) AuroraEmerald else AuroraCyan,
                                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun StatKpiCard(
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        }
    }
}
