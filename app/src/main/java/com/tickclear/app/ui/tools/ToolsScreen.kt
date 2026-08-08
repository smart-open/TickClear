package com.tickclear.app.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.FilterBAndW
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.EmojiFoodBeverage
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Games
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.WbIncandescent
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tickclear.app.R
import com.tickclear.app.ui.theme.Spacing

private data class ToolEntry(
    val route: String,
    val titleRes: Int,
    val descRes: Int,
    val icon: ImageVector,
)

private data class ToolCategory(
    val titleRes: Int,
    val entries: List<ToolEntry>,
)

private val TOOL_CATEGORIES = listOf(
    ToolCategory(
        titleRes = R.string.tools_cat_health,
        entries = listOf(
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_WATER,
                titleRes = R.string.tools_water_title,
                descRes = R.string.tools_water_desc,
                icon = Icons.Filled.LocalDrink,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_REST,
                titleRes = R.string.tools_rest_title,
                descRes = R.string.tools_rest_desc,
                icon = Icons.Filled.Weekend,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_EYECARE,
                titleRes = R.string.tools_eyecare_title,
                descRes = R.string.tools_eyecare_desc,
                icon = Icons.Filled.Visibility,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_NAP,
                titleRes = R.string.tools_nap_title,
                descRes = R.string.tools_nap_desc,
                icon = Icons.Filled.Hotel,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_WHITE_NOISE,
                titleRes = R.string.tools_white_noise_title,
                descRes = R.string.tools_white_noise_desc,
                icon = Icons.Filled.Audiotrack,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_HEARING,
                titleRes = R.string.tools_hearing_title,
                descRes = R.string.tools_hearing_desc,
                icon = Icons.AutoMirrored.Filled.VolumeUp,
            ),
        ),
    ),
    ToolCategory(
        titleRes = R.string.tools_cat_security,
        entries = listOf(
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_VOICE,
                titleRes = R.string.tools_voice_title,
                descRes = R.string.tools_voice_desc,
                icon = Icons.Filled.Mic,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_VAULT,
                titleRes = R.string.tools_vault_title,
                descRes = R.string.tools_vault_desc,
                icon = Icons.Filled.Lock,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_CAMERA_DETECT,
                titleRes = R.string.tools_camera_detect_title,
                descRes = R.string.tools_camera_detect_desc,
                icon = Icons.Filled.CameraAlt,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_CLIPBOARD_GUARD,
                titleRes = R.string.tools_clipboard_guard_title,
                descRes = R.string.tools_clipboard_guard_desc,
                icon = Icons.Filled.Lock,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_PRIVACY,
                titleRes = R.string.tools_privacy_title,
                descRes = R.string.tools_privacy_desc,
                icon = Icons.Filled.Security,
            ),
        ),
    ),
    ToolCategory(
        titleRes = R.string.tools_cat_life,
        entries = listOf(
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_QR,
                titleRes = R.string.tools_qr_title,
                descRes = R.string.tools_qr_desc,
                icon = Icons.Filled.QrCode,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_EXPIRY,
                titleRes = R.string.tools_expiry_title,
                descRes = R.string.tools_expiry_desc,
                icon = Icons.Filled.Event,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_BARCODE,
                titleRes = R.string.tools_barcode_title,
                descRes = R.string.tools_barcode_desc,
                icon = Icons.Filled.ViewWeek,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_COOK_TIMER,
                titleRes = R.string.tools_cook_timer_title,
                descRes = R.string.tools_cook_timer_desc,
                icon = Icons.Filled.Restaurant,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_POINTS,
                titleRes = R.string.points_title,
                descRes = R.string.points_desc,
                icon = Icons.Filled.EmojiEvents,
            ),
        ),
    ),
    ToolCategory(
        titleRes = R.string.tools_cat_sim,
        entries = listOf(
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_ANIMAL,
                titleRes = R.string.tools_animal_title,
                descRes = R.string.tools_animal_desc,
                icon = Icons.Filled.Pets,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_SIM_CAN,
                titleRes = R.string.tools_sim_can_title,
                descRes = R.string.tools_sim_can_desc,
                icon = Icons.Filled.EmojiFoodBeverage,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_SIM_CANDLE,
                titleRes = R.string.tools_sim_candle_title,
                descRes = R.string.tools_sim_candle_desc,
                icon = Icons.Filled.Cake,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_SIM_WOOD,
                titleRes = R.string.tools_sim_wood_title,
                descRes = R.string.tools_sim_wood_desc,
                icon = Icons.Filled.Spa,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_SIM_LIGHTER,
                titleRes = R.string.tools_sim_lighter_title,
                descRes = R.string.tools_sim_lighter_desc,
                icon = Icons.Filled.LocalFireDepartment,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_SIM_FIREWORKS,
                titleRes = R.string.tools_sim_fireworks_title,
                descRes = R.string.tools_sim_fireworks_desc,
                icon = Icons.Filled.Celebration,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_SIM_PINBALL,
                titleRes = R.string.tools_sim_pinball_title,
                descRes = R.string.tools_sim_pinball_desc,
                icon = Icons.Filled.SportsEsports,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_SIM_GLASS,
                titleRes = R.string.sim_glass_title,
                descRes = R.string.sim_glass_desc,
                icon = Icons.Filled.LocalBar,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_SIM_FLUTE,
                titleRes = R.string.sim_flute_title,
                descRes = R.string.sim_flute_desc,
                icon = Icons.Filled.GraphicEq,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_FORTUNE,
                titleRes = R.string.tools_fortune_title,
                descRes = R.string.tools_fortune_desc,
                icon = Icons.Filled.EmojiEmotions,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_RPS,
                titleRes = R.string.tools_rps_title,
                descRes = R.string.tools_rps_desc,
                icon = Icons.Filled.Games,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_VIBE,
                titleRes = R.string.tools_vibe_title,
                descRes = R.string.tools_vibe_desc,
                icon = Icons.Filled.Vibration,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_PET,
                titleRes = R.string.pet_title,
                descRes = R.string.pet_desc,
                icon = Icons.Filled.Pets,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_DOODLE,
                titleRes = R.string.doodle_title,
                descRes = R.string.doodle_desc,
                icon = Icons.Filled.Brush,
            ),
        ),
    ),
    ToolCategory(
        titleRes = R.string.tools_cat_utility,
        entries = listOf(
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_FLASHLIGHT,
                titleRes = R.string.tools_flashlight_title,
                descRes = R.string.tools_flashlight_desc,
                icon = Icons.Filled.FlashlightOn,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_RULER,
                titleRes = R.string.tools_ruler_title,
                descRes = R.string.tools_ruler_desc,
                icon = Icons.Filled.Straighten,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_NOISE,
                titleRes = R.string.tools_noise_title,
                descRes = R.string.tools_noise_desc,
                icon = Icons.Filled.GraphicEq,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_LOTTERY,
                titleRes = R.string.tools_lottery_title,
                descRes = R.string.tools_lottery_desc,
                icon = Icons.Filled.Casino,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_LEVEL,
                titleRes = R.string.tools_level_title,
                descRes = R.string.tools_level_desc,
                icon = Icons.Filled.ScreenRotation,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_CLOCK_OVERLAY,
                titleRes = R.string.tools_clock_overlay_title,
                descRes = R.string.tools_clock_overlay_desc,
                icon = Icons.Filled.Schedule,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_MOSAIC,
                titleRes = R.string.tools_mosaic_title,
                descRes = R.string.tools_mosaic_desc,
                icon = Icons.Filled.BlurOn,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_WATERMARK,
                titleRes = R.string.tools_watermark_title,
                descRes = R.string.tools_watermark_desc,
                icon = Icons.Filled.BlurOn,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_COMPASS,
                titleRes = R.string.tools_compass_title,
                descRes = R.string.tools_compass_desc,
                icon = Icons.Filled.Explore,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_BACKFILL,
                titleRes = R.string.tools_backfill_title,
                descRes = R.string.tools_backfill_desc,
                icon = Icons.Filled.EditCalendar,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_ARRIVAL,
                titleRes = R.string.tools_arrival_title,
                descRes = R.string.tools_arrival_desc,
                icon = Icons.Filled.LocationOn,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_BACKUP,
                titleRes = R.string.tools_backup_title,
                descRes = R.string.tools_backup_desc,
                icon = Icons.Filled.Backup,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_IMG_COMPRESS,
                titleRes = R.string.tools_img_compress_title,
                descRes = R.string.tools_img_compress_desc,
                icon = Icons.Filled.Compress,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_IMG_GRAY,
                titleRes = R.string.tools_img_gray_title,
                descRes = R.string.tools_img_gray_desc,
                icon = Icons.Filled.FilterBAndW,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_REFLECTOR,
                titleRes = R.string.tools_reflector_title,
                descRes = R.string.tools_reflector_desc,
                icon = Icons.Filled.WbIncandescent,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_DEADPIXEL,
                titleRes = R.string.deadpixel_title,
                descRes = R.string.deadpixel_desc,
                icon = Icons.Filled.Smartphone,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_MAGNET,
                titleRes = R.string.magnet_title,
                descRes = R.string.magnet_desc,
                icon = Icons.Filled.Tune,
            ),
        ),
    ),
    ToolCategory(
        titleRes = R.string.tools_cat_selfcheck,
        entries = listOf(
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_VISION,
                titleRes = R.string.tools_vision_title,
                descRes = R.string.tools_vision_desc,
                icon = Icons.Filled.RemoveRedEye,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_MOOD,
                titleRes = R.string.tools_mood_title,
                descRes = R.string.tools_mood_desc,
                icon = Icons.Filled.Mood,
            ),
        ),
    ),
    ToolCategory(
        titleRes = R.string.tools_cat_productivity,
        entries = listOf(
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_POMODORO,
                titleRes = R.string.tools_pomodoro_title,
                descRes = R.string.tools_pomodoro_desc,
                icon = Icons.Filled.Timer,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_CALC,
                titleRes = R.string.tools_calc_title,
                descRes = R.string.tools_calc_desc,
                icon = Icons.Filled.Calculate,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_COUNTDOWN,
                titleRes = R.string.tools_countdown_title,
                descRes = R.string.tools_countdown_desc,
                icon = Icons.Filled.HourglassBottom,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_LOAN,
                titleRes = R.string.tools_loan_title,
                descRes = R.string.tools_loan_desc,
                icon = Icons.Filled.AccountBalance,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_TAX,
                titleRes = R.string.tools_tax_title,
                descRes = R.string.tools_tax_desc,
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
            ),
        ),
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    onNavigate: (String) -> Unit,
    vm: ToolsViewModel = hiltViewModel(),
) {
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    // 用 route 索引所有分类里的 ToolEntry，常用工具按 route 解析展示
    val entriesByRoute: Map<String, ToolEntry> = remember(TOOL_CATEGORIES) {
        TOOL_CATEGORIES.flatMap { it.entries }.associateBy { it.route }
    }
    val pinnedEntries: List<ToolEntry> = remember(favorites, entriesByRoute) {
        favorites.mapNotNull { entriesByRoute[it] }
    }
    // favorites 是 List，56 张卡片各做一次 `in` 是 O(n²)；转 Set 后为 O(1)。
    val favoriteSet: Set<String> = remember(favorites) { favorites.toSet() }
    val pinnedRows: List<List<ToolEntry>> = remember(pinnedEntries) { pinnedEntries.chunked(3) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.tools_title)) })
        },
    ) { innerPadding ->
        // 全部 56 张工具卡此前放在 verticalScroll 里一次性全量组合，进入工具页时
        // 首帧要测量/布局 28 行卡片，低端机可见明显卡顿。改为 LazyColumn 只组合可见行。
        // 仍保持「chunked Row + weight」的两列布局（LazyVerticalGrid 与外层滚动嵌套曾导致闪退）。
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // 常用工具置顶：仅当列表非空时显示，水平 Row 紧凑卡片，可点 × 移除
            if (pinnedRows.isNotEmpty()) {
                item(key = "favorites_header") {
                    Text(
                        text = stringResource(R.string.tools_favorites_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.xs),
                    )
                }
                itemsIndexed(pinnedRows, key = { i, _ -> "fav_row_$i" }) { _, row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        row.forEach { entry ->
                            PinnedToolCard(
                                entry = entry,
                                onOpen = { onNavigate(entry.route) },
                                onUnpin = { vm.toggleFavorite(entry.route) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // 不足 3 个时用占位补齐，保持三列对齐
                        repeat(3 - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            TOOL_CATEGORIES.forEach { category ->
                item(key = "cat_${category.titleRes}") {
                    Text(
                        text = stringResource(category.titleRes),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xs),
                    )
                }
                val rows = category.entries.chunked(2)
                itemsIndexed(rows, key = { i, _ -> "cat_${category.titleRes}_row_$i" }) { _, row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        row.forEach { entry ->
                            ToolCard(
                                entry = entry,
                                isPinned = entry.route in favoriteSet,
                                onClick = { onNavigate(entry.route) },
                                onTogglePin = { vm.toggleFavorite(entry.route) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolCard(
    entry: ToolEntry,
    isPinned: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md)
                    .padding(end = 28.dp), // 给右上角星标留出空间
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Icon(
                    imageVector = entry.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(entry.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(entry.descRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 右上角星标：已置顶填充黄/主色，未置顶仅描边。点击切换置顶，不冒泡到卡片 onClick
            IconButton(
                onClick = onTogglePin,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(36.dp),
            ) {
                Icon(
                    imageVector = if (isPinned) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = stringResource(
                        if (isPinned) R.string.tools_unpin else R.string.tools_pin,
                    ),
                    tint = if (isPinned) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 置顶区紧凑卡片：图标（小染色圆圈）+ 标题（左对齐），删除按钮靠右垂直居中。
 * 图标与文字均调小以适配三列窄卡片；删除需二次确认，避免误触。
 */
@Composable
private fun PinnedToolCard(
    entry: ToolEntry,
    onOpen: () -> Unit,
    onUnpin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.clickable(onClick = onOpen),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 图标放进小染色圆圈，尺寸调小以适配三列窄卡片（一行最多5字）
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = entry.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(13.dp),
                )
            }
            Spacer(modifier = Modifier.width(Spacing.xs))
            Text(
                text = stringResource(entry.titleRes),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
            )
            // 弹性空白把删除按钮推到最右；文字与删除均不带 weight，永不被挤压/折行
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = { showConfirm = true },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.tools_unpin),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    onUnpin()
                }) { Text(stringResource(R.string.dialog_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
            title = { Text(stringResource(R.string.tools_unpin_title)) },
            text = {
                Text(stringResource(R.string.tools_unpin_msg, stringResource(entry.titleRes)))
            },
        )
    }
}
