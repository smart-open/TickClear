package com.tickclear.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
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
    TopLevelDestination(Routes.STATS, R.string.tab_stats, Icons.Filled.BarChart),
    TopLevelDestination(Routes.SETTINGS, R.string.tab_settings, Icons.Filled.Settings),
)

/** 路由常量（含子路由，Phase 3/5/6 逐步接入）。 */
object Routes {
    const val TODAY = "today"
    const val TASKS = "tasks"
    const val STATS = "stats"
    const val ASSISTANT = "assistant"
    const val SETTINGS = "settings"
    const val RECYCLE_BIN = "tasks/recycleBin"
    const val ASR_CONFIG = "settings/asr"
    const val LLM_CONFIG = "settings/llm"
    const val DEBUG = "settings/debug"
    const val ABOUT = "settings/about"
    const val ASSISTANT_CONFIG = "assistant/config"
}
