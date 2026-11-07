package com.regainfocus.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import com.regainfocus.app.ui.theme.AuroraCyan
import com.regainfocus.app.ui.theme.AuroraEmerald

enum class SensitivePermissionType {
    USAGE_ACCESS,
    OVERLAY_SHIELD,
    ACCESSIBILITY_SHIELD
}

/**
 * Standalone, two-button Prominent In-App Disclosure Dialog meeting Google Play's
 * User Data & Sensitive Permissions Policy requirements:
 * 1) Shown prior to redirecting the user to any Android system permission toggle.
 * 2) Clearly states what data is accessed, how it is used, and that zero data leaves the device.
 * 3) Requires explicit affirmative consent ("I Agree — Open Settings") vs ("Not Now").
 */
@Composable
fun ProminentPermissionDisclosureDialog(
    permissionType: SensitivePermissionType,
    onAcceptAndOpenSettings: () -> Unit,
    onDecline: () -> Unit
) {
    val (icon, title, bodyPoints) = when (permissionType) {
        SensitivePermissionType.USAGE_ACCESS -> Triple(
            Icons.Default.QueryStats,
            "Usage Access Permission Disclosure",
            listOf(
                "What data is accessed: Foreground app package names, open counts, and time-in-app timestamps via Android's UsageStatsManager.",
                "How it is used: (1) To display your daily, weekly, and monthly screen-time analytics, and (2) to detect when an app you chose to block is opened during an active Focus Session or Daily App Limit.",
                "Privacy guarantee: RegainFocus has zero INTERNET permission in its manifest. Usage data is processed 100% locally on your phone and is never uploaded, shared, or sold."
            )
        )

        SensitivePermissionType.OVERLAY_SHIELD -> Triple(
            Icons.Default.Layers,
            "Display Over Other Apps Disclosure",
            listOf(
                "Why this permission is requested: Android 10–15 requires 'Display over other apps' so RegainFocus can display your Focus Countdown Shield when you open a distracting app during an active Focus Session.",
                "What it does NOT do: RegainFocus never draws hidden overlays, never intercepts taps over other apps, and only displays the full-screen Focus Shield when you open an app you explicitly selected to block.",
                "You can revoke this permission at any time in Android Settings."
            )
        )

        SensitivePermissionType.ACCESSIBILITY_SHIELD -> Triple(
            Icons.Default.Shield,
            "Accessibility Service Prominent Disclosure",
            listOf(
                "What data is accessed: RegainFocus uses Android's AccessibilityService API solely to receive top-level window package transition events (the package name of the currently opened app).",
                "No screen reading: Window content retrieval (canRetrieveWindowContent) is strictly disabled. RegainFocus CANNOT read screen text, messages, passwords, keystrokes, or financial information.",
                "How it is used: Solely to detect when an app you selected in your Blocking Group or Daily Limit is opened and immediately show your Focus Timer Shield.",
                "100% On-Device Privacy: RegainFocus declares zero INTERNET permission. No data ever leaves your device."
            )
        )
    }

    AlertDialog(
        onDismissRequest = onDecline,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AuroraEmerald
            )
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                bodyPoints.forEach { bullet ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                    ) {
                        Text(
                            text = "• $bullet",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onAcceptAndOpenSettings,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AuroraEmerald,
                    contentColor = Color(0xFF032518)
                )
            ) {
                Text("I Agree — Open Settings", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text("Not Now", color = AuroraCyan)
            }
        }
    )
}
