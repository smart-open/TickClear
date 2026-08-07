package com.tickclear.app.ui.tools

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tickclear.app.R
import com.tickclear.app.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import androidx.hilt.navigation.compose.hiltViewModel

private val NAP_OPTIONS = listOf(20, 30, 45, 60, 90)
private val NAP_SCENES = listOf(
    "rain" to R.string.white_noise_rain,
    "stream" to R.string.white_noise_stream,
    "cafe" to R.string.white_noise_cafe,
    "waves" to R.string.white_noise_waves,
    "wind" to R.string.white_noise_wind,
    "fire" to R.string.white_noise_fire,
    "white" to R.string.white_noise_white,
    "pink" to R.string.white_noise_pink,
    "fan" to R.string.white_noise_fan,
)
private val FADE_OPTIONS = listOf(0, 5, 10, 15)

/**
 * 午休小憩（V2.9++）：选择时长 → 一次性精确闹钟唤醒；可选白噪音助眠 + 渐隐。
 * 开始后有「小憩中」睡眠态（渐隐动画转入），显示倒计时环，到点闹钟唤醒并自动停音。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NapScreen(
    vm: NapViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val durationMin by vm.durationMin.collectAsStateWithLifecycle()
    val noiseEnabled by vm.noiseEnabled.collectAsStateWithLifecycle()
    val scene by vm.scene.collectAsStateWithLifecycle()
    val fadeMin by vm.fadeMin.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    val remainingSec by vm.remainingSec.collectAsStateWithLifecycle()
    val phase by vm.phase.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val wakeTime = remember(durationMin) {
        LocalTime.now().plusMinutes(durationMin.toLong()).format(DateTimeFormatter.ofPattern("HH:mm"))
    }
    val sceneLabel = when (scene) {
        "cafe" -> stringResource(R.string.white_noise_cafe)
        "stream" -> stringResource(R.string.white_noise_stream)
        else -> stringResource(R.string.white_noise_rain)
    }
    val phaseText: String? = when (phase) {
        NapPhase.PLAYING -> stringResource(R.string.nap_phase_playing, sceneLabel)
        NapPhase.FADING -> stringResource(R.string.nap_phase_fading)
        NapPhase.SILENT -> stringResource(R.string.nap_phase_silent)
        NapPhase.NONE -> null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_nap_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = !active,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Text(stringResource(R.string.nap_duration_label), style = MaterialTheme.typography.titleSmall)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        NAP_OPTIONS.forEach { min ->
                            FilterChip(
                                selected = durationMin == min,
                                onClick = { vm.setDuration(min) },
                                label = { Text(stringResource(R.string.nap_min_label, min)) },
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.nap_smart_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // 白噪音助眠开关
                    NapNoiseRow(
                        enabled = noiseEnabled,
                        onToggle = { vm.setNoiseEnabled(it) },
                    )
                    if (noiseEnabled) {
                        Text(stringResource(R.string.white_noise_scene), style = MaterialTheme.typography.titleSmall)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            NAP_SCENES.forEach { (key, labelRes) ->
                                FilterChip(
                                    selected = scene == key,
                                    onClick = { vm.setScene(key) },
                                    label = { Text(stringResource(labelRes)) },
                                )
                            }
                        }
                        Text(stringResource(R.string.nap_fade_label), style = MaterialTheme.typography.titleSmall)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            FADE_OPTIONS.forEach { min ->
                                FilterChip(
                                    selected = fadeMin == min,
                                    onClick = { vm.setFadeMin(min) },
                                    label = {
                                        Text(
                                            if (min <= 0) {
                                                stringResource(R.string.nap_fade_off)
                                            } else {
                                                stringResource(R.string.nap_fade_min, min)
                                            },
                                        )
                                    },
                                )
                            }
                        }
                        if (fadeMin > 0) {
                            Text(
                                text = stringResource(R.string.nap_fade_hint, fadeMin),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    val startToast = stringResource(R.string.nap_start_toast, wakeTime)
                    Button(
                        onClick = {
                            vm.start(context)
                            scope.launch { snackbarHostState.showSnackbar(startToast) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.nap_start))
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.nap_wake_at, wakeTime),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = active,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    Text(
                        stringResource(R.string.nap_sleeping),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Box(
                        modifier = Modifier.size(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        NapCountdownRing(
                            remainingSec = remainingSec,
                            totalSec = durationMin * 60,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Text(
                            text = fmtCountdown(remainingSec),
                            style = MaterialTheme.typography.displaySmall,
                            textAlign = TextAlign.Center,
                        )
                    }
                    if (phaseText != null) {
                        Text(
                            phaseText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (phase == NapPhase.SILENT) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            textAlign = TextAlign.Center,
                        )
                    }
                    Button(
                        onClick = { vm.cancel(context) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.nap_end))
                    }
                }
            }
        }
    }
}

@Composable
private fun NapNoiseRow(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(R.string.nap_noise_enable), style = MaterialTheme.typography.titleSmall)
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

/** 倒计时环：整圈底环 + 从 12 点顺时针扫过的进度弧，中心文字由调用方叠加。 */
@Composable
private fun NapCountdownRing(remainingSec: Int, totalSec: Int, modifier: Modifier = Modifier) {
    val frac = if (totalSec <= 0) 0f else (remainingSec.toFloat() / totalSec).coerceIn(0f, 1f)
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val progressColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val strokeW = 12.dp.toPx()
        val inset = strokeW / 2f
        val arc = size.width - strokeW
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = strokeW),
            topLeft = Offset(inset, inset),
            size = Size(arc, arc),
        )
        if (frac > 0f) {
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * frac,
                useCenter = false,
                style = Stroke(width = strokeW, cap = StrokeCap.Round),
                topLeft = Offset(inset, inset),
                size = Size(arc, arc),
            )
        }
    }
}

private fun fmtCountdown(sec: Int): String {
    val m = sec / 60
    val s = sec % 60
    return String.format(java.util.Locale.ROOT, "%02d:%02d", m, s)
}
