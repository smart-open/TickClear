package com.tickclear.app.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Security
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
                icon = Icons.Filled.CameraAlt,
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
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_SCALE,
                titleRes = R.string.tools_scale_title,
                descRes = R.string.tools_scale_desc,
                icon = Icons.Filled.Scale,
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

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.tools_title)) })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        ) {
            // 常用工具置顶：仅当列表非空时显示，水平 Row 紧凑卡片，可点 × 移除
            if (pinnedEntries.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.tools_favorites_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.xs),
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    pinnedEntries.chunked(3).forEach { row ->
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
            }

            TOOL_CATEGORIES.forEach { category ->
                Text(
                    text = stringResource(category.titleRes),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.xs),
                )
                // V2.8X 修复：LazyVerticalGrid 放在 verticalScroll 的 Column 内会在测量时
                // 因无限高度约束抛 IllegalStateException 闪退。改用非 Lazy 的 chunked Row。
                // 同行内横向 spacedBy(Spacing.sm)；多行之间用内层 Column 纵向 spacedBy 避免上下贴边。
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    category.entries.chunked(2).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            row.forEach { entry ->
                                ToolCard(
                                    entry = entry,
                                    isPinned = entry.route in favorites,
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
