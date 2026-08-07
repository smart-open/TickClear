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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.withFrameMillis
import com.tickclear.app.R
import com.tickclear.app.domain.tools.GlassSynth
import com.tickclear.app.ui.components.Haptic
import com.tickclear.app.ui.theme.Spacing
import kotlin.math.roundToInt

/**
 * 拟声玻璃杯敲击（V2.9++ 模拟解压）。
 * 点击杯身不同位置，越靠杯口音越高、越靠杯底音越低，敲出 1234567 的清脆叮声。
 * 纯 Canvas + AudioTrack 合成，零新依赖。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassCupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var particles by remember { mutableStateOf(emptyList<SimParticle>()) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    var note by remember { mutableIntStateOf(0) } // 0=未敲；1..7=当前音符
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

    fun knock(nx: Float, ny: Float) {
        // ny=0 顶部(杯口)→高音(7)，ny=1 底部(杯底)→低音(1)
        val idx = ((1f - ny) * 7f).roundToInt().coerceIn(1, 7)
        note = idx
        particles = particles + SimParticle(
            x = nx, y = ny, vx = 0f, vy = 0f,
            life = 0.6f, maxLife = 0.6f, hue = 195f, radius = 8f, ring = true,
        )
        GlassSynth.play(idx)
        Haptic.vibrate(context, 25)
    }

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
            SimStatCard(
                value = if (note == 0) "—" else note.toString(),
                label = if (note == 0) stringResource(R.string.sim_glass_tap) else solfege.getOrElse(note - 1) { "" },
            )
            Spacer(Modifier.height(Spacing.md))

            // 主题色须在组合作用域内取值（MaterialTheme.colorScheme 是 @Composable getter），
            // 不能放进 Canvas 的 DrawScope lambda，故先在此捕获为普通 Color 再传入。
            val outlineColor = MaterialTheme.colorScheme.outline
            val primaryColor = MaterialTheme.colorScheme.primary

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .height(360.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val nx = if (canvasSize.width > 0) offset.x / canvasSize.width else 0.5f
                            val ny = if (canvasSize.height > 0) offset.y / canvasSize.height else 0.5f
                            knock(nx, ny)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    canvasSize = size
                    val w = size.width
                    val h = size.height
                    val cx = w / 2f
                    val topY = h * 0.14f
                    val botY = h * 0.86f
                    val topW = w * 0.52f
                    val botW = w * 0.36f

                    // 接地软阴影：让玻璃杯"落地"而非悬浮（二巡精修）
                    drawSoftShadow(
                        center = Offset(cx, botY + 12f),
                        radiusX = botW * 0.62f,
                        radiusY = 13f,
                        maxAlpha = 0.20f,
                    )

                    // ===== 3D 玻璃杯体（横向渐变模拟圆柱受光） =====
                    val body = Path().apply {
                        moveTo(cx - topW / 2f, topY)
                        lineTo(cx + topW / 2f, topY)
                        lineTo(cx + botW / 2f, botY)
                        lineTo(cx - botW / 2f, botY)
                        close()
                    }
                    // 玻璃杯壁：半透明横向渐变（左暗→高光带→右暗），保留通透感
                    drawPath(
                        path = body,
                        brush = Brush.horizontalGradient(
                            0f to Color.White.copy(alpha = 0.10f),
                            0.22f to Color.White.copy(alpha = 0.55f),
                            0.5f to Color.White.copy(alpha = 0.18f),
                            0.78f to Color.White.copy(alpha = 0.40f),
                            1f to Color.White.copy(alpha = 0.08f),
                            startX = cx - topW / 2f,
                            endX = cx + topW / 2f,
                        ),
                    )
                    drawPath(path = body, color = outlineColor, style = Stroke(width = 3f))

                    // 杯口高光：强化玻璃反光的"湿润感"（二巡精修）
                    drawGloss(
                        center = Offset(cx - topW * 0.12f, topY + 30f),
                        radiusX = topW * 0.14f,
                        radiusY = (botY - topY) * 0.16f,
                        alpha = 0.45f,
                    )

                    // 液面高度随当前音符（note 1..7 → 由下至上）；未敲时半杯
                    val liquidFrac = if (note == 0) 0.5f else (note - 1) / 6f
                    val liquidY = botY - (botY - topY) * liquidFrac
                    val liqW = topW + (botW - topW) * ((liquidY - topY) / (botY - topY))
                    // 水：液面以下淡蓝渐变
                    val water = Path().apply {
                        moveTo(cx - liqW / 2f, liquidY)
                        lineTo(cx + liqW / 2f, liquidY)
                        lineTo(cx + botW / 2f, botY)
                        lineTo(cx - botW / 2f, botY)
                        close()
                    }
                    drawPath(
                        path = water,
                        brush = Brush.verticalGradient(
                            0f to simColor(195f, 0.22f),
                            1f to simColor(205f, 0.42f),
                            startY = liquidY,
                            endY = botY,
                        ),
                    )
                    // 水面椭圆（俯视）+ 水线高光
                    drawOval(
                        color = simColor(195f, 0.55f),
                        topLeft = Offset(cx - liqW / 2f, liquidY - 7f),
                        size = Size(liqW, 14f),
                    )
                    drawOval(
                        color = Color.White.copy(alpha = 0.35f),
                        topLeft = Offset(cx - liqW / 2f + 3f, liquidY - 5f),
                        size = Size(liqW - 6f, 9f),
                    )

                    // 杯口椭圆（3D 口沿）
                    drawOval(
                        color = outlineColor,
                        topLeft = Offset(cx - topW / 2f, topY - 8f),
                        size = Size(topW, 16f),
                    )
                    drawOval(
                        color = Color.White.copy(alpha = 0.35f),
                        topLeft = Offset(cx - topW / 2f + 3f, topY - 6f),
                        size = Size(topW - 6f, 12f),
                    )

                    // 左侧竖直高光条（玻璃反光）
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.0f),
                            0.5f to Color.White.copy(alpha = 0.5f),
                            1f to Color.White.copy(alpha = 0.0f),
                            startY = topY,
                            endY = botY,
                        ),
                        topLeft = Offset(cx - topW * 0.30f, topY + 12f),
                        size = Size(topW * 0.055f, botY - topY - 24f),
                        cornerRadius = CornerRadius(topW * 0.03f),
                    )

                    // 7 个音符刻度：顶部=7(高)，底部=1(低)
                    for (k in 1..7) {
                        val y = topY + (botY - topY) * ((7 - k) / 6f)
                        val selected = note == k
                        drawCircle(
                            color = if (selected) primaryColor
                            else primaryColor.copy(alpha = 0.5f),
                            radius = if (selected) 9f else 6f,
                            center = Offset(cx, y),
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
            Spacer(Modifier.height(Spacing.md))
        }
    }
}
