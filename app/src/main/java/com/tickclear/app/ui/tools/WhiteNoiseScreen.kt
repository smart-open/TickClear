package com.tickclear.app.ui.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tickclear.app.R
import com.tickclear.app.domain.tools.NoiseSynth
import com.tickclear.app.ui.theme.Spacing
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 睡眠白噪音合集（雨声 / 咖啡馆 / 溪流）：本地程序化循环音效，无需联网。
 * 支持多音轨混音——每条音轨独立开关、独立音量，由系统混音器叠加（V2.9++）。
 * 离开页面自动停止全部轨道。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhiteNoiseScreen(onBack: () -> Unit) {
    val scenes = listOf(
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
    // 已加入混音的轨道：key -> 音量（0..1）；空 Map 表示全部停止。
    val mix = remember { mutableStateMapOf<String, Float>() }

    fun addLayer(key: String, volume: Float = 0.7f) {
        mix[key] = volume
        NoiseSynth.playLayer(key, volume)
    }

    fun removeLayer(key: String) {
        mix.remove(key)
        NoiseSynth.stopLayer(key)
    }

    fun stopAll() {
        NoiseSynth.stopAll()
        mix.clear()
    }

    DisposableEffect(Unit) {
        onDispose { NoiseSynth.stopAll() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_white_noise_title)) },
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
            SimHintCard(stringResource(R.string.tools_white_noise_hint))

            Text(
                stringResource(R.string.white_noise_mix_tip),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )

            scenes.forEach { (key, labelRes) ->
                val active = mix.containsKey(key)
                val vol = mix[key] ?: 0.7f
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (active) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                stringResource(labelRes),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Button(
                                onClick = {
                                    if (active) removeLayer(key) else addLayer(key)
                                },
                                modifier = Modifier.height(40.dp),
                            ) {
                                Icon(
                                    imageVector = if (active) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = Spacing.xs),
                                )
                                Text(
                                    if (active) {
                                        stringResource(R.string.white_noise_remove_track)
                                    } else {
                                        stringResource(R.string.white_noise_add_track)
                                    },
                                )
                            }
                        }

                        if (active) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            ) {
                                MiniWaveform(
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    stringResource(R.string.white_noise_playing),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            val pct = (vol * 100).roundToInt()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(stringResource(R.string.white_noise_volume))
                                Text(
                                    "$pct%",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                            Slider(
                                value = vol,
                                onValueChange = {
                                    mix[key] = it
                                    NoiseSynth.setLayerVolume(key, it)
                                },
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        }
                    }
                }
            }

            if (mix.isNotEmpty()) {
                Button(
                    onClick = { stopAll() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = null,
                        modifier = Modifier.padding(end = Spacing.xs),
                    )
                    Text(stringResource(R.string.white_noise_stop_all))
                }
            }
        }
    }
}

/** 播放时的动态声波：用无限过渡驱动的相位沿正弦路径绘制平滑波形，仅 playing 时运行。 */
@Composable
private fun MiniWaveform(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Restart),
        label = "wavePhase",
    )
    val amp by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "waveAmp",
    )
    Canvas(modifier = modifier.height(36.dp)) {
        val w = size.width
        val h = size.height
        val mid = h / 2f
        val a = (h / 2.4f) * amp
        val path = Path()
        val steps = 48
        for (i in 0..steps) {
            val x = w * i / steps
            val y = mid + a * sin(x / w * 6f * PI.toFloat() + phase)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}
