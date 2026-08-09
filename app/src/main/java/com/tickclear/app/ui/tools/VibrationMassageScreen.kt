package com.tickclear.app.ui.tools

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tickclear.app.R
import com.tickclear.app.domain.tools.MassageVibrator
import com.tickclear.app.ui.theme.Spacing

/**
 * 振动按摩（休闲解压）：选择模式后调用系统振动循环播放，离开页面自动停止。
 * 纯本地、零新依赖；振动仅作体感反馈，不涉及任何健康疗效宣称。
 *
 * V2.9++ Bug 排查补丁：
 *  - 进入页面立刻做一次 25ms 强触感诊断（testPulse），让用户明确感知到硬件通断；
 *  - 行内展示硬件信息（API 级别 / 是否有振动器），便于「没震动」类问题定位；
 *  - 顶部提示说明已补充权限开启简要说明。
 *
 * 本轮优化：
 *  - 去掉「试一下」次级按钮（诊断仅在进入页面做一次）；
 *  - 模式扩到 10 个，且支持多选组合（FlowRow 自动换行排布）；
 *  - 放大震动：抬高振幅下限 + 提高占空比 + 无振幅控制设备用拉长 ON 段补偿。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VibrationMassageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val modes = listOf(
        "gentle" to R.string.vibe_mode_gentle,
        "strong" to R.string.vibe_mode_strong,
        "wave" to R.string.vibe_mode_wave,
        "rhythm" to R.string.vibe_mode_rhythm,
        "pulse" to R.string.vibe_mode_pulse,
        "knead" to R.string.vibe_mode_knead,
        "tap" to R.string.vibe_mode_tap,
        "roll" to R.string.vibe_mode_roll,
        "shock" to R.string.vibe_mode_shock,
        "heart" to R.string.vibe_mode_heart,
    )
    // 多选组合：所选模式的波形首尾相接拼成一个长循环
    var selectedModes by remember { mutableStateOf(setOf("gentle")) }
    var running by remember { mutableStateOf(false) }
    // 一次性诊断：进入页面立刻给一下 + 取得状态字符串。
    var diagnose by remember { mutableStateOf<String?>(null) }

    fun toggle() {
        if (running) {
            MassageVibrator.stop(context)
            running = false
        } else {
            if (selectedModes.isEmpty()) return
            MassageVibrator.start(context, selectedModes)
            running = true
        }
    }

    // 切换模式时若正在运行，立即以新模式组合重启
    LaunchedEffect(selectedModes) {
        if (running) {
            if (selectedModes.isEmpty()) {
                MassageVibrator.stop(context)
                running = false
            } else {
                MassageVibrator.start(context, selectedModes)
            }
        }
    }
    // 进入页面一次性诊断：硬件空闲时弹一下，便于用户感知电机是否可用。
    LaunchedEffect(Unit) {
        diagnose = MassageVibrator.describe(context)
        val felt = MassageVibrator.testPulse(context)
        if (!felt) {
            Toast.makeText(context, R.string.vibe_no_motor, Toast.LENGTH_LONG).show()
        }
    }
    // 离开页面保险：停止振动
    DisposableEffect(Unit) {
        onDispose { MassageVibrator.stop(context) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_vibe_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SimHintCard(stringResource(R.string.tools_vibe_hint))

            // 诊断信息行：把硬件状态直接显示给用户，「没震动」类问题不再静默。
            if (diagnose != null) {
                Text(
                    text = diagnose!!,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                stringResource(R.string.vibe_mode_label),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )
            // 模式标签并排成行，排满自动换行；支持多选组合。
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                modes.forEach { (key, labelRes) ->
                    FilterChip(
                        selected = key in selectedModes,
                        onClick = {
                            selectedModes = if (key in selectedModes) {
                                selectedModes - key
                            } else {
                                selectedModes + key
                            }
                        },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }

            // 运行时的震动波扩散环（V2.9++）：从中心向外连续扩散 3 圈，
            // 中央脉动点保留作为触觉节拍指示（V2.9++ 二巡升级）。
            if (running) {
                val infinite = rememberInfiniteTransition(label = "vibePulse")
                val pulse by infinite.animateFloat(
                    initialValue = 0.35f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
                    label = "vibePulseAlpha",
                )
                val wave by infinite.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Restart),
                    label = "vibeWave",
                )
                val ringColor = MaterialTheme.colorScheme.primary
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Box(
                        modifier = Modifier.size(160.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val maxR = size.minDimension / 2f * 0.95f
                            for (i in 0..2) {
                                var f = wave + i / 3f
                                if (f > 1f) f -= 1f
                                val r = maxR * f
                                val alpha = (1f - f) * 0.65f
                                drawCircle(
                                    color = ringColor.copy(alpha = alpha),
                                    radius = r,
                                    center = Offset(cx, cy),
                                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(ringColor.copy(alpha = pulse)),
                        )
                    }
                    Text(
                        stringResource(R.string.vibe_running),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.sm))
            Button(
                onClick = ::toggle,
                enabled = selectedModes.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) {
                Icon(
                    imageVector = if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.padding(end = Spacing.xs),
                )
                Text(if (running) stringResource(R.string.vibe_stop) else stringResource(R.string.vibe_start))
            }
        }
    }
}
