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
import androidx.compose.ui.graphics.Color
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

/** 全部色相铺满彩虹，单次爆炸即「五颜六色」。 */
private val FIREWORK_HUES = listOf(0f, 25f, 50f, 75f, 110f, 140f, 175f, 200f, 230f, 265f, 295f, 320f, 345f)

/**
 * 单个"发射器火箭"。从屏幕底部 [startY]=1 起飞，沿抛物线飞向点击位置 [targetX]/[targetY]，
 * 到点炸开（粒子群 + 烟花声 + 触感）。[travelTime] 控制飞行速度。
 */
private class FireworkRocket(
    val targetX: Float,
    val targetY: Float,
    val hue: Float,
    val travelTime: Float,
) {
    var t: Float = 0f // 已飞行时间（秒）
}

/** 爆炸瞬间的白光闪：快速膨胀并淡出，营造"炸开那一下"的刺眼感。 */
private class BurstFlash(
    val x: Float,
    val y: Float,
    var life: Float,
    val maxLife: Float,
)

/**
 * 模拟烟花（V2.9++ 模拟解压，V2.11++ 重画大改）。
 * - 点击屏幕任意位置 → 从底部（y=1）发射一颗"火箭"，沿抛物线飞向点击位置；
 * - 火箭抵达（或飞行 [travelTime] 秒）→ 炸出多色相（五颜六色）大粒子群 + 爆点白光闪 + 真实爆炸音 + 触感；
 * - 双击 = 3 发短间隔连发（每发独立飞行），炮竹齐鸣感；
 * - 粒子用径向光晕 + 速度拖尾 + 中心亮芯 + twinkle 闪烁，呈现自然散开与坠落；
 * - 爆炸声优先播放真实录音 firework_boom（Freesound #624413，CC0），缺失回退合成音。
 * 纯 Canvas + AudioTrack/MediaPlayer，零新依赖。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimFireworksScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var particles by remember { mutableStateOf(emptyList<SimParticle>()) }
    var rockets by remember { mutableStateOf(emptyList<FireworkRocket>()) }
    var flashes by remember { mutableStateOf(emptyList<BurstFlash>()) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    DisposableEffect(Unit) {
        onDispose { FoleySynth.stop() }
    }

    /**
     * 点击屏幕时调用：在画布内从底部弹一颗火箭飞向 (nx, ny)，
     * 注意：音效/触感延迟到 "抵达" 才触发，「先静后响」才像真放烟花。
     */
    fun launchTo(nx: Float, ny: Float, travelTime: Float = 0.85f) {
        val hue = FIREWORK_HUES[Random.nextInt(FIREWORK_HUES.size)]
        rockets = rockets + FireworkRocket(
            targetX = nx.coerceIn(0.05f, 0.95f),
            targetY = ny.coerceIn(0.10f, 0.85f),
            hue = hue,
            travelTime = travelTime,
        )
    }

    /** 当火箭"到达"（飞行时间用尽或超越目标）时调用：炸粒子 + 白光 + 音 + 触感。 */
    fun burstAt(nx: Float, ny: Float, hue: Float) {
        // 多色相（整条彩虹）一次性炸开 = 五颜六色；粒子数翻倍更壮观。
        particles = particles + burst(nx, ny, 80, 0.72f, 1.35f, FIREWORK_HUES, 5f)
        // 爆点白光闪。
        flashes = flashes + BurstFlash(nx, ny, 0.24f, 0.24f)
        // 真实爆炸音优先；缺失回退合成。
        FoleySynth.playFirework(context)
        Haptic.vibrate(context, 25)
    }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            val now = withFrameMillis { it }
            val dt = if (last == 0L) 0.016f else ((now - last) / 1000f).coerceAtMost(0.05f)
            last = now

            // 推进火箭：到点即引爆，未到则继续上升。
            if (rockets.isNotEmpty()) {
                val newRockets = ArrayList<FireworkRocket>(rockets.size)
                for (r in rockets) {
                    r.t += dt
                    if (r.t >= r.travelTime) {
                        burstAt(r.targetX, r.targetY, r.hue)
                    } else {
                        newRockets.add(r)
                    }
                }
                rockets = newRockets
            }
            if (particles.isNotEmpty()) particles = stepParticles(particles, dt)
            // 推进白光闪。
            if (flashes.isNotEmpty()) {
                val kept = ArrayList<BurstFlash>(flashes.size)
                for (f in flashes) {
                    f.life -= dt
                    if (f.life > 0f) kept.add(f)
                }
                flashes = kept
            }
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
                                launchTo(nx, ny)
                            },
                            onDoubleTap = { offset ->
                                val nx = if (canvasSize.width > 0) offset.x / canvasSize.width else 0.5f
                                val ny = if (canvasSize.height > 0) offset.y / canvasSize.height else 0.5f
                                scope.launch {
                                    repeat(3) { i ->
                                        val dx = (Random.nextFloat() - 0.5f) * 0.10f
                                        val dy = (Random.nextFloat() - 0.5f) * 0.10f
                                        launchTo((nx + dx).coerceIn(0.05f, 0.95f), (ny + dy).coerceIn(0.10f, 0.85f))
                                        delay(110)
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

                    // 1) 绘制飞行中的火箭：抛物线轨迹 + 高速向上 + 又长又亮的拖尾。
                    for (r in rockets) {
                        val f = (r.t / r.travelTime).coerceIn(0f, 1.05f)
                        // 抛物线 x：起点 targetX 左侧 5% 处（带上推偏置），终点严格 = targetX
                        val sx = r.targetX - 0.05f * (1f - f)
                        val x = sx + (r.targetX - sx) * f
                        // y：起 1.0 终 targetY，中间按 (1 - (1 - f)²) 缓速起步，便于"看得到发射"
                        val baseY = 1f + (r.targetY - 1f) * f
                        val arc = -0.04f * (1f - f) * f // 抛物线凸起分量（向上）
                        val y = (baseY + arc).coerceIn(0f, 1f)

                        val cx = x * w
                        val cy = y * h

                        // 拉丝：火箭身后一道明亮长拖尾（向上的相反方向），越接近顶端越亮。
                        val tailLen = h * 0.14f
                        val tailEnd = Offset(cx, cy + tailLen)
                        drawLine(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    simColor(r.hue, 0.0f),
                                    simColor(r.hue, 1f),
                                ),
                                startY = cy,
                                endY = tailEnd.y,
                            ),
                            start = Offset(cx, cy),
                            end = tailEnd,
                            strokeWidth = 5f,
                            cap = StrokeCap.Round,
                        )

                        // 火箭头部：亮芯 + 径向光晕 + 内核白点
                        val headR = 13f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    simColor(r.hue, 1f),
                                    simColor(r.hue, 0.6f),
                                    simColor(r.hue, 0f),
                                ),
                                center = Offset(cx, cy),
                                radius = headR * 2.2f,
                            ),
                            radius = headR * 2.2f,
                            center = Offset(cx, cy),
                        )
                        // 内部小白芯
                        drawCircle(
                            color = Color.White.copy(alpha = 0.95f),
                            radius = headR * 0.42f,
                            center = Offset(cx, cy),
                        )
                    }

                    // 2) 白光闪：在爆点快速膨胀并淡出，模拟"炸开那一下"的刺眼强光。
                    for (fl in flashes) {
                        val fa = (fl.life / fl.maxLife).coerceIn(0f, 1f)
                        val fr = (1f - fa) * 70f + 28f
                        val fx = fl.x * w
                        val fy = fl.y * h
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = fa),
                                    Color.White.copy(alpha = fa * 0.45f),
                                    Color.White.copy(alpha = 0f),
                                ),
                                center = Offset(fx, fy),
                                radius = fr,
                            ),
                            radius = fr,
                            center = Offset(fx, fy),
                        )
                    }

                    // 3) 绘制爆炸粒子群（拖尾 + 光晕 + 亮芯 + twinkle 闪烁）
                    for (pt in particles) {
                        val base = (pt.life / pt.maxLife).coerceIn(0f, 1f)
                        // twinkle：轻微闪烁，像真实火花在高空中明灭。
                        val twinkle = 0.7f + 0.3f * kotlin.math.sin(pt.life * 42f + pt.x * 60f)
                        val a = base * twinkle
                        val cx = pt.x * w
                        val cy = pt.y * h

                        // 拖尾：沿速度反方向画一道渐隐短线，速度越快拖尾越长。
                        val speedPx = kotlin.math.hypot(pt.vx * w, pt.vy * h)
                        if (speedPx > 6f && a > 0.05f) {
                            val tail = 0.045f
                            val tx = cx - pt.vx * w * tail
                            val ty = cy - pt.vy * h * tail
                            drawLine(
                                color = simColor(pt.hue, a * 0.6f),
                                start = Offset(tx, ty),
                                end = Offset(cx, cy),
                                strokeWidth = pt.radius * 0.8f,
                                cap = StrokeCap.Round,
                            )
                        }

                        // 径向光晕：从亮到透明的自然拖尾渐隐（替代原先的平涂椭圆）。
                        val glowR = pt.radius * 3.6f
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
