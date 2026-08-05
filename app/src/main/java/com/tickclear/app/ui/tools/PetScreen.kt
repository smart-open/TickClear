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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import kotlin.math.sin
import kotlin.random.Random

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
    var phase by remember { mutableStateOf(0f) }
    var blink by remember { mutableStateOf(0f) }
    var toyTimer by remember { mutableStateOf(0f) }

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
        while (true) {
            val now = withFrameMillis { it }
            val dt = if (last == 0L) 0.016f else ((now - last) / 1000f).coerceAtMost(0.05f)
            last = now
            phase += dt
            blink -= dt
            if (blink < -3f) blink = 0.18f
            if (toyTimer > 0f) toyTimer = (toyTimer - dt).coerceAtLeast(0f)

            if (kind == PetKind.FISH) {
                for (f in fishes) {
                    var nx = f.x + f.dir * f.speed * dt
                    if (nx < 0.08f) { nx = 0.08f; f.dir = 1 }
                    if (nx > 0.92f) { nx = 0.92f; f.dir = -1 }
                    f.x = nx
                    f.bob += dt * 3f
                }
                val stillFood = mutableListOf<FoodPellet>()
                for (p in food) {
                    val py = p.y + p.vy * dt
                    val eater = fishes.minByOrNull { kotlin.math.abs(it.x - p.x) + kotlin.math.abs(it.y - py) * 2 }
                    if (eater != null && kotlin.math.abs(eater.x - p.x) < 0.08f && kotlin.math.abs(eater.y - py) < 0.08f) {
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
                        detectTapGestures {
                            if (kind != PetKind.FISH) pet()
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
    drawRect(color = Color(0xFF1E88E5).copy(alpha = 0.18f), topLeft = Offset(0f, 0f), size = Size(w, h))
    drawRect(color = Color(0xFF1E88E5).copy(alpha = 0.10f), topLeft = Offset(0f, h * 0.7f), size = Size(w, h * 0.3f))
    for (f in fishes) {
        val cx = f.x * w
        val cy = f.y * h + sin(f.bob) * 6f
        val len = w * 0.10f
        val col = simColor(f.hue, 0.9f)
        drawOval(color = col, topLeft = Offset(cx - len / 2, cy - len * 0.35f), size = Size(len, len * 0.7f))
        val tail = Path().apply {
            moveTo(cx - len / 2, cy)
            lineTo(cx - len / 2 - len * 0.35f, cy - len * 0.28f)
            lineTo(cx - len / 2 - len * 0.35f, cy + len * 0.28f)
            close()
        }
        drawPath(tail, col)
        drawCircle(color = onSurface, radius = len * 0.06f, center = Offset(cx + len * 0.28f, cy - len * 0.05f))
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
    val wag = sin(phase * 6f) * 0.3f
    drawCircle(color = primary.copy(alpha = 0.85f), radius = bodyR, center = Offset(cx, cy + bodyR * 0.4f))
    drawCircle(color = primary.copy(alpha = 0.95f), radius = bodyR * 0.8f, center = Offset(cx, cy - bodyR * 0.5f))
    drawCircle(color = primary, radius = bodyR * 0.3f, center = Offset(cx - bodyR * 0.7f, cy - bodyR * 0.95f))
    drawCircle(color = primary, radius = bodyR * 0.3f, center = Offset(cx + bodyR * 0.7f, cy - bodyR * 0.95f))
    drawCircle(color = onSurface, radius = bodyR * 0.09f, center = Offset(cx - bodyR * 0.28f, cy - bodyR * 0.55f))
    drawCircle(color = onSurface, radius = bodyR * 0.09f, center = Offset(cx + bodyR * 0.28f, cy - bodyR * 0.55f))
    drawCircle(color = Color(0xFF3E2723), radius = bodyR * 0.12f, center = Offset(cx, cy - bodyR * 0.35f))
    val tail = Path().apply {
        moveTo(cx + bodyR * 0.9f, cy + bodyR * 0.4f)
        lineTo(cx + bodyR * 1.5f, cy + bodyR * 0.4f + wag * bodyR)
        lineTo(cx + bodyR * 0.9f, cy + bodyR * 0.7f)
        close()
    }
    drawPath(tail, primary.copy(alpha = 0.85f))
    drawParticles(particles, w, h)
}

private fun DrawScope.drawPig(phase: Float, particles: List<SimParticle>, primary: Color, onSurface: Color) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h * 0.52f
    val r = w * 0.17f
    val sway = sin(phase * 4f) * (w * 0.01f)
    drawCircle(color = Color(0xFFF48FB1), radius = r, center = Offset(cx + sway, cy))
    drawCircle(color = Color(0xFFF06292), radius = r * 0.3f, center = Offset(cx - r * 0.6f + sway, cy - r * 0.8f))
    drawCircle(color = Color(0xFFF06292), radius = r * 0.3f, center = Offset(cx + r * 0.6f + sway, cy - r * 0.8f))
    drawOval(color = Color(0xFFEC407A), topLeft = Offset(cx - r * 0.4f + sway, cy - r * 0.05f), size = Size(r * 0.8f, r * 0.5f))
    drawCircle(color = Color(0xFFAD1457), radius = r * 0.07f, center = Offset(cx - r * 0.18f + sway, cy + r * 0.15f))
    drawCircle(color = Color(0xFFAD1457), radius = r * 0.07f, center = Offset(cx + r * 0.18f + sway, cy + r * 0.15f))
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
    drawCircle(color = Color(0xFFBDBDBD), radius = r * 0.95f, center = Offset(cx, cy + r * 0.55f))
    drawCircle(color = Color(0xFFE0E0E0), radius = r * 0.8f, center = Offset(cx, cy - r * 0.4f + pounce))
    val earL = Path().apply {
        moveTo(cx - r * 0.7f, cy - r * 0.7f + pounce)
        lineTo(cx - r * 0.3f, cy - r * 1.1f + pounce)
        lineTo(cx - r * 0.1f, cy - r * 0.7f + pounce)
        close()
    }
    val earR = Path().apply {
        moveTo(cx + r * 0.7f, cy - r * 0.7f + pounce)
        lineTo(cx + r * 0.3f, cy - r * 1.1f + pounce)
        lineTo(cx + r * 0.1f, cy - r * 0.7f + pounce)
        close()
    }
    drawPath(earL, Color(0xFFBDBDBD))
    drawPath(earR, Color(0xFFBDBDBD))
    val eyeH = if (blink > 0f) r * 0.04f else r * 0.16f
    drawOval(color = onSurface, topLeft = Offset(cx - r * 0.42f, cy - r * 0.5f + pounce - eyeH / 2), size = Size(r * 0.16f, eyeH))
    drawOval(color = onSurface, topLeft = Offset(cx + r * 0.26f, cy - r * 0.5f + pounce - eyeH / 2), size = Size(r * 0.16f, eyeH))
    drawCircle(color = Color(0xFFF06292), radius = r * 0.1f, center = Offset(cx, cy - r * 0.28f + pounce))
    drawLine(color = onSurface.copy(alpha = 0.5f), start = Offset(cx - r * 0.15f, cy - r * 0.25f + pounce), end = Offset(cx - r * 0.7f, cy - r * 0.2f + pounce), strokeWidth = 1.5f)
    drawLine(color = onSurface.copy(alpha = 0.5f), start = Offset(cx + r * 0.15f, cy - r * 0.25f + pounce), end = Offset(cx + r * 0.7f, cy - r * 0.2f + pounce), strokeWidth = 1.5f)
    if (toyTimer > 0f) {
        val tx = cx + r * 1.4f
        val ty = cy - r * 0.9f + sin(phase * 8f) * r * 0.2f
        drawLine(color = primary, start = Offset(cx + r * 0.8f, cy - r * 0.4f), end = Offset(tx, ty), strokeWidth = 2f)
        drawCircle(color = simColor(50f, 0.9f), radius = r * 0.14f, center = Offset(tx, ty))
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
