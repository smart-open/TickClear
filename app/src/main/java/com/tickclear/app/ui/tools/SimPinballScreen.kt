package com.tickclear.app.ui.tools

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
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

private const val BALL_COUNT = 12        // 台面蓝色目标弹珠数
private const val SHOTS = 10             // 每局发射次数
private const val BALL_R = 0.020f        // 弹珠半径（归一化）
private const val TRAIL_LEN = 8
private const val POPUP_LIFE = 900L      // 飘分存活时长（ms）
private const val LAUNCH_X = 0.5f        // 发射台中心（台底居中）
private const val LAUNCH_Y = 0.88f
private const val MIN_LAUNCH = 0.45f     // 最小发射速度（归一化/秒）
private const val MAX_LAUNCH = 1.85f     // 最大发射速度
private const val MAX_DRAG = 220f        // 手指拖动到该像素距离即满力
private val GLOW = Color(0xFFFFAB40)      // 拖尾辉光暖琥珀色
private val BLUE = Color(0xFF42A5F5)            // 蓝珠
private val BLUE_LIGHT = Color(0xFF90CAF9)
private val RED = Color(0xFFFF5252)             // 红珠（发射珠）
private val RED_LIGHT = Color(0xFFFF8A80)
private const val PREFS = "pinball_prefs"

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

/** 台面随机摆放 [BALL_COUNT] 颗互不重叠、静止的蓝色弹珠（id 0..11），避开底部发射台。 */
private fun makeBalls(): List<Ball> {
    val list = ArrayList<Ball>(BALL_COUNT)
    val placed = ArrayList<Offset>(BALL_COUNT)
    var id = 0
    var guard = 0
    while (list.size < BALL_COUNT && guard < 800) {
        guard++
        val x = BALL_R + 0.06f + Random.nextFloat() * (1f - 2 * BALL_R - 0.12f)
        val y = BALL_R + 0.08f + Random.nextFloat() * (LAUNCH_Y - 2 * BALL_R - 0.18f)
        val p = Offset(x, y)
        if (placed.all {
                val dx = it.x - p.x; val dy = it.y - p.y
                sqrt(dx * dx + dy * dy) > 2.6f * BALL_R
            }
        ) {
            placed.add(p)
            list.add(Ball(id++, x, y, 0f, 0f, listOf(p)))
        }
    }
    return list
}

private fun loadBestTotal(ctx: Context): Int =
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("best_total", 0)

private fun saveBestTotal(ctx: Context, value: Int) {
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("best_total", value).apply()
}

/**
 * 虚拟弹珠台（V2.13 重做）。
 * 干净台面：12 颗蓝色目标弹珠 + 底部长方块发射台（单一发射口随触摸方向转动，已修正 90° 偏差）。
 * 在台面按住并拖动设定发射口朝向与力度，松手从发射台射出红色弹珠；红珠直线飞行、撞墙反弹、
 * 与蓝珠弹性碰撞，每个首次接触到的蓝珠 +1 分（边沿检测，避免一次接触重复计分）。
 * 共 10 发，结算本局总分、单发最佳 / 最差，历史最高分（单局总分）跨会话持久化保留。
 * 视觉：弹珠拖尾辉光、命中飘分；碰撞声优先真实录音 marble_click，缺失回退合成「叮」。
 * 纯 Canvas + AudioTrack/MediaPlayer，零新依赖。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimPinballScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary

    var balls by remember { mutableStateOf(makeBalls()) }
    var score by remember { mutableIntStateOf(0) }
    var shotsUsed by remember { mutableIntStateOf(0) }
    var bestTotal by remember { mutableIntStateOf(loadBestTotal(context)) }
    var singleBest by remember { mutableIntStateOf(0) }
    var singleWorst by remember { mutableIntStateOf(0) }
    var sessionOver by remember { mutableStateOf(false) }
    var popups by remember { mutableStateOf<List<Popup>>(emptyList()) }
    val perShot = remember { IntArray(SHOTS) }   // 每发得分缓存（非重组）
    var nextId by remember { mutableIntStateOf(BALL_COUNT) }
    // 瞄准状态（拖动手势驱动，Canvas 内实时绘制）
    var aiming by remember { mutableStateOf(false) }
    var aimDir by remember { mutableStateOf(Offset(0f, -1f)) }
    var aimPower by remember { mutableStateOf(0.5f) }
    var dragStartPx by remember { mutableStateOf(Offset.Zero) }

    fun refreshExtremes() {
        var best = 0
        var worst = Int.MAX_VALUE
        for (i in 0 until shotsUsed) {
            val s = perShot[i]
            if (s > best) best = s
            if (s < worst) worst = s
        }
        singleBest = best
        singleWorst = if (worst == Int.MAX_VALUE) 0 else worst
    }

    fun launch(dir: Offset, power: Float) {
        if (sessionOver || shotsUsed >= SHOTS) return
        val speed = MIN_LAUNCH + (MAX_LAUNCH - MIN_LAUNCH) * power
        var dx = dir.x
        var dy = dir.y
        if (dx == 0f && dy == 0f) { dx = 0f; dy = -1f }
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
        perShot[shotsUsed] = 0
        shotsUsed++
        balls = balls + b
        FoleySynth.playPop(context)
        Haptic.vibrate(context, 14)
    }

    fun reset() {
        balls = makeBalls()
        nextId = BALL_COUNT
        score = 0
        shotsUsed = 0
        bestTotal = loadBestTotal(context)
        singleBest = 0
        singleWorst = 0
        sessionOver = false
        perShot.fill(0)
        popups = emptyList()
        aiming = false
        aimDir = Offset(0f, -1f)
        aimPower = 0.5f
    }

    // 帧循环：零重力推进 + 墙壁/弹珠碰撞 + 计分（边沿检测）
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
                if (b.id >= BALL_COUNT && b.vx == 0f && b.vy == 0f) continue
                val damp = (1f - 0.55f * dt).coerceAtLeast(0.45f)
                var vx = b.vx * damp
                var vy = b.vy * damp
                var x = b.x + vx * dt
                var y = b.y + vy * dt
                if (x < BALL_R) { x = BALL_R; vx = -vx * 0.9f }
                if (x > 1 - BALL_R) { x = 1 - BALL_R; vx = -vx * 0.9f }
                if (y < BALL_R) { y = BALL_R; vy = -vy * 0.9f }
                if (y > 1 - BALL_R) { y = 1 - BALL_R; vy = -vy * 0.9f }
                if (vx * vx + vy * vy < 0.0004f) { vx = 0f; vy = 0f }
                next[i] = b.copy(
                    x = x, y = y, vx = vx, vy = vy,
                    trail = (b.trail + Offset(x, y)).takeLast(TRAIL_LEN),
                )
            }
            // 2) 两两碰撞 + 计分（仅「新接触」+1，分离后再撞才再计）
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
                                val jImp = -(1f + 0.95f) * vn / 2f
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
                            // 红珠（发射珠）撞蓝珠（目标）→ 本次发射 +1 分
                            val redId = if (a.id >= BALL_COUNT) a.id else c.id
                            val blueId = if (a.id < BALL_COUNT) a.id else c.id
                            if (redId >= BALL_COUNT && blueId < BALL_COUNT) {
                                val slot = redId - BALL_COUNT
                                if (slot in 0 until SHOTS) {
                                    perShot[slot]++
                                    scored++
                                    hitX = (a.x + c.x) / 2f
                                    hitY = (a.y + c.y) / 2f
                                }
                            }
                        }
                    }
                }
            }
            prevContacts.clear()
            prevContacts.addAll(curContacts)

            if (scored > 0) {
                score += scored
                refreshExtremes()
                popups = popups + Popup(hitX, hitY, "+$scored", tNow, simColor(Random.nextFloat() * 360f, 1f))
                if (tNow - lastHitMs >= 70L) {
                    FoleySynth.playPop(context)
                    Haptic.vibrate(context, if (scored >= 3) 22 else 12)
                    lastHitMs = tNow
                }
            }

            balls = next

            // 本局结束判定：已发满 10 发且所有红珠静止
            if (shotsUsed >= SHOTS && !sessionOver) {
                val anyRedMoving = next.any {
                    it.id >= BALL_COUNT && (it.vx * it.vx + it.vy * it.vy) > 0.0004f
                }
                if (!anyRedMoving) {
                    sessionOver = true
                    if (score > bestTotal) {
                        bestTotal = score
                        saveBestTotal(context, score)
                    }
                }
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

            // 第 1 行：最高分 / 单发最佳 / 单发最差
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                SimStatCard(
                    value = bestTotal.toString(),
                    label = stringResource(R.string.tools_sim_pinball_best_total),
                    modifier = Modifier.weight(1f),
                    horizontal = true,
                    compact = true,
                )
                SimStatCard(
                    value = singleBest.toString(),
                    label = stringResource(R.string.tools_sim_pinball_single_best),
                    modifier = Modifier.weight(1f),
                    horizontal = true,
                    compact = true,
                )
                SimStatCard(
                    value = singleWorst.toString(),
                    label = stringResource(R.string.tools_sim_pinball_single_worst),
                    modifier = Modifier.weight(1f),
                    horizontal = true,
                    compact = true,
                )
            }
            Spacer(Modifier.height(Spacing.sm))

            // 第 2 行：本局总分 / 已发射
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                SimStatCard(
                    value = score.toString(),
                    label = stringResource(R.string.tools_sim_pinball_score),
                    modifier = Modifier.weight(1f),
                    horizontal = true,
                    compact = true,
                )
                SimStatCard(
                    value = "$shotsUsed/$SHOTS",
                    label = stringResource(R.string.tools_sim_pinball_shots),
                    modifier = Modifier.weight(1f),
                    horizontal = true,
                    compact = true,
                )
            }
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
                    val bx = LAUNCH_X * w
                    val by = LAUNCH_Y * h

                    // 拖尾辉光
                    for (ball in balls) {
                        val tr = ball.trail
                        val n = tr.size
                        val isRed = ball.id >= BALL_COUNT
                        for (i in tr.indices) {
                            val f = if (n > 1) i.toFloat() / (n - 1) else 1f
                            val a = 0.05f + 0.16f * f
                            val r = BALL_R * w * (0.35f + 0.55f * f)
                            drawCircle(
                                color = if (isRed) GLOW else BLUE_LIGHT,
                                radius = r,
                                center = Offset(tr[i].x * w, tr[i].y * h),
                                alpha = a,
                            )
                        }
                    }

                    // 弹珠：蓝珠=目标，红珠=发射珠
                    for (ball in balls) {
                        val bxx = ball.x * w
                        val byy = ball.y * h
                        val isRed = ball.id >= BALL_COUNT
                        drawSoftShadow(
                            center = Offset(bxx, byy + BALL_R * w * 0.95f),
                            radiusX = BALL_R * w * 0.95f,
                            radiusY = BALL_R * w * 0.4f,
                            maxAlpha = 0.18f,
                        )
                        fillSphere(
                            center = Offset(bxx, byy),
                            radius = BALL_R * w,
                            base = if (isRed) RED else BLUE,
                            rimLight = false,
                        )
                        drawRimLight(
                            center = Offset(bxx, byy),
                            radius = BALL_R * w,
                            tint = if (isRed) RED_LIGHT else BLUE_LIGHT,
                            alpha = 0.40f,
                        )
                    }

                    // 发射台：长方块底座 + 单一发射口（炮管随 aimDir 正确指向，修正顺时针 90° 偏差）
                    val blockW = 0.22f * w     // 长方块：长
                    val blockH = 0.052f * h    // 矮
                    drawSoftShadow(
                        center = Offset(bx, by + blockH * 0.5f),
                        radiusX = blockW * 0.55f,
                        radiusY = blockH * 0.7f,
                        maxAlpha = 0.22f,
                    )
                    // 长方块底座（深色，与炮管形成对比）
                    fillRoundRect3D(
                        topLeft = Offset(bx - blockW / 2f, by - blockH / 2f),
                        size = Size(blockW, blockH),
                        cornerRadius = blockH * 0.5f,
                        base = Color(0xFF37474F),
                    )
                    // 顶面高光，增加立体感
                    drawGloss(
                        center = Offset(bx, by - blockH * 0.22f),
                        radiusX = blockW * 0.42f,
                        radiusY = blockH * 0.20f,
                        alpha = 0.18f,
                    )
                    // 旋转发射口（炮管）：基准几何沿 +x，旋转后正好指向 aimDir（消除 90° 偏差）
                    val ang = atan2(aimDir.y, aimDir.x)
                    drawContext.canvas.save()
                    drawContext.canvas.nativeCanvas.rotate(Math.toDegrees(ang.toDouble()).toFloat(), bx, by)
                    val tubeW = blockH * 0.52f
                    val tubeH = blockW * 0.42f
                    // 炮管从底座中心沿 +x 伸出
                    fillRoundRect3D(
                        topLeft = Offset(bx, by - tubeW / 2f),
                        size = Size(tubeH, tubeW),
                        cornerRadius = tubeW / 2f,
                        base = primary,
                    )
                    // 发射口（muzzle）位于炮管末端
                    drawCircle(color = RED_LIGHT, radius = tubeW * 0.5f, center = Offset(bx + tubeH, by))
                    drawContext.canvas.restore()

                    // 瞄准指示：发射台沿手指方向画箭头，长度随力度
                    if (aiming) {
                        val lenN = 0.06f + aimPower * 0.30f
                        val tipX = LAUNCH_X + aimDir.x * lenN
                        val tipY = LAUNCH_Y + aimDir.y * lenN
                        val sx = bx
                        val sy = by
                        val tx = tipX * w
                        val ty = tipY * h
                        drawLine(color = primary, start = Offset(sx, sy), end = Offset(tx, ty), strokeWidth = 5f)
                        val a = atan2(aimDir.y, aimDir.x)
                        val ah = 0.03f * w
                        val a1 = a + 2.6f
                        val a2 = a - 2.6f
                        drawLine(color = primary, start = Offset(tx, ty), end = Offset(tx + cos(a1) * ah, ty + sin(a1) * ah), strokeWidth = 5f)
                        drawLine(color = primary, start = Offset(tx, ty), end = Offset(tx + cos(a2) * ah, ty + sin(a2) * ah), strokeWidth = 5f)
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

            Spacer(Modifier.height(Spacing.sm))
            Button(
                onClick = { launch(aimDir, aimPower) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !sessionOver && shotsUsed < SHOTS,
            ) { Text(stringResource(R.string.tools_sim_pinball_launch)) }

            if (sessionOver) {
                Spacer(Modifier.height(Spacing.sm))
                SimHintCard(stringResource(R.string.tools_sim_pinball_over))
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
