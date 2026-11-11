package com.sleepoff.app.ui.screens

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sleepoff.app.domain.SleepMode
import com.sleepoff.app.domain.TimerState
import com.sleepoff.app.ui.components.CircularCountdown
import com.sleepoff.app.ui.components.ModeSelector
import com.sleepoff.app.ui.components.PresetChip
import com.sleepoff.app.ui.components.SleepCard
import com.sleepoff.app.viewmodel.HomeViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onOpenSettings: () -> Unit,
    onOpenConfigure: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val bt by vm.bluetoothStatus.collectAsState()
    val timer by vm.timer.collectAsState()
    val lastMode by vm.lastMode.collectAsState()
    val lastDur by vm.lastDurationMinutes.collectAsState()
    val perms by vm.permState.collectAsState()

    val presets = listOf(15, 30, 45, 60, 90, 120)
    var selectedPreset by remember { mutableIntStateOf(presets.indexOf(lastDur).coerceAtLeast(1)) }
    var selectedMode by remember(lastMode.name) { mutableStateOf(lastMode) }
    var showCancelDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "SleepOff",
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Smart Bluetooth Sleep Timer",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row {
                IconButton(onClick = onOpenSchedule) {
                    Text("⏰", style = MaterialTheme.typography.titleLarge)
                }
                IconButton(onClick = onOpenHistory) {
                    Text("🕘", style = MaterialTheme.typography.titleLarge)
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            }
        }

        // Bluetooth status card
        SleepCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (bt.enabled) Icons.Filled.Bluetooth else Icons.Filled.BluetoothDisabled,
                        contentDescription = null,
                        tint = if (bt.enabled) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = when {
                                !bt.supported -> "Bluetooth not available"
                                bt.enabled && bt.deviceNames.isEmpty() -> "Bluetooth is on"
                                bt.enabled && bt.deviceNames.size == 1 -> "Connected: ${bt.deviceNames.first()}"
                                bt.enabled -> "Connected: ${bt.deviceNames.first()} +${bt.deviceNames.size - 1} more"
                                else -> "Bluetooth is off"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when {
                                !bt.supported -> ""
                                bt.enabled && bt.deviceNames.isEmpty() -> "No audio device connected"
                                bt.enabled -> "Ready for sleep timer"
                                else -> "Turn Bluetooth on to auto-disconnect"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (!bt.enabled && bt.supported) {
                    FilledTonalButton(onClick = { vm.openBluetoothSettings() }) {
                        Text("Enable")
                    }
                }
            }
        }

        // Countdown
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val label = when (timer.state) {
                TimerState.IDLE -> "Ready to sleep"
                TimerState.RUNNING -> "Music stops in"
                TimerState.PAUSED -> "Paused"
            }
            CircularCountdown(
                progress = timer.progress,
                remainingMs = timer.remainingMs,
                label = label,
                size = 280.dp
            )
        }

        if (timer.state == TimerState.RUNNING && timer.endEpochMs > 0) {
            val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            Text(
                text = "Stops at ${fmt.format(Date(timer.endEpochMs))}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }

        if (timer.state == TimerState.IDLE) {
            // Presets
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    presets.take(3).forEachIndexed { i, m ->
                        Box(modifier = Modifier.weight(1f)) {
                            PresetChip(
                                label = "$m min",
                                selected = selectedPreset == i,
                                onClick = { selectedPreset = i }
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    presets.drop(3).forEachIndexed { i2, m ->
                        val i = i2 + 3
                        Box(modifier = Modifier.weight(1f)) {
                            PresetChip(
                                label = "$m min",
                                selected = selectedPreset == i,
                                onClick = { selectedPreset = i }
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = onOpenConfigure,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Customize…")
                }
            }

            // Mode selector
            SleepCard {
                Text(
                    text = "Shutdown mode",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                ModeSelector(selected = selectedMode, onSelect = { selectedMode = it })
            }

            Button(
                onClick = {
                    val dur = if (selectedPreset in presets.indices) presets[selectedPreset] else lastDur
                    vm.startTimer(dur, selectedMode)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Text("Start Timer", style = MaterialTheme.typography.titleLarge)
            }
        } else {
            // Running / paused controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (timer.state == TimerState.RUNNING) {
                    FilledTonalButton(
                        onClick = { vm.pauseTimer() },
                        modifier = Modifier.weight(1f).height(54.dp)
                    ) { Text("Pause") }
                } else {
                    Button(
                        onClick = { vm.resumeTimer() },
                        modifier = Modifier.weight(1f).height(54.dp)
                    ) { Text("Resume") }
                }
                OutlinedButton(
                    onClick = { showCancelDialog = true },
                    modifier = Modifier.weight(1f).height(54.dp)
                ) { Text("Cancel") }
            }
            // Extend buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(5, 10, 15).forEach { mins ->
                    FilledTonalButton(
                        onClick = { vm.extendBy(mins) },
                        modifier = Modifier.weight(1f)
                    ) { Text("+${mins}m") }
                }
            }
            // Show current mode while running
            Text(
                text = "Mode: ${timer.mode.label} — ${timer.mode.description}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }

        // Battery saver card
        SleepCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.BatterySaver, null,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Battery saving",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "SleepOff uses an exact alarm and a low-impact foreground service, so it works with the screen off and in Doze mode without draining the battery.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Setup reminder if permissions are missing
        val needsSetup =
            !perms.notificationsGranted ||
            !perms.bluetoothConnectGranted ||
            !perms.exactAlarmGranted ||
            !perms.batteryOptExempt
        if (needsSetup) {
            FilledTonalButton(onClick = onOpenSetup, modifier = Modifier.fillMaxWidth()) {
                Text("Open setup for best reliability")
            }
        }
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Cancel timer?") },
            text = { Text("The sleep timer will be cancelled and music will keep playing.") },
            confirmButton = {
                TextButton(onClick = { showCancelDialog = false; vm.cancelTimer() }) { Text("Cancel timer") }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) { Text("Keep running") }
            }
        )
    }
}
