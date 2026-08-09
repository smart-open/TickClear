package com.tickclear.app.ui.tools

import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tickclear.app.R
import com.tickclear.app.domain.tools.FoleySynth
import com.tickclear.app.ui.components.Haptic
import com.tickclear.app.ui.theme.Spacing
import kotlin.math.sqrt
import androidx.compose.runtime.withFrameMillis
import kotlin.random.Random

private const val BALL_COUNT = 12
private const val BALL_R = 0.018f
private const val PEG_R = 0.022f

private data class Ball(var x: Float, var y: Float, var vx: Float, var vy: Float)

/** 在顶部随机位置生成一颗弹珠，带微小初速，形成"随机分布"的弹珠雨。 */
private fun spawnBall(): Ball = Ball(
    x = 0.05f + Random.nextFloat() * 0.90f,
    y = 0.02f + Random.nextFloat() * 0.55f,
    vx = (Random.nextFloat() - 0.5f) * 0.12f,
    vy = Random.nextFloat() * 0.06f,
)

/**
 * 虚拟弹珠台（V2.9++ 模拟解压，V2.11++ 12 球弹珠雨重做）。
 * 12 颗弹珠随机分布、受重力下落，与钉板碰撞反弹并计分；落底自动从顶部重新撒下，保持 12 颗持续弹跳。
 * 碰撞声优先真实录音 marble_click（CC0），缺失回退合成「叮」。
 * 纯 Canvas + AudioTrack/MediaPlayer，零新依赖。
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
    var balls by remember { mutableStateOf(List(BALL_COUNT) { spawnBall() }) }
    var score by remember { mutableIntStateOf(0) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    DisposableEffect(Unit) {
        onDispose { FoleySynth.stop() }
    }

    fun reset() {
        balls = List(BALL_COUNT) { spawnBall() }
        score = 0
    }

    // 弹珠持续在动 → 帧循环持续运行（类比"粒子在飞"），离开页面即随组合销毁挂起。
    LaunchedEffect(Unit) {
        var last = 0L
        var lastHitMs = 0L
        while (true) {
            val now = withFrameMillis { it }
            val dt = if (last == 0L) 0.016f else ((now - last) / 1000f).coerceAtMost(0.04f)
            last = now
            val tNow = System.currentTimeMillis()
            var scored = 0
            var hit = false
            val next = balls.map { b ->
                var nb = b.copy(vy = b.vy + SIM_GRAVITY * dt)
                nb = nb.copy(x = nb.x + nb.vx * dt, y = nb.y + nb.vy * dt)
                if (nb.x < BALL_R) nb = nb.copy(x = BALL_R, vx = -nb.vx * 0.8f)
                if (nb.x > 1 - BALL_R) nb = nb.copy(x = 1 - BALL_R, vx = -nb.vx * 0.8f)
                if (nb.y < BALL_R) nb = nb.copy(y = BALL_R, vy = -nb.vy * 0.8f)
                for (peg in pegs) {
                    val dx = nb.x - peg.x
                    val dy = nb.y - peg.y
                    val d = sqrt(dx * dx + dy * dy)
                    val minD = BALL_R + PEG_R
                    if (d < minD && d > 1e-4f) {
                        val nx = dx / d
                        val ny = dy / d
                        nb = nb.copy(x = peg.x + nx * minD, y = peg.y + ny * minD)
                        val vDotN = nb.vx * nx + nb.vy * ny
                        if (vDotN < 0) {
                            nb = nb.copy(
                                vx = (nb.vx - 2 * vDotN * nx) * 0.9f,
                                vy = (nb.vy - 2 * vDotN * ny) * 0.9f,
                            )
                            scored++
                            hit = true
                        }
                    }
                }
                nb = nb.copy(vx = nb.vx * 0.999f, vy = nb.vy * 0.999f)
                if (nb.y > 1.05f) spawnBall() else nb
            }
            if (scored > 0) score += scored
            // 12 球齐撞时音/震节流，避免过载成噪声。
            if (hit && tNow - lastHitMs >= 70L) {
                FoleySynth.playPop(context)
                Haptic.vibrate(context, 12)
                lastHitMs = tNow
            }
            balls = next
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
            SimHintCard(stringResource(R.string.tools_sim_pinball_hint))
            Spacer(Modifier.height(Spacing.sm))
            SimStatCard(
                value = score.toString(),
                label = stringResource(R.string.tools_unit_points),
                horizontal = true,
            )
            Spacer(Modifier.height(Spacing.md))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .height(420.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    canvasSize = size
                    val w = size.width
                    val h = size.height

                    // 钉子：3D 受光金属球 + 接地软阴影（二巡精修）
                    for (peg in pegs) {
                        val px = peg.x * w
                        val py = peg.y * h
                        drawSoftShadow(
                            center = Offset(px, py + PEG_R * w * 0.95f),
                            radiusX = PEG_R * w * 0.95f,
                            radiusY = PEG_R * w * 0.38f,
                            maxAlpha = 0.16f,
                        )
                        fillSphere(Offset(px, py), PEG_R * w, primary)
                    }
                    // 弹珠：3D 球体 + 接地软阴影 + 材质辉光边（二巡精修）
                    for (ball in balls) {
                        val bx = ball.x * w
                        val by = ball.y * h
                        drawSoftShadow(
                            center = Offset(bx, by + BALL_R * w * 0.95f),
                            radiusX = BALL_R * w * 0.95f,
                            radiusY = BALL_R * w * 0.4f,
                            maxAlpha = 0.18f,
                        )
                        fillSphere(Offset(bx, by), BALL_R * w, Color(0xFFFF5252), rimLight = false)
                        drawRimLight(center = Offset(bx, by), radius = BALL_R * w, tint = Color(0xFFFF8A80), alpha = 0.40f)
                    }
                }
            }

            Spacer(Modifier.height(Spacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Button(
                    onClick = { reset() },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.tools_sim_pinball_reset)) }
            }
            Spacer(Modifier.height(Spacing.md))
        }
    }
}
