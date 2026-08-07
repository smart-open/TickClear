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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlin.math.sin
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
    var blowProgress by remember { mutableFloatStateOf(0f) }
    var flicker by remember { mutableFloatStateOf(1f) }
    var flameLean by remember { mutableFloatStateOf(0f) }
    var particles by remember { mutableStateOf(emptyList<SimParticle>()) }
    var blowing by remember { mutableStateOf(false) } // 长按（无麦克风时）吹气中
    // 无障碍：系统关闭动画时冻结火焰摇曳，只保留必要状态反馈
    val motionReduced = remember(context) { isMotionReduced(context) }

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

    // 麦克风吹气检测。
    // key 必须包含 lit：熄灭时内层 while(lit) 结束并释放 AudioRecord，
    // 若不以 lit 为 key，重新点燃后录音不会重启 → 复燃后永远吹不灭（已修复的功能缺陷）。
    LaunchedEffect(micPermission.status is PermissionStatus.Granted, lit) {
        if (!lit) return@LaunchedEffect
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

    // 火焰摇曳 + 烟雾推进。
    // 仅在「燃烧中 或 仍有余烟」时驱动帧循环：熄灭且烟散尽后自动挂起，避免常驻空转耗电。
    val animating = lit || particles.isNotEmpty()
    LaunchedEffect(animating, motionReduced) {
        if (!animating) return@LaunchedEffect
        var last = 0L
        var t = 0f
        while (true) {
            val now = withFrameMillis { it }
            val dt = if (last == 0L) 0.016f else ((now - last) / 1000f).coerceAtMost(0.05f)
            last = now
            t += dt
            if (lit) {
                if (motionReduced) {
                    flicker = 1f
                    flameLean = 0f
                } else {
                    // 双频正弦叠加的平滑摇曳：逐帧纯随机会抖成噪点，这样才像真火
                    flicker = 0.92f + sin(t * 9f) * 0.06f + sin(t * 23f) * 0.045f
                    flameLean = sin(t * 5.5f) * 0.30f + blowProgress * 2.0f
                }
            }
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
                    val w = size.width
                    val h = size.height
                    val candleW = w * 0.17f
                    val candleH = h * 0.42f
                    val candleX = (w - candleW) / 2f
                    val candleTop = h * 0.40f
                    val candleBottom = candleTop + candleH
                    val cx = w / 2f
                    val wax = Color(0xFFF2E2B8)

                    // 桌面接触投影（点燃时光更强，影子更实）
                    drawSoftShadow(
                        center = Offset(cx, candleBottom + candleH * 0.055f),
                        radiusX = candleW * 1.55f,
                        radiusY = candleH * 0.075f,
                        maxAlpha = if (lit) 0.32f else 0.22f,
                    )
                    // 烛台底盘
                    fillOvoid(
                        topLeft = Offset(cx - candleW * 0.95f, candleBottom - candleH * 0.03f),
                        size = Size(candleW * 1.9f, candleH * 0.11f),
                        base = outline,
                    )
                    // 二巡：底盘边缘辉光
                    drawOval(
                        color = outline.lighten(0.30f).copy(alpha = 0.22f),
                        topLeft = Offset(cx - candleW * 0.95f, candleBottom - candleH * 0.03f),
                        size = Size(candleW * 1.9f, candleH * 0.11f),
                        style = Stroke(width = candleW * 0.03f),
                    )
                    // 蜡体：圆柱受光（横向渐变比平涂立体得多）
                    fillCylinder(
                        topLeft = Offset(candleX, candleTop),
                        size = Size(candleW, candleH),
                        base = wax,
                        cornerRadius = candleW * 0.08f,
                    )
                    // 两道凝固的蜡泪
                    fillRoundRect3D(
                        topLeft = Offset(candleX + candleW * 0.10f, candleTop + candleH * 0.04f),
                        size = Size(candleW * 0.17f, candleH * 0.30f),
                        cornerRadius = candleW * 0.09f,
                        base = wax.lighten(0.22f),
                    )
                    fillRoundRect3D(
                        topLeft = Offset(candleX + candleW * 0.68f, candleTop + candleH * 0.02f),
                        size = Size(candleW * 0.14f, candleH * 0.18f),
                        cornerRadius = candleW * 0.07f,
                        base = wax.lighten(0.16f),
                    )
                    // 顶部融蜡口：亮边 + 内凹暗池
                    fillOvoid(
                        topLeft = Offset(candleX, candleTop - candleW * 0.13f),
                        size = Size(candleW, candleW * 0.26f),
                        base = wax.lighten(0.30f),
                    )
                    drawOval(
                        color = wax.darken(0.26f),
                        topLeft = Offset(candleX + candleW * 0.16f, candleTop - candleW * 0.085f),
                        size = Size(candleW * 0.68f, candleW * 0.17f),
                    )
                    // 烛芯
                    val wickTop = candleTop - candleW * 0.06f - candleH * 0.045f
                    drawLine(
                        color = Color(0xFF3A2A1A),
                        start = Offset(cx, candleTop - candleW * 0.04f),
                        end = Offset(cx, wickTop),
                        strokeWidth = candleW * 0.05f,
                    )

                    if (lit) {
                        val flameHalfW = candleW * 0.30f * flicker
                        drawFlame(
                            baseX = cx,
                            baseY = wickTop + candleH * 0.012f,
                            halfWidth = flameHalfW,
                            height = candleH * 0.26f * flicker * (1f - blowProgress * 0.55f),
                            leanX = flameLean * flameHalfW,
                            alpha = 1f - blowProgress * 0.25f,
                        )
                    }

                    for (pt in particles) {
                        val a = (pt.life / pt.maxLife).coerceIn(0f, 1f)
                        if (pt.hue < 0f) {
                            // 灰烟：越飘越大越淡，用径向渐变做出蓬松感
                            val r = pt.radius * (1f + (1f - a) * 1.6f)
                            drawOval(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF9E9E9E).copy(alpha = a * 0.42f),
                                        Color(0xFF9E9E9E).copy(alpha = 0f),
                                    ),
                                    center = Offset(pt.x * w, pt.y * h),
                                    radius = r,
                                ),
                                topLeft = Offset(pt.x * w - r, pt.y * h - r),
                                size = Size(r * 2, r * 2),
                            )
                        } else {
                            drawOval(
                                color = simColor(pt.hue, a),
                                topLeft = Offset(pt.x * w - pt.radius, pt.y * h - pt.radius),
                                size = Size(pt.radius * 2, pt.radius * 2),
                            )
                        }
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
