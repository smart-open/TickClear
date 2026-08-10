package com.tickclear.app.ui.tools

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tickclear.app.R
import com.tickclear.app.domain.tools.FoleySynth
import com.tickclear.app.ui.components.Haptic
import com.tickclear.app.ui.theme.Spacing
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private const val BALL_COUNT = 12        // 台面随机摆放的弹珠数
private const val BALL_R = 0.018f        // 弹珠半径（归一化）
private const val PEG_R = 0.022f         // 钉板钉半径（仅作障碍，不计入分）
private const val TRAIL_LEN = 8
private const val POPUP_LIFE = 900L      // 飘分存活时长（ms）
private const val LAUNCH_X = 0.5f        // 发射点（台面底部居中）
private const val LAUNCH_Y = 0.90f
private const val MIN_LAUNCH = 0.35f     // 最小发射速度（归一化/秒）
private const val MAX_LAUNCH = 1.75f     // 最大发射速度
private const val MAX_DRAG = 220f        // 手指拖动到该像素距离即满力
private const val MAX_BALLS = 40         // 弹珠上限（含发射的），超出丢弃最旧发射珠
private val GLOW = Color(0xFFFFAB40)      // 拖尾辉光暖琥珀色

private data class Ball(
    val id: Int,
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val trail: List<Offset> = emptyList(),
)

/** 命中飘分：在命中点升起并淡出。 */
private data class Popup(val x: Float, val y: Float, val text: String, val born: Long, val hue: Color)

/** 台面随机摆放 [BALL_COUNT] 颗互不重叠、静止的弹珠（id 0..11）。 */
private fun makeBalls(): List<Ball> {
    val list = ArrayList<Ball>(BALL_COUNT)
    val placed = ArrayList<Offset>(BALL_COUNT)
    var id = 0
    var guard = 0
    while (list.size < BALL_COUNT && guard < 800) {
        guard++
        val x = BALL_R + 0.05f + Random.nextFloat() * (1f - 2 * BALL_R - 0.10f)
        val y = BALL_R + 0.05f + Random.nextFloat() * (1f - 2 * BALL_R - 0.10f)
        val p = Offset(x, y)
        if (placed.all { dist(it.x, it.y, p.x, p.y) > 2.6f * BALL_R }) {
            placed.add(p)
            list.add(Ball(id++, x, y, 0f, 0f, listOf(p)))
        }
    }
    return list
}

/**
 * 虚拟弹珠台（V2.12++ 全面重做）。
 * 取消自由落体与弹珠雨：台面随机摆放 12 颗静止弹珠；底部「发射弹珠」控件由手指拖动
 * 设定方向+力度（甩得越远越快），松手即从台底居中发射一颗弹珠；零重力直线飞行，
 * 与墙壁及弹珠弹性碰撞，每撞中一颗弹珠 +1 分（边沿检测，避免一次接触重复计分）。
 * 视觉：弹珠拖尾辉光、钉子命中闪光、命中飘分；碰撞声优先真实录音 marble_click，缺失回退合成「叮」。
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
    var balls by remember { mutableStateOf(makeBalls()) }
    var score by remember { mutableIntStateOf(0) }
    var nextId by remember { mutableIntStateOf(BALL_COUNT) }
    var popups by remember { mutableStateOf<List<Popup>>(emptyList()) }
    var pegFlash by remember { mutableStateOf(LongArray(pegs.size)) }
    // 发射瞄准状态（拖动手势驱动，Canvas 内实时绘制）
    var aiming by remember { mutableStateOf(false) }
    var aimDir by remember { mutableStateOf(Offset(0f, -1f)) }
    var aimPower by remember { mutableStateOf(0.5f) }
    var dragStartPx by remember { mutableStateOf(Offset.Zero) }

    DisposableEffect(Unit) {
        onDispose { FoleySynth.stop() }
    }

    /** 从台底发射点按方向+力度发射一颗弹珠。 */
    fun launch(dir: Offset, power: Float) {
        val speed = MIN_LAUNCH + (MAX_LAUNCH - MIN_LAUNCH) * power
        var dx = dir.x
        var dy = dir.y
        if (dx == 0f && dy == 0f) { dx = 0f; dy = -1f }   // 默认竖直向上
        val len = sqrt(dx * dx + dy * dy)
        val ux = dx / len
        val uy = dy / len
        val b = Ball(
            id = nextId,
            x = LAUNCH_X, y = LAUNCH_Y,
            vx = ux * speed, vy = uy * speed,
            trail = listOf(Offset(LAUNCH_X, LAUNCH_Y)),
        )
        nextId++
        balls = balls + b
        FoleySynth.playPop(context)
        Haptic.vibrate(context, 14)
    }

    fun reset() {
        balls = makeBalls()
        nextId = BALL_COUNT
        score = 0
        popups = emptyList()
        pegFlash = LongArray(pegs.size)
        aiming = false
        aimDir = Offset(0f, -1f)
        aimPower = 0.5f
    }

    // 帧循环：零重力推进 + 墙壁/钉子/弹珠碰撞 + 计分（边沿检测）
    LaunchedEffect(Unit) {
        var last = 0L
        var lastHitMs = 0L
        val prevContacts = mutableSetOf<Long>()
        while (true) {
            val now = withFrameMillis { it }
            val dt = if (last == 0L) 0.016f else ((now - last) / 1000f).coerceAtMost(0.04f)
            last = now
            val tNow = System.currentTimeMillis()

            val next = balls.toMutableList()
            // 1) 运动积分（无重力，仅线性阻尼使其最终静止）
            for (i in next.indices) {
                val b = next[i]
                val damp = (1f - 0.6f * dt).coerceAtLeast(0.4f)
                var vx = b.vx * damp
                var vy = b.vy * damp
                var x = b.x + vx * dt
                var y = b.y + vy * dt
                if (x < BALL_R) { x = BALL_R; vx = -vx * 0.88f }
                if (x > 1 - BALL_R) { x = 1 - BALL_R; vx = -vx * 0.88f }
                if (y < BALL_R) { y = BALL_R; vy = -vy * 0.88f }
                if (y > 1 - BALL_R) { y = 1 - BALL_R; vy = -vy * 0.88f }
                // 钉子（仅作障碍，不计入分）
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
                            val nf = pegFlash.copyOf()
                            nf[pegIdx] = tNow
                            pegFlash = nf
                        }
                    }
                }
                if (vx * vx + vy * vy < 0.0004f) { vx = 0f; vy = 0f }
                next[i] = b.copy(x = x, y = y, vx = vx, vy = vy,
                    trail = (b.trail + Offset(x, y)).takeLast(TRAIL_LEN))
            }
            // 2) 弹珠两两碰撞 + 计分（仅「新接触」+1，分离后再撞才再计）
            val curContacts = mutableSetOf<Long>()
            var scored = 0
            var hitX = 0f
            var hitY = 0f
            for (i in 0 until next.size) {
                for (j in i + 1 until next.size) {
                    val a = next[i]
                    val c = next[j]
                    val dx = c.x - a.x
                    val dy = c.y - a.y
                    val d = sqrt(dx * dx + dy * dy)
                    val minD = 2 * BALL_R
                    if (d < minD && d > 1e-4f) {
                        val lo = minOf(a.id, c.id)
                        val hi = maxOf(a.id, c.id)
                        val key = lo.toLong() * 100000L + hi.toLong()
                        curContacts.add(key)
                        if (!prevContacts.contains(key)) {
                            val nx = dx / d
                            val ny = dy / d
                            val dvx = c.vx - a.vx
                            val dvy = c.vy - a.vy
                            val vn = dvx * nx + dvy * ny
                            if (vn < 0f) {    // 仅在相互靠近时交换法向动量
                                val jImp = -(1f + 0.96f) * vn / 2f
                                next[i] = a.copy(vx = a.vx - jImp * nx, vy = a.vy - jImp * ny)
                                next[j] = c.copy(vx = c.vx + jImp * nx, vy = c.vy + jImp * ny)
                            }
                            val overlap = minD - d
                            next[i] = next[i].copy(
                                x = next[i].x - nx * overlap / 2f,
                                y = next[i].y - ny * overlap / 2f,
                            )
                            next[j] = next[j].copy(
                                x = next[j].x + nx * overlap / 2f,
                                y = next[j].y + ny * overlap / 2f,
                            )
                            scored++
                            hitX = (a.x + c.x) / 2f
                            hitY = (a.y + c.y) / 2f
                        }
                    }
                }
            }
            prevContacts.clear()
            prevContacts.addAll(curContacts)

            if (scored > 0) {
                score += scored
                popups = popups + Popup(hitX, hitY, "+$scored", tNow, simColor(Random.nextFloat() * 360f, 1f))
                if (tNow - lastHitMs >= 70L) {
                    FoleySynth.playPop(context)
                    Haptic.vibrate(context, if (scored >= 3) 22 else 12)
                    lastHitMs = tNow
                }
            }
            balls = if (next.size > MAX_BALLS) {
                val targets = next.filter { it.id < BALL_COUNT }
                val shots = next.filter { it.id >= BALL_COUNT }.takeLast(MAX_BALLS - BALL_COUNT)
                targets + shots
            } else next

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
                    .height(420.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { dragStartPx = it; aiming = true },
                            onDrag = { change, _ ->
                                    change.consume()
                                    val d = change.position - dragStartPx
                                    val len = d.getDistance()
                                    if (len > 4f) {
                                        aimDir = d / len
                                        aimPower = (len / MAX_DRAG).coerceIn(0.15f, 1f)
                                    }
                                },
                                onDragEnd = { aiming = false; launch(aimDir, aimPower) },
                            )
                        },
                ) {
                    val w = size.width
                    val h = size.height
                    val tNow = System.currentTimeMillis()

                    // 拖尾辉光
                    for (ball in balls) {
                        val tr = ball.trail
                        val n = tr.size
                        for (i in tr.indices) {
                            val f = if (n > 1) i.toFloat() / (n - 1) else 1f
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

                    // 发射点标记
                    drawSoftShadow(
                        center = Offset(LAUNCH_X * w, LAUNCH_Y * h + BALL_R * w * 0.95f),
                        radiusX = BALL_R * w * 1.4f,
                        radiusY = BALL_R * w * 0.55f,
                        maxAlpha = 0.18f,
                    )
                    fillSphere(Offset(LAUNCH_X * w, LAUNCH_Y * h), BALL_R * w * 1.3f, primary, rimLight = false)

                    // 钉子（仅障碍）：3D 受光金属球 + 接地软阴影 + 命中闪光圈
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

                    // 瞄准指示：从发射点沿手指方向画出箭头，长度随力度
                    if (aiming) {
                        val lenN = 0.05f + aimPower * 0.34f
                        val tipX = LAUNCH_X + aimDir.x * lenN
                        val tipY = LAUNCH_Y + aimDir.y * lenN
                        val sx = LAUNCH_X * w
                        val sy = LAUNCH_Y * h
                        val tx = tipX * w
                        val ty = tipY * h
                        drawLine(
                            color = primary,
                            start = Offset(sx, sy),
                            end = Offset(tx, ty),
                            strokeWidth = 5f,
                        )
                        val ang = atan2(aimDir.y, aimDir.x)
                        val ah = 0.03f * w
                        val a1 = ang + 2.6f
                        val a2 = ang - 2.6f
                        drawLine(color = primary, start = Offset(tx, ty),
                            end = Offset(tx + cos(a1) * ah, ty + sin(a1) * ah), strokeWidth = 5f)
                        drawLine(color = primary, start = Offset(tx, ty),
                            end = Offset(tx + cos(a2) * ah, ty + sin(a2) * ah), strokeWidth = 5f)
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
            // 发射弹珠（位于重置按钮上方）：按住并向任意方向甩出，力度越大越快
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { dragStartPx = it; aiming = true },
                            onDrag = { change, _ ->
                                change.consume()
                                val d = change.position - dragStartPx
                                val len = d.getDistance()
                                if (len > 4f) {
                                    aimDir = d / len
                                    aimPower = (len / MAX_DRAG).coerceIn(0.15f, 1f)
                                }
                            },
                            onDragEnd = { aiming = false; launch(aimDir, aimPower) },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(14.dp),
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            stringResource(R.string.tools_sim_pinball_launch),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            Button(
                onClick = { reset() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.tools_sim_pinball_reset)) }
            Spacer(Modifier.height(Spacing.md))
        }
    }
}
