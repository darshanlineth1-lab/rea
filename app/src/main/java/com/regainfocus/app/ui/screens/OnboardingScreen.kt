package com.regainfocus.app.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.regainfocus.app.domain.model.DeviceEnforcementStatus
import com.regainfocus.app.ui.components.ProminentPermissionDisclosureDialog
import com.regainfocus.app.ui.components.SensitivePermissionType
import com.regainfocus.app.ui.theme.AuroraCyan
import com.regainfocus.app.ui.theme.AuroraEmerald
import com.regainfocus.app.util.TimeFormatters
import kotlin.math.roundToInt

@Composable
fun OnboardingScreen(
    enforcementStatus: DeviceEnforcementStatus,
    onRefreshPermissions: () -> Unit,
    onRequestPermissionWithDisclosure: (SensitivePermissionType) -> Unit,
    onComplete: (dailyFocusMinutes: Int, dailyScreenGoalMinutes: Int) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    var focusGoalSlider by remember { mutableFloatStateOf(120f) }
    var screenBudgetSlider by remember { mutableFloatStateOf(180f) }

    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { onRefreshPermissions() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRefreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = AuroraEmerald.copy(alpha = 0.18f),
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = AuroraEmerald,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = "Welcome to RegainFocus",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "100% On-Device Deep Focus & Strict App Blocker",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "1. Set Your Daily Targets",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Daily Study / Focus Goal", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        TimeFormatters.formatMinutesCompact(focusGoalSlider.roundToInt()),
                        style = MaterialTheme.typography.titleSmall,
                        color = AuroraEmerald
                    )
                }
                Slider(
                    value = focusGoalSlider,
                    onValueChange = { focusGoalSlider = (it / 15f).roundToInt() * 15f },
                    valueRange = 30f..360f
                )

                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Daily Screen-Time Budget", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        TimeFormatters.formatMinutesCompact(screenBudgetSlider.roundToInt()),
                        style = MaterialTheme.typography.titleSmall,
                        color = AuroraCyan
                    )
                }
                Slider(
                    value = screenBudgetSlider,
                    onValueChange = { screenBudgetSlider = (it / 15f).roundToInt() * 15f },
                    valueRange = 60f..480f
                )
            }
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
                    text = "2. Optional Focus & Screen-Time Permissions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "RegainFocus declares zero INTERNET permission. Each sensitive permission shows a clear disclosure before opening Android Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                PermissionSetupRow(
                    icon = Icons.Default.QueryStats,
                    title = "Screen-Time Usage Access",
                    subtitle = "Reads per-app screen time & foreground package opens locally",
                    isGranted = enforcementStatus.hasUsageStatsPermission,
                    onGrant = {
                        onRequestPermissionWithDisclosure(SensitivePermissionType.USAGE_ACCESS)
                    }
                )

                if (enforcementStatus.supportsAccessibilityBlocker) {
                    PermissionSetupRow(
                        icon = Icons.Default.Shield,
                        title = "Accessibility Focus Shield",
                        subtitle = "Detects blocked app launches (no screen content reading)",
                        isGranted = enforcementStatus.isAccessibilityServiceEnabled,
                        onGrant = {
                            onRequestPermissionWithDisclosure(SensitivePermissionType.ACCESSIBILITY_SHIELD)
                        }
                    )
                } else {
                    PermissionSetupRow(
                        icon = Icons.Default.Layers,
                        title = "Focus Shield Display (Over Other Apps)",
                        subtitle = "Allows showing your Focus Countdown Shield over blocked apps",
                        isGranted = enforcementStatus.canDrawOverlays,
                        onGrant = {
                            onRequestPermissionWithDisclosure(SensitivePermissionType.OVERLAY_SHIELD)
                        }
                    )
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    PermissionSetupRow(
                        icon = Icons.Default.Notifications,
                        title = "Timer & Goal Notifications",
                        subtitle = "Displays live focus countdown & break alerts",
                        isGranted = enforcementStatus.hasNotificationPermission,
                        onGrant = {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    )
                }
            }
        }

        Button(
            onClick = {
                onComplete(
                    focusGoalSlider.roundToInt(),
                    screenBudgetSlider.roundToInt()
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AuroraEmerald,
                contentColor = Color(0xFF032518)
            )
        ) {
            Text(
                text = "Launch RegainFocus Dashboard",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PermissionSetupRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isGranted: Boolean,
    onGrant: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isGranted) AuroraEmerald else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            if (isGranted) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Enabled",
                    tint = AuroraEmerald,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                OutlinedButton(
                    onClick = onGrant,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Review", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
