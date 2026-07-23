package com.tickclear.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * 响应式尺寸档位（按当前窗口宽度 dp）：
 * - COMPACT   < 600dp   （手机竖屏）
 * - MEDIUM    600–840   （手机横屏 / 小平板）
 * - EXPANDED  ≥ 840dp   （大平板 / 折叠展开）
 * 断点与 Material 指南一致；用 LocalConfiguration 宽度，旋转/分屏会随之更新。
 */
enum class AppSizeClass { COMPACT, MEDIUM, EXPANDED }

@Composable
fun rememberAppSizeClass(): AppSizeClass {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return when {
        widthDp < 600 -> AppSizeClass.COMPACT
        widthDp < 840 -> AppSizeClass.MEDIUM
        else -> AppSizeClass.EXPANDED
    }
}
