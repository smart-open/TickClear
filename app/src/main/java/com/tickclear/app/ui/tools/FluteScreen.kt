package com.tickclear.app.ui.tools

import android.Manifest
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.tickclear.app.R
import com.tickclear.app.domain.tools.FluteSynth
import com.tickclear.app.ui.theme.Spacing
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 吹笛子（V2.9++ 模拟解压）。
 *
 * 采集 → 分析 → 合成：用平台原生 [AudioRecord] 实时采集麦克风气流声，对时域样本做 RMS
 * 能量分析并阈值判定气息强度，把强度作为控制信号驱动 [FluteSynth] 生成对应音高的笛声。
 * 音高由用户点选（C 大调 8 音），吹气强度控制吹响与音量。未授权麦克风时降级为「按住吹奏」。
 * 纯本地、零外部 API/模型、零新依赖。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun FluteScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val micPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    // C 大调 8 音：do re mi fa sol la ti do'
    val noteFreqs = floatArrayOf(
        523.25f, 587.33f, 659.25f, 698.46f, 783.99f, 880.00f, 987.77f, 1046.50f,
    )
    val solfege = stringResource(R.string.sim_flute_solfege).split('|')
    var selected by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) } // 麦克风吹奏会话进行中
    var breath by remember { mutableFloatStateOf(0f) } // 当前气息强度 0..1（仪表用）

    val micGranted = micPermission.status is PermissionStatus.Granted

    LaunchedEffect(Unit) {
        if (!micGranted) micPermission.launchPermissionRequest()
    }

    // 麦克风吹奏：开启后拉起 AudioRecord 循环，RMS 能量→气息强度→FluteSynth。
    // playing 翻 false 时 LaunchedEffect 被取消，finally 保证释放音轨与录音机。
    LaunchedEffect(playing) {
        if (!playing || !micGranted) return@LaunchedEffect
        FluteSynth.start()
        FluteSynth.setNote(noteFreqs[selected])
        breath = 0f
        try {
            withContext(Dispatchers.Default) {
                runCatching {
                    val rec = createMicRecorder()
                    if (rec.state != AudioRecord.STATE_INITIALIZED) {
                        rec.release()
                        return@withContext
                    }
                    rec.startRecording()
                    val buf = ShortArray(1024)
                    try {
                        while (playing) {
                            val n = rec.read(buf, 0, buf.size)
                            if (n <= 0) break
                            var sum = 0.0
                            for (i in 0 until n) {
                                val v = buf[i] / 32768.0
                                sum += v * v
                            }
                            val rms = sqrt(sum / n).toFloat()
                            // 阈值判定：超过阈值才吹响，气息越足音量越大。
                            val level = if (rms > 0.04f) {
                                ((rms - 0.04f) / 0.22f).coerceIn(0.18f, 1f)
                            } else {
                                0f
                            }
                            FluteSynth.setNote(noteFreqs[selected])
                            FluteSynth.setIntensity(level)
                            breath = level
                            delay(40)
                        }
                    } finally {
                        runCatching { rec.stop() }
                        rec.release()
                    }
                }
            }
        } finally {
            FluteSynth.setIntensity(0f)
            FluteSynth.stop()
            breath = 0f
        }
    }

    DisposableEffect(Unit) {
        onDispose { FluteSynth.stop() }
    }

    fun pickNote(index: Int) {
        selected = index
        FluteSynth.setNote(noteFreqs[index])
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sim_flute_title)) },
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
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SimHintCard(
                when {
                    !micGranted -> stringResource(R.string.sim_flute_mic_denied)
                    else -> stringResource(R.string.sim_flute_hint)
                },
            )
            Spacer(Modifier.height(Spacing.sm))
            SimStatCard(
                value = solfege.getOrElse(selected) { "" },
                label = stringResource(R.string.sim_flute_pick_note),
            )
            Spacer(Modifier.height(Spacing.md))

            // 简易长笛示意（横管 + 音孔），纯视觉点缀
            // 主题色须在组合作用域取值后传入 Canvas（DrawScope 内不可调用 @Composable getter）。
            val fluteColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val cy = h / 2f
                    val fluteH = h * 0.22f
                    val left = w * 0.08f
                    val right = w * 0.92f
                    drawRoundRect(
                        color = fluteColor,
                        topLeft = Offset(left, cy - fluteH / 2f),
                        size = Size(right - left, fluteH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(fluteH / 2f),
                    )
                    // 音孔
                    val holeCount = 6
                    for (k in 0 until holeCount) {
                        val x = left + (right - left) * (0.30f + 0.10f * k)
                        drawCircle(
                            color = Color(0xFF1A1A1A),
                            radius = fluteH * 0.28f,
                            center = Offset(x, cy),
                        )
                    }
                }
            }
            Spacer(Modifier.height(Spacing.md))

            // 音高选择：C 大调 8 音
            Text(
                text = stringResource(R.string.sim_flute_pick_note),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                solfege.forEachIndexed { index, label ->
                    val isSel = selected == index
                    Button(
                        onClick = { pickNote(index) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = label,
                            color = if (isSel) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            Spacer(Modifier.height(Spacing.md))

            // 气息强度仪表
            Text(
                text = stringResource(R.string.sim_flute_breath),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.xs))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(breath.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(9.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
            Spacer(Modifier.height(Spacing.md))

            // 主控制：有麦克风权限→开始/停止会话；无→按住吹奏（降级）
            if (micGranted) {
                Button(
                    onClick = {
                        if (playing) {
                            playing = false
                        } else {
                            playing = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.GraphicEq, contentDescription = null)
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        if (playing) stringResource(R.string.sim_flute_stop)
                        else stringResource(R.string.sim_flute_start),
                    )
                }
            } else {
                // 降级路径：按住即吹响当前选中音，松开即停（无需麦克风）。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    FluteSynth.start()
                                    FluteSynth.setNote(noteFreqs[selected])
                                    FluteSynth.setIntensity(0.85f)
                                    breath = 0.85f
                                    awaitRelease()
                                    FluteSynth.setIntensity(0f)
                                    breath = 0f
                                    FluteSynth.stop()
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.sim_flute_blow),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.md))
        }
    }
}

@SuppressLint("MissingPermission")
private fun createMicRecorder(): AudioRecord {
    val sr = 44100
    val minBuf = AudioRecord.getMinBufferSize(
        sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
    )
    return AudioRecord(
        MediaRecorder.AudioSource.MIC, sr,
        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        maxOf(minBuf, 2048),
    )
}
