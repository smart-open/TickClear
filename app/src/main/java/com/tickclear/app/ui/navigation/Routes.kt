package com.tickclear.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Today
import androidx.compose.ui.graphics.vector.ImageVector
import com.tickclear.app.R

/** 底部一级导航目标。 */
data class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
)

val topLevelDestinations = listOf(
    TopLevelDestination(Routes.TODAY, R.string.tab_today, Icons.Filled.Today),
    TopLevelDestination(Routes.ASSISTANT, R.string.tab_assistant, Icons.Filled.SmartToy),
    TopLevelDestination(Routes.TASKS, R.string.tab_tasks, Icons.Filled.CheckBox),
    TopLevelDestination(Routes.HABITS, R.string.tab_habits, Icons.Filled.Star),
    TopLevelDestination(Routes.TOOLS, R.string.tab_tools, Icons.Filled.Build),
    TopLevelDestination(Routes.SETTINGS, R.string.tab_settings, Icons.Filled.Settings),
)

/** 路由常量（含子路由）。 */
object Routes {
    const val TODAY = "today"
    const val TASKS = "tasks"
    const val HABITS = "habits"
    const val STATS = "stats"
    const val TOOLS = "tools"
    const val ASSISTANT = "assistant"
    const val SETTINGS = "settings"
    const val RECYCLE_BIN = "tasks/recycleBin"
    const val DEBUG = "settings/debug"
    const val ABOUT = "settings/about"
    const val VOICE_HISTORY = "settings/voiceHistory"
    // V2.8X++：ASR_CONFIG/LLM_CONFIG/ASSISTANT_CONFIG 三个从未接线的死常量已删；
    // 助手配置直达改用 "assistant?openConfig=true" 参数化路由（见 TickClearNavGraph）。

    // V2.9：工具箱子路由（统计 Tab 改造为工具箱；统计详情仍经 Today 进度环进入 Routes.STATS）。
    const val TOOLS_WATER = "tools/water"
    const val TOOLS_REST = "tools/rest"
    const val TOOLS_VOICE = "tools/voice"
    const val TOOLS_VAULT = "tools/vault"
}
