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
import kotlin.math.cos
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
        listOf(
            // 大号金鱼：橙红
            Fish(
                x = 0.22f, y = 0.34f, dir = 1,
                speed = 0.055f, bob = Random.nextFloat() * 6.28f,
                sizeScale = 1.35f,
                bodyColor = Color(0xFFFF7043),
                finColor = Color(0xFFFFAB91),
                stripeColor = null,
                eyeColor = Color(0xFF3E2723),
            ),
            // 中号金鱼：金黄
            Fish(
                x = 0.74f, y = 0.26f, dir = -1,
                speed = 0.065f, bob = Random.nextFloat() * 6.28f,
                sizeScale = 1.0f,
                bodyColor = Color(0xFFFFCA28),
                finColor = Color(0xFFFFE082),
                stripeColor = null,
                eyeColor = Color(0xFF3E2723),
            ),
            // 中号蓝紫条纹鱼
            Fish(
                x = 0.78f, y = 0.62f, dir = -1,
                speed = 0.07f, bob = Random.nextFloat() * 6.28f,
                sizeScale = 1.0f,
                bodyColor = Color(0xFF9575CD),
                finColor = Color(0xFFB39DDB),
                stripeColor = Color(0xFF5E35B1),
                eyeColor = Color(0xFF263238),
            ),
            // 小号金鱼：橙
            Fish(
                x = 0.26f, y = 0.74f, dir = 1,
                speed = 0.08f, bob = Random.nextFloat() * 6.28f,
                sizeScale = 0.7f,
                bodyColor = Color(0xFFFF8A50),
                finColor = Color(0xFFFFB74D),
                stripeColor = null,
                eyeColor = Color(0xFF3E2723),
            ),
            // 小号银鱼
            Fish(
                x = 0.66f, y = 0.82f, dir = 1,
                speed = 0.075f, bob = Random.nextFloat() * 6.28f,
                sizeScale = 0.7f,
                bodyColor = Color(0xFFB0BEC5),
                finColor = Color(0xFFECEFF1),
                stripeColor = Color(0xFF78909C),
                eyeColor = Color(0xFF263238),
            ),
        )
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
    var speed: Float, var bob: Float,
    val sizeScale: Float,
    val bodyColor: Color,
    val finColor: Color,
    val stripeColor: Color?,
    val eyeColor: Color,
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
        val baseLen = w * 0.10f * f.sizeScale
        val dirF = f.dir.toFloat()
        fun lx(dx: Float) = cx + dx * dirF
        fun ly(dy: Float) = cy + dy

        val bodyLen = baseLen * 1.9f
        val bodyHeight = baseLen * 0.78f

        // 身体（水滴形）
        val bodyPath = Path().apply {
            moveTo(lx(bodyLen * 0.45f), cy)
            cubicTo(lx(bodyLen * 0.55f), ly(-bodyHeight * 0.55f), lx(-bodyLen * 0.35f), ly(-bodyHeight * 0.65f), lx(-bodyLen * 0.55f), cy)
            cubicTo(lx(-bodyLen * 0.35f), ly(bodyHeight * 0.65f), lx(bodyLen * 0.55f), ly(bodyHeight * 0.55f), lx(bodyLen * 0.45f), cy)
            close()
        }
        fillPath3D(bodyPath, f.bodyColor)

        // 尾鳍（扇形，随波摆动）
        val tailWag = sin(f.bob * 1.5f) * baseLen * 0.12f
        val tailPath = Path().apply {
            moveTo(lx(-bodyLen * 0.45f), cy)
            lineTo(lx(-bodyLen * 0.95f), ly(-baseLen * 0.55f + tailWag))
            lineTo(lx(-bodyLen * 0.82f), cy + tailWag * 0.3f)
            lineTo(lx(-bodyLen * 0.95f), ly(baseLen * 0.55f + tailWag))
            close()
        }
        fillPath3D(tailPath, f.finColor)

        // 背鳍
        val dorsalPath = Path().apply {
            moveTo(lx(-bodyLen * 0.18f), ly(-bodyHeight * 0.32f))
            quadraticTo(lx(-bodyLen * 0.28f), ly(-bodyHeight * 0.88f), lx(bodyLen * 0.18f), ly(-bodyHeight * 0.55f))
            lineTo(lx(bodyLen * 0.08f), ly(-bodyHeight * 0.32f))
            close()
        }
        fillPath3D(dorsalPath, f.finColor)

        // 腹鳍
        val ventralPath = Path().apply {
            moveTo(lx(bodyLen * 0.02f), ly(bodyHeight * 0.32f))
            quadraticTo(lx(-bodyLen * 0.18f), ly(bodyHeight * 0.88f), lx(-bodyLen * 0.38f), ly(bodyHeight * 0.42f))
            lineTo(lx(-bodyLen * 0.12f), ly(bodyHeight * 0.32f))
            close()
        }
        fillPath3D(ventralPath, f.finColor)

        // 胸鳍
        val pectoralPath = Path().apply {
            moveTo(lx(bodyLen * 0.22f), ly(bodyHeight * 0.08f))
            quadraticTo(lx(bodyLen * 0.55f), ly(bodyHeight * 0.58f), lx(bodyLen * 0.12f), ly(bodyHeight * 0.48f))
            close()
        }
        fillPath3D(pectoralPath, f.finColor)

        // 条纹（蓝紫鱼等）
        f.stripeColor?.let { stripe ->
            drawContext.canvas.save()
            drawContext.canvas.clipPath(bodyPath)
            for (i in -2..2) {
                val sx = bodyLen * 0.1f * i
                drawLine(
                    color = stripe.copy(alpha = 0.55f),
                    start = Offset(lx(sx), ly(-bodyHeight * 0.55f)),
                    end = Offset(lx(sx - bodyLen * 0.12f), ly(bodyHeight * 0.55f)),
                    strokeWidth = baseLen * 0.08f,
                )
            }
            drawContext.canvas.restore()
        }

        // 鳞片（金鱼）
        if (f.stripeColor == null) {
            for (row in 0..2) {
                for (col in 0..3) {
                    val sx = bodyLen * 0.05f + col * bodyLen * 0.14f - row * bodyLen * 0.04f
                    val sy = -bodyHeight * 0.22f + row * bodyHeight * 0.22f
                    drawArc(
                        color = Color.White.copy(alpha = 0.20f),
                        startAngle = 0f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(lx(sx) - baseLen * 0.08f, cy + sy),
                        size = Size(baseLen * 0.16f, baseLen * 0.10f),
                        style = Stroke(width = 1.1f),
                    )
                }
            }
        }

        // 眼睛
        val eyeX = bodyLen * 0.32f
        val eyeY = -bodyHeight * 0.08f
        val eyeR = baseLen * 0.11f
        drawCircle(color = Color.White, radius = eyeR, center = Offset(lx(eyeX), cy + eyeY))
        drawCircle(color = f.eyeColor, radius = eyeR * 0.55f, center = Offset(lx(eyeX + baseLen * 0.02f), cy + eyeY))
        drawCircle(color = Color.White, radius = eyeR * 0.22f, center = Offset(lx(eyeX + baseLen * 0.035f), cy + eyeY - baseLen * 0.025f))

        // 嘴线
        drawLine(
            color = f.bodyColor.darken(0.25f),
            start = Offset(lx(bodyLen * 0.45f), cy),
            end = Offset(lx(bodyLen * 0.52f), cy),
            strokeWidth = 1.2f,
        )

        // 身体高光
        drawGloss(Offset(lx(bodyLen * 0.08f), cy - bodyHeight * 0.22f), baseLen * 0.22f, baseLen * 0.12f, 0.45f)
    }
    for (p in food) {
        drawCircle(color = Color(0xFFFBC02D), radius = 5f, center = Offset(p.x * w, p.y * h))
    }
    drawParticles(particles, w, h)
}

/** 正面坐姿卷毛小狗：圆润身体 + 蓬松卷毛 + 垂耳 + 胸前发光云朵心。 */
private fun DrawScope.drawDog(phase: Float, particles: List<SimParticle>, primary: Color, onSurface: Color) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h * 0.58f
    val r = w * 0.18f
    val sway = sin(phase * 3f) * r * 0.02f
    val fur = Color(0xFFB78B5F)        // 暖棕毛色
    val furDark = fur.darken(0.18f)
    val furLight = fur.lighten(0.32f)
    val cream = Color(0xFFFFF3E0)
    val black = Color(0xFF2A2118)
    val tongue = Color(0xFFFF8A80)

    drawSoftShadow(Offset(cx + sway, cy + r * 1.15f), r * 1.1f, r * 0.32f)

    // 尾巴（身后摇摆）
    val wag = sin(phase * 8f) * r * 0.18f
    drawPath(
        Path().apply {
            moveTo(cx - r * 0.65f + sway, cy + r * 0.35f)
            quadraticTo(cx - r * 1.25f, cy + r * 0.25f + wag, cx - r * 1.05f, cy - r * 0.35f + wag)
        },
        color = furDark,
        style = Stroke(width = r * 0.24f, cap = StrokeCap.Round),
    )

    // 后腿（坐姿，两侧）
    fillOvoid(Offset(cx - r * 0.72f + sway, cy + r * 0.92f), Size(r * 0.36f, r * 0.48f), furDark)
    fillOvoid(Offset(cx + r * 0.72f + sway, cy + r * 0.92f), Size(r * 0.36f, r * 0.48f), furDark)

    // 身体
    fillSphere(Offset(cx + sway, cy + r * 0.35f), r * 1.02f, fur, rimLight = false)
    drawRimLight(Offset(cx + sway, cy + r * 0.35f), r * 1.02f, furLight, alpha = 0.28f)

    // 胸口浅色毛
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(cream.copy(alpha = 0.9f), cream.copy(alpha = 0.4f), Color.Transparent),
            center = Offset(cx + sway, cy + r * 0.42f),
            radius = r * 0.55f,
        ),
        topLeft = Offset(cx - r * 0.5f + sway, cy + r * 0.05f),
        size = Size(r, r * 0.75f),
    )

    // 胸前发光云朵心
    val glowCenter = Offset(cx + sway, cy + r * 0.42f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFFFF9C4).copy(alpha = 0.7f), Color(0xFFFFF176).copy(alpha = 0.35f), Color.Transparent),
            center = glowCenter,
            radius = r * 0.42f,
        ),
        radius = r * 0.42f,
        center = glowCenter,
    )
    listOf(
        Offset(-r * 0.18f, -r * 0.08f),
        Offset(r * 0.18f, -r * 0.08f),
        Offset(0f, -r * 0.18f),
        Offset(-r * 0.08f, r * 0.1f),
        Offset(r * 0.08f, r * 0.1f),
    ).forEach { off ->
        drawCircle(
            color = Color(0xFFFFFAD7).copy(alpha = 0.85f),
            radius = r * 0.14f,
            center = Offset(glowCenter.x + off.x, glowCenter.y + off.y),
        )
    }

    // 前腿
    fillOvoid(Offset(cx - r * 0.32f + sway, cy + r * 0.95f), Size(r * 0.30f, r * 0.55f), furLight)
    fillOvoid(Offset(cx + r * 0.32f + sway, cy + r * 0.95f), Size(r * 0.30f, r * 0.55f), furLight)
    drawCircle(color = black.copy(alpha = 0.35f), radius = r * 0.07f, center = Offset(cx - r * 0.32f + sway, cy + r * 1.25f))
    drawCircle(color = black.copy(alpha = 0.35f), radius = r * 0.07f, center = Offset(cx + r * 0.32f + sway, cy + r * 1.25f))

    // 头
    val headCy = cy - r * 0.55f
    fillSphere(Offset(cx + sway, headCy), r * 0.92f, fur, rimLight = false)
    drawRimLight(Offset(cx + sway, headCy), r * 0.92f, furLight, alpha = 0.30f)

    // 卷毛（头部外圈蓬松小球）
    for (i in 0..7) {
        val ang = i * 0.785f + phase * 0.5f
        val rx = cos(ang) * r * 0.95f
        val ry = sin(ang) * r * 0.88f
        fillSphere(Offset(cx + rx + sway, headCy + ry), r * 0.22f, if (i % 2 == 0) fur else furLight, rimLight = false)
    }

    // 垂耳
    fillPath3D(
        Path().apply {
            moveTo(cx - r * 0.72f + sway, headCy - r * 0.25f)
            quadraticTo(cx - r * 1.15f, headCy + r * 0.05f, cx - r * 0.82f + sway, headCy + r * 0.72f)
            quadraticTo(cx - r * 0.55f, headCy + r * 0.38f, cx - r * 0.48f + sway, headCy - r * 0.12f)
            close()
        },
        furDark,
    )
    fillPath3D(
        Path().apply {
            moveTo(cx + r * 0.72f + sway, headCy - r * 0.25f)
            quadraticTo(cx + r * 1.15f, headCy + r * 0.05f, cx + r * 0.82f + sway, headCy + r * 0.72f)
            quadraticTo(cx + r * 0.55f, headCy + r * 0.38f, cx + r * 0.48f + sway, headCy - r * 0.12f)
            close()
        },
        furDark,
    )

    // 口鼻部（浅色）
    fillOvoid(Offset(cx + sway, headCy + r * 0.22f), Size(r * 0.62f, r * 0.46f), cream)
    // 鼻子
    drawOval(color = black, topLeft = Offset(cx - r * 0.12f + sway, headCy + r * 0.08f), size = Size(r * 0.24f, r * 0.16f))
    drawGloss(Offset(cx - r * 0.04f + sway, headCy + r * 0.09f), r * 0.04f, r * 0.025f, 0.6f)

    // 嘴巴 + 吐舌
    drawPath(
        Path().apply {
            moveTo(cx - r * 0.16f + sway, headCy + r * 0.32f)
            quadraticTo(cx - r * 0.05f + sway, headCy + r * 0.44f, cx + sway, headCy + r * 0.32f)
            quadraticTo(cx + r * 0.05f + sway, headCy + r * 0.44f, cx + r * 0.16f + sway, headCy + r * 0.32f)
        },
        color = black,
        style = Stroke(width = 2f, cap = StrokeCap.Round),
    )
    drawOval(
        color = tongue,
        topLeft = Offset(cx - r * 0.08f + sway, headCy + r * 0.36f),
        size = Size(r * 0.16f, r * 0.22f),
    )
    drawLine(
        color = black.copy(alpha = 0.25f),
        start = Offset(cx + sway, headCy + r * 0.36f),
        end = Offset(cx + sway, headCy + r * 0.52f),
        strokeWidth = 1f,
    )

    // 眼睛
    drawCircle(color = black, radius = r * 0.10f, center = Offset(cx - r * 0.28f + sway, headCy - r * 0.08f))
    drawCircle(color = black, radius = r * 0.10f, center = Offset(cx + r * 0.28f + sway, headCy - r * 0.08f))
    drawCircle(color = Color.White, radius = r * 0.035f, center = Offset(cx - r * 0.25f + sway, headCy - r * 0.12f))
    drawCircle(color = Color.White, radius = r * 0.035f, center = Offset(cx + r * 0.31f + sway, headCy - r * 0.12f))

    drawParticles(particles, w, h)
}

/** 正面坐姿微笑小猪：圆润身体 + 大圆耳 + 圆鼻 + 微笑 + 腮红。 */
private fun DrawScope.drawPig(phase: Float, particles: List<SimParticle>, primary: Color, onSurface: Color) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h * 0.55f
    val r = w * 0.17f
    val sway = sin(phase * 4f) * (w * 0.008f)
    val base = Color(0xFFF6A5C0)      // 粉
    val baseDark = base.darken(0.12f)
    val baseLight = base.lighten(0.38f)
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
    fillSphere(Offset(cx + sway, cy + r * 0.42f), r * 1.12f, base, rimLight = false)
    drawRimLight(Offset(cx + sway, cy + r * 0.42f), r * 1.12f, baseLight, alpha = 0.30f)
    // 肚皮浅色
    drawOval(color = baseLight.copy(alpha = 0.55f), topLeft = Offset(cx - r * 0.52f + sway, cy + r * 0.58f), size = Size(r * 1.04f, r * 0.72f))

    // 头
    fillSphere(Offset(cx + sway, cy - r * 0.28f), r * 0.95f, base, rimLight = false)
    drawRimLight(Offset(cx + sway, cy - r * 0.28f), r * 0.95f, baseLight, alpha = 0.30f)

    // 耳朵（大圆耳）
    fillPath3D(
        Path().apply {
            moveTo(cx - r * 0.55f + sway, cy - r * 0.95f)
            quadraticTo(cx - r * 1.05f, cy - r * 1.38f, cx - r * 0.35f + sway, cy - r * 1.28f)
            quadraticTo(cx - r * 0.15f, cy - r * 1.18f, cx - r * 0.15f + sway, cy - r * 0.95f)
            close()
        },
        base,
    )
    fillPath3D(
        Path().apply {
            moveTo(cx + r * 0.55f + sway, cy - r * 0.95f)
            quadraticTo(cx + r * 1.05f, cy - r * 1.38f, cx + r * 0.35f + sway, cy - r * 1.28f)
            quadraticTo(cx + r * 0.15f, cy - r * 1.18f, cx + r * 0.15f + sway, cy - r * 0.95f)
            close()
        },
        base,
    )
    // 耳内浅粉
    fillPath3D(
        Path().apply {
            moveTo(cx - r * 0.45f + sway, cy - r * 1.0f)
            quadraticTo(cx - r * 0.72f, cy - r * 1.25f, cx - r * 0.32f + sway, cy - r * 1.20f)
            quadraticTo(cx - r * 0.22f, cy - r * 1.14f, cx - r * 0.25f + sway, cy - r * 1.02f)
            close()
        },
        pink.copy(alpha = 0.5f),
    )
    fillPath3D(
        Path().apply {
            moveTo(cx + r * 0.45f + sway, cy - r * 1.0f)
            quadraticTo(cx + r * 0.72f, cy - r * 1.25f, cx + r * 0.32f + sway, cy - r * 1.20f)
            quadraticTo(cx + r * 0.22f, cy - r * 1.14f, cx + r * 0.25f + sway, cy - r * 1.02f)
            close()
        },
        pink.copy(alpha = 0.5f),
    )

    // 口鼻部（浅色椭圆）
    fillSphere(Offset(cx + sway, cy + r * 0.02f), r * 0.48f, baseLight, rimLight = false)
    // 鼻孔
    drawOval(color = pink, topLeft = Offset(cx - r * 0.24f + sway, cy - r * 0.04f), size = Size(r * 0.14f, r * 0.18f))
    drawOval(color = pink, topLeft = Offset(cx + r * 0.10f + sway, cy - r * 0.04f), size = Size(r * 0.14f, r * 0.18f))
    drawOval(color = Color.White.copy(alpha = 0.4f), topLeft = Offset(cx - r * 0.20f + sway, cy - r * 0.06f), size = Size(r * 0.04f, r * 0.06f))
    drawOval(color = Color.White.copy(alpha = 0.4f), topLeft = Offset(cx + r * 0.14f + sway, cy - r * 0.06f), size = Size(r * 0.04f, r * 0.06f))

    // 微笑
    drawPath(
        Path().apply {
            moveTo(cx - r * 0.18f + sway, cy + r * 0.22f)
            quadraticTo(cx + sway, cy + r * 0.36f, cx + r * 0.18f + sway, cy + r * 0.22f)
        },
        color = black,
        style = Stroke(width = 2f, cap = StrokeCap.Round),
    )

    // 眼睛
    drawCircle(color = black, radius = r * 0.075f, center = Offset(cx - r * 0.30f + sway, cy - r * 0.35f))
    drawCircle(color = black, radius = r * 0.075f, center = Offset(cx + r * 0.30f + sway, cy - r * 0.35f))
    drawCircle(color = Color.White, radius = r * 0.028f, center = Offset(cx - r * 0.29f + sway, cy - r * 0.37f))
    drawCircle(color = Color.White, radius = r * 0.028f, center = Offset(cx + r * 0.31f + sway, cy - r * 0.37f))

    // 腮红
    drawOval(color = pink.copy(alpha = 0.4f), topLeft = Offset(cx - r * 0.68f + sway, cy - r * 0.12f), size = Size(r * 0.26f, r * 0.16f))
    drawOval(color = pink.copy(alpha = 0.4f), topLeft = Offset(cx + r * 0.42f + sway, cy - r * 0.12f), size = Size(r * 0.26f, r * 0.16f))

    drawParticles(particles, w, h)
}

/** 橘白闭眼小猫：圆润身体 + 条纹 + 咖啡杯 + 逗猫棒。 */
private fun DrawScope.drawCat(phase: Float, blink: Float, toyTimer: Float, particles: List<SimParticle>, primary: Color, onSurface: Color) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h * 0.58f
    val r = w * 0.17f
    val pounce = if (toyTimer > 0f) sin(phase * 12f) * (w * 0.015f) else 0f
    val orange = Color(0xFFE69A5F)     // 橘色
    val orangeDark = orange.darken(0.20f)
    val orangeLight = orange.lighten(0.28f)
    val cream = Color(0xFFFFF8E7)
    val white = Color(0xFFFFFFFF)
    val pink = Color(0xFFE48AA6)
    val black = Color(0xFF2B2118)

    drawSoftShadow(Offset(cx, cy + r * 1.25f), r * 1.15f, r * 0.34f)

    // 尾巴（卷到身前右侧）
    drawPath(
        Path().apply {
            moveTo(cx + r * 0.7f, cy + r * 0.55f)
            quadraticTo(cx + r * 1.4f, cy + r * 0.8f, cx + r * 1.2f, cy - r * 0.05f)
            quadraticTo(cx + r * 1.1f, cy - r * 0.5f, cx + r * 0.65f, cy - r * 0.32f)
        },
        color = orangeDark,
        style = Stroke(width = r * 0.26f, cap = StrokeCap.Round),
    )

    // 身体（坐姿梨形）
    val bodyPath = Path().apply {
        moveTo(cx - r * 0.8f, cy + r * 1.15f)
        cubicTo(cx - r * 1.0f, cy + r * 0.4f, cx - r * 0.7f, cy - r * 0.18f, cx, cy - r * 0.22f)
        cubicTo(cx + r * 0.7f, cy - r * 0.18f, cx + r * 1.0f, cy + r * 0.4f, cx + r * 0.8f, cy + r * 1.15f)
        close()
    }
    fillPath3D(bodyPath, orange)
    drawRimLight(Offset(cx, cy + r * 0.55f), r * 0.85f, orangeLight, alpha = 0.22f)

    // 肚皮浅色
    drawOval(color = cream.copy(alpha = 0.7f), topLeft = Offset(cx - r * 0.32f, cy + r * 0.35f), size = Size(r * 0.64f, r * 0.85f))

    // 身体条纹
    listOf(-0.25f, 0f, 0.25f).forEach { ox ->
        drawArc(
            color = orangeDark.copy(alpha = 0.5f),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(cx + r * ox - r * 0.18f, cy + r * 0.35f),
            size = Size(r * 0.36f, r * 0.22f),
            style = Stroke(width = r * 0.06f),
        )
    }

    // 前爪
    fillOvoid(Offset(cx - r * 0.42f, cy + r * 1.02f), Size(r * 0.36f, r * 0.36f), white)
    fillOvoid(Offset(cx + r * 0.42f, cy + r * 1.02f), Size(r * 0.36f, r * 0.36f), white)
    drawLine(color = orangeDark, start = Offset(cx - r * 0.42f, cy + r * 1.05f), end = Offset(cx - r * 0.42f, cy + r * 1.28f), strokeWidth = 1.5f)
    drawLine(color = orangeDark, start = Offset(cx + r * 0.42f, cy + r * 1.05f), end = Offset(cx + r * 0.42f, cy + r * 1.28f), strokeWidth = 1.5f)

    // 头
    val headCy = cy - r * 0.45f + pounce
    fillSphere(Offset(cx, headCy), r * 0.95f, orange, rimLight = false)
    drawRimLight(Offset(cx, headCy), r * 0.95f, orangeLight, alpha = 0.3f)

    // 额头条纹
    listOf(0f, -0.18f, 0.18f).forEach { ox ->
        drawPath(
            Path().apply {
                moveTo(cx + r * ox, headCy - r * 0.78f)
                quadraticTo(cx + r * ox * 0.7f, headCy - r * 0.35f, cx + r * ox * 0.4f, headCy - r * 0.12f)
            },
            color = orangeDark.copy(alpha = 0.5f),
            style = Stroke(width = r * 0.08f, cap = StrokeCap.Round),
        )
    }

    // 耳朵（外 + 内粉）
    val earTop = headCy - r * 0.95f
    fillPath3D(Path().apply { moveTo(cx - r * 0.78f, headCy - r * 0.1f); lineTo(cx - r * 1.0f, earTop); lineTo(cx - r * 0.22f, headCy - r * 0.55f); close() }, orange)
    fillPath3D(Path().apply { moveTo(cx - r * 0.66f, headCy - r * 0.2f); lineTo(cx - r * 0.78f, earTop + r * 0.28f); lineTo(cx - r * 0.36f, headCy - r * 0.5f); close() }, pink)
    fillPath3D(Path().apply { moveTo(cx + r * 0.78f, headCy - r * 0.1f); lineTo(cx + r * 1.0f, earTop); lineTo(cx + r * 0.22f, headCy - r * 0.55f); close() }, orange)
    fillPath3D(Path().apply { moveTo(cx + r * 0.66f, headCy - r * 0.2f); lineTo(cx + r * 0.78f, earTop + r * 0.28f); lineTo(cx + r * 0.36f, headCy - r * 0.5f); close() }, pink)

    // 白色口鼻部
    fillOvoid(Offset(cx, headCy + r * 0.28f), Size(r * 0.52f, r * 0.36f), cream)

    // 闭眼弯月
    drawPath(
        Path().apply {
            moveTo(cx - r * 0.38f, headCy - r * 0.05f)
            quadraticTo(cx - r * 0.25f, headCy + r * 0.08f, cx - r * 0.12f, headCy - r * 0.05f)
        },
        color = black,
        style = Stroke(width = 2.5f, cap = StrokeCap.Round),
    )
    drawPath(
        Path().apply {
            moveTo(cx + r * 0.12f, headCy - r * 0.05f)
            quadraticTo(cx + r * 0.25f, headCy + r * 0.08f, cx + r * 0.38f, headCy - r * 0.05f)
        },
        color = black,
        style = Stroke(width = 2.5f, cap = StrokeCap.Round),
    )

    // 鼻子
    fillPath3D(
        Path().apply {
            moveTo(cx, headCy + r * 0.18f)
            lineTo(cx - r * 0.08f, headCy + r * 0.10f)
            lineTo(cx + r * 0.08f, headCy + r * 0.10f)
            close()
        },
        pink,
    )

    // 嘴（ω）
    val mY = headCy + r * 0.22f
    drawPath(
        Path().apply {
            moveTo(cx - r * 0.12f, mY)
            quadraticTo(cx - r * 0.06f, mY + r * 0.07f, cx, mY)
            quadraticTo(cx + r * 0.06f, mY + r * 0.07f, cx + r * 0.12f, mY)
        },
        color = black,
        style = Stroke(width = 1.5f, cap = StrokeCap.Round),
    )

    // 胡须
    val wY = headCy + r * 0.08f
    listOf(
        Triple(-0.18f, -0.55f, -0.03f),
        Triple(-0.18f, -0.32f, 0f),
        Triple(-0.18f, -0.08f, 0.03f),
    ).forEach { (sx, ex, dy) ->
        drawPath(
            Path().apply { moveTo(cx + r * sx, wY); lineTo(cx + r * ex, wY + r * dy) },
            color = onSurface.copy(alpha = 0.4f),
            style = Stroke(width = 1.2f, cap = StrokeCap.Round),
        )
    }
    listOf(
        Triple(0.18f, 0.55f, -0.03f),
        Triple(0.18f, 0.32f, 0f),
        Triple(0.18f, 0.08f, 0.03f),
    ).forEach { (sx, ex, dy) ->
        drawPath(
            Path().apply { moveTo(cx + r * sx, wY); lineTo(cx + r * ex, wY + r * dy) },
            color = onSurface.copy(alpha = 0.4f),
            style = Stroke(width = 1.2f, cap = StrokeCap.Round),
        )
    }

    // 咖啡杯（左侧）
    val cupCx = cx - r * 1.45f
    val cupCy = cy + r * 0.55f
    val cupW = r * 0.55f
    val cupH = r * 0.70f
    fillRoundRect3D(Offset(cupCx - cupW / 2, cupCy - cupH / 2), Size(cupW, cupH), r * 0.06f, Color(0xFF8D6E63))
    drawArc(
        color = Color(0xFF8D6E63),
        startAngle = 270f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(cupCx + cupW * 0.25f, cupCy - cupH * 0.18f),
        size = Size(cupW * 0.35f, cupH * 0.36f),
        style = Stroke(width = r * 0.06f),
    )
    drawOval(color = Color(0xFF5D4037).copy(alpha = 0.9f), topLeft = Offset(cupCx - cupW * 0.38f, cupCy - cupH * 0.40f), size = Size(cupW * 0.76f, cupH * 0.22f))
    drawCircle(color = cream.copy(alpha = 0.8f), radius = r * 0.08f, center = Offset(cupCx, cupCy - cupH * 0.35f))
    drawPath(
        Path().apply {
            moveTo(cupCx - r * 0.04f, cupCy - cupH * 0.38f)
            quadraticTo(cupCx, cupCy - cupH * 0.30f, cupCx + r * 0.04f, cupCy - cupH * 0.38f)
        },
        color = cream.copy(alpha = 0.8f),
        style = Stroke(width = 2f, cap = StrokeCap.Round),
    )

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