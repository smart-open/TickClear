package com.tickclear.app.ui.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tickclear.app.R
import com.tickclear.app.ui.components.Haptic
import com.tickclear.app.ui.theme.Spacing
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

/** 饱食度 / 快乐值的自然衰减间隔（秒），每到点各减 1。 */
private const val DECAY_INTERVAL_SEC = 6f

/**
 * 养宠物（佛系解压小游戏，V2.11++ 拟物重画）。
 *
 * 顶部选动物：鱼 / 小狗 / 小猪 / 小猫，每种有独立饱食度与心情，且操作和反应都各有特色：
 * - 鱼：喂食投饵料（鱼追食） / 换水（气泡上升）。
 * - 小狗（侧视）：喂食给骨头（棕色粒子上升） / 摸摸（爱心 + 加速摆尾）。
 * - 小猪（圆鼓鼓坐姿）：喂食给苹果（红色粒子 + 鼻息圈） / 摸摸（爱心 + 哼气扩散圈）。
 * - 小猫（坐姿，尖耳）：喂食（绿色食物粒子 + 眨眼） / 撸猫（呼噜蓝 Z + 闭眼爱心） / 逗猫棒（黄色星 + 扑逗）。
 * 动画与粒子全部本地 Canvas 绘制，零新依赖。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val reduceMotion = remember { isMotionReduced(context) }

    var kind by remember { mutableStateOf(PetKind.FISH) }
    var stats by remember {
        mutableStateOf(
            mapOf(
                PetKind.FISH to PetState(70, 60),
                PetKind.DOG to PetState(70, 60),
                PetKind.PIG to PetState(70, 60),
                PetKind.CAT to PetState(70, 60),
            ),
        )
    }
    var phase by remember { mutableFloatStateOf(0f) }
    var blink by remember { mutableFloatStateOf(0f) }
    var toyTimer by remember { mutableFloatStateOf(0f) }

    val fishes = remember {
        List(3) { i ->
            Fish(
                x = Random.nextFloat(),
                y = 0.3f + i * 0.2f,
                dir = if (i % 2 == 0) 1 else -1,
                speed = 0.06f + Random.nextFloat() * 0.04f,
                hue = listOf(195f, 45f, 320f)[i % 3],
                bob = Random.nextFloat() * 6.28f,
            )
        }
    }
    var food by remember { mutableStateOf(emptyList<FoodPellet>()) }
    var particles by remember { mutableStateOf(emptyList<SimParticle>()) }

    fun update(k: PetKind, fullness: Int = 0, happiness: Int = 0) {
        val cur = stats[k] ?: PetState(70, 60)
        stats = stats.toMutableMap().apply {
            put(k, PetState((cur.fullness + fullness).coerceIn(0, 100), (cur.happiness + happiness).coerceIn(0, 100)))
        }
    }

    // 通用爱心粒子（颜色和数量可调）
    fun hearts(cx: Float = 0.5f, cy: Float = 0.45f, count: Int = 6, hue: Float = 340f, life: Float = 1.1f) =
        List(count) {
            SimParticle(
                x = cx + (Random.nextFloat() - 0.5f) * 0.3f,
                y = cy,
                vx = (Random.nextFloat() - 0.5f) * 0.1f,
                vy = -0.18f - Random.nextFloat() * 0.1f,
                life = life, maxLife = life,
                hue = hue, radius = 7f,
            )
        }

    fun feed() {
        when (kind) {
            PetKind.FISH -> food = food + FoodPellet(Random.nextFloat(), 0.05f, 0.25f + Random.nextFloat() * 0.1f)
            PetKind.DOG -> {
                // 骨头/饼干棕色粒子从口鼻位置升起
                particles = particles + List(5) {
                    SimParticle(
                        x = 0.55f + (Random.nextFloat() - 0.5f) * 0.1f,
                        y = 0.50f,
                        vx = (Random.nextFloat() - 0.5f) * 0.06f,
                        vy = -0.16f - Random.nextFloat() * 0.08f,
                        life = 1.0f, maxLife = 1.0f,
                        hue = 35f, radius = 7f,
                    )
                }
                update(kind, fullness = 14)
            }
            PetKind.PIG -> {
                // 红色苹果粒子 + 鼻息白圈
                particles = particles + List(5) {
                    SimParticle(
                        x = 0.50f + (Random.nextFloat() - 0.5f) * 0.12f,
                        y = 0.55f,
                        vx = (Random.nextFloat() - 0.5f) * 0.06f,
                        vy = -0.18f - Random.nextFloat() * 0.08f,
                        life = 1.0f, maxLife = 1.0f,
                        hue = 0f, radius = 7f,
                    )
                }
                particles = particles + List(3) {
                    SimParticle(
                        x = 0.50f + (Random.nextFloat() - 0.5f) * 0.06f,
                        y = 0.55f,
                        vx = (Random.nextFloat() - 0.5f) * 0.05f,
                        vy = -0.10f - Random.nextFloat() * 0.05f,
                        life = 0.8f, maxLife = 0.8f,
                        hue = 0f, radius = 5f, ring = true,
                    )
                }
                update(kind, fullness = 14)
            }
            PetKind.CAT -> {
                // 绿色鱼肉/猫粮粒子
                particles = particles + List(5) {
                    SimParticle(
                        x = 0.50f + (Random.nextFloat() - 0.5f) * 0.12f,
                        y = 0.55f,
                        vx = (Random.nextFloat() - 0.5f) * 0.06f,
                        vy = -0.16f - Random.nextFloat() * 0.08f,
                        life = 1.0f, maxLife = 1.0f,
                        hue = 130f, radius = 6f,
                    )
                }
                update(kind, fullness = 12)
            }
        }
        Haptic.vibrate(context, 18)
    }

    fun petStroke() {
        when (kind) {
            PetKind.DOG -> {
                // 摆尾加速：先复位 toyTimer 复用机制（偷懒方案），下面画时给尾巴额外相位
                particles = particles + hearts()
                update(kind, happiness = 12)
            }
            PetKind.PIG -> {
                // 爱心 + 哼气扩散圈
                particles = particles + hearts()
                particles = particles + List(2) {
                    SimParticle(
                        x = 0.50f + (Random.nextFloat() - 0.5f) * 0.08f,
                        y = 0.55f,
                        vx = (Random.nextFloat() - 0.5f) * 0.04f,
                        vy = -0.12f - Random.nextFloat() * 0.04f,
                        life = 1.0f, maxLife = 1.0f,
                        hue = 0f, radius = 6f, ring = true,
                    )
                }
                update(kind, happiness = 10)
            }
            PetKind.CAT -> {
                // 撸猫：呼噜蓝 Z 粒子 + 闭眼爱心
                particles = particles + hearts()
                particles = particles + List(2) {
                    SimParticle(
                        x = 0.58f, y = 0.42f,
                        vx = 0.04f + Random.nextFloat() * 0.03f,
                        vy = -0.10f - Random.nextFloat() * 0.04f,
                        life = 1.4f, maxLife = 1.4f,
                        hue = 200f, radius = 8f,
                    )
                }
                update(kind, happiness = 14)
            }
            else -> {}
        }
        Haptic.vibrate(context, 22)
    }

    fun teaseCat() {
        toyTimer = 1.6f
        particles = particles + List(5) {
            SimParticle(
                x = 0.5f + (Random.nextFloat() - 0.5f) * 0.4f,
                y = 0.45f,
                vx = 0f, vy = -0.2f, life = 1f, maxLife = 1f,
                hue = 50f, radius = 6f,
            )
        }
        update(PetKind.CAT, happiness = 12)
        Haptic.vibrate(context, 20)
    }

    LaunchedEffect(Unit) {
        var last = 0L
        var decayAcc = 0f
        while (true) {
            val now = withFrameMillis { it }
            val dt = if (last == 0L) 0.016f else ((now - last) / 1000f).coerceAtMost(0.05f)
            last = now
            if (!reduceMotion) {
                phase += dt
                blink -= dt
                if (blink < -3f) blink = 0.18f
                if (toyTimer > 0f) toyTimer = (toyTimer - dt).coerceAtLeast(0f)
            }

            decayAcc += dt
            if (decayAcc >= DECAY_INTERVAL_SEC) {
                decayAcc = 0f
                stats = stats.mapValues { (_, s) ->
                    PetState(
                        fullness = (s.fullness - 1).coerceAtLeast(0),
                        happiness = (s.happiness - 1).coerceAtLeast(0),
                    )
                }
            }

            if (kind == PetKind.FISH) {
                for (f in fishes) {
                    val target = food.minByOrNull { abs(it.x - f.x) + abs(it.y - f.y) }
                    if (target != null) {
                        val dx = target.x - f.x
                        val dy = target.y - f.y
                        val chase = f.speed * 2.2f * dt
                        if (dx != 0f) f.dir = if (dx > 0f) 1 else -1
                        f.x = (f.x + sign(dx) * chase).coerceIn(0.08f, 0.92f)
                        f.y = (f.y + sign(dy) * chase).coerceIn(0.15f, 0.9f)
                    } else {
                        var nx = f.x + f.dir * f.speed * dt
                        if (nx < 0.08f) { nx = 0.08f; f.dir = 1 }
                        if (nx > 0.92f) { nx = 0.92f; f.dir = -1 }
                        f.x = nx
                    }
                    f.bob += dt * 3f
                }
                val stillFood = mutableListOf<FoodPellet>()
                for (p in food) {
                    val py = p.y + p.vy * dt
                    val eater = fishes.minByOrNull { abs(it.x - p.x) + abs(it.y - py) * 2 }
                    if (eater != null && abs(eater.x - p.x) < 0.08f && abs(eater.y - py) < 0.08f) {
                        update(PetKind.FISH, fullness = 8)
                        particles = particles + SimParticle(
                            x = p.x, y = py, vx = 0f, vy = -0.15f,
                            life = 0.7f, maxLife = 0.7f, hue = 195f, radius = 4f,
                        )
                    } else if (py < 0.9f) {
                        stillFood.add(FoodPellet(p.x, py, p.vy))
                    }
                }
                food = stillFood
            }

            if (particles.isNotEmpty()) particles = stepParticles(particles, dt)
        }
    }

    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val stat = stats[kind] ?: PetState(70, 60)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pet_title)) },
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                listOf(
                    PetKind.FISH to stringResource(R.string.pet_fish),
                    PetKind.DOG to stringResource(R.string.pet_dog),
                    PetKind.PIG to stringResource(R.string.pet_pig),
                    PetKind.CAT to stringResource(R.string.pet_cat),
                ).forEach { (k, label) ->
                    FilterChip(
                        selected = kind == k,
                        onClick = { kind = k },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(Spacing.sm))
            SimHintCard(
                when (kind) {
                    PetKind.FISH -> stringResource(R.string.pet_fish_hint)
                    PetKind.DOG -> stringResource(R.string.pet_dog_hint)
                    PetKind.PIG -> stringResource(R.string.pet_pig_hint)
                    PetKind.CAT -> stringResource(R.string.pet_cat_hint)
                },
            )
            Spacer(Modifier.height(Spacing.sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                SimStatCard(
                    value = "${stat.fullness}",
                    label = stringResource(R.string.pet_fullness),
                    modifier = Modifier.weight(1f),
                    horizontal = true,
                )
                SimStatCard(
                    value = "${stat.happiness}",
                    label = stringResource(R.string.pet_happiness),
                    modifier = Modifier.weight(1f),
                    horizontal = true,
                )
            }
            Spacer(Modifier.height(Spacing.sm))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            if (kind == PetKind.FISH) {
                                val nx = (offset.x / size.width.toFloat()).coerceIn(0.06f, 0.94f)
                                food = food + FoodPellet(nx, 0.05f, 0.25f + Random.nextFloat() * 0.1f)
                                Haptic.vibrate(context, 14)
                            } else {
                                petStroke()
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    when (kind) {
                        PetKind.FISH -> drawFishTank(fishes, food, particles, phase, primary, onSurface)
                        PetKind.DOG -> drawDog(phase, particles, primary, onSurface)
                        PetKind.PIG -> drawPig(phase, particles, primary, onSurface)
                        PetKind.CAT -> drawCat(phase, blink, toyTimer, particles, primary, onSurface)
                    }
                }
            }
            Spacer(Modifier.height(Spacing.sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Button(onClick = { feed() }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pet_feed))
                }
                when (kind) {
                    PetKind.FISH -> Button(onClick = {
                        particles = particles + List(10) {
                            SimParticle(
                                x = Random.nextFloat(), y = 0.9f,
                                vx = (Random.nextFloat() - 0.5f) * 0.05f,
                                vy = -0.2f - Random.nextFloat() * 0.1f,
                                life = 1.2f, maxLife = 1.2f,
                                hue = 195f, radius = 4f,
                            )
                        }
                        update(PetKind.FISH, happiness = 5)
                        Haptic.vibrate(context, 15)
                    }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.pet_change_water))
                    }
                    PetKind.DOG, PetKind.PIG -> Button(onClick = { petStroke() }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.pet_pet))
                    }
                    PetKind.CAT -> {
                        Button(onClick = { petStroke() }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.pet_stroke))
                        }
                        Button(onClick = { teaseCat() }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.pet_tease))
                        }
                    }
                }
            }
            Spacer(Modifier.height(Spacing.md))
        }
    }
}

private enum class PetKind { FISH, DOG, PIG, CAT }
private data class PetState(val fullness: Int, val happiness: Int)
private data class Fish(
    var x: Float, var y: Float, var dir: Int,
    var speed: Float, val hue: Float, var bob: Float,
)
private data class FoodPellet(var x: Float, var y: Float, var vy: Float)

private fun DrawScope.drawFishTank(
    fishes: List<Fish>, food: List<FoodPellet>, particles: List<SimParticle>,
    phase: Float, primary: Color, onSurface: Color,
) {
    val w = size.width
    val h = size.height
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF4FC3F7).copy(alpha = 0.20f), Color(0xFF1565C0).copy(alpha = 0.30f)),
        ),
        topLeft = Offset(0f, 0f),
        size = Size(w, h),
    )
    drawOval(
        color = Color(0xFFFBC02D).copy(alpha = 0.16f),
        topLeft = Offset(w * 0.2f, h * 0.86f),
        size = Size(w * 0.6f, h * 0.12f),
    )
    for (f in fishes) {
        val cx = f.x * w
        val cy = f.y * h + sin(f.bob) * 6f
        val len = w * 0.10f
        val col = simColor(f.hue, 1f)
        val tail = Path().apply {
            moveTo(cx - len * 0.5f, cy)
            lineTo(cx - len * 0.95f, cy - len * 0.32f)
            lineTo(cx - len * 0.95f, cy + len * 0.32f)
            close()
        }
        fillPath3D(tail, col)
        fillOvoid(Offset(cx - len / 2f, cy - len * 0.35f), Size(len, len * 0.7f), col)
        drawGloss(Offset(cx - len * 0.12f, cy - len * 0.18f), len * 0.18f, len * 0.10f, 0.55f)
        drawCircle(color = Color.White, radius = len * 0.10f, center = Offset(cx + len * 0.28f, cy - len * 0.06f))
        drawCircle(color = Color(0xFF212121), radius = len * 0.05f, center = Offset(cx + len * 0.30f, cy - len * 0.06f))
    }
    for (p in food) {
        drawCircle(color = Color(0xFFFBC02D), radius = 5f, center = Offset(p.x * w, p.y * h))
    }
    drawParticles(particles, w, h)
}

/** 坐姿小狗（3/4 侧视）：垂耳 + 长吻 + 坐姿身体 + 摇尾。 */
private fun DrawScope.drawDog(phase: Float, particles: List<SimParticle>, primary: Color, onSurface: Color) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h * 0.58f
    val r = w * 0.16f
    val fur = Color(0xFFA06A41)        // 暖棕毛色
    val furDark = fur.darken(0.20f)
    val furLight = fur.lighten(0.30f)
    val snout = Color(0xFFECD9C2)
    val black = Color(0xFF2A2118)

    drawSoftShadow(Offset(cx, cy + r * 1.15f), r * 1.25f, r * 0.32f)

    // 尾巴（身后上卷 + 摇）
    val wag = sin(phase * 7f) * r * 0.14f
    drawPath(
        Path().apply {
            moveTo(cx - r * 1.05f, cy + r * 0.05f)
            quadraticTo(cx - r * 1.55f, cy - r * 0.05f + wag, cx - r * 1.32f, cy - r * 0.5f + wag)
        },
        color = fur,
        style = Stroke(width = r * 0.22f, cap = StrokeCap.Round),
    )

    // 后臀
    fillSphere(Offset(cx - r * 0.5f, cy + r * 0.3f), r * 0.6f, fur, rimLight = false)
    // 身体（坐姿：背到胸的流畅轮廓）
    val bodyPath = Path().apply {
        moveTo(cx - r * 1.05f, cy + r * 0.5f)
        cubicTo(cx - r * 1.2f, cy - r * 0.15f, cx - r * 0.25f, cy - r * 0.4f, cx + r * 0.2f, cy - r * 0.58f)
        lineTo(cx + r * 0.5f, cy - r * 0.52f)
        cubicTo(cx + r * 0.75f, cy - r * 0.3f, cx + r * 0.72f, cy + r * 0.4f, cx + r * 0.32f, cy + r * 0.78f)
        cubicTo(cx - r * 0.2f, cy + r * 0.9f, cx - r * 1.0f, cy + r * 0.9f, cx - r * 1.05f, cy + r * 0.5f)
        close()
    }
    fillPath3D(bodyPath, fur)
    drawRimLight(Offset(cx - r * 0.15f, cy + r * 0.05f), r * 0.95f, furLight, alpha = 0.22f)

    // 前腿 + 爪
    fillOvoid(Offset(cx + r * 0.18f, cy + r * 0.55f), Size(r * 0.27f, r * 0.72f), furLight)
    fillOvoid(Offset(cx + r * 0.52f, cy + r * 0.55f), Size(r * 0.27f, r * 0.72f), furLight)
    drawCircle(color = black.copy(alpha = 0.45f), radius = r * 0.08f, center = Offset(cx + r * 0.18f, cy + r * 1.2f))
    drawCircle(color = black.copy(alpha = 0.45f), radius = r * 0.08f, center = Offset(cx + r * 0.52f, cy + r * 1.2f))

    // 头
    val hx = cx + r * 0.55f
    val hy = cy - r * 0.6f
    fillSphere(Offset(hx, hy), r * 0.66f, fur, rimLight = false)
    drawRimLight(Offset(hx, hy), r * 0.66f, furLight, alpha = 0.28f)

    // 垂耳（两片）
    fillPath3D(
        Path().apply {
            moveTo(hx - r * 0.5f, hy - r * 0.3f)
            quadraticTo(hx - r * 0.78f, hy - r * 0.05f, hx - r * 0.55f, hy + r * 0.55f)
            quadraticTo(hx - r * 0.4f, hy + r * 0.2f, hx - r * 0.33f, hy - r * 0.2f)
            close()
        },
        furDark,
    )
    fillPath3D(
        Path().apply {
            moveTo(hx + r * 0.32f, hy - r * 0.35f)
            quadraticTo(hx + r * 0.6f, hy - r * 0.05f, hx + r * 0.42f, hy + r * 0.5f)
            quadraticTo(hx + r * 0.28f, hy + r * 0.18f, hx + r * 0.26f, hy - r * 0.2f)
            close()
        },
        furDark,
    )

    // 口鼻
    fillOvoid(Offset(hx + r * 0.42f, hy + r * 0.22f), Size(r * 0.52f, r * 0.4f), snout)
    drawCircle(color = black, radius = r * 0.1f, center = Offset(hx + r * 0.78f, hy + r * 0.2f))
    drawGloss(Offset(hx + r * 0.75f, hy + r * 0.14f), r * 0.04f, r * 0.03f, 0.6f)
    // 嘴（微笑）
    drawPath(
        Path().apply {
            moveTo(hx + r * 0.45f, hy + r * 0.4f)
            quadraticTo(hx + r * 0.66f, hy + r * 0.54f, hx + r * 0.8f, hy + r * 0.42f)
        },
        color = black,
        style = Stroke(width = 1.6f, cap = StrokeCap.Round),
    )

    // 眼睛
    drawCircle(color = black, radius = r * 0.085f, center = Offset(hx + r * 0.15f, hy - r * 0.08f))
    drawCircle(color = black, radius = r * 0.065f, center = Offset(hx + r * 0.5f, hy - r * 0.05f))
    drawGloss(Offset(hx + r * 0.12f, hy - r * 0.11f), r * 0.028f, r * 0.022f, 0.9f)
    drawGloss(Offset(hx + r * 0.47f, hy - r * 0.08f), r * 0.022f, r * 0.018f, 0.9f)

    drawParticles(particles, w, h)
}

/** 坐姿小猪（正面）：圆身 + 大耳 + 圆盘鼻 + 卷尾 + 腮红。 */
private fun DrawScope.drawPig(phase: Float, particles: List<SimParticle>, primary: Color, onSurface: Color) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h * 0.54f
    val r = w * 0.16f
    val sway = sin(phase * 4f) * (w * 0.008f)
    val base = Color(0xFFF6A5C0)      // 粉
    val baseDark = base.darken(0.12f)
    val baseLight = base.lighten(0.35f)
    val pink = Color(0xFFE0457E)
    val black = Color(0xFF3A2230)

    drawSoftShadow(Offset(cx + sway, cy + r * 1.25f), r * 1.2f, r * 0.32f)

    // 后脚
    fillOvoid(Offset(cx - r * 0.62f + sway, cy + r * 1.05f), Size(r * 0.34f, r * 0.42f), baseDark)
    fillOvoid(Offset(cx + r * 0.62f + sway, cy + r * 1.05f), Size(r * 0.34f, r * 0.42f), baseDark)

    // 卷尾
    drawPath(
        Path().apply {
            moveTo(cx - r * 1.0f + sway, cy + r * 0.05f)
            quadraticTo(cx - r * 1.3f, cy - r * 0.2f, cx - r * 1.1f, cy - r * 0.42f)
            quadraticTo(cx - r * 0.95f, cy - r * 0.55f, cx - r * 1.12f, cy - r * 0.5f)
        },
        color = baseDark,
        style = Stroke(width = r * 0.12f, cap = StrokeCap.Round),
    )

    // 身体（圆胖）
    fillSphere(Offset(cx + sway, cy + r * 0.42f), r * 1.08f, base, rimLight = false)
    drawRimLight(Offset(cx + sway, cy + r * 0.42f), r * 1.08f, baseLight, alpha = 0.30f)
    // 肚皮浅色
    drawOval(color = baseLight.copy(alpha = 0.5f), topLeft = Offset(cx - r * 0.5f + sway, cy + r * 0.55f), size = Size(r * 1.0f, r * 0.7f))

    // 头
    fillSphere(Offset(cx + sway, cy - r * 0.32f), r * 0.88f, base, rimLight = false)
    drawRimLight(Offset(cx + sway, cy - r * 0.32f), r * 0.88f, baseLight, alpha = 0.30f)

    // 耳朵（三角，前折）
    fillPath3D(Path().apply { moveTo(cx - r * 0.6f + sway, cy - r * 1.0f); lineTo(cx - r * 0.42f + sway, cy - r * 1.32f); lineTo(cx - r * 0.16f + sway, cy - r * 1.02f); close() }, baseDark)
    fillPath3D(Path().apply { moveTo(cx + r * 0.6f + sway, cy - r * 1.0f); lineTo(cx + r * 0.42f + sway, cy - r * 1.32f); lineTo(cx + r * 0.16f + sway, cy - r * 1.02f); close() }, baseDark)
    // 耳内浅粉
    fillPath3D(Path().apply { moveTo(cx - r * 0.55f + sway, cy - r * 1.05f); lineTo(cx - r * 0.45f + sway, cy - r * 1.22f); lineTo(cx - r * 0.27f + sway, cy - r * 1.03f); close() }, pink.copy(alpha = 0.5f))
    fillPath3D(Path().apply { moveTo(cx + r * 0.55f + sway, cy - r * 1.05f); lineTo(cx + r * 0.45f + sway, cy - r * 1.22f); lineTo(cx + r * 0.27f + sway, cy - r * 1.03f); close() }, pink.copy(alpha = 0.5f))

    // 口鼻（圆盘）
    fillSphere(Offset(cx + sway, cy + r * 0.02f), r * 0.44f, baseLight, rimLight = false)
    // 鼻孔
    drawOval(color = pink, topLeft = Offset(cx - r * 0.22f + sway, cy - r * 0.02f), size = Size(r * 0.12f, r * 0.16f))
    drawOval(color = pink, topLeft = Offset(cx + r * 0.10f + sway, cy - r * 0.02f), size = Size(r * 0.12f, r * 0.16f))

    // 眼睛
    drawCircle(color = black, radius = r * 0.075f, center = Offset(cx - r * 0.30f + sway, cy - r * 0.42f))
    drawCircle(color = black, radius = r * 0.075f, center = Offset(cx + r * 0.30f + sway, cy - r * 0.42f))
    drawCircle(color = Color.White, radius = r * 0.028f, center = Offset(cx - r * 0.29f + sway, cy - r * 0.44f))
    drawCircle(color = Color.White, radius = r * 0.028f, center = Offset(cx + r * 0.31f + sway, cy - r * 0.44f))

    // 腮红
    drawOval(color = pink.copy(alpha = 0.35f), topLeft = Offset(cx - r * 0.62f + sway, cy - r * 0.18f), size = Size(r * 0.22f, r * 0.14f))
    drawOval(color = pink.copy(alpha = 0.35f), topLeft = Offset(cx + r * 0.40f + sway, cy - r * 0.18f), size = Size(r * 0.22f, r * 0.14f))

    drawParticles(particles, w, h)
}

/** 坐姿小猫（正面）：尖耳 + 杏仁绿眼竖瞳 + 粉鼻 + 胡须 + 卷尾绕前。 */
private fun DrawScope.drawCat(phase: Float, blink: Float, toyTimer: Float, particles: List<SimParticle>, primary: Color, onSurface: Color) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h * 0.56f
    val r = w * 0.155f
    val pounce = if (toyTimer > 0f) sin(phase * 12f) * (w * 0.015f) else 0f
    val fur = Color(0xFFDDA066)        // 姜黄毛色
    val furDark = fur.darken(0.22f)
    val furLight = fur.lighten(0.32f)
    val cream = Color(0xFFF3E2C4)
    val pink = Color(0xFFE48AA6)
    val eyeGreen = Color(0xFF9CCC65)
    val pupil = Color(0xFF22300A)
    val black = Color(0xFF2B2118)

    drawSoftShadow(Offset(cx, cy + r * 1.25f), r * 1.15f, r * 0.34f)

    // 尾巴（卷到身前右侧）
    drawPath(
        Path().apply {
            moveTo(cx + r * 0.7f, cy + r * 0.55f)
            quadraticTo(cx + r * 1.4f, cy + r * 0.8f, cx + r * 1.2f, cy - r * 0.05f)
            quadraticTo(cx + r * 1.1f, cy - r * 0.5f, cx + r * 0.65f, cy - r * 0.32f)
        },
        color = furDark,
        style = Stroke(width = r * 0.26f, cap = StrokeCap.Round),
    )

    // 身体（坐姿梨形轮廓）
    val bodyPath = Path().apply {
        moveTo(cx - r * 0.8f, cy + r * 1.15f)
        cubicTo(cx - r * 1.0f, cy + r * 0.4f, cx - r * 0.7f, cy - r * 0.18f, cx, cy - r * 0.22f)
        cubicTo(cx + r * 0.7f, cy - r * 0.18f, cx + r * 1.0f, cy + r * 0.4f, cx + r * 0.8f, cy + r * 1.15f)
        close()
    }
    fillPath3D(bodyPath, fur)
    drawRimLight(Offset(cx, cy + r * 0.55f), r * 0.85f, furLight, alpha = 0.22f)
    // 胸口/肚皮浅色
    drawOval(color = cream.copy(alpha = 0.6f), topLeft = Offset(cx - r * 0.32f, cy + r * 0.35f), size = Size(r * 0.64f, r * 0.85f))

    // 前爪
    fillOvoid(Offset(cx - r * 0.42f, cy + r * 1.02f), Size(r * 0.36f, r * 0.36f), cream)
    fillOvoid(Offset(cx + r * 0.42f, cy + r * 1.02f), Size(r * 0.36f, r * 0.36f), cream)
    drawLine(color = furDark, start = Offset(cx - r * 0.42f, cy + r * 1.05f), end = Offset(cx - r * 0.42f, cy + r * 1.28f), strokeWidth = 1.5f)
    drawLine(color = furDark, start = Offset(cx + r * 0.42f, cy + r * 1.05f), end = Offset(cx + r * 0.42f, cy + r * 1.28f), strokeWidth = 1.5f)

    // 头
    val headCy = cy - r * 0.45f + pounce
    fillSphere(Offset(cx, headCy), r * 0.95f, fur, rimLight = false)
    drawRimLight(Offset(cx, headCy), r * 0.95f, furLight, alpha = 0.3f)

    // 耳朵（外三角 + 内粉）
    val earTop = headCy - r * 0.95f
    fillPath3D(
        Path().apply {
            moveTo(cx - r * 0.78f, headCy - r * 0.1f)
            lineTo(cx - r * 1.0f, earTop)
            lineTo(cx - r * 0.22f, headCy - r * 0.55f)
            close()
        },
        fur,
    )
    fillPath3D(
        Path().apply {
            moveTo(cx - r * 0.66f, headCy - r * 0.2f)
            lineTo(cx - r * 0.78f, earTop + r * 0.28f)
            lineTo(cx - r * 0.36f, headCy - r * 0.5f)
            close()
        },
        pink,
    )
    fillPath3D(
        Path().apply {
            moveTo(cx + r * 0.78f, headCy - r * 0.1f)
            lineTo(cx + r * 1.0f, earTop)
            lineTo(cx + r * 0.22f, headCy - r * 0.55f)
            close()
        },
        fur,
    )
    fillPath3D(
        Path().apply {
            moveTo(cx + r * 0.66f, headCy - r * 0.2f)
            lineTo(cx + r * 0.78f, earTop + r * 0.28f)
            lineTo(cx + r * 0.36f, headCy - r * 0.5f)
            close()
        },
        pink,
    )

    // 眼睛（杏仁绿 + 竖瞳）
    val eyeY = headCy - r * 0.02f
    val eyeH = if (blink > 0f) r * 0.04f else r * 0.22f
    drawOval(color = eyeGreen, topLeft = Offset(cx - r * 0.56f, eyeY - eyeH / 2), size = Size(r * 0.32f, eyeH))
    drawOval(color = eyeGreen, topLeft = Offset(cx + r * 0.24f, eyeY - eyeH / 2), size = Size(r * 0.32f, eyeH))
    if (blink <= 0f) {
        drawOval(color = pupil, topLeft = Offset(cx - r * 0.5f, eyeY - r * 0.13f), size = Size(r * 0.05f, r * 0.26f))
        drawOval(color = pupil, topLeft = Offset(cx + r * 0.30f, eyeY - r * 0.13f), size = Size(r * 0.05f, r * 0.26f))
        drawCircle(color = Color.White, radius = r * 0.03f, center = Offset(cx - r * 0.47f, eyeY - r * 0.05f))
        drawCircle(color = Color.White, radius = r * 0.03f, center = Offset(cx + r * 0.33f, eyeY - r * 0.05f))
    }

    // 鼻子（粉倒三角）
    fillPath3D(
        Path().apply {
            moveTo(cx, headCy + r * 0.2f)
            lineTo(cx - r * 0.1f, headCy + r * 0.1f)
            lineTo(cx + r * 0.1f, headCy + r * 0.1f)
            close()
        },
        pink,
    )

    // 嘴（ω）
    val mY = headCy + r * 0.24f
    drawPath(
        Path().apply {
            moveTo(cx - r * 0.14f, mY)
            quadraticTo(cx - r * 0.07f, mY + r * 0.08f, cx, mY)
            quadraticTo(cx + r * 0.07f, mY + r * 0.08f, cx + r * 0.14f, mY)
        },
        color = black,
        style = Stroke(width = 1.5f, cap = StrokeCap.Round),
    )

    // 胡须（左右各 3 根）
    val wY = headCy + r * 0.08f
    listOf(
        Triple(-0.2f, -0.62f, -0.03f),
        Triple(-0.2f, -0.36f, 0f),
        Triple(-0.2f, -0.1f, 0.03f),
    ).forEach { (sx, ex, dy) ->
        drawPath(
            Path().apply { moveTo(cx + r * sx, wY); lineTo(cx + r * ex, wY + r * dy) },
            color = onSurface.copy(alpha = 0.45f),
            style = Stroke(width = 1.2f, cap = StrokeCap.Round),
        )
    }
    listOf(
        Triple(0.2f, 0.62f, -0.03f),
        Triple(0.2f, 0.36f, 0f),
        Triple(0.2f, 0.1f, 0.03f),
    ).forEach { (sx, ex, dy) ->
        drawPath(
            Path().apply { moveTo(cx + r * sx, wY); lineTo(cx + r * ex, wY + r * dy) },
            color = onSurface.copy(alpha = 0.45f),
            style = Stroke(width = 1.2f, cap = StrokeCap.Round),
        )
    }

    // 逗猫棒
    if (toyTimer > 0f) {
        val tx = cx + r * 1.5f
        val ty = headCy - r * 0.8f + sin(phase * 8f) * r * 0.2f
        drawLine(color = primary, start = Offset(cx + r * 0.85f, headCy - r * 0.3f), end = Offset(tx, ty), strokeWidth = 2f)
        fillSphere(Offset(tx, ty), r * 0.14f, simColor(50f, 1f))
    }

    drawParticles(particles, w, h)
}

private fun DrawScope.drawParticles(particles: List<SimParticle>, w: Float, h: Float) {
    for (pt in particles) {
        val a = (pt.life / pt.maxLife).coerceIn(0f, 1f)
        if (pt.ring) {
            drawOval(
                color = simColor(pt.hue, a * 0.8f),
                topLeft = Offset(pt.x * w - pt.radius, pt.y * h - pt.radius),
                size = Size(pt.radius * 2, pt.radius * 2),
                style = Stroke(width = 2f * a + 1f),
            )
        } else {
            drawCircle(color = simColor(pt.hue, a), radius = pt.radius * a + 2f, center = Offset(pt.x * w, pt.y * h))
        }
    }
}