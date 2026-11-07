package com.regainfocus.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.regainfocus.app.domain.model.ThemeMode

private val DarkAuroraScheme = darkColorScheme(
    primary = AuroraEmerald,
    onPrimary = Color(0xFF042619),
    primaryContainer = Color(0xFF0E3E2C),
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = AuroraCyan,
    onSecondary = Color(0xFF062536),
    secondaryContainer = Color(0xFF0F354A),
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = AuroraIndigo,
    onTertiary = Color(0xFF141842),
    tertiaryContainer = Color(0xFF242B5E),
    onTertiaryContainer = Color(0xFFE0E7FF),
    error = AuroraCoral,
    onError = Color(0xFF4C0519),
    errorContainer = Color(0xFF651127),
    onErrorContainer = Color(0xFFFFE4E6),
    background = MidnightBg,
    onBackground = MidnightTextPrimary,
    surface = MidnightSurface,
    onSurface = MidnightTextPrimary,
    surfaceVariant = MidnightSurfaceHigh,
    onSurfaceVariant = MidnightTextSecondary,
    surfaceContainerLowest = Color(0xFF050A0E),
    surfaceContainerLow = MidnightSurfaceLow,
    surfaceContainer = MidnightSurface,
    surfaceContainerHigh = MidnightSurfaceHigh,
    surfaceContainerHighest = MidnightSurfaceHighest,
    outline = MidnightOutline,
    outlineVariant = Color(0xFF1D3040)
)

private val AmoledDarkScheme = DarkAuroraScheme.copy(
    background = AmoledBg,
    surface = AmoledSurface,
    surfaceContainerLowest = AmoledBg,
    surfaceContainerLow = AmoledSurface,
    surfaceContainer = AmoledSurface,
    surfaceContainerHigh = AmoledSurfaceHigh
)

private val LightSageScheme = lightColorScheme(
    primary = LightSagePrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF064E3B),
    secondary = LightSageSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFFAFE),
    onSecondaryContainer = Color(0xFF164E63),
    tertiary = Color(0xFF4F46E5),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0E7FF),
    onTertiaryContainer = Color(0xFF1E1B4B),
    error = Color(0xFFE11D48),
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E6),
    onErrorContainer = Color(0xFF881337),
    background = LightSageBg,
    onBackground = LightSageTextPrimary,
    surface = LightSageSurface,
    onSurface = LightSageTextPrimary,
    surfaceVariant = LightSageSurfaceHigh,
    onSurfaceVariant = LightSageTextSecondary,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = LightSageSurfaceLow,
    surfaceContainer = LightSageSurface,
    surfaceContainerHigh = LightSageSurfaceHigh,
    surfaceContainerHighest = Color(0xFFD5E3DD),
    outline = Color(0xFFB5C9C0),
    outlineVariant = Color(0xFFD5E3DD)
)

data class RegainExtendedColors(
    val isDark: Boolean,
    val accentEmerald: Color,
    val accentCyan: Color,
    val accentAmber: Color,
    val strictCrimson: Color
)

val LocalRegainColors = staticCompositionLocalOf {
    RegainExtendedColors(
        isDark = true,
        accentEmerald = AuroraEmerald,
        accentCyan = AuroraCyan,
        accentAmber = AuroraAmber,
        strictCrimson = StrictCrimson
    )
}

@Composable
fun RegainFocusTheme(
    themeMode: ThemeMode = ThemeMode.DARK_AURORA,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        ThemeMode.DARK_AURORA, ThemeMode.AMOLED_DARK -> true
        ThemeMode.LIGHT_SAGE -> false
        ThemeMode.SYSTEM -> systemDark
    }
    val scheme = when (themeMode) {
        ThemeMode.DARK_AURORA -> DarkAuroraScheme
        ThemeMode.AMOLED_DARK -> AmoledDarkScheme
        ThemeMode.LIGHT_SAGE -> LightSageScheme
        ThemeMode.SYSTEM -> if (systemDark) DarkAuroraScheme else LightSageScheme
    }
    val extended = RegainExtendedColors(
        isDark = useDark,
        accentEmerald = if (useDark) AuroraEmerald else LightSagePrimary,
        accentCyan = if (useDark) AuroraCyan else LightSageSecondary,
        accentAmber = if (useDark) AuroraAmber else Color(0xFFD97706),
        strictCrimson = StrictCrimson
    )

    CompositionLocalProvider(LocalRegainColors provides extended) {
        MaterialTheme(
            colorScheme = scheme,
            typography = RegainTypography,
            shapes = RegainShapes,
            content = content
        )
    }
}
