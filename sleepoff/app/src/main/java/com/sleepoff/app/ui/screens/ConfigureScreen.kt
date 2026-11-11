package com.sleepoff.app.ui.screens

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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.activity.compose.BackHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sleepoff.app.domain.SleepMode
import com.sleepoff.app.ui.components.ModeSelector
import com.sleepoff.app.ui.components.SleepCard
import com.sleepoff.app.viewmodel.HomeViewModel

@Composable
fun ConfigureScreen(
    vm: HomeViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val btDelay by vm.btDelaySeconds.collectAsState()
    val fadeMin by vm.fadeMinutes.collectAsState()
    val restoreVol by vm.restoreVolume.collectAsState()
    val batterySaver by vm.batterySaver.collectAsState()
    val lastDur by vm.lastDurationMinutes.collectAsState()
    val lastMode by vm.lastMode.collectAsState()

    var hours by remember(lastDur) { mutableIntStateOf(lastDur / 60) }
    var minutes by remember(lastDur) { mutableIntStateOf(lastDur % 60) }
    var mode by remember(lastMode.name) { mutableStateOf(lastMode) }
    var saved by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Timer Configuration",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        SleepCard {
            Text("Custom duration", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Hours", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    androidx.compose.material3.OutlinedTextField(
                        value = hours.toString(),
                        onValueChange = { it.toIntOrNull()?.let { v -> hours = v.coerceIn(0, 12) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.padding(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Minutes", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    androidx.compose.material3.OutlinedTextField(
                        value = minutes.toString(),
                        onValueChange = { it.toIntOrNull()?.let { v -> minutes = v.coerceIn(0, 59) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Total: ${hours}h ${minutes}m",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SleepCard {
            Text("Shutdown mode", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            ModeSelector(selected = mode, onSelect = { mode = it })
        }

        SleepCard {
            Text("Delay before Bluetooth off", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Wait this long after pausing music before turning Bluetooth off.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text("${btDelay} seconds", style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = btDelay.toFloat(),
                onValueChange = { vm.setBtDelay(it.toInt()) },
                valueRange = 0f..30f,
                steps = 29
            )
        }

        SleepCard {
            Text("Gentle fade duration", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Volume fades over the final minutes in Gentle mode.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text("$fadeMin minutes", style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = fadeMin.toFloat(),
                onValueChange = { vm.setFadeMinutes(it.toInt()) },
                valueRange = 1f..15f,
                steps = 13
            )
        }

        SleepCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Restore volume after pause", style = MaterialTheme.typography.titleMedium)
                    Text("Bring media volume back to its original level after the timer fires.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = restoreVol, onCheckedChange = { vm.setRestoreVolume(it) })
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Battery saver mode", style = MaterialTheme.typography.titleMedium)
                    Text("Slower notifications and inexact alarms. Timer may fire a little late in deep Doze.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = batterySaver, onCheckedChange = { vm.setBatterySaver(it) })
            }
        }

        Button(
            onClick = {
                val totalMin = hours * 60 + minutes
                if (totalMin > 0) {
                    vm.setLastDurationMinutes(totalMin)
                    vm.setLastMode(mode)
                    saved = true
                    onBack()
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = MaterialTheme.shapes.extraLarge
        ) { Text(if (saved) "Saved" else "Save and return") }
    }
}
