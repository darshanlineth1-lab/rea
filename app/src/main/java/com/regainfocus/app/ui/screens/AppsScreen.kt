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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.regainfocus.app.data.local.AppDailyLimitEntity
import com.regainfocus.app.data.local.BlockingGroupEntity
import com.regainfocus.app.data.local.FocusScheduleEntity
import com.regainfocus.app.domain.model.AppUsageItem
import com.regainfocus.app.domain.model.InstalledAppInfo
import com.regainfocus.app.domain.model.PersistedTimerSessionState
import com.regainfocus.app.domain.model.ScreenTimeOverview
import com.regainfocus.app.ui.components.AppIconBadge
import com.regainfocus.app.ui.components.HourlySparkBars
import com.regainfocus.app.ui.theme.AuroraCyan
import com.regainfocus.app.ui.theme.AuroraEmerald
import com.regainfocus.app.ui.theme.StrictCrimson
import com.regainfocus.app.ui.theme.WarmAmber
import com.regainfocus.app.util.TimeFormatters
import kotlin.math.roundToInt

@Composable
fun AppsScreen(
    activeSession: PersistedTimerSessionState,
    blockingGroups: List<BlockingGroupEntity>,
    installedApps: List<InstalledAppInfo>,
    appLimits: List<AppDailyLimitEntity>,
    schedules: List<FocusScheduleEntity>,
    screenTimeOverview: ScreenTimeOverview,
    isLoadingUsage: Boolean,
    onRefreshUsage: () -> Unit,
    onSaveGroup: (BlockingGroupEntity) -> Unit,
    onDeleteGroup: (BlockingGroupEntity) -> Unit,
    onUpsertLimit: (pkg: String, label: String, mins: Int, enabled: Boolean, lockHours: Int) -> Unit,
    onDeleteLimit: (pkg: String) -> Unit,
    onSaveSchedule: (FocusScheduleEntity) -> Unit,
    onDeleteSchedule: (FocusScheduleEntity) -> Unit,
    onRequestUsageAccess: () -> Unit
) {
    var selectedSubTab by rememberSaveable { mutableIntStateOf(0) }
    val tabTitles = listOf("Block Groups", "Screen Time", "Schedules")

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Apps & Screen Time",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onRefreshUsage) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh usage", tint = AuroraCyan)
            }
        }

        TabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = Color.Transparent
        ) {
            tabTitles.forEachIndexed { idx, title ->
                Tab(
                    selected = selectedSubTab == idx,
                    onClick = { selectedSubTab = idx },
                    text = { Text(title) }
                )
            }
        }

        when (selectedSubTab) {
            0 -> BlockingGroupsTab(
                activeSession = activeSession,
                blockingGroups = blockingGroups,
                installedApps = installedApps,
                onSaveGroup = onSaveGroup,
                onDeleteGroup = onDeleteGroup
            )
            1 -> ScreenTimeAndLimitsTab(
                screenTimeOverview = screenTimeOverview,
                installedApps = installedApps,
                appLimits = appLimits,
                isLoadingUsage = isLoadingUsage,
                onUpsertLimit = onUpsertLimit,
                onDeleteLimit = onDeleteLimit,
                onRequestUsageAccess = onRequestUsageAccess
            )
            2 -> SchedulesTab(
                schedules = schedules,
                blockingGroups = blockingGroups,
                onSaveSchedule = onSaveSchedule,
                onDeleteSchedule = onDeleteSchedule
            )
        }
    }
}

@Composable
private fun BlockingGroupsTab(
    activeSession: PersistedTimerSessionState,
    blockingGroups: List<BlockingGroupEntity>,
    installedApps: List<InstalledAppInfo>,
    onSaveGroup: (BlockingGroupEntity) -> Unit,
    onDeleteGroup: (BlockingGroupEntity) -> Unit
) {
    var editingGroup by remember { mutableStateOf<BlockingGroupEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    val appLabelMap = remember(installedApps) {
        installedApps.associateBy { it.packageName }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Button(
            onClick = {
                editingGroup = null
                showEditor = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AuroraEmerald,
                contentColor = Color(0xFF032518)
            )
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Create Custom Blocking Group", fontWeight = FontWeight.Bold)
        }

        blockingGroups.forEach { group ->
            val isLockedByActiveStrict = activeSession.isStrictActive && activeSession.blockingGroupId == group.id
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Surface(
                                shape = CircleShape,
                                color = Color(group.colorHex).copy(alpha = 0.22f),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isLockedByActiveStrict) Icons.Default.Lock else Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = Color(group.colorHex),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = group.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${group.blockedPackages.size} apps · ${group.defaultDurationMinutes}m default",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (!isLockedByActiveStrict) {
                            Row {
                                IconButton(onClick = {
                                    editingGroup = group
                                    showEditor = true
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                                }
                                IconButton(onClick = { onDeleteGroup(group) }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete")
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = StrictCrimson.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = "STRICT LOCKED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StrictCrimson,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    if (group.description.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = group.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val previewLabels = group.blockedPackages.take(5).map { pkg ->
                        appLabelMap[pkg]?.label ?: pkg.substringAfterLast('.')
                    }
                    if (previewLabels.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Targets: " + previewLabels.joinToString(", ") +
                                if (group.blockedPackages.size > 5) " +${group.blockedPackages.size - 5} more" else "",
                            style = MaterialTheme.typography.labelMedium,
                            color = AuroraCyan
                        )
                    }
                }
            }
        }
    }

    if (showEditor) {
        GroupEditorDialog(
            existing = editingGroup,
            installedApps = installedApps,
            onDismiss = { showEditor = false },
            onSave = { saved ->
                onSaveGroup(saved)
                showEditor = false
            }
        )
    }
}

@Composable
private fun GroupEditorDialog(
    existing: BlockingGroupEntity?,
    installedApps: List<InstalledAppInfo>,
    onDismiss: () -> Unit,
    onSave: (BlockingGroupEntity) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var description by remember { mutableStateOf(existing?.description.orEmpty()) }
    var duration by remember { mutableFloatStateOf((existing?.defaultDurationMinutes ?: 30).toFloat()) }
    var blockShorts by remember { mutableStateOf(existing?.blockShortsAndReels ?: true) }
    var selectedPackages by remember { mutableStateOf(existing?.blockedPackages?.toSet() ?: emptySet()) }
    var search by remember { mutableStateOf("") }

    val filteredApps = remember(installedApps, search) {
        if (search.isBlank()) installedApps
        else installedApps.filter { it.label.contains(search, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New Blocking Group" else "Edit Blocking Group") },
        text = {
            Column(
                modifier = Modifier.height(430.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Group Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    "Default Duration: ${duration.roundToInt()}m",
                    style = MaterialTheme.typography.labelLarge
                )
                Slider(
                    value = duration,
                    onValueChange = { duration = ((it / 5f).roundToInt() * 5).toFloat() },
                    valueRange = 10f..120f
                )

                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Search Installed Apps (${selectedPackages.size} selected)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    filteredApps.forEach { app ->
                        val checked = app.packageName in selectedPackages
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedPackages = if (checked) {
                                        selectedPackages - app.packageName
                                    } else {
                                        selectedPackages + app.packageName
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIconBadge(app.icon, app.label, size = 32.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.label, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    app.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    selectedPackages = if (isChecked) {
                                        selectedPackages + app.packageName
                                    } else {
                                        selectedPackages - app.packageName
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(
                            BlockingGroupEntity(
                                id = existing?.id ?: 0L,
                                name = name.trim(),
                                description = description.trim(),
                                colorHex = existing?.colorHex ?: 0xFF2EE59D,
                                blockedPackages = selectedPackages.toList(),
                                defaultDurationMinutes = duration.roundToInt(),
                                strictByDefault = true,
                                blockShortsAndReels = blockShorts,
                                createdAtMillis = existing?.createdAtMillis ?: System.currentTimeMillis()
                            )
                        )
                    }
                }
            ) {
                Text("Save Group")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ScreenTimeAndLimitsTab(
    screenTimeOverview: ScreenTimeOverview,
    installedApps: List<InstalledAppInfo>,
    appLimits: List<AppDailyLimitEntity>,
    isLoadingUsage: Boolean,
    onUpsertLimit: (pkg: String, label: String, mins: Int, enabled: Boolean, lockHours: Int) -> Unit,
    onDeleteLimit: (pkg: String) -> Unit,
    onRequestUsageAccess: () -> Unit
) {
    var rangeIndex by rememberSaveable { mutableIntStateOf(0) } // 0 = Today, 1 = 7 Days, 2 = 30 Days
    var limitDialogTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showAddAnyLimitPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!screenTimeOverview.hasUsagePermission) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Usage Access Required for Real Screen Time",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Android requires explicit Usage Access permission to calculate daily, weekly, monthly, and per-app screen time via UsageStatsManager.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onRequestUsageAccess,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AuroraCyan,
                            contentColor = Color(0xFF042230)
                        )
                    ) {
                        Text("Review & Grant Usage Access", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            // Range Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Today", "Last 7 Days", "Last 30 Days").forEachIndexed { idx, label ->
                    FilterChip(
                        selected = rangeIndex == idx,
                        onClick = { rangeIndex = idx },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Summary + Hourly Chart Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    val displayedTotal = when (rangeIndex) {
                        0 -> screenTimeOverview.todayTotalMillis
                        1 -> screenTimeOverview.weeklyTotalMillis
                        else -> screenTimeOverview.monthlyTotalMillis
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = when (rangeIndex) {
                                    0 -> "Today's Total Screen Time"
                                    1 -> "7-Day Screen Time"
                                    else -> "30-Day Screen Time"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = TimeFormatters.formatDurationMillisCompact(displayedTotal),
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = AuroraCyan
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "7d Avg / Day",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = TimeFormatters.formatDurationMillisCompact(screenTimeOverview.dailyAverageWeekMillis),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (rangeIndex == 0) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "Hourly Activity Today",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        HourlySparkBars(hourlyMinutes = screenTimeOverview.hourlyMinutesToday)
                    }
                }
            }

            // Active Per-App Daily Limits Section
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
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.HourglassBottom, contentDescription = null, tint = AuroraEmerald)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Per-App Daily Budgets",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        TextButton(onClick = { showAddAnyLimitPicker = true }) {
                            Text("+ Add Limit")
                        }
                    }

                    if (appLimits.isEmpty()) {
                        Text(
                            text = "Tap any app below or '+ Add Limit' to enforce automatic daily screen-time caps.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        appLimits.forEach { lim ->
                            val locked = lim.strictLockedUntilMillis > System.currentTimeMillis()
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(lim.appLabel, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = "${lim.dailyLimitMinutes}m/day" +
                                                if (locked) " · 🔒 Strict-Locked" else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (locked) StrictCrimson else AuroraEmerald
                                        )
                                    }
                                    if (!locked) {
                                        IconButton(onClick = { onDeleteLimit(lim.packageName) }) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete limit")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Most-Used Apps List
            val appList: List<AppUsageItem> = when (rangeIndex) {
                0 -> screenTimeOverview.topAppsToday
                1 -> screenTimeOverview.topAppsWeek
                else -> screenTimeOverview.topAppsMonth
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Most-Used Apps",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (isLoadingUsage && appList.isEmpty()) {
                        Text("Loading real usage stats...", style = MaterialTheme.typography.bodySmall)
                    } else if (appList.isEmpty()) {
                        Text(
                            text = "No foreground app activity recorded in this window yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        appList.forEach { item ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        limitDialogTarget = item.packageName to item.appLabel
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AppIconBadge(item.icon, item.appLabel, size = 38.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(item.appLabel, style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                text = TimeFormatters.formatDurationMillisCompact(item.usageMillis),
                                                style = MaterialTheme.typography.titleSmall,
                                                color = AuroraCyan,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        LinearProgressIndicator(
                                            progress = { item.fractionOfTotal },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(50)),
                                            color = if (item.dailyLimitMinutes != null &&
                                                (item.usageMillis / 60_000L) >= item.dailyLimitMinutes
                                            ) StrictCrimson else AuroraCyan,
                                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                        )
                                        if (item.dailyLimitMinutes != null || item.launchCount > 0) {
                                            Spacer(Modifier.height(3.dp))
                                            Text(
                                                text = buildString {
                                                    if (item.launchCount > 0) append("${item.launchCount} opens")
                                                    if (item.dailyLimitMinutes != null) {
                                                        if (isNotEmpty()) append(" · ")
                                                        append("Limit: ${item.dailyLimitMinutes}m/day")
                                                    }
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    limitDialogTarget?.let { (pkg, label) ->
        val existing = appLimits.firstOrNull { it.packageName == pkg }
        var mins by remember(pkg) { mutableFloatStateOf((existing?.dailyLimitMinutes ?: 45).toFloat()) }
        var strictLock24h by remember(pkg) { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { limitDialogTarget = null },
            title = { Text("Daily Limit: $label") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Block $label for the rest of the day after ${mins.roundToInt()} minutes:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = mins,
                        onValueChange = { mins = ((it / 5f).roundToInt() * 5).coerceAtLeast(5).toFloat() },
                        valueRange = 5f..240f
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Strict-Lock Limit for 24h", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = strictLock24h, onCheckedChange = { strictLock24h = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpsertLimit(pkg, label, mins.roundToInt(), true, if (strictLock24h) 24 else 0)
                        limitDialogTarget = null
                    }
                ) {
                    Text("Save Limit")
                }
            },
            dismissButton = {
                TextButton(onClick = { limitDialogTarget = null }) { Text("Cancel") }
            }
        )
    }

    if (showAddAnyLimitPicker) {
        AlertDialog(
            onDismissRequest = { showAddAnyLimitPicker = false },
            title = { Text("Pick App for Daily Limit") },
            text = {
                Column(
                    modifier = Modifier
                        .height(340.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    installedApps.forEach { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showAddAnyLimitPicker = false
                                    limitDialogTarget = app.packageName to app.label
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIconBadge(app.icon, app.label, size = 32.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(app.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddAnyLimitPicker = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun SchedulesTab(
    schedules: List<FocusScheduleEntity>,
    blockingGroups: List<BlockingGroupEntity>,
    onSaveSchedule: (FocusScheduleEntity) -> Unit,
    onDeleteSchedule: (FocusScheduleEntity) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    val dayShortLabels = listOf("M", "T", "W", "T", "F", "S", "S")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Button(
            onClick = { showCreateDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AuroraEmerald,
                contentColor = Color(0xFF032518)
            )
        ) {
            Icon(Icons.Default.Schedule, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add Study Schedule Block", fontWeight = FontWeight.Bold)
        }

        if (schedules.isEmpty()) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    text = "Plan recurring study hours (e.g., 19:00–21:00 Weekdays) to receive automatic focus reminders.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(18.dp)
                )
            }
        } else {
            schedules.forEach { sched ->
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = sched.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = String.format(
                                        "%02d:%02d – %02d:%02d%s",
                                        sched.startHour,
                                        sched.startMinute,
                                        sched.endHour,
                                        sched.endMinute,
                                        if (sched.strictMode) " · Strict" else ""
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AuroraEmerald
                                )
                            }
                            Switch(
                                checked = sched.isEnabled,
                                onCheckedChange = { onSaveSchedule(sched.copy(isEnabled = it)) }
                            )
                            IconButton(onClick = { onDeleteSchedule(sched) }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete")
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            dayShortLabels.forEachIndexed { bit, lbl ->
                                val active = (sched.daysOfWeekMask and (1 shl bit)) != 0
                                Surface(
                                    shape = CircleShape,
                                    color = if (active) AuroraEmerald.copy(alpha = 0.25f)
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = lbl,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (active) AuroraEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var title by remember { mutableStateOf("Evening Deep Study") }
        var startHour by remember { mutableFloatStateOf(19f) }
        var durationHours by remember { mutableFloatStateOf(2f) }
        var daysMask by remember { mutableIntStateOf(0b0011111) } // Mon-Fri
        var strict by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Study Schedule") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Schedule Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        String.format(
                            "Start Hour: %02d:00 · Duration: %dh",
                            startHour.roundToInt(),
                            durationHours.roundToInt()
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = startHour,
                        onValueChange = { startHour = it.roundToInt().toFloat() },
                        valueRange = 5f..22f
                    )
                    Slider(
                        value = durationHours,
                        onValueChange = { durationHours = it.roundToInt().coerceAtLeast(1).toFloat() },
                        valueRange = 1f..6f
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        dayShortLabels.forEachIndexed { bit, lbl ->
                            val selected = (daysMask and (1 shl bit)) != 0
                            FilterChip(
                                selected = selected,
                                onClick = { daysMask = daysMask xor (1 shl bit) },
                                label = { Text(lbl) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Recommend Strict Mode", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = strict, onCheckedChange = { strict = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sH = startHour.roundToInt()
                        val eH = (sH + durationHours.roundToInt()) % 24
                        onSaveSchedule(
                            FocusScheduleEntity(
                                title = title.ifBlank { "Study Block" },
                                startHour = sH,
                                startMinute = 0,
                                endHour = eH,
                                endMinute = 0,
                                daysOfWeekMask = if (daysMask == 0) 0b1111111 else daysMask,
                                blockingGroupId = blockingGroups.firstOrNull()?.id,
                                strictMode = strict,
                                isEnabled = true
                            )
                        )
                        showCreateDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }
}
