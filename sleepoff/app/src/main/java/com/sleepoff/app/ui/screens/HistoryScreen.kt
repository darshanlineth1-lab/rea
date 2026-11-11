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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sleepoff.app.domain.HistoryEntry
import com.sleepoff.app.domain.SleepMode
import com.sleepoff.app.ui.components.SleepCard
import com.sleepoff.app.viewmodel.HomeViewModel
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(vm: HomeViewModel, onBack: () -> Unit = {}) {
    BackHandler(onBack = onBack)
    val historyJson by vm.historyJson.collectAsState(initial = "[]")
    var confirmClear by remember { mutableStateOf(false) }

    val entries = try {
        val arr = JSONArray(historyJson)
        val out = mutableListOf<HistoryEntry>()
        for (i in (arr.length() - 1) downTo 0.coerceAtLeast(arr.length() - 50)) {
            val o = arr.getJSONObject(i)
            out += HistoryEntry(
                startedEpochMs = o.optLong("startedEpochMs"),
                endedEpochMs = o.optLong("endedEpochMs"),
                durationMs = o.optLong("durationMs"),
                mode = runCatching { SleepMode.valueOf(o.optString("mode")) }.getOrDefault(SleepMode.MUSIC_BT),
                musicPaused = o.optBoolean("musicPaused"),
                bluetoothTurnedOff = o.optBoolean("bluetoothTurnedOff"),
                bluetoothAttempted = o.optBoolean("bluetoothAttempted"),
                note = if (o.isNull("note")) null else o.optString("note")
            )
        }
        out
    } catch (_: Throwable) { emptyList() }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Timer History", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface)
            IconButton(onClick = { confirmClear = true }) {
                Icon(Icons.Filled.Delete, contentDescription = "Clear history")
            }
        }
        Spacer(Modifier.height(8.dp))

        if (entries.isEmpty()) {
            SleepCard {
                Text("No completed timers yet.", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text("Finished timers will appear here.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(entries) { e -> HistoryItemCard(e) }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all history?") },
            text = { Text("This removes every recorded timer entry.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; vm.clearHistory() }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun HistoryItemCard(e: HistoryEntry) {
    val df = SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault())
    val durMin = (e.durationMs / 60000).toInt()
    val outcomes = buildList {
        if (e.mode == SleepMode.BT_ONLY) add("Music kept playing") else add(if (e.musicPaused) "Music paused" else "Music could not be paused")
        add(when {
            e.bluetoothTurnedOff -> "Bluetooth turned off"
            e.bluetoothAttempted -> "Bluetooth still on"
            else -> "Bluetooth untouched"
        })
    }
    SleepCard {
        Text(df.format(Date(e.endedEpochMs)), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(2.dp))
        Text("${e.mode.label} • $durMin minutes",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        outcomes.forEach {
            Text("• $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        if (!e.note.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(e.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}


