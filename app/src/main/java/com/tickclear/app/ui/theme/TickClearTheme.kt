package com.tickclear.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

enum class ThemeMode {
    LIGHT, DARK, DYNAMIC
}

@Composable
fun TickClearTheme(
    mode: ThemeMode = ThemeMode.LIGHT,
    content: @Composable () -> Unit,
) {
    val colorScheme = when (mode) {
        ThemeMode.LIGHT -> LightColorScheme
        ThemeMode.DARK -> DarkColorScheme
        ThemeMode.DYNAMIC -> {
            // 低于 API 31 无动态取色，回退到浅色
            if (Build.VERSION.SDK_INT >= 31) {
                if (isSystemInDarkTheme()) dynamicDarkColorScheme(LocalContext.current)
                else dynamicLightColorScheme(LocalContext.current)
            } else {
                LightColorScheme
            }
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = TickClearTypography,
        shapes = TickClearShapes,
        content = content,
    )
}
