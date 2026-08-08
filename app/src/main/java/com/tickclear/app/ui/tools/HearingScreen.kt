package com.tickclear.app.ui.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tickclear.app.R
import com.tickclear.app.ui.theme.Spacing
import kotlin.math.cos
import kotlin.math.sin

private val WEAR_OPTIONS = listOf(15, 30, 45, 60, 90, 120)

/** 仪表盘直径与轨道外边距，二者共同决定「dB 数字」下沉到中心与底部垂直中点的偏移量。 */
private val GAUGE_DIAMETER = 220.dp
private val GAUGE_TRACK_MARGIN = 22.dp
/** 圆心到轨道半径 = 直径/2 - 外边距；数字下沉量取该半径的一半，即圆心与圆周底部的垂直中点。 */
private val GAUGE_NUMBER_OFFSET = (GAUGE_DIAMETER / 2 - GAUGE_TRACK_MARGIN) / 2

/**
 * 听力保护（V2.9++）：设置总开关、音量安全阈值、建议最大佩戴时长。
 * 实际监测在 [com.tickclear.app.domain.hearing.HearingMonitor]（App 运行时生效）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HearingScreen(
    vm: HearingViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val enabled by vm.enabled.collectAsStateWithLifecycle()
    val volume by vm.volumeThreshold.collectAsStateWithLifecycle()
    val wear by vm.maxWearMin.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_hearing_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.hearing_enable_label), style = MaterialTheme.typography.titleSmall)
                Switch(checked = enabled, onCheckedChange = { vm.setEnabled(it) })
            }

            Text(
                text = stringResource(R.string.hearing_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (enabled) {
                DbGauge(
                    value = volume,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.CenterHorizontally),
                )
                Text(
                    text = stringResource(R.string.hearing_volume_hint, volume),
                    style = MaterialTheme.typography.titleSmall,
                )
                VolumeThresholdSlider(
                    value = volume,
                    onValueChange = { vm.setVolumeThreshold(it) },
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(stringResource(R.string.hearing_wear_label), style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    WEAR_OPTIONS.forEach { min ->
                        FilterChip(
                            selected = wear == min,
                            onClick = { vm.setMaxWearMin(min) },
                            label = { Text(stringResource(R.string.hearing_wear_min, min)) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 听力保护音量安全仪表盘：270° 弧形表盘，三段分区彩弧（安全 / 警戒 / 危险），
 * 外圈刻度、指针与中心轴，下方附分区图例。纯 Canvas 静态绘制（阈值变化时重绘）。
 */
@Composable
private fun DbGauge(value: Int, modifier: Modifier = Modifier) {
    val safeColor = Color(0xFF43A047)
    val cautionColor = Color(0xFFF9A825)
    val dangerColor = Color(0xFFE53935)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant
    val needleColor = MaterialTheme.colorScheme.onSurface
    val surfaceColor = MaterialTheme.colorScheme.surface

    val startAngle = 135f
    val sweep = 270f
    val v = value.coerceIn(0, 100)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(GAUGE_DIAMETER),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val rTrack = size.minDimension / 2f - GAUGE_TRACK_MARGIN.toPx()
                val trackW = 18.dp.toPx()
                val zoneW = 10.dp.toPx()

                // 底环
                drawArc(
                    color = trackColor,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = trackW, cap = StrokeCap.Round),
                )

                // 分区彩弧（平头衔接，避免圆头叠盖）
                val safeEnd = 60f / 100f * sweep
                val cautionEnd = 85f / 100f * sweep
                val zoneStroke = Stroke(width = zoneW, cap = StrokeCap.Butt)
                drawArc(safeColor, startAngle, safeEnd, false, style = zoneStroke)
                drawArc(cautionColor, startAngle + safeEnd, cautionEnd - safeEnd, false, style = zoneStroke)
                drawArc(dangerColor, startAngle + cautionEnd, sweep - cautionEnd, false, style = zoneStroke)

                // 外圈刻度：每 10 一段主刻度，其余次刻度
                val rTickIn = rTrack + trackW / 2f + 2.dp.toPx()
                val rTickOut = rTickIn + 10.dp.toPx()
                for (i in 0..10) {
                    val a = Math.toRadians((startAngle + i / 10f * sweep).toDouble())
                    val ca = cos(a).toFloat()
                    val sa = sin(a).toFloat()
                    val isMajor = i % 5 == 0
                    val col = when {
                        i <= 6 -> safeColor
                        i <= 8 -> cautionColor
                        else -> dangerColor
                    }
                    val inner = if (isMajor) rTickIn else rTickIn + 4.dp.toPx()
                    drawLine(
                        color = if (isMajor) col else tickColor,
                        start = Offset(cx + ca * inner, cy + sa * inner),
                        end = Offset(cx + ca * rTickOut, cy + sa * rTickOut),
                        strokeWidth = (if (isMajor) 2.5f else 1.5f).dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }

                // 指针：从中心轴指向当前阈值，后端带配重
                val ang = Math.toRadians((startAngle + v / 100f * sweep).toDouble())
                val ca = cos(ang).toFloat()
                val sa = sin(ang).toFloat()
                val rNeedle = rTrack - 16.dp.toPx()
                val rTail = 16.dp.toPx()
                drawLine(
                    color = needleColor,
                    start = Offset(cx - ca * rTail, cy - sa * rTail),
                    end = Offset(cx + ca * rNeedle, cy + sa * rNeedle),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawCircle(color = needleColor, radius = 7.dp.toPx(), center = Offset(cx, cy))
                drawCircle(color = surfaceColor, radius = 3.dp.toPx(), center = Offset(cx, cy))
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.offset(y = GAUGE_NUMBER_OFFSET),
            ) {
                Text(
                    text = "dB",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "$value",
                    style = MaterialTheme.typography.headlineLarge,
                    color = needleColor,
                )
            }
        }

        Spacer(Modifier.height(Spacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ZoneChip(safeColor, stringResource(R.string.hearing_zone_safe))
            ZoneChip(cautionColor, stringResource(R.string.hearing_zone_caution))
            ZoneChip(dangerColor, stringResource(R.string.hearing_zone_danger))
        }
    }
}

/**
 * 音量安全阈值调节器（美化版）：卡片式外壳 + 动态分区配色滑块（安全绿 → 警戒黄 → 危险红，
 * 与仪表盘分区一致）+ 0 / 50 / 100 刻度标签。
 */
@Composable
private fun VolumeThresholdSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val safeColor = Color(0xFF43A047)
    val cautionColor = Color(0xFFF9A825)
    val dangerColor = Color(0xFFE53935)
    val zone = when {
        value <= 60 -> safeColor
        value <= 85 -> cautionColor
        else -> dangerColor
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.toInt()) },
                valueRange = 0f..100f,
                steps = 0,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = zone,
                    activeTrackColor = zone,
                    inactiveTrackColor = zone.copy(alpha = 0.25f),
                    activeTickColor = MaterialTheme.colorScheme.surface,
                    inactiveTickColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                ),
            )
            // 0-50-100 刻度标签，与滑块行程对齐（留出滑块半径余量）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ScaleLabel("0")
                ScaleLabel("50")
                ScaleLabel("100")
            }
        }
    }
}

@Composable
private fun ScaleLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ZoneChip(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
