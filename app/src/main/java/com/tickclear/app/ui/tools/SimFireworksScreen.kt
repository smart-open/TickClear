package com.tickclear.app.ui.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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

/** 一次爆炸的调色板：决定这朵烟花是单色球 / 双色 / 全彩虹。 */
private data class Palette(val hues: List<Float>)

/** 随机生成调色板：约 1/3 全彩虹、1/3 单色球、1/3 双色，让每次烟花各有性格。 */
private fun randomPalette(): Palette {
    val roll = Random.nextFloat()
    return if (roll < 0.34f) {
        Palette(FIREWORK_HUES)
    } else if (roll < 0.67f) {
        val h = FIREWORK_HUES[Random.nextInt(FIREWORK_HUES.size)]
        Palette(listOf(h))
    } else {
        val a = FIREWORK_HUES[Random.nextInt(FIREWORK_HUES.size)]
        val b = FIREWORK_HUES[(FIREWORK_HUES.indexOf(a) + 5) % FIREWORK_HUES.size]
        Palette(listOf(a, b))
    }
}

/** 烟花类型：牡丹=球形炸开；柳叶=低重力慢垂长拖尾；随机=每次随机其一；
 *  连发=快速齐射（一簇密集连开）。 */
private enum class FireworkType(val labelRes: Int) {
    PEONY(R.string.tools_sim_fireworks_type_peony),
    WILLOW(R.string.tools_sim_fireworks_type_willow),
    RANDOM(R.string.tools_sim_fireworks_type_random),
    BURST(R.string.tools_sim_fireworks_type_burst),
}

/** 不同烟花类型的炸开形态参数。 */
private data class BurstShape(
    val count: Int,
    val speed: Float,
    val life: Float,
    val gravityScale: Float,
    val trailScale: Float,
)

private fun shapeFor(type: FireworkType): BurstShape = when (type) {
    FireworkType.PEONY -> BurstShape(80, 0.72f, 1.35f, 1f, 1f)
    FireworkType.WILLOW -> BurstShape(58, 0.55f, 2.4f, 0.30f, 2.8f)
    FireworkType.RANDOM -> BurstShape(80, 0.72f, 1.35f, 1f, 1f) // 仅占位，实际发射时已被替换为 PEONY/WILLOW
    FireworkType.BURST -> BurstShape(64, 0.70f, 1.25f, 1f, 1.1f) // 中等、密集齐开
}

/**
 * 单个"发射器火箭"。从屏幕底部 [startY]=1 起飞，沿抛物线飞向点击位置 [targetX]/[targetY]，
 * 到点炸开（粒子群 + 烟花声 + 触感）。[travelTime] 控制飞行速度。[palette]/[type] 决定炸开颜色与形态。
 */
private class FireworkRocket(
    val targetX: Float,
    val targetY: Float,
    val palette: Palette,
    val type: FireworkType,
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
 * 模拟烟花（V2.9++ 模拟解压，V2.11++ 重画，V2.11++ 调色板+齐射+类型切换+发射咻声）。
 * - 点击屏幕任意位置 → 从底部（y=1）发射一颗"火箭"，起飞时播「咻」声；
 * - 火箭抵达（或飞行 [travelTime] 秒）→ 炸出大粒子群 + 爆点白光闪 + 真实爆炸音 + 触感；
 * - 每朵烟花随机"单色球/双色/全彩虹"调色板；可选"牡丹/柳叶/随机/连发"类型：
 *   牡丹=球形炸开、柳叶=低重力慢垂长拖尾、随机=两者任取、连发=一簇快速齐开；
 * - 双击 = 5 发短间隔齐射（目标与飞行时长略错开），炮竹齐鸣感更强；
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
    var typeMode by remember { mutableStateOf(FireworkType.RANDOM) }
    var lastWhistleMs by remember { mutableStateOf(0L) }

    DisposableEffect(Unit) {
        onDispose { FoleySynth.stop() }
    }

    /**
     * 连射弹幕：以 [baseX]/[baseY] 为中心，短间隔连发 [shots] 颗火箭。
     * [sweep] 控制横向扫动幅度；[spreadX] 控制随机散布。
     * 每颗都带自身调色板与类型，到达即炸，形成炫酷的连续爆花。
     */
    fun launchBarrage(baseX: Float, baseY: Float, shots: Int, gapMs: Long, spreadX: Float, sweep: Float) {
        scope.launch {
            repeat(shots) { i ->
                val f = if (shots > 1) i.toFloat() / (shots - 1) else 0f
                val sx = (baseX + spreadX * (Random.nextFloat() - 0.5f) + sweep * (f - 0.5f))
                    .coerceIn(0.05f, 0.95f)
                val sy = (baseY + 0.10f * (Random.nextFloat() - 0.5f)).coerceIn(0.10f, 0.85f)
                val tt = 0.58f + Random.nextFloat() * 0.22f
                rockets = rockets + FireworkRocket(
                    targetX = sx,
                    targetY = sy,
                    palette = randomPalette(),
                    type = typeMode,
                    travelTime = tt,
                )
                val nowMs = System.currentTimeMillis()
                if (nowMs - lastWhistleMs >= 110L) {
                    FoleySynth.playLaunch(context)
                    lastWhistleMs = nowMs
                }
                delay(gapMs)
            }
        }
    }

    /**
     * 点击屏幕时调用：在画布内从底部弹一颗火箭飞向 (nx, ny)，起飞播「咻」。
     * 注意：爆炸音/触感延迟到 "抵达" 才触发，「先咻后响」才像真放烟花。
     */
    fun launchTo(nx: Float, ny: Float, travelTime: Float = 0.85f) {
        // 连发：不单发，改为连射弹幕（见 launchBarrage）
        if (typeMode == FireworkType.BURST) {
            launchBarrage(nx, ny, shots = 9, gapMs = 64L, spreadX = 0.14f, sweep = 0.0f)
            return
        }
        val concrete = if (typeMode == FireworkType.RANDOM)
            if (Random.nextBoolean()) FireworkType.PEONY else FireworkType.WILLOW
        else typeMode
        rockets = rockets + FireworkRocket(
            targetX = nx.coerceIn(0.05f, 0.95f),
            targetY = ny.coerceIn(0.10f, 0.85f),
            palette = randomPalette(),
            type = concrete,
            travelTime = travelTime,
        )
        // 发射「咻」：节流 ~110ms，避免齐射时多重口哨叠成噪声。
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastWhistleMs >= 110L) {
            FoleySynth.playLaunch(context)
            lastWhistleMs = nowMs
        }
    }

    /** 当火箭"到达"（飞行时间用尽或超越目标）时调用：炸粒子 + 白光 + 音 + 触感。 */
    fun burstAt(nx: Float, ny: Float, palette: Palette, type: FireworkType) {
        // 按本发火箭的类型调色板与形态炸开（单色球/双色/全彩虹 × 牡丹/柳叶），更壮观。
        val s = shapeFor(type)
        particles = particles + burst(nx, ny, s.count, s.speed, s.life, palette.hues, 5f, s.gravityScale, s.trailScale)
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
                        burstAt(r.targetX, r.targetY, r.palette, r.type)
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

            // 烟花类型切换：牡丹（球形）/柳叶（慢垂长拖尾）/随机/连发（快速齐射）。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.tools_sim_fireworks_type_label),
                    style = MaterialTheme.typography.labelMedium,
                )
                listOf(
                    FireworkType.PEONY,
                    FireworkType.WILLOW,
                    FireworkType.RANDOM,
                    FireworkType.BURST,
                ).forEach { t ->
                    FilterChip(
                        selected = typeMode == t,
                        onClick = { typeMode = t },
                        label = { Text(stringResource(t.labelRes)) },
                    )
                }
            }
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
                                    // 齐射：5 发短间隔、目标与飞行时长略错开，炮竹齐鸣更密更自然。
                                    repeat(5) { i ->
                                        val dx = (Random.nextFloat() - 0.5f) * 0.16f
                                        val dy = (Random.nextFloat() - 0.5f) * 0.12f
                                        launchTo(
                                            (nx + dx).coerceIn(0.05f, 0.95f),
                                            (ny + dy).coerceIn(0.10f, 0.85f),
                                            0.78f + Random.nextFloat() * 0.18f,
                                        )
                                        delay(60)
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
                        val trailHue = r.palette.hues.first()

                        // 拉丝：火箭身后一道明亮长拖尾（向上的相反方向），越接近顶端越亮。
                        val tailLen = h * 0.14f
                        val tailEnd = Offset(cx, cy + tailLen)
                        drawLine(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    simColor(trailHue, 0.0f),
                                    simColor(trailHue, 1f),
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
                                    simColor(trailHue, 1f),
                                    simColor(trailHue, 0.6f),
                                    simColor(trailHue, 0f),
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

                        // 拖尾：沿速度反方向画一道渐隐短线，速度越快拖尾越长；柳叶型拖尾倍率更大。
                        val speedPx = kotlin.math.hypot(pt.vx * w, pt.vy * h)
                        if (speedPx > 6f && a > 0.05f) {
                            val tail = 0.045f * pt.trailScale
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
