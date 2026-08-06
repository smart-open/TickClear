package com.tickclear.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class ThemeMode {
    LIGHT, DARK, DYNAMIC
}

/** 逐层解包 [ContextWrapper] 找到宿主 Activity；Service/预览等无 Activity 场景返回 null。 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun TickClearTheme(
    mode: ThemeMode = ThemeMode.LIGHT,
    skin: ThemeSkin = ThemeSkin.BLUE,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    // 是否深色：必须与下方 colorScheme 的分支判定完全一致，否则系统栏图标会与内容明暗相反。
    // DYNAMIC 在 API<31 回退浅色方案，故此处也必须带上版本判断。
    val dark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.DYNAMIC -> Build.VERSION.SDK_INT >= 31 && isSystemInDarkTheme()
    }
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

    // 系统栏与应用内主题对齐：
    // 框架窗口主题走资源限定符（values/ 与 values-night/）跟随**系统**深色模式，而应用内主题由
    // ThemeMode 设置决定，两者天然可能相反（如系统深色 + 应用内浅色，为本 App 默认组合）。
    // 不做这层同步时，statusBarColor 透出的是深色 windowBackground、windowLightStatusBar 又给出
    // 与内容相反的图标明暗，表现为浅色界面上下夹黑色带、状态栏图标看不清。
    // 这里按已解析的 dark 值统一驱动系统栏底色与图标明暗，不改动布局（不启用 edge-to-edge），零回归。
    val view = LocalView.current
    if (!view.isInEditMode) {
        val barColor = colorScheme.background.toArgb()
        SideEffect {
            val window = context.findActivity()?.window ?: return@SideEffect
            window.statusBarColor = barColor
            window.navigationBarColor = barColor
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
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
