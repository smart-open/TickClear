package com.tickclear.app.ui.tools

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cake
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.tickclear.app.domain.tools.FoleySynth
import com.tickclear.app.ui.components.Haptic
import com.tickclear.app.ui.theme.Spacing
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.runtime.withFrameMillis

/**
 * 虚拟吹蜡烛（V2.9++ 模拟解压）。
 * 对着麦克风吹气（或长按屏幕）把蜡烛吹灭，带火焰摇曳与青烟动画。
 * 麦克风权限未授予时自动降级为「长按吹灭」。纯 Canvas + AudioTrack 合成，零新依赖。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun SimCandleScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val micPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    val outline = MaterialTheme.colorScheme.outline

    var lit by remember { mutableStateOf(true) }
    var blowProgress by remember { mutableStateOf(0f) }
    var flicker by remember { mutableStateOf(1f) }
    var particles by remember { mutableStateOf(emptyList<SimParticle>()) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    var blowing by remember { mutableStateOf(false) } // 长按（无麦克风时）吹气中

    LaunchedEffect(Unit) {
        if (micPermission.status !is PermissionStatus.Granted) micPermission.launchPermissionRequest()
    }

    DisposableEffect(Unit) {
        onDispose { FoleySynth.stop() }
    }

    fun extinguish() {
        if (!lit) return
        lit = false
        blowProgress = 0f
        // 一缕青烟从烛芯升起
        particles = particles + List(12) { i ->
            SimParticle(
                x = 0.5f + (kotlin.random.Random.nextFloat() - 0.5f) * 0.02f,
                y = 0.34f,
                vx = (kotlin.random.Random.nextFloat() - 0.5f) * 0.04f,
                vy = -0.12f - kotlin.random.Random.nextFloat() * 0.05f,
                life = 1.1f,
                maxLife = 1.1f,
                hue = -1f, // 负 hue 表示灰烟
                radius = 5f + i * 0.6f,
            )
        }
        FoleySynth.play("blow")
        Haptic.vibrate(context, 30)
    }

    // 麦克风吹气检测
    LaunchedEffect(micPermission.status is PermissionStatus.Granted) {
        if (micPermission.status !is PermissionStatus.Granted) return@LaunchedEffect
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
                    while (lit) {
                        val n = rec.read(buf, 0, buf.size)
                        if (n <= 0) break
                        var sum = 0.0
                        for (i in 0 until n) {
                            val v = buf[i] / 32768.0
                            sum += v * v
                        }
                        val rms = sqrt(sum / n).toFloat()
                        if (rms > 0.045f) {
                            blowProgress += 0.16f
                            if (blowProgress >= 1f) extinguish()
                        } else {
                            blowProgress = (blowProgress - 0.05f).coerceAtLeast(0f)
                        }
                        delay(60)
                    }
                } finally {
                    runCatching { rec.stop() }
                    rec.release()
                }
            }
        }
    }

    // 火焰摇曳 + 烟雾推进
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            val now = withFrameMillis { it }
            val dt = if (last == 0L) 0.016f else ((now - last) / 1000f).coerceAtMost(0.05f)
            last = now
            flicker = 0.86f + kotlin.random.Random.nextFloat() * 0.28f
            if (particles.isNotEmpty()) particles = stepParticles(particles, dt)
        }
    }

    // 长按吹气（麦克风未授权时的降级路径）推进循环
    LaunchedEffect(blowing) {
        if (!blowing) return@LaunchedEffect
        while (blowing && lit) {
            blowProgress += 0.05f
            if (blowProgress >= 1f) extinguish()
            delay(60)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_sim_candle_title)) },
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
                    !lit -> stringResource(R.string.tools_sim_candle_out)
                    micPermission.status is PermissionStatus.Granted -> stringResource(R.string.tools_sim_candle_hint)
                    else -> stringResource(R.string.tools_sim_candle_mic_denied)
                },
            )
            Spacer(Modifier.height(Spacing.md))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .height(320.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                if (!lit) return@detectTapGestures
                                if (micPermission.status is PermissionStatus.Granted) return@detectTapGestures
                                blowing = true
                                awaitRelease()
                                blowing = false
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    canvasSize = size
                    val w = size.width
                    val h = size.height
                    val candleW = w * 0.16f
                    val candleH = h * 0.42f
                    val candleX = (w - candleW) / 2f
                    val candleTop = h * 0.40f
                    val candleBottom = candleTop + candleH
                    val cx = w / 2f

                    drawRoundRect(
                        color = Color(0xFFEAD9B0),
                        topLeft = Offset(candleX, candleTop),
                        size = Size(candleW, candleH),
                    )
                    drawRoundRect(
                        color = outline,
                        topLeft = Offset(candleX - candleW * 0.18f, candleBottom),
                        size = Size(candleW * 1.36f, candleH * 0.06f),
                    )
                    drawLine(
                        color = Color(0xFF3A2A1A),
                        start = Offset(cx, candleTop),
                        end = Offset(cx, candleTop - candleH * 0.04f),
                        strokeWidth = 3f,
                    )

                    if (lit) {
                        val flameH = candleH * 0.20f * flicker * (1 - blowProgress)
                        val flameW = candleW * 0.30f * flicker
                        val flameCy = candleTop - candleH * 0.04f - flameH / 2f
                        drawOval(
                            color = Color(0xFFFF6A00),
                            topLeft = Offset(cx - flameW, flameCy - flameH / 2),
                            size = Size(flameW * 2, flameH),
                        )
                        drawOval(
                            color = Color(0xFFFFC107),
                            topLeft = Offset(cx - flameW * 0.62f, flameCy - flameH * 0.30f),
                            size = Size(flameW * 1.24f, flameH * 0.7f),
                        )
                        drawOval(
                            color = Color(0xFFFFFFFF),
                            topLeft = Offset(cx - flameW * 0.30f, flameCy - flameH * 0.10f),
                            size = Size(flameW * 0.6f, flameH * 0.4f),
                        )
                    }

                    for (pt in particles) {
                        val a = (pt.life / pt.maxLife).coerceIn(0f, 1f)
                        val color = if (pt.hue < 0f) Color.Gray.copy(alpha = a * 0.5f) else simColor(pt.hue, a)
                        drawOval(
                            color = color,
                            topLeft = Offset(pt.x * w - pt.radius, pt.y * h - pt.radius),
                            size = Size(pt.radius * 2, pt.radius * 2),
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.md))
            if (!lit) {
                Button(onClick = { lit = true; particles = emptyList() }) {
                    Icon(Icons.Filled.Cake, contentDescription = null)
                    Spacer(Modifier.width(Spacing.xs))
                    Text(stringResource(R.string.tools_sim_candle_relight))
                }
            } else if (micPermission.status is PermissionStatus.Granted) {
                Text(stringResource(R.string.tools_sim_candle_blow), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Button(onClick = { blowProgress += 0.05f; if (blowProgress >= 1f) extinguish() }) {
                    Text(stringResource(R.string.tools_sim_candle_out))
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
