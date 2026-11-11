package com.sleepoff.app.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * SleepOff dark AMOLED palette — deep navy/black background with blue+purple
 * accents, per the spec. Light theme is intentionally NOT provided: SleepOff
 * is a nighttime app, so AMOLED-dark is the only experience.
 */
private val amoledScheme = darkColorScheme(
    background = Color(0xFF000000),
    surface = Color(0xFF0B1020),
    surfaceVariant = Color(0xFF141A2E),
    onSurface = Color(0xFFE6E9F5),
    onSurfaceVariant = Color(0xFF9AA2BD),
    primary = Color(0xFF8FA8FF),       // soft blue
    primaryContainer = Color(0xFF1E2A55),
    onPrimaryContainer = Color(0xFFD3DDFF),
    secondary = Color(0xFFB991FF),     // soft purple
    secondaryContainer = Color(0xFF2E1F52),
    onSecondaryContainer = Color(0xFFEADDFF),
    tertiary = Color(0xFFFFB0C7),
    outline = Color(0xFF2B3350),
    outlineVariant = Color(0xFF3C4668),
    error = Color(0xFFFF8A8A),
    onPrimary = Color(0xFF0A0F24),
)

private val sleepTypography = Typography(
    displayLarge = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.Light, letterSpacing = (-1).sp),
    headlineLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun SleepOffTheme(content: @Composable () -> Unit) {
    // Always dark/AMOLED — explicit product decision for a sleep timer.
    MaterialTheme(
        colorScheme = amoledScheme,
        typography = sleepTypography,
        content = content
    )
}
