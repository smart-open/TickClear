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
    // V2.9++：工具箱新增（贷款测算 / 个税测算）
    const val TOOLS_LOAN = "tools/loan"
    const val TOOLS_TAX = "tools/tax"
    // V2.9++：工具箱新增（图片压缩 / 图片黑白）
    const val TOOLS_IMG_COMPRESS = "tools/imgCompress"
    const val TOOLS_IMG_GRAY = "tools/imgGray"
    // V2.9++：工具箱新增（烹饪多组定时器 / 动物拟声）
    const val TOOLS_COOK_TIMER = "tools/cookTimer"
    const val TOOLS_ANIMAL = "tools/animal"
    // V2.9++：工具箱新增「模拟解压」分类（动物拟声归纳于此 + 6 个解压玩具）
    const val TOOLS_SIM_CAN = "tools/simCan"
    const val TOOLS_SIM_CANDLE = "tools/simCandle"
    const val TOOLS_SIM_WOOD = "tools/simWood"
    const val TOOLS_SIM_LIGHTER = "tools/simLighter"
    const val TOOLS_SIM_FIREWORKS = "tools/simFireworks"
    const val TOOLS_SIM_PINBALL = "tools/simPinball"
    // V2.9++：模拟解压（拟声玻璃杯敲击）
    const val TOOLS_SIM_GLASS = "tools/simGlass"
    // V2.9++：模拟解压（吹笛子：麦克风采集气流→时域能量分析→驱动 AudioTrack 合成笛声）
    const val TOOLS_SIM_FLUTE = "tools/simFlute"
    // V2.9++：娱乐休闲（今日运势 / 石头剪刀布 / 振动按摩）
    const val TOOLS_FORTUNE = "tools/fortune"
    const val TOOLS_RPS = "tools/rps"
    const val TOOLS_VIBE = "tools/vibe"
    // V2.9++：助眠（睡眠白噪音）
    const val TOOLS_WHITE_NOISE = "tools/whiteNoise"
    // V2.9++：实用工具（补光反光板）
    const val TOOLS_REFLECTOR = "tools/reflector"
    // V2.9++：模拟解压（养宠物：鱼/狗/猪/猫）
    const val TOOLS_PET = "tools/pet"
    // V2.9++：模拟解压（手指涂鸦画板）
    const val TOOLS_DOODLE = "tools/doodle"
    // V2.9++：实用工具（屏幕坏点检测）
    const val TOOLS_DEADPIXEL = "tools/deadPixel"
    // V2.9++：实用工具（地磁场观测）
    const val TOOLS_MAGNET = "tools/magnet"
    // V2.9++：生活助手（家庭成员积分仪）
    const val TOOLS_POINTS = "tools/points"
}
