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
    skin: ThemeSkin = ThemeSkin.BLUE,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when (mode) {
        ThemeMode.LIGHT -> skinScheme(skin, dark = false)
        ThemeMode.DARK -> skinScheme(skin, dark = true)
        ThemeMode.DYNAMIC -> {
            // 低于 API 31 无动态取色，回退到该皮肤浅色方案
            if (Build.VERSION.SDK_INT >= 31) {
                if (isSystemInDarkTheme()) dynamicDarkColorScheme(context)
                else dynamicLightColorScheme(context)
            } else {
                skinScheme(skin, dark = false)
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
