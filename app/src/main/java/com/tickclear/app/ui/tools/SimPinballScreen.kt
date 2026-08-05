package com.tickclear.app.ui.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.tickclear.app.R
import com.tickclear.app.domain.tools.FoleySynth
import com.tickclear.app.ui.components.Haptic
import com.tickclear.app.ui.theme.Spacing
import kotlin.math.min
import kotlin.math.sqrt
import androidx.compose.runtime.withFrameMillis

private val LAUNCH = Offset(0.5f, 0.9f)
private const val BALL_R = 0.018f
private const val PEG_R = 0.022f

private data class Ball(var x: Float, var y: Float, var vx: Float, var vy: Float)

/**
 * 虚拟弹珠台（V2.9++ 模拟解压）。
 * 底部拖拽瞄准、松手发射弹珠；弹珠受重力下落，与钉板碰撞反弹并计分。
 * 纯 Canvas + 拖拽手势 + AudioTrack 合成，零新依赖。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimPinballScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    val pegs = remember {
        listOf(
            Offset(0.5f, 0.20f),
            Offset(0.40f, 0.33f), Offset(0.60f, 0.33f),
            Offset(0.30f, 0.46f), Offset(0.50f, 0.46f), Offset(0.70f, 0.46f),
            Offset(0.40f, 0.59f), Offset(0.60f, 0.59f),
        )
    }
    var ball by remember { mutableStateOf(Ball(LAUNCH.x, LAUNCH.y, 0f, 0f)) }
    var ballMoving by remember { mutableStateOf(false) }
    var aiming by remember { mutableStateOf(false) }
    var aimPoint by remember { mutableStateOf(LAUNCH) }
    var score by remember { mutableStateOf(0) }
    var tick by remember { mutableStateOf(0L) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    DisposableEffect(Unit) {
        onDispose { FoleySynth.stop() }
    }

    fun reset() {
        ball = Ball(LAUNCH.x, LAUNCH.y, 0f, 0f)
        ballMoving = false
        aiming = false
    }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            val now = withFrameMillis { it }
            val dt = if (last == 0L) 0.016f else ((now - last) / 1000f).coerceAtMost(0.04f)
            last = now
            if (ballMoving) {
                var b = ball
                b = b.copy(vy = b.vy + SIM_GRAVITY * dt)
                b = b.copy(x = b.x + b.vx * dt, y = b.y + b.vy * dt)
                if (b.x < BALL_R) b = b.copy(x = BALL_R, vx = -b.vx * 0.8f)
                if (b.x > 1 - BALL_R) b = b.copy(x = 1 - BALL_R, vx = -b.vx * 0.8f)
                if (b.y < BALL_R) b = b.copy(y = BALL_R, vy = -b.vy * 0.8f)
                if (b.y > 1.05f) { reset(); tick++; continue }
                for (peg in pegs) {
                    val dx = b.x - peg.x
                    val dy = b.y - peg.y
                    val d = sqrt(dx * dx + dy * dy)
                    val minD = BALL_R + PEG_R
                    if (d < minD && d > 1e-4f) {
                        val nx = dx / d
                        val ny = dy / d
                        b = b.copy(x = peg.x + nx * minD, y = peg.y + ny * minD)
                        val vDotN = b.vx * nx + b.vy * ny
                        if (vDotN < 0) {
                            b = b.copy(
                                vx = (b.vx - 2 * vDotN * nx) * 0.9f,
                                vy = (b.vy - 2 * vDotN * ny) * 0.9f,
                            )
                            score += 1
                            FoleySynth.play("pop")
                            Haptic.vibrate(context, 12)
                        }
                    }
                }
                b = b.copy(vx = b.vx * 0.999f, vy = b.vy * 0.999f)
                if (b.y > 0.88f && sqrt(b.vx * b.vx + b.vy * b.vy) < 0.02f) {
                    reset()
                    tick++
                    continue
                }
                ball = b
            }
            tick++
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_sim_pinball_title)) },
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
            Text(
                stringResource(R.string.tools_sim_pinball_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                stringResource(R.string.tools_sim_pinball_score, score),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(Spacing.md))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .height(420.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                if (ballMoving) return@detectDragGestures
                                aiming = true
                                aimPoint = if (canvasSize.width > 0) {
                                    Offset(offset.x / canvasSize.width, offset.y / canvasSize.height)
                                } else offset
                            },
                            onDrag = { change, _ ->
                                aimPoint = if (canvasSize.width > 0) {
                                    Offset(change.position.x / canvasSize.width, change.position.y / canvasSize.height)
                                } else change.position
                            },
                            onDragEnd = {
                                if (!aiming) return@detectDragGestures
                                aiming = false
                                val dx = aimPoint.x - LAUNCH.x
                                val dy = aimPoint.y - LAUNCH.y
                                val len = sqrt(dx * dx + dy * dy)
                                if (len > 0.03f) {
                                    val speed = min(len * 2.4f, 1.3f)
                                    ball = Ball(
                                        LAUNCH.x, LAUNCH.y,
                                        dx / len * speed, dy / len * speed,
                                    )
                                    ballMoving = true
                                }
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    canvasSize = size
                    tick // 观察：每帧触发重绘
                    val w = size.width
                    val h = size.height

                    for (peg in pegs) {
                        drawOval(
                            color = primary,
                            topLeft = Offset(peg.x * w - PEG_R * w, peg.y * h - PEG_R * h),
                            size = Size(PEG_R * 2 * w, PEG_R * 2 * h),
                        )
                    }
                    drawOval(
                        color = outline,
                        topLeft = Offset(LAUNCH.x * w - 4f, LAUNCH.y * h - 4f),
                        size = Size(8f, 8f),
                    )
                    if (aiming) {
                        drawLine(
                            color = primary,
                            start = Offset(LAUNCH.x * w, LAUNCH.y * h),
                            end = Offset(aimPoint.x * w, aimPoint.y * h),
                            strokeWidth = 3f,
                        )
                    }
                    drawOval(
                        color = Color(0xFFFF5252),
                        topLeft = Offset(ball.x * w - BALL_R * w, ball.y * h - BALL_R * h),
                        size = Size(BALL_R * 2 * w, BALL_R * 2 * h),
                    )
                }
            }

            Spacer(Modifier.height(Spacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Button(
                    onClick = { score = 0; reset() },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.tools_sim_pinball_reset)) }
            }
            Spacer(Modifier.height(Spacing.md))
        }
    }
}
