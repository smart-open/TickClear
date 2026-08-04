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
    const val TOOLS_EYECARE = "tools/eyecare"
    const val TOOLS_VOICE = "tools/voice"
    const val TOOLS_VAULT = "tools/vault"
    // V2.9++：工具箱新增（二维码 / 到期提醒 / 午休小憩 / 听力保护）
    const val TOOLS_QR = "tools/qr"
    const val TOOLS_EXPIRY = "tools/expiry"
    const val TOOLS_NAP = "tools/nap"
    const val TOOLS_HEARING = "tools/hearing"
    // V2.9++：工具箱新增（手电筒 / 测距仪 / 噪音检测 / 抽签器）
    const val TOOLS_FLASHLIGHT = "tools/flashlight"
    const val TOOLS_RULER = "tools/ruler"
    const val TOOLS_NOISE = "tools/noise"
    const val TOOLS_LOTTERY = "tools/lottery"
    // V2.9++：工具箱新增（视力自测 / 情绪打卡 / 番茄专注 / 表格计算）
    const val TOOLS_VISION = "tools/vision"
    const val TOOLS_MOOD = "tools/mood"
    const val TOOLS_POMODORO = "tools/pomodoro"
    const val TOOLS_CALC = "tools/calc"
    // V2.9++：工具箱新增（水平仪 / 称重器 / 悬浮时钟 / 倒计时）
    const val TOOLS_LEVEL = "tools/level"
    const val TOOLS_SCALE = "tools/scale"
    const val TOOLS_CLOCK_OVERLAY = "tools/clockOverlay"
    const val TOOLS_COUNTDOWN = "tools/countdown"
    // V2.9++：工具箱新增（条码识别 / 马赛克 / 指南针 / 打卡补录）
    const val TOOLS_BARCODE = "tools/barcode"
    const val TOOLS_MOSAIC = "tools/mosaic"
    const val TOOLS_COMPASS = "tools/compass"
    const val TOOLS_BACKFILL = "tools/backfill"
    // V2.9++：工具箱新增（去水印 / 摄像头检测 / 剪贴板保护）
    const val TOOLS_WATERMARK = "tools/watermark"
    const val TOOLS_CAMERA_DETECT = "tools/cameraDetect"
    const val TOOLS_CLIPBOARD_GUARD = "tools/clipboardGuard"
    // V2.9++：工具箱新增（到站提醒 / 备份导出 / 隐私检查）
    const val TOOLS_ARRIVAL = "tools/arrival"
    const val TOOLS_BACKUP = "tools/backup"
    const val TOOLS_PRIVACY = "tools/privacy"
}
