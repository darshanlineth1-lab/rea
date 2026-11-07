package com.regainfocus.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.regainfocus.app.BuildConfig
import com.regainfocus.app.domain.model.DeviceEnforcementStatus
import com.regainfocus.app.domain.model.PersistedTimerSessionState
import com.regainfocus.app.domain.model.ThemeMode
import com.regainfocus.app.domain.model.UserSettings
import com.regainfocus.app.ui.components.SensitivePermissionType
import com.regainfocus.app.ui.theme.AuroraEmerald
import com.regainfocus.app.ui.theme.StrictCrimson
import com.regainfocus.app.util.TimeFormatters
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: UserSettings,
    activeSession: PersistedTimerSessionState,
    enforcementStatus: DeviceEnforcementStatus,
    onRequestPermissionWithDisclosure: (SensitivePermissionType) -> Unit,
    onUpdateTheme: (ThemeMode) -> Unit,
    onUpdateSettings: (requiresUnlockedStrict: Boolean, (UserSettings) -> UserSettings) -> Unit,
    onExportJson: suspend () -> String,
    onImportJson: (String) -> Unit,
    onClearHistory: () -> Unit,
    onPostMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showClearConfirm by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val content = onExportJson()
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(content.encodeToByteArray())
                    }
                    onPostMessage("Exported JSON backup successfully.")
                }.onFailure {
                    onPostMessage("Failed to write backup file.")
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val raw = context.contentResolver.openInputStream(uri)?.use {
                    it.readBytes().decodeToString()
                }
                if (!raw.isNullOrBlank()) {
                    onImportJson(raw)
                }
            }.onFailure {
                onPostMessage("Could not read selected backup file.")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Settings & Privacy",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        // 1. Theme Selection
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Theme & Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { onUpdateTheme(mode) },
                            label = { Text(mode.label) }
                        )
                    }
                }
            }
        }

        // 2. Daily Goals & Timer Defaults
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Goals & Timer Customization", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                Text(
                    "Daily Focus Goal: ${TimeFormatters.formatMinutesCompact(settings.dailyFocusGoalMinutes)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = settings.dailyFocusGoalMinutes.toFloat(),
                    onValueChange = { v ->
                        val stepped = ((v / 15f).roundToInt() * 15).coerceAtLeast(15)
                        onUpdateSettings(false) { it.copy(dailyFocusGoalMinutes = stepped) }
                    },
                    valueRange = 15f..360f
                )

                Text(
                    "Daily Screen-Time Budget: ${TimeFormatters.formatMinutesCompact(settings.dailyScreenTimeGoalMinutes)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = settings.dailyScreenTimeGoalMinutes.toFloat(),
                    onValueChange = { v ->
                        val stepped = ((v / 15f).roundToInt() * 15).coerceAtLeast(30)
                        onUpdateSettings(false) { it.copy(dailyScreenTimeGoalMinutes = stepped) }
                    },
                    valueRange = 30f..480f
                )

                Text(
                    "Default Pomodoro Focus: ${settings.pomodoroWorkMinutes}m · Short Break: ${settings.pomodoroShortBreakMinutes}m · Long Break: ${settings.pomodoroLongBreakMinutes}m",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = settings.pomodoroWorkMinutes.toFloat(),
                    onValueChange = { v ->
                        val stepped = ((v / 5f).roundToInt() * 5).coerceAtLeast(10)
                        onUpdateSettings(false) { it.copy(pomodoroWorkMinutes = stepped) }
                    },
                    valueRange = 10f..90f
                )

                SettingToggleRow(
                    title = "Play Ambient Sound During Breaks",
                    subtitle = "Continue rainfall / brown noise through short & long breaks",
                    checked = settings.playSoundDuringBreaks,
                    onCheckedChange = { enabled ->
                        onUpdateSettings(false) { it.copy(playSoundDuringBreaks = enabled) }
                    }
                )
            }
        }

        // 3. Permissions & Consent Center
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = AuroraEmerald)
                    Spacer(Modifier.width(8.dp))
                    Text("Permissions & Consent Disclosures", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }

                PermissionActionRow(
                    label = "Usage Access (Screen Time & App Detection)",
                    granted = enforcementStatus.hasUsageStatsPermission,
                    onClick = {
                        onRequestPermissionWithDisclosure(SensitivePermissionType.USAGE_ACCESS)
                    }
                )

                if (enforcementStatus.supportsAccessibilityBlocker) {
                    PermissionActionRow(
                        label = "Accessibility Focus Shield (Package-Only)",
                        granted = enforcementStatus.isAccessibilityServiceEnabled,
                        onClick = {
                            onRequestPermissionWithDisclosure(SensitivePermissionType.ACCESSIBILITY_SHIELD)
                        }
                    )
                } else {
                    PermissionActionRow(
                        label = "Focus Shield Display (Over Other Apps)",
                        granted = enforcementStatus.canDrawOverlays,
                        onClick = {
                            onRequestPermissionWithDisclosure(SensitivePermissionType.OVERLAY_SHIELD)
                        }
                    )
                }
            }
        }

        // 4. Notifications & Study Reminders
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Notifications & Reminders", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                SettingToggleRow(
                    title = "Session Completion Alerts",
                    subtitle = "Notify when focus timers and strict blocks finish",
                    checked = settings.enableSessionNotifications,
                    onCheckedChange = { v -> onUpdateSettings(false) { it.copy(enableSessionNotifications = v) } }
                )
                SettingToggleRow(
                    title = "Break Phase Reminders",
                    subtitle = "Notify on Pomodoro work/break transitions",
                    checked = settings.enableBreakReminders,
                    onCheckedChange = { v -> onUpdateSettings(false) { it.copy(enableBreakReminders = v) } }
                )
                SettingToggleRow(
                    title = "Daily Study Goal Reminder (${String.format("%02d:00", settings.dailyGoalReminderHour)})",
                    subtitle = "Evening nudge if your daily focus goal isn't met yet",
                    checked = settings.enableDailyGoalReminder,
                    onCheckedChange = { v -> onUpdateSettings(false) { it.copy(enableDailyGoalReminder = v) } }
                )
                if (settings.enableDailyGoalReminder) {
                    Slider(
                        value = settings.dailyGoalReminderHour.toFloat(),
                        onValueChange = { v ->
                            onUpdateSettings(false) { it.copy(dailyGoalReminderHour = v.roundToInt()) }
                        },
                        valueRange = 16f..23f
                    )
                }
            }
        }

        // 5. Data Management & Local Privacy
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Local Data Management", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { exportLauncher.launch("regainfocus-backup.json") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Export JSON")
                    }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                        enabled = !activeSession.isStrictActive,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Import JSON")
                    }
                }

                OutlinedButton(
                    onClick = { showClearConfirm = true },
                    enabled = !activeSession.isStrictActive,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, tint = StrictCrimson)
                    Spacer(Modifier.width(6.dp))
                    Text("Reset Session History", color = StrictCrimson)
                }
            }
        }

        // 6. Privacy & Build Audit Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PrivacyTip, contentDescription = null, tint = AuroraEmerald)
                    Spacer(Modifier.width(8.dp))
                    Text("Privacy & Minimum-Privilege Architecture", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    text = "RegainFocus v${BuildConfig.VERSION_NAME} · 100% Local-First Android Application.\n" +
                        "• Zero INTERNET and zero ACCESS_NETWORK_STATE permissions in AndroidManifest.xml.\n" +
                        "• Zero SMS, Contacts, Microphone, Camera, Location, or Notification-Listener permissions.\n" +
                        "• Narrow Android 11+ <queries> visibility (no QUERY_ALL_PACKAGES).\n" +
                        "• Built with Kotlin, Jetpack Compose, Material 3, Room, DataStore & WorkManager (MIT foundation: Focus-Modes-App).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Reset All Focus History?") },
            text = { Text("This deletes stored focus sessions, habit check-ins, and interception logs from local storage.") },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirm = false
                        onClearHistory()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StrictCrimson)
                ) {
                    Text("Delete History")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PermissionActionRow(
    label: String,
    granted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (granted) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AuroraEmerald, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Granted", style = MaterialTheme.typography.labelMedium, color = AuroraEmerald)
            }
        } else {
            OutlinedButton(onClick = onClick, shape = RoundedCornerShape(10.dp)) {
                Text("Review & Grant", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
