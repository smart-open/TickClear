package com.tickclear.app.ui.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tickclear.app.R
import com.tickclear.app.domain.tools.FoleySynth
import com.tickclear.app.ui.components.Haptic
import com.tickclear.app.ui.theme.Spacing
import androidx.compose.runtime.withFrameMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

private val FIREWORK_HUES = listOf(0f, 35f, 130f, 190f, 260f, 310f, 340f)

/**
 * 模拟烟花（V2.9++ 模拟解压，V2.9++ 二巡升级）。
 * - 点击屏幕任意位置即在该处放一发烟花；
 * - 双击触发「连发」：在 ~0.5s 内连射 6 发（位置带轻微抖动），配合音效与震动；
 * - 粒子采用径向光晕 + 速度方向拖尾，自然呈现「拖尾渐隐」。
 * 纯 Canvas + AudioTrack 合成，零新依赖。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimFireworksScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var particles by remember { mutableStateOf(emptyList<SimParticle>()) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    DisposableEffect(Unit) {
        onDispose { FoleySynth.stop() }
    }

    /** 在归一化坐标 (nx, ny) 处放一发烟花（粒子 + 音效 + 触感）。 */
    fun burstAt(nx: Float, ny: Float) {
        particles = particles + burst(nx, ny, 40, 0.7f, 1.3f, FIREWORK_HUES, 5f)
        FoleySynth.play("firework")
        Haptic.vibrate(context, 25)
    }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            val now = withFrameMillis { it }
            val dt = if (last == 0L) 0.016f else ((now - last) / 1000f).coerceAtMost(0.05f)
            last = now
            if (particles.isNotEmpty()) particles = stepParticles(particles, dt)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_sim_fireworks_title)) },
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
            SimHintCard(stringResource(R.string.tools_sim_fireworks_hint))
            Spacer(Modifier.height(Spacing.md))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .height(420.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { offset ->
                                val nx = if (canvasSize.width > 0) offset.x / canvasSize.width else 0.5f
                                val ny = if (canvasSize.height > 0) offset.y / canvasSize.height else 0.5f
                                burstAt(nx, ny)
                            },
                            onDoubleTap = { offset ->
                                val nx = if (canvasSize.width > 0) offset.x / canvasSize.width else 0.5f
                                val ny = if (canvasSize.height > 0) offset.y / canvasSize.height else 0.5f
                                scope.launch {
                                    repeat(6) { i ->
                                        val dx = (Random.nextFloat() - 0.5f) * 0.06f
                                        val dy = (Random.nextFloat() - 0.5f) * 0.06f
                                        burstAt((nx + dx).coerceIn(0f, 1f), (ny + dy).coerceIn(0f, 1f))
                                        delay(85)
                                    }
                                }
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    canvasSize = size
                    val w = size.width
                    val h = size.height
                    for (pt in particles) {
                        val a = (pt.life / pt.maxLife).coerceIn(0f, 1f)
                        val cx = pt.x * w
                        val cy = pt.y * h

                        // 拖尾：沿速度反方向画一道渐隐短线，速度越快拖尾越长（V2.9++ 二巡）。
                        val speedPx = kotlin.math.hypot(pt.vx * w, pt.vy * h)
                        if (speedPx > 6f && a > 0.05f) {
                            val tail = 0.04f
                            val tx = cx - pt.vx * w * tail
                            val ty = cy - pt.vy * h * tail
                            drawLine(
                                color = simColor(pt.hue, a * 0.55f),
                                start = Offset(tx, ty),
                                end = Offset(cx, cy),
                                strokeWidth = pt.radius * 0.7f,
                                cap = StrokeCap.Round,
                            )
                        }

                        // 径向光晕：从亮到透明的自然拖尾渐隐（替代原先的平涂椭圆）。
                        val glowR = pt.radius * 3.2f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    simColor(pt.hue, a),
                                    simColor(pt.hue, a * 0.5f),
                                    simColor(pt.hue, 0f),
                                ),
                                center = Offset(cx, cy),
                                radius = glowR,
                            ),
                            radius = glowR,
                            center = Offset(cx, cy),
                        )

                        // 中心亮芯：极小但亮，叠加后更像火花。
                        drawCircle(
                            color = simColor((pt.hue + 30f) % 360f, a),
                            radius = pt.radius * 0.55f,
                            center = Offset(cx, cy),
                        )
                    }
                }
            }
            Spacer(Modifier.height(Spacing.md))
        }
    }
}