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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
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
 * 养宠物（佛系解压小游戏，V2.9++ 模拟解压）。
 *
 * 顶部选动物：鱼 / 小狗 / 小猪 / 小猫，每种有独立饱食度与心情，以及不同交互：
 * - 鱼：几尾小鱼游动，点「喂食」落饵料，鱼会追食；「换水」刷新水体并冒泡。
 * - 小狗 / 小猪：点「喂食」给食物；点「摸摸」（或点屏幕）冒爱心、摇尾/扭动。
 * - 小猫：点「喂食」放饭碗；「撸猫」呼噜冒爱心；「逗猫棒」逗它扑逗。
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

    fun feed() {
        when (kind) {
            PetKind.FISH -> food = food + FoodPellet(Random.nextFloat(), 0.05f, 0.25f + Random.nextFloat() * 0.1f)
            else -> update(kind, fullness = 12)
        }
        Haptic.vibrate(context, 18)
    }

    fun pet() {
        particles = particles + List(6) {
            SimParticle(
                x = 0.5f + (Random.nextFloat() - 0.5f) * 0.3f,
                y = 0.45f,
                vx = (Random.nextFloat() - 0.5f) * 0.1f,
                vy = -0.18f - Random.nextFloat() * 0.1f,
                life = 1.1f, maxLife = 1.1f,
                hue = 340f, radius = 7f,
            )
        }
        update(kind, happiness = 10)
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

            // 饱食度/快乐值原先只增不减，几下就顶到 100 再也不动，喂食与互动随即失去意义。
            // 这里按真实时间缓慢衰减，让照顾宠物变成持续行为。
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
                    // 追饵：原实现鱼只左右巡游，饲料垂直下沉，两者几乎撞不上，
                    // 投喂基本看不到「被吃掉」，饱食度也就永远加不上。
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

    // 组合作用域捕获主题色（DrawScope 内不可调用 @Composable getter）
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
                )
                SimStatCard(
                    value = "${stat.happiness}",
                    label = stringResource(R.string.pet_happiness),
                    modifier = Modifier.weight(1f),
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
                            // 鱼缸此前点了完全没反应；改为在点击处投一粒饲料，和「喂食」按钮同一套逻辑。
                            if (kind == PetKind.FISH) {
                                val nx = (offset.x / size.width.toFloat()).coerceIn(0.06f, 0.94f)
                                food = food + FoodPellet(nx, 0.05f, 0.25f + Random.nextFloat() * 0.1f)
                                Haptic.vibrate(context, 14)
                            } else {
                                pet()
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
                    PetKind.DOG, PetKind.PIG -> Button(onClick = { pet() }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.pet_pet))
                    }
                    PetKind.CAT -> {
                        Button(onClick = { pet() }, modifier = Modifier.weight(1f)) {
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
    // 水体：竖直渐变（上浅下深）替代平涂，立刻有空间纵深
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF4FC3F7).copy(alpha = 0.20f), Color(0xFF1565C0).copy(alpha = 0.30f)),
        ),
        topLeft = Offset(0f, 0f),
        size = Size(w, h),
    )
    // 底部沙床柔光带
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
        // 尾鳍（先画，根部被身体压住）
        val tail = Path().apply {
            moveTo(cx - len * 0.5f, cy)
            lineTo(cx - len * 0.95f, cy - len * 0.32f)
            lineTo(cx - len * 0.95f, cy + len * 0.32f)
            close()
        }
        fillPath3D(tail, col)
        // 身体：受光椭球
        fillOvoid(Offset(cx - len / 2f, cy - len * 0.35f), Size(len, len * 0.7f), col)
        // 表面光泽
        drawGloss(Offset(cx - len * 0.12f, cy - len * 0.18f), len * 0.18f, len * 0.10f, 0.55f)
        // 眼睛（白底 + 黑瞳）
        drawCircle(color = Color.White, radius = len * 0.10f, center = Offset(cx + len * 0.28f, cy - len * 0.06f))
        drawCircle(color = Color(0xFF212121), radius = len * 0.05f, center = Offset(cx + len * 0.30f, cy - len * 0.06f))
    }
    for (p in food) {
        drawCircle(color = Color(0xFFFBC02D), radius = 5f, center = Offset(p.x * w, p.y * h))
    }
    drawParticles(particles, w, h)
}

private fun DrawScope.drawDog(phase: Float, particles: List<SimParticle>, primary: Color, onSurface: Color) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h * 0.55f
    val bodyR = w * 0.16f
    val wag = sin(phase * 6f) * 0.35f
    drawContactShadow(Offset(cx, cy + bodyR * 1.25f), bodyR * 1.1f, bodyR * 0.35f)
    // 尾巴（先画，根部被身体压住）
    val tail = Path().apply {
        moveTo(cx + bodyR * 0.9f, cy + bodyR * 0.4f)
        lineTo(cx + bodyR * 1.6f, cy + bodyR * 0.4f + wag * bodyR)
        lineTo(cx + bodyR * 0.9f, cy + bodyR * 0.75f)
        close()
    }
    fillPath3D(tail, primary)
    // 身体 + 头：受光球面
    fillSphere(Offset(cx, cy + bodyR * 0.4f), bodyR, primary)
    fillSphere(Offset(cx, cy - bodyR * 0.55f), bodyR * 0.8f, primary)
    // 耳朵
    fillSphere(Offset(cx - bodyR * 0.7f, cy - bodyR * 1.0f), bodyR * 0.32f, primary.darken(0.15f))
    fillSphere(Offset(cx + bodyR * 0.7f, cy - bodyR * 1.0f), bodyR * 0.32f, primary.darken(0.15f))
    // 口鼻
    fillOvoid(Offset(cx - bodyR * 0.05f, cy - bodyR * 0.35f), Size(bodyR * 0.6f, bodyR * 0.45f), primary.lighten(0.25f))
    // 眼睛
    drawCircle(color = onSurface, radius = bodyR * 0.10f, center = Offset(cx - bodyR * 0.28f, cy - bodyR * 0.55f))
    drawCircle(color = onSurface, radius = bodyR * 0.10f, center = Offset(cx + bodyR * 0.28f, cy - bodyR * 0.55f))
    // 鼻子 + 高光
    drawCircle(color = Color(0xFF3E2723), radius = bodyR * 0.13f, center = Offset(cx, cy - bodyR * 0.33f))
    drawGloss(Offset(cx - bodyR * 0.04f, cy - bodyR * 0.38f), bodyR * 0.05f, bodyR * 0.03f, 0.7f)
    drawParticles(particles, w, h)
}

private fun DrawScope.drawPig(phase: Float, particles: List<SimParticle>, primary: Color, onSurface: Color) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h * 0.52f
    val r = w * 0.17f
    val sway = sin(phase * 4f) * (w * 0.01f)
    val base = Color(0xFFF48FB1)
    drawContactShadow(Offset(cx + sway, cy + r * 1.15f), r * 1.1f, r * 0.32f)
    // 耳朵
    val earL = Path().apply {
        moveTo(cx - r * 0.7f + sway, cy - r * 0.7f)
        lineTo(cx - r * 0.3f + sway, cy - r * 1.15f)
        lineTo(cx - r * 0.1f + sway, cy - r * 0.7f)
        close()
    }
    val earR = Path().apply {
        moveTo(cx + r * 0.7f + sway, cy - r * 0.7f)
        lineTo(cx + r * 0.3f + sway, cy - r * 1.15f)
        lineTo(cx + r * 0.1f + sway, cy - r * 0.7f)
        close()
    }
    fillPath3D(earL, base.darken(0.1f))
    fillPath3D(earR, base.darken(0.1f))
    // 头：受光球面
    fillSphere(Offset(cx + sway, cy), r, base)
    // 口鼻
    fillOvoid(Offset(cx - r * 0.4f + sway, cy - r * 0.05f), Size(r * 0.8f, r * 0.5f), Color(0xFFEC407A))
    drawCircle(color = Color(0xFFAD1457), radius = r * 0.07f, center = Offset(cx - r * 0.18f + sway, cy + r * 0.15f))
    drawCircle(color = Color(0xFFAD1457), radius = r * 0.07f, center = Offset(cx + r * 0.18f + sway, cy + r * 0.15f))
    // 眼睛
    drawCircle(color = onSurface, radius = r * 0.08f, center = Offset(cx - r * 0.32f + sway, cy - r * 0.35f))
    drawCircle(color = onSurface, radius = r * 0.08f, center = Offset(cx + r * 0.32f + sway, cy - r * 0.35f))
    drawParticles(particles, w, h)
}

private fun DrawScope.drawCat(phase: Float, blink: Float, toyTimer: Float, particles: List<SimParticle>, primary: Color, onSurface: Color) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h * 0.52f
    val r = w * 0.16f
    val pounce = if (toyTimer > 0f) sin(phase * 12f) * (w * 0.02f) else 0f
    val base = Color(0xFFBDBDBD)
    drawContactShadow(Offset(cx, cy + r * 1.15f), r * 1.1f, r * 0.32f)
    // 耳朵
    val earL = Path().apply {
        moveTo(cx - r * 0.7f, cy - r * 0.7f + pounce)
        lineTo(cx - r * 0.3f, cy - r * 1.15f + pounce)
        lineTo(cx - r * 0.1f, cy - r * 0.7f + pounce)
        close()
    }
    val earR = Path().apply {
        moveTo(cx + r * 0.7f, cy - r * 0.7f + pounce)
        lineTo(cx + r * 0.3f, cy - r * 1.15f + pounce)
        lineTo(cx + r * 0.1f, cy - r * 0.7f + pounce)
        close()
    }
    fillPath3D(earL, base.darken(0.05f))
    fillPath3D(earR, base.darken(0.05f))
    // 身体 + 头：受光球面
    fillSphere(Offset(cx, cy + r * 0.55f), r * 0.95f, Color(0xFF9E9E9E))
    fillSphere(Offset(cx, cy - r * 0.4f + pounce), r * 0.8f, base)
    // 眼睛（眨眼）
    val eyeH = if (blink > 0f) r * 0.04f else r * 0.16f
    drawOval(color = Color(0xFF2E7D32), topLeft = Offset(cx - r * 0.46f, cy - r * 0.5f + pounce - eyeH / 2), size = Size(r * 0.18f, eyeH))
    drawOval(color = Color(0xFF2E7D32), topLeft = Offset(cx + r * 0.28f, cy - r * 0.5f + pounce - eyeH / 2), size = Size(r * 0.18f, eyeH))
    // 鼻子
    drawCircle(color = Color(0xFFF06292), radius = r * 0.1f, center = Offset(cx, cy - r * 0.28f + pounce))
    // 胡须
    drawLine(color = onSurface.copy(alpha = 0.5f), start = Offset(cx - r * 0.15f, cy - r * 0.25f + pounce), end = Offset(cx - r * 0.75f, cy - r * 0.2f + pounce), strokeWidth = 1.5f)
    drawLine(color = onSurface.copy(alpha = 0.5f), start = Offset(cx + r * 0.15f, cy - r * 0.25f + pounce), end = Offset(cx + r * 0.75f, cy - r * 0.2f + pounce), strokeWidth = 1.5f)
    // 逗猫棒
    if (toyTimer > 0f) {
        val tx = cx + r * 1.4f
        val ty = cy - r * 0.9f + sin(phase * 8f) * r * 0.2f
        drawLine(color = primary, start = Offset(cx + r * 0.8f, cy - r * 0.4f), end = Offset(tx, ty), strokeWidth = 2f)
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
