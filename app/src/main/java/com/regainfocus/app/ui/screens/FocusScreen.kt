package com.regainfocus.app.ui.screens

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.regainfocus.app.RegainFocusApplication
import com.regainfocus.app.data.local.BlockingGroupEntity
import com.regainfocus.app.data.local.FocusSessionEntity
import com.regainfocus.app.domain.model.DeviceEnforcementStatus
import com.regainfocus.app.domain.model.InstalledAppInfo
import com.regainfocus.app.domain.model.PersistedTimerSessionState
import com.regainfocus.app.domain.model.SessionOutcome
import com.regainfocus.app.domain.model.SoundscapeType
import com.regainfocus.app.domain.model.StrictEnforcementLevel
import com.regainfocus.app.domain.model.TimerModeType
import com.regainfocus.app.domain.model.TimerPhase
import com.regainfocus.app.domain.model.UserSettings
import com.regainfocus.app.ui.components.AppIconBadge
import com.regainfocus.app.ui.components.AuroraGoalRing
import com.regainfocus.app.ui.theme.AuroraCyan
import com.regainfocus.app.ui.theme.AuroraEmerald
import com.regainfocus.app.ui.theme.StrictCrimson
import com.regainfocus.app.ui.theme.WarmAmber
import com.regainfocus.app.util.TimeFormatters
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val SESSION_TAGS = listOf("Study", "Coding", "Exam Prep", "Reading", "Writing", "Deep Work")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusScreen(
    settings: UserSettings,
    activeSession: PersistedTimerSessionState,
    liveDisplayMillis: Long,
    liveStrictRemainingMillis: Long,
    blockingGroups: List<BlockingGroupEntity>,
    installedApps: List<InstalledAppInfo>,
    enforcementStatus: DeviceEnforcementStatus,
    recentSessions: List<FocusSessionEntity>,
    onStartSession: (
        mode: TimerModeType,
        workMinutes: Int,
        shortBreakMinutes: Int,
        longBreakMinutes: Int,
        cycles: Int,
        strictLevel: StrictEnforcementLevel,
        selectedGroup: BlockingGroupEntity?,
        extraPackages: Set<String>,
        blockShortsAndReels: Boolean,
        tag: String,
        note: String
    ) -> Boolean,
    onTogglePauseFlexible: () -> Unit,
    onSkipBreakFlexible: () -> Unit,
    onStopFlexible: () -> Unit,
    onSelectSoundscape: (SoundscapeType) -> Unit,
    onUpdateVolume: (Float) -> Unit
) {
    val context = LocalContext.current

    if (activeSession.isActive) {
        ActiveFocusSessionContent(
            settings = settings,
            activeSession = activeSession,
            liveDisplayMillis = liveDisplayMillis,
            liveStrictRemainingMillis = liveStrictRemainingMillis,
            enforcementStatus = enforcementStatus,
            onTogglePauseFlexible = onTogglePauseFlexible,
            onSkipBreakFlexible = onSkipBreakFlexible,
            onStopFlexible = onStopFlexible,
            onSelectSoundscape = onSelectSoundscape,
            onUpdateVolume = onUpdateVolume
        )
        return
    }

    // Idle Setup State
    var selectedMode by rememberSaveable { mutableStateOf(TimerModeType.POMODORO) }
    var workMinutes by rememberSaveable(settings.pomodoroWorkMinutes, settings.customCountdownMinutes) {
        mutableIntStateOf(settings.pomodoroWorkMinutes)
    }
    var shortBreakMinutes by rememberSaveable(settings.pomodoroShortBreakMinutes) {
        mutableIntStateOf(settings.pomodoroShortBreakMinutes)
    }
    var longBreakMinutes by rememberSaveable(settings.pomodoroLongBreakMinutes) {
        mutableIntStateOf(settings.pomodoroLongBreakMinutes)
    }
    var cycles by rememberSaveable(settings.pomodoroCyclesBeforeLongBreak) {
        mutableIntStateOf(settings.pomodoroCyclesBeforeLongBreak)
    }

    var selectedStrictLevel by rememberSaveable(settings.defaultStrictLevel) {
        mutableStateOf(settings.defaultStrictLevel)
    }
    var selectedGroupId by rememberSaveable(blockingGroups.firstOrNull()?.id) {
        mutableStateOf(blockingGroups.firstOrNull()?.id)
    }
    val selectedGroup = blockingGroups.firstOrNull { it.id == selectedGroupId }
    var extraCustomPackages by remember { mutableStateOf<Set<String>>(emptySet()) }
    var blockShortsReels by rememberSaveable { mutableStateOf(true) }
    var selectedTag by rememberSaveable { mutableStateOf("Study") }
    var sessionNote by rememberSaveable { mutableStateOf("") }

    var showCustomAppPicker by remember { mutableStateOf(false) }
    var showStrictConfirmationDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Focus Session",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        // Timer Mode Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TimerModeType.entries.forEach { mode ->
                FilterChip(
                    selected = selectedMode == mode,
                    onClick = {
                        selectedMode = mode
                        workMinutes = when (mode) {
                            TimerModeType.POMODORO -> settings.pomodoroWorkMinutes
                            TimerModeType.COUNTDOWN -> settings.customCountdownMinutes
                            TimerModeType.STOPWATCH -> 30
                        }
                    },
                    label = { Text(mode.displayName) },
                    leadingIcon = if (selectedMode == mode) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Duration Config Card
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AuroraGoalRing(
                    progress = 1f,
                    centerTitle = if (selectedMode == TimerModeType.STOPWATCH) {
                        "00:00"
                    } else {
                        String.format("%02d:00", workMinutes)
                    },
                    centerSubtitle = when (selectedMode) {
                        TimerModeType.POMODORO -> "$cycles rounds · ${shortBreakMinutes}m/${longBreakMinutes}m breaks"
                        TimerModeType.COUNTDOWN -> "Countdown Focus"
                        TimerModeType.STOPWATCH -> if (selectedStrictLevel != StrictEnforcementLevel.NONE) {
                            "Min ${workMinutes}m Strict Lock"
                        } else "Open-Ended Stopwatch"
                    },
                    ringSize = 156.dp
                )

                Spacer(Modifier.height(14.dp))

                if (selectedMode != TimerModeType.STOPWATCH || selectedStrictLevel != StrictEnforcementLevel.NONE) {
                    val labelText = if (selectedMode == TimerModeType.STOPWATCH) {
                        "Minimum Strict Lock Commitment: ${workMinutes}m"
                    } else {
                        "Focus Duration: ${workMinutes} minutes"
                    }
                    Text(labelText, style = MaterialTheme.typography.titleSmall)
                    Slider(
                        value = workMinutes.toFloat(),
                        onValueChange = { workMinutes = ((it / 5f).roundToInt() * 5).coerceAtLeast(5) },
                        valueRange = 5f..180f
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(15, 25, 45, 60, 90).forEach { preset ->
                            FilterChip(
                                selected = workMinutes == preset,
                                onClick = { workMinutes = preset },
                                label = { Text("${preset}m") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                if (selectedMode == TimerModeType.POMODORO) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Short Break: ${shortBreakMinutes}m", style = MaterialTheme.typography.labelMedium)
                            Slider(
                                value = shortBreakMinutes.toFloat(),
                                onValueChange = { shortBreakMinutes = it.roundToInt() },
                                valueRange = 0f..20f
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Cycles: $cycles", style = MaterialTheme.typography.labelMedium)
                            Slider(
                                value = cycles.toFloat(),
                                onValueChange = { cycles = it.roundToInt().coerceAtLeast(1) },
                                valueRange = 1f..8f
                            )
                        }
                    }
                }
            }
        }

        // Strict Enforcement Mode Card (Highest Priority Feature)
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = AuroraEmerald)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Strict App Blocking Level",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                StrictEnforcementLevel.entries.forEach { level ->
                    val selected = selectedStrictLevel == level
                    val subtitle = when (level) {
                        StrictEnforcementLevel.NONE ->
                            "Blocks selected apps during Work phases; allows pausing or ending early."
                        StrictEnforcementLevel.STANDARD_STRICT ->
                            "No pause, no skip, no early exit. Intercepts blocked apps across launcher & reboot until 00:00."
                        StrictEnforcementLevel.DEVICE_OWNER_STRICT ->
                            if (enforcementStatus.isDeviceOwnerApp) {
                                "Provisioned! Suspends blocked packages at OS level + blocks Force-Stop & Uninstall."
                            } else {
                                "Requires dedicated ADB Device Owner provisioning (see Settings -> Device Owner)."
                            }
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) {
                            AuroraEmerald.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedStrictLevel = level }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = level.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (selected) AuroraEmerald else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (selected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = AuroraEmerald)
                            }
                        }
                    }
                }

                if (selectedStrictLevel == StrictEnforcementLevel.STANDARD_STRICT &&
                    !enforcementStatus.isAccessibilityServiceEnabled
                ) {
                    OutlinedButton(
                        onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.WarningAmber, contentDescription = null, tint = WarmAmber)
                        Spacer(Modifier.width(8.dp))
                        Text("Enable Accessibility Blocker to Use Mode A")
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text("Blocking Group", style = MaterialTheme.typography.titleSmall)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    blockingGroups.forEach { group ->
                        FilterChip(
                            selected = selectedGroupId == group.id,
                            onClick = {
                                selectedGroupId = if (selectedGroupId == group.id) null else group.id
                            },
                            label = { Text("${group.name} (${group.blockedPackages.size})") }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { showCustomAppPicker = true },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            if (extraCustomPackages.isEmpty()) "+ Select Individual Apps"
                            else "+ ${extraCustomPackages.size} Extra Apps Selected"
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Shorts/Reels", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.width(6.dp))
                        Switch(
                            checked = blockShortsReels,
                            onCheckedChange = { blockShortsReels = it }
                        )
                    }
                }
            }
        }

        // Tag, Note & Ambient Soundscape Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Session Tag & Soundscape", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SESSION_TAGS.forEach { tag ->
                        FilterChip(
                            selected = selectedTag == tag,
                            onClick = { selectedTag = tag },
                            label = { Text(tag) }
                        )
                    }
                }

                OutlinedTextField(
                    value = sessionNote,
                    onValueChange = { sessionNote = it },
                    label = { Text("Study Goal Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                SoundscapeControlSection(
                    selected = settings.selectedSoundscape,
                    volume = settings.soundVolume,
                    onSelect = onSelectSoundscape,
                    onVolumeChange = onUpdateVolume
                )
            }
        }

        // Start Focus Button
        Button(
            onClick = {
                if (selectedStrictLevel != StrictEnforcementLevel.NONE) {
                    showStrictConfirmationDialog = true
                } else {
                    onStartSession(
                        selectedMode,
                        workMinutes,
                        shortBreakMinutes,
                        longBreakMinutes,
                        cycles,
                        selectedStrictLevel,
                        selectedGroup,
                        extraCustomPackages,
                        blockShortsReels,
                        selectedTag,
                        sessionNote
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (selectedStrictLevel != StrictEnforcementLevel.NONE) StrictCrimson else AuroraEmerald,
                contentColor = if (selectedStrictLevel != StrictEnforcementLevel.NONE) Color.White else Color(0xFF032518)
            )
        ) {
            Icon(
                imageVector = if (selectedStrictLevel != StrictEnforcementLevel.NONE) Icons.Default.Lock else Icons.Default.PlayArrow,
                contentDescription = null
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (selectedStrictLevel != StrictEnforcementLevel.NONE) {
                    "Activate Strict Focus Session"
                } else {
                    "Start Focus Session"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // Session History Section
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Recent Focus History",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (recentSessions.isEmpty()) {
                    Text(
                        text = "No focus sessions recorded yet. Start a timer above to log your first verified block!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val fmt = remember { DateTimeFormatter.ofPattern("MMM d, HH:mm") }
                    recentSessions.take(8).forEach { s ->
                        val dt = Instant.ofEpochMilli(s.startTimeEpochMillis)
                            .atZone(ZoneId.systemDefault())
                            .format(fmt)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${s.tag} · ${s.modeType.displayName}",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "$dt · ${s.strictEnforcementMode.badge}" +
                                            if (s.interceptionCount > 0) " · 🛡 ${s.interceptionCount} blocked" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "${(s.effectiveWorkSeconds / 60L).coerceAtLeast(1)}m",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (s.outcome == SessionOutcome.STOPPED_EARLY) WarmAmber else AuroraEmerald
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
    }

    // Transparent Pre-Activation Strict Mode Confirmation Dialog
    if (showStrictConfirmationDialog) {
        val totalBlockedCount = ((selectedGroup?.blockedPackages?.toSet() ?: emptySet()) + extraCustomPackages).size
        AlertDialog(
            onDismissRequest = { showStrictConfirmationDialog = false },
            icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = StrictCrimson) },
            title = { Text("Confirm ${selectedStrictLevel.title}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Before starting, please confirm how Strict Mode operates:",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "• Pause, Skip, Stop, and Unlock buttons will be completely disabled until the timer reaches 00:00.\n" +
                            "• $totalBlockedCount selected apps will be intercepted across Home Launcher, Recents, Split-Screen, and Device Reboots.\n" +
                            "• Access restores automatically the instant your timer finishes.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = WarmAmber.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = if (selectedStrictLevel == StrictEnforcementLevel.STANDARD_STRICT) {
                                "Transparency Notice (Mode A): On standard personal Android phones, RegainFocus uses official Accessibility + Foreground Services and guards System Settings, but Android OS always permits determined system-level Force-Stop or permission revocation via deep system menus. For OS-enforced package suspension, use Mode B (Device Owner)."
                            } else {
                                "Device Owner Notice (Mode B): Blocked apps will be OS-suspended via DevicePolicyManager and Force-Stop / Uninstall controls will be restricted until the timer reaches 00:00."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showStrictConfirmationDialog = false
                        onStartSession(
                            selectedMode,
                            workMinutes,
                            shortBreakMinutes,
                            longBreakMinutes,
                            cycles,
                            selectedStrictLevel,
                            selectedGroup,
                            extraCustomPackages,
                            blockShortsReels,
                            selectedTag,
                            sessionNote
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StrictCrimson, contentColor = Color.White)
                ) {
                    Text("I Understand — Lock & Start")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStrictConfirmationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Individual App Multi-Select Picker Dialog
    if (showCustomAppPicker) {
        var searchQuery by remember { mutableStateOf("") }
        val filtered = remember(installedApps, searchQuery) {
            if (searchQuery.isBlank()) installedApps
            else installedApps.filter { it.label.contains(searchQuery, ignoreCase = true) }
        }

        AlertDialog(
            onDismissRequest = { showCustomAppPicker = false },
            title = { Text("Select Extra Apps to Block") },
            text = {
                Column(modifier = Modifier.height(380.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search installed apps") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        filtered.forEach { appInfo ->
                            val checked = appInfo.packageName in extraCustomPackages
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        extraCustomPackages = if (checked) {
                                            extraCustomPackages - appInfo.packageName
                                        } else {
                                            extraCustomPackages + appInfo.packageName
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppIconBadge(appInfo.icon, appInfo.label, size = 34.dp)
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(appInfo.label, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        appInfo.category,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { isChecked ->
                                        extraCustomPackages = if (isChecked) {
                                            extraCustomPackages + appInfo.packageName
                                        } else {
                                            extraCustomPackages - appInfo.packageName
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showCustomAppPicker = false }) {
                    Text("Done (${extraCustomPackages.size})")
                }
            }
        )
    }
}

@Composable
private fun ActiveFocusSessionContent(
    settings: UserSettings,
    activeSession: PersistedTimerSessionState,
    liveDisplayMillis: Long,
    liveStrictRemainingMillis: Long,
    enforcementStatus: DeviceEnforcementStatus,
    onTogglePauseFlexible: () -> Unit,
    onSkipBreakFlexible: () -> Unit,
    onStopFlexible: () -> Unit,
    onSelectSoundscape: (SoundscapeType) -> Unit,
    onUpdateVolume: (Float) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val app = context.applicationContext as RegainFocusApplication

    val ringProgress = if (activeSession.timerMode == TimerModeType.STOPWATCH) {
        1f
    } else {
        val denom = activeSession.phaseDurationMillis.coerceAtLeast(1L).toFloat()
        (1f - (liveDisplayMillis.toFloat() / denom)).coerceIn(0.02f, 1f)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = if (activeSession.isStrictActive) {
                StrictCrimson.copy(alpha = 0.18f)
            } else {
                AuroraEmerald.copy(alpha = 0.16f)
            }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (activeSession.isStrictActive) Icons.Default.Lock else Icons.Default.Shield,
                    contentDescription = null,
                    tint = if (activeSession.isStrictActive) StrictCrimson else AuroraEmerald,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = activeSession.strictLevel.badge,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (activeSession.isStrictActive) StrictCrimson else AuroraEmerald
                )
            }
        }

        Text(
            text = "${activeSession.phase.label} · ${activeSession.sessionTag}",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        if (activeSession.sessionNote.isNotBlank()) {
            Text(
                text = "\"${activeSession.sessionNote}\"",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        AuroraGoalRing(
            progress = ringProgress,
            centerTitle = TimeFormatters.formatMillisClock(liveDisplayMillis),
            centerSubtitle = if (activeSession.timerMode == TimerModeType.POMODORO) {
                "Round ${activeSession.currentCycle} of ${activeSession.totalCycles}"
            } else {
                activeSession.timerMode.displayName
            },
            ringSize = 236.dp,
            strokeWidth = 16.dp
        )

        // Shield Metrics Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${activeSession.blockedPackages.size}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = AuroraCyan
                    )
                    Text(
                        text = "Apps Restricted",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${activeSession.interceptionCount}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = AuroraEmerald
                    )
                    Text(
                        text = "Attempts Blocked",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Controls: Strict Mode shows ZERO pause/skip/stop controls!
        if (activeSession.isStrictActive) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = StrictCrimson.copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Strict Session Locked — ${TimeFormatters.formatMillisClock(liveStrictRemainingMillis)} Until Auto-Unlock",
                        style = MaterialTheme.typography.titleSmall,
                        color = StrictCrimson,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Pause, skip, and early exit are disabled to protect your commitment. Blocked apps restore automatically at 00:00.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    if (activeSession.strictLevel == StrictEnforcementLevel.DEVICE_OWNER_STRICT &&
                        activeSession.useLockTaskInDeviceOwner &&
                        activity != null &&
                        !enforcementStatus.isInLockTaskMode
                    ) {
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = { app.container.deviceOwnerManager.startLockTaskIfPermitted(activity) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Enter Kiosk Lock Task Pinning")
                        }
                    }
                }
            }
        } else {
            // Flexible Mode Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onTogglePauseFlexible,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AuroraEmerald,
                        contentColor = Color(0xFF032518)
                    )
                ) {
                    Icon(
                        imageVector = if (activeSession.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (activeSession.isPaused) "Resume" else "Pause", fontWeight = FontWeight.Bold)
                }

                if (activeSession.phase == TimerPhase.SHORT_BREAK || activeSession.phase == TimerPhase.LONG_BREAK) {
                    OutlinedButton(
                        onClick = onSkipBreakFlexible,
                        modifier = Modifier.height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Skip")
                    }
                }

                OutlinedButton(
                    onClick = onStopFlexible,
                    modifier = Modifier.height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, tint = StrictCrimson)
                    Spacer(Modifier.width(4.dp))
                    Text("Finish", color = StrictCrimson)
                }
            }
        }

        // Live Soundscape Switcher Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SoundscapeControlSection(
                    selected = settings.selectedSoundscape,
                    volume = settings.soundVolume,
                    onSelect = onSelectSoundscape,
                    onVolumeChange = onUpdateVolume
                )
            }
        }
    }
}

@Composable
private fun SoundscapeControlSection(
    selected: SoundscapeType,
    volume: Float,
    onSelect: (SoundscapeType) -> Unit,
    onVolumeChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = AuroraCyan, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Ambient Focus Soundscape", style = MaterialTheme.typography.titleSmall)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SoundscapeType.entries.forEach { sound ->
                FilterChip(
                    selected = selected == sound,
                    onClick = { onSelect(sound) },
                    label = { Text(sound.title) }
                )
            }
        }

        if (selected != SoundscapeType.NONE) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Volume", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(12.dp))
                Slider(
                    value = volume,
                    onValueChange = onVolumeChange,
                    valueRange = 0.05f..1.0f,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
