package com.tickclear.app.ui.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
) {
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
                                    onClick = { onNavigate(entry.route) },
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
    onClick: () -> Unit,
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
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
    }
}
