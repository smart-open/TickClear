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
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.withFrameMillis
import com.tickclear.app.R
import com.tickclear.app.domain.tools.GlassSynth
import com.tickclear.app.ui.components.Haptic
import com.tickclear.app.ui.theme.Spacing
import kotlin.math.roundToInt

/**
 * 拟声玻璃杯敲击（V2.9++ 模拟解压）。
 * 7 个一模一样的玻璃杯水平排列，装有不同高度的水（水越多音越低），
 * 点一下某个杯子就敲出对应音符（do re mi fa sol la ti → 1234567）。
 * 真实钢琴单音素材优先（GlassSynth），零新依赖。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassCupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var particles by remember { mutableStateOf(emptyList<SimParticle>()) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    var note by remember { mutableIntStateOf(0) } // 0=未敲；1..7=当前音符（=杯子序号）
    val solfege = stringResource(R.string.sim_glass_solfege).split('|')

    DisposableEffect(Unit) {
        onDispose { GlassSynth.stop() }
    }

    // 仅在存在涟漪粒子时运行帧循环，静止即挂起（省电 + reduced-motion 基线）
    val glassAnimating = particles.isNotEmpty()
    LaunchedEffect(glassAnimating) {
        if (!glassAnimating) return@LaunchedEffect
        var last = 0L
        while (particles.isNotEmpty()) {
            val now = withFrameMillis { it }
            val dt = if (last == 0L) 0.016f else ((now - last) / 1000f).coerceAtMost(0.05f)
            last = now
            particles = stepParticles(particles, dt)
        }
    }

    fun knock(cup: Int, nx: Float, ny: Float) {
        note = cup
        particles = particles + SimParticle(
            x = nx, y = ny, vx = 0f, vy = 0f,
            life = 0.6f, maxLife = 0.6f, hue = 195f, radius = 8f, ring = true,
        )
        GlassSynth.play(context, cup)
        Haptic.vibrate(context, 25)
    }

    // 主题色须在组合作用域内取值（MaterialTheme.colorScheme 是 @Composable getter），
    // 不能放进 Canvas 的 DrawScope lambda，故先在此捕获为普通 Color 再传入。
    val outlineColor = MaterialTheme.colorScheme.outline
    val primaryColor = MaterialTheme.colorScheme.primary

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sim_glass_title)) },
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
            SimHintCard(stringResource(R.string.sim_glass_hint))
            Spacer(Modifier.height(Spacing.sm))
            // 当前音符卡片：数字与唱名同行（如「4 发」），水平单行不换行、居中
            SimStatCard(
                value = if (note == 0) "—" else note.toString(),
                label = if (note == 0) stringResource(R.string.sim_glass_tap) else solfege.getOrElse(note - 1) { "" },
                horizontal = true,
            )
            Spacer(Modifier.height(Spacing.md))

            // 7 个玻璃杯：画布内按 x 划分 7 个等宽槽位，点哪个槽位敲哪个杯子
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val w = canvasSize.width
                            val cup = if (w > 0) ((offset.x / w) * 7f).roundToInt().coerceIn(0, 6) else 0
                            val nx = if (w > 0) offset.x / w else 0.5f
                            val ny = if (canvasSize.height > 0) offset.y / canvasSize.height else 0.5f
                            knock(cup + 1, nx, ny)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    canvasSize = size
                    val w = size.width
                    val h = size.height
                    val n = 7
                    val slotW = w / n
                    val topY = h * 0.10f
                    val botY = h * 0.90f
                    val cupW = slotW * 0.60f // 杯口宽
                    val cupWB = cupW * 0.82f // 杯底宽（略收口）

                    for (i in 0 until n) {
                        val cx = slotW * (i + 0.5f)
                        val selected = (note - 1) == i
                        // 水越多音越低：杯 0(最低音)最满，杯 6(最高音)最浅
                        val fill = 0.85f - (i / 6f) * 0.70f
                        drawGlassCup(
                            cx = cx, topY = topY, botY = botY,
                            topW = cupW, botW = cupWB, fill = fill,
                            selected = selected,
                            primary = primaryColor, outline = outlineColor,
                        )
                    }

                    // 敲击涟漪（立体环 + 内圈高光）
                    for (pt in particles) {
                        val a = (pt.life / pt.maxLife).coerceIn(0f, 1f)
                        val rad = pt.radius
                        drawOval(
                            color = simColor(pt.hue, a * 0.8f),
                            topLeft = Offset(pt.x * w - rad, pt.y * h - rad),
                            size = Size(rad * 2, rad * 2),
                            style = Stroke(width = 3f * a + 1f),
                        )
                        drawOval(
                            color = Color.White.copy(alpha = a * 0.4f),
                            topLeft = Offset(pt.x * w - rad * 0.6f, pt.y * h - rad * 0.6f),
                            size = Size(rad * 1.2f, rad * 1.2f),
                            style = Stroke(width = 1f),
                        )
                    }
                }
            }

            // 每个杯子下方的唱名标签，当前敲中的高亮
            Spacer(Modifier.height(Spacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                for (i in 0 until 7) {
                    val active = (note - 1) == i
                    Text(
                        text = solfege.getOrElse(i) { "" },
                        style = if (active) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
                        color = if (active) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(36.dp),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.md))
        }
    }
}

/**
 * 在 DrawScope 内绘制一个玻璃杯：半透明杯身 + 木纹般的水 + 杯口/水面椭圆 + 高光条。
 * [fill] 为水面距杯底的比例（0=空，1=满）；[selected] 时高亮并加柔光。
 */
private fun DrawScope.drawGlassCup(
    cx: Float, topY: Float, botY: Float,
    topW: Float, botW: Float, fill: Float,
    selected: Boolean, primary: Color, outline: Color,
) {
    val h = botY - topY
    val topL = cx - topW / 2f
    val topR = cx + topW / 2f
    val botL = cx - botW / 2f
    val botR = cx + botW / 2f

    // 接地软阴影：让玻璃杯“落地”而非悬浮
    drawSoftShadow(
        center = Offset(cx, botY + 10f),
        radiusX = botW * 0.6f,
        radiusY = 11f,
        maxAlpha = 0.18f,
    )

    // 杯体（梯形），选中的加柔光底
    if (selected) {
        drawOval(
            color = primary.copy(alpha = 0.16f),
            topLeft = Offset(topL - 6f, topY - 12f),
            size = Size(topW + 12f, h + 24f),
        )
    }
    val body = Path().apply {
        moveTo(topL, topY)
        lineTo(topR, topY)
        lineTo(botR, botY)
        lineTo(botL, botY)
        close()
    }
    // 玻璃杯壁：半透明横向渐变（左暗→高光带→右暗），保留通透感
    drawPath(
        path = body,
        brush = Brush.horizontalGradient(
            0f to Color.White.copy(alpha = 0.10f),
            0.25f to Color.White.copy(alpha = 0.50f),
            0.5f to Color.White.copy(alpha = 0.16f),
            0.75f to Color.White.copy(alpha = 0.38f),
            1f to Color.White.copy(alpha = 0.08f),
            startX = topL,
            endX = topR,
        ),
    )
    drawPath(path = body, color = if (selected) primary else outline, style = Stroke(width = if (selected) 4f else 2.5f))

    // 水：液面以下淡蓝渐变（杯身梯形内按高度插值半宽）
    val waterY = botY - h * fill
    val t = ((waterY - topY) / h).coerceIn(0f, 1f)
    val wHalf = (botW / 2f) + (topW / 2f - botW / 2f) * t
    val wl = cx - wHalf
    val wr = cx + wHalf
    val water = Path().apply {
        moveTo(wl, waterY)
        lineTo(wr, waterY)
        lineTo(botR, botY)
        lineTo(botL, botY)
        close()
    }
    drawPath(
        path = water,
        brush = Brush.verticalGradient(
            0f to simColor(200f, 0.30f),
            1f to simColor(210f, 0.52f),
            startY = waterY,
            endY = botY,
        ),
    )
    // 水面椭圆（俯视）+ 水线高光
    drawOval(color = simColor(200f, 0.60f), topLeft = Offset(wl, waterY - 5f), size = Size(wr - wl, 10f))
    drawOval(color = Color.White.copy(alpha = 0.30f), topLeft = Offset(wl + 3f, waterY - 3f), size = Size((wr - wl) - 6f, 6f))

    // 杯口椭圆（3D 口沿）
    drawOval(color = if (selected) primary else outline, topLeft = Offset(topL, topY - 7f), size = Size(topW, 14f))
    drawOval(color = Color.White.copy(alpha = 0.35f), topLeft = Offset(topL + 3f, topY - 5f), size = Size(topW - 6f, 10f))

    // 左侧竖直高光条（玻璃反光）
    drawRoundRect(
        brush = Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.0f),
            0.5f to Color.White.copy(alpha = 0.5f),
            1f to Color.White.copy(alpha = 0.0f),
            startY = topY,
            endY = botY,
        ),
        topLeft = Offset(topL + topW * 0.10f, topY + 8f),
        size = Size(topW * 0.06f, h - 16f),
        cornerRadius = CornerRadius(topW * 0.03f),
    )
}
