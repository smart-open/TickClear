package com.tickclear.app.ui.tools

import android.graphics.Paint
import android.graphics.Typeface
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tickclear.app.R
import com.tickclear.app.domain.tools.FoleySynth
import com.tickclear.app.ui.components.Haptic
import com.tickclear.app.ui.theme.Spacing
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.compose.runtime.withFrameMillis
import kotlin.random.Random

private const val BALL_COUNT = 12
private const val BALL_R = 0.018f
private const val PEG_R = 0.022f
private const val TRAIL_LEN = 8
private const val COMBO_WINDOW = 800L   // 连击窗口（ms）：窗口内连续碰撞累计连击
private const val POPUP_LIFE = 900L     // 飘分存活时长（ms）
private val GLOW = Color(0xFFFFAB40)    // 拖尾辉光暖琥珀色

private data class Ball(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val trail: List<Offset> = emptyList(),
)

/** 命中飘分：在命中点升起并淡出。 */
private data class Popup(val x: Float, val y: Float, val text: String, val born: Long, val hue: Color)

/** 在顶部随机位置生成一颗弹珠，带微小初速，形成"随机分布"的弹珠雨。 */
private fun spawnBall(): Ball {
    val x = 0.05f + Random.nextFloat() * 0.90f
    val y = 0.02f + Random.nextFloat() * 0.55f
    val vx = (Random.nextFloat() - 0.5f) * 0.12f
    val vy = Random.nextFloat() * 0.06f
    return Ball(x, y, vx, vy, listOf(Offset(x, y)))
}

/** 连击数越高颜色越"烫"：绿→黄→橙→红。 */
private fun comboColor(c: Int): Color = when {
    c >= 8 -> Color(0xFFFF1744)
    c >= 5 -> Color(0xFFFF9100)
    c >= 3 -> Color(0xFFFFEA00)
    else -> Color(0xFFB2FF59)
}

/**
 * 虚拟弹珠台（V2.9++ 模拟解压，V2.11++ 12 球弹珠雨重做，V2.11++ 视觉手感打磨）。
 * 12 颗弹珠随机分布、受重力下落，与钉板碰撞反弹并计分；落底自动从顶部重新撒下，保持 12 颗持续弹跳。
 * 视觉手感：弹珠拖尾辉光、钉子命中闪光、连击计数（分数倍率）、命中飘分。
 * 碰撞声优先真实录音 marble_click（CC0），缺失回退合成「叮」。
 * 纯 Canvas + AudioTrack/MediaPlayer，零新依赖。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimPinballScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
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
    var combo by remember { mutableIntStateOf(0) }
    var comboExpire by remember { mutableStateOf(0L) }
    var popups by remember { mutableStateOf<List<Popup>>(emptyList()) }
    var pegFlash by remember { mutableStateOf(LongArray(pegs.size)) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    DisposableEffect(Unit) {
        onDispose { FoleySynth.stop() }
    }

    fun reset() {
        balls = List(BALL_COUNT) { spawnBall() }
        score = 0
        combo = 0
        comboExpire = 0L
        popups = emptyList()
        pegFlash = LongArray(pegs.size)
    }

    // 弹珠持续在动 → 帧循环持续运行（类比"粒子在飞"），离开页面即随组合销毁挂起。
    LaunchedEffect(Unit) {
        var last = 0L
        var lastHitMs = 0L
        var lastComboMs = 0L
        while (true) {
            val now = withFrameMillis { it }
            val dt = if (last == 0L) 0.016f else ((now - last) / 1000f).coerceAtMost(0.04f)
            last = now
            val tNow = System.currentTimeMillis()

            // 连击窗口过期则清零
            if (tNow > comboExpire && combo != 0) combo = 0

            var scored = 0
            var hit = false
            var lastHitX = 0f
            var lastHitY = 0f
            val next = balls.mapIndexed { idx, b ->
                var x = b.x
                var y = b.y
                var vx = b.vx
                var vy = b.vy
                vy += SIM_GRAVITY * dt
                x += vx * dt
                y += vy * dt
                if (x < BALL_R) { x = BALL_R; vx = -vx * 0.8f }
                if (x > 1 - BALL_R) { x = 1 - BALL_R; vx = -vx * 0.8f }
                if (y < BALL_R) { y = BALL_R; vy = -vy * 0.8f }
                for (pegIdx in pegs.indices) {
                    val peg = pegs[pegIdx]
                    val dx = x - peg.x
                    val dy = y - peg.y
                    val d = sqrt(dx * dx + dy * dy)
                    val minD = BALL_R + PEG_R
                    if (d < minD && d > 1e-4f) {
                        val nx = dx / d
                        val ny = dy / d
                        x = peg.x + nx * minD
                        y = peg.y + ny * minD
                        val vDotN = vx * nx + vy * ny
                        if (vDotN < 0) {
                            vx = (vx - 2 * vDotN * nx) * 0.9f
                            vy = (vy - 2 * vDotN * ny) * 0.9f
                            scored++
                            hit = true
                            lastHitX = peg.x
                            lastHitY = peg.y
                            val nf = pegFlash.copyOf()
                            nf[pegIdx] = tNow
                            pegFlash = nf
                        }
                    }
                }
                vx *= 0.999f
                vy *= 0.999f
                val newTrail = (b.trail + Offset(x, y)).takeLast(TRAIL_LEN)
                if (y > 1.05f) {
                    val nb = spawnBall()
                    Ball(nb.x, nb.y, nb.vx, nb.vy, listOf(Offset(nb.x, nb.y)))
                } else {
                    Ball(x, y, vx, vy, newTrail)
                }
            }
            balls = next

            if (scored > 0) {
                // 窗口内连续命中则累计连击，否则重置为 1
                combo = if (tNow - lastComboMs < COMBO_WINDOW) combo + 1 else 1
                lastComboMs = tNow
                comboExpire = tNow + COMBO_WINDOW
                val mult = combo.coerceAtMost(10)
                val gained = scored * mult
                score += gained
                if (hit) {
                    popups = popups + Popup(lastHitX, lastHitY, "+$gained", tNow, comboColor(combo))
                }
            }
            // 12 球齐撞时音/震节流，避免过载成噪声。
            if (hit && tNow - lastHitMs >= 70L) {
                FoleySynth.playPop(context)
                Haptic.vibrate(context, if (combo >= 5) 22 else 12)
                lastHitMs = tNow
            }
            popups = popups.filter { tNow - it.born < POPUP_LIFE }
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
                    val tNow = System.currentTimeMillis()

                    // 拖尾辉光：每颗弹珠身后拖出渐隐暖色光晕
                    for (ball in balls) {
                        val tr = ball.trail
                        val n = tr.size
                        for (i in tr.indices) {
                            val f = if (n > 1) i.toFloat() / (n - 1) else 1f // 0 最旧 → 1 最新
                            val a = 0.05f + 0.16f * f
                            val r = BALL_R * w * (0.35f + 0.55f * f)
                            drawCircle(
                                color = GLOW,
                                radius = r,
                                center = Offset(tr[i].x * w, tr[i].y * h),
                                alpha = a,
                            )
                        }
                    }

                    // 钉子：3D 受光金属球 + 接地软阴影 + 命中闪光圈
                    for (pegIdx in pegs.indices) {
                        val peg = pegs[pegIdx]
                        val px = peg.x * w
                        val py = peg.y * h
                        drawSoftShadow(
                            center = Offset(px, py + PEG_R * w * 0.95f),
                            radiusX = PEG_R * w * 0.95f,
                            radiusY = PEG_R * w * 0.38f,
                            maxAlpha = 0.16f,
                        )
                        fillSphere(Offset(px, py), PEG_R * w, primary)
                        val fl = tNow - pegFlash[pegIdx]
                        if (fl in 0..160) {
                            val fa = 1f - fl / 160f
                            drawCircle(
                                color = Color.White,
                                radius = PEG_R * w * (1.25f + 0.5f * fa),
                                center = Offset(px, py),
                                alpha = 0.5f * fa,
                                style = Stroke(width = 3f),
                            )
                        }
                    }

                    // 弹珠：3D 球体 + 接地软阴影 + 材质辉光边
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

                    // 连击数（顶部居中，脉冲放大 + 颜色随连击升级）
                    if (combo >= 2 && tNow < comboExpire) {
                        val pulse = 1f + 0.12f * sin(tNow / 90f)
                        val size = (if (combo >= 5) 36f else 28f) * (w / 360f) * pulse
                        val paint = Paint().apply {
                            isAntiAlias = true
                            textAlign = Paint.Align.CENTER
                            textSize = size
                            color = comboColor(combo).toArgb()
                            typeface = Typeface.DEFAULT_BOLD
                            setShadowLayer(8f, 0f, 0f, Color.Black.toArgb())
                        }
                        drawContext.canvas.nativeCanvas.drawText("连击 x$combo", w / 2f, h * 0.10f, paint)
                    }

                    // 飘分：命中处升起并淡出
                    for (p in popups) {
                        val age = (tNow - p.born) / 1000f
                        val life = POPUP_LIFE / 1000f
                        if (age < life) {
                            val a = 1f - age / life
                            val rise = age * 0.12f * h
                            val paint = Paint().apply {
                                isAntiAlias = true
                                textAlign = Paint.Align.CENTER
                                textSize = 22f * (w / 360f)
                                color = p.hue.toArgb()
                                alpha = (a * 255).toInt()
                                typeface = Typeface.DEFAULT_BOLD
                                setShadowLayer(6f, 0f, 0f, Color.Black.toArgb())
                            }
                            drawContext.canvas.nativeCanvas.drawText(p.text, p.x * w, p.y * h - rise, paint)
                        }
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
