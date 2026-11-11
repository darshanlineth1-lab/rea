package com.sleepoff.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.sleepoff.app.domain.SleepMode
import com.sleepoff.app.ui.components.ModeSelector
import com.sleepoff.app.ui.components.SleepCard
import com.sleepoff.app.viewmodel.HomeViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun ScheduleScreen(
    vm: HomeViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val enabled by vm.scheduleEnabled.collectAsState()
    val hour by vm.scheduleHour.collectAsState()
    val minute by vm.scheduleMinute.collectAsState()
    val dur by vm.scheduleDuration.collectAsState()
    val mode by vm.scheduleMode.collectAsState()

    var showTimePicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Sleep Schedule", style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface)

        SleepCard {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Daily schedule", style = MaterialTheme.typography.titleMedium)
                    Text("Start a sleep timer automatically every day at the chosen time.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = enabled, onCheckedChange = {
                    vm.setSchedule(it, hour, minute, dur, mode)
                })
            }
        }

        SleepCard {
            Text("Start time", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            val hh = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
            Text(hh, style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { showTimePicker = true }) { Text("Change time") }
            if (enabled) {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
                }
                Spacer(Modifier.height(6.dp))
                val fmt = SimpleDateFormat("EEE HH:mm", Locale.getDefault())
                Text("Next run: ${fmt.format(cal.time)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("Schedule is off.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        SleepCard {
            Text("Timer duration", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 45, 60, 90).forEach { m ->
                    FilterChip(
                        selected = dur == m,
                        onClick = { vm.setSchedule(enabled, hour, minute, m, mode) },
                        label = { Text("${m}m") }
                    )
                }
            }
        }

        SleepCard {
            Text("Shutdown mode", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            ModeSelector(selected = mode, onSelect = { vm.setSchedule(enabled, hour, minute, dur, it) })
        }
    }

    if (showTimePicker) {
        val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Select start time") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    showTimePicker = false
                    vm.setSchedule(enabled, state.hour, state.minute, dur, mode)
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            }
        )
    }
}
