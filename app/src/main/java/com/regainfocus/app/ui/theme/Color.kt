package com.regainfocus.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Regain-Inspired Emerald Aurora & Midnight Slate Palette (Original Design System)
val AuroraEmerald = Color(0xFF2EE59D)
val AuroraEmeraldDim = Color(0xFF19B377)
val AuroraTeal = Color(0xFF14B8A6)
val AuroraCyan = Color(0xFF38BDF8)
val AuroraIndigo = Color(0xFF818CF8)
val AuroraAmber = Color(0xFFFBBF24)
val WarmAmber = AuroraAmber
val AuroraCoral = Color(0xFFFB7185)
val StrictCrimson = Color(0xFFF43F5E)

// Dark Aurora Surfaces
val MidnightBg = Color(0xFF080F14)
val MidnightSurface = Color(0xFF0E1821)
val MidnightSurfaceLow = Color(0xFF111D27)
val MidnightSurfaceHigh = Color(0xFF172633)
val MidnightSurfaceHighest = Color(0xFF203344)
val MidnightOutline = Color(0xFF2A4155)
val MidnightTextPrimary = Color(0xFFF1F7FA)
val MidnightTextSecondary = Color(0xFF9BB0C1)

// Pitch AMOLED Surfaces
val AmoledBg = Color(0xFF000000)
val AmoledSurface = Color(0xFF090D10)
val AmoledSurfaceHigh = Color(0xFF121A20)

// Light Sage Surfaces
val LightSageBg = Color(0xFFF4F8F6)
val LightSageSurface = Color(0xFFFFFFFF)
val LightSageSurfaceLow = Color(0xFFEEF4F1)
val LightSageSurfaceHigh = Color(0xFFE3ECE8)
val LightSagePrimary = Color(0xFF0B8A5F)
val LightSageSecondary = Color(0xFF0E7490)
val LightSageTextPrimary = Color(0xFF0D1B22)
val LightSageTextSecondary = Color(0xFF4A626D)

object RegainGradients {
    val auroraHeroDark = Brush.linearGradient(
        colors = listOf(
            Color(0xFF0E2A28),
            Color(0xFF0D2130),
            Color(0xFF121A2A)
        )
    )

    val auroraHeroLight = Brush.linearGradient(
        colors = listOf(
            Color(0xFFD7F7E9),
            Color(0xFFDDF2FA),
            Color(0xFFEBF5F0)
        )
    )

    val strictLockBanner = Brush.linearGradient(
        colors = listOf(
            Color(0xFF3B1220),
            Color(0xFF25122B),
            Color(0xFF131D2B)
        )
    )

    val emeraldCyanRing = Brush.sweepGradient(
        colors = listOf(
            AuroraEmerald,
            AuroraCyan,
            AuroraIndigo,
            AuroraEmerald
        )
    )

    val primaryButton = Brush.horizontalGradient(
        colors = listOf(
            AuroraEmerald,
            AuroraTeal
        )
    )
}
