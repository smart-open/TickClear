package com.tickclear.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ── 品牌色（D.7 设计令牌）──────────────────────────────
val ClearBlue = Color(0xFF2F6BFF)      // 清空蓝 · 主色
val ClearBlueDark = Color(0xFF6E9BFF)
val Mint = Color(0xFF21C19B)           // 薄荷绿（扩展）
val Violet = Color(0xFF7C5CFF)
val Ink = Color(0xFF1A1C20)
val InkSoft = Color(0xFF5A6068)
val SurfaceLight = Color(0xFFF7F8FA)
val SurfaceDark = Color(0xFF121419)
val Danger = Color(0xFFE5484D)
val Warning = Color(0xFFF5A623)
val Success = Color(0xFF2BA24A)

internal val LightColorScheme = lightColorScheme(
    primary = ClearBlue,
    onPrimary = Color.White,
    secondary = ClearBlueDark,
    onSecondary = Color.White,
    tertiary = Violet,
    background = SurfaceLight,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEEF1F6),
    onSurfaceVariant = InkSoft,
    error = Danger,
    onError = Color.White,
    outline = Color(0xFFD5DAE2),
    primaryContainer = Color(0xFFE4ECFF),
    onPrimaryContainer = Color(0xFF0B3AA8),
)

internal val DarkColorScheme = darkColorScheme(
    primary = ClearBlueDark,
    onPrimary = Ink,
    secondary = ClearBlue,
    onSecondary = Color.White,
    tertiary = Color(0xFF9B86FF),
    background = SurfaceDark,
    onBackground = Color(0xFFE8EAEF),
    surface = Color(0xFF1B1F27),
    onSurface = Color(0xFFE8EAEF),
    surfaceVariant = Color(0xFF262B35),
    onSurfaceVariant = Color(0xFFAEB6C2),
    error = Danger,
    onError = Color.White,
    outline = Color(0xFF3A4150),
    primaryContainer = Color(0xFF1B3A8C),
    onPrimaryContainer = Color(0xFFCFDBFF),
)
