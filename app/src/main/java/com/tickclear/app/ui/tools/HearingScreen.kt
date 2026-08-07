package com.tickclear.app.ui.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
                Text(stringResource(R.string.hearing_volume_label), style = MaterialTheme.typography.titleSmall)
                Text(
                    text = stringResource(R.string.hearing_volume_current, volume),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = volume.toFloat(),
                    onValueChange = { vm.setVolumeThreshold(it.toInt()) },
                    valueRange = 0f..100f,
                    steps = 0,
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
 * 听力保护音量安全表盘：270° 弧形，三段分区（安全 / 警戒 / 危险），指针指向当前阈值。
 * 纯 Canvas 静态绘制（仅在阈值变化时重绘），无需常驻帧循环。
 */
@Composable
private fun DbGauge(value: Int, modifier: Modifier = Modifier) {
    val safeColor = Color(0xFF43A047)
    val cautionColor = Color(0xFFF9A825)
    val dangerColor = Color(0xFFE53935)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val needleColor = MaterialTheme.colorScheme.onSurface

    val startAngle = 135f
    val sweep = 270f
    val v = value.coerceIn(0, 100)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(180.dp),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
            val cx = size.width / 2f
            val cy = size.height / 2f

            drawArc(trackColor, startAngle, sweep, false, style = stroke)
            val safeEnd = 60f / 100f * sweep
            val cautionEnd = 85f / 100f * sweep
            drawArc(safeColor, startAngle, safeEnd, false, style = stroke)
            drawArc(cautionColor, startAngle + safeEnd, cautionEnd - safeEnd, false, style = stroke)
            drawArc(dangerColor, startAngle + cautionEnd, sweep - cautionEnd, false, style = stroke)

            val ang = Math.toRadians((startAngle + v / 100f * sweep).toDouble())
            val rOuter = size.minDimension / 2f - stroke.width / 2f - 6.dp.toPx()
            val rInner = size.minDimension / 2f * 0.30f
            val nx = cx + (rOuter * cos(ang)).toFloat()
            val ny = cy + (rOuter * sin(ang)).toFloat()
            val ix = cx + (rInner * cos(ang)).toFloat()
            val iy = cy + (rInner * sin(ang)).toFloat()
            drawLine(
                color = needleColor,
                start = Offset(ix, iy),
                end = Offset(nx, ny),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawCircle(color = needleColor, radius = 4.dp.toPx(), center = Offset(cx, cy))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$value",
                style = MaterialTheme.typography.headlineMedium,
                color = needleColor,
            )
            Text(
                text = "dB",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
