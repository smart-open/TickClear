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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tickclear.app.R
import kotlin.math.max
import com.tickclear.app.domain.tools.FoleySynth
import com.tickclear.app.ui.components.Haptic
import com.tickclear.app.ui.theme.Spacing
import androidx.compose.runtime.withFrameMillis

/**
 * 拟物敲木鱼（V2.9++ 模拟解压）。
 * 点一下敲一下、计数 + 木鱼“笃”声 + 触觉反馈，敲出的涟漪圈提供即时视觉回报。
 * 纯 Canvas + AudioTrack 合成，零新依赖。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimWoodFishScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var count by remember { mutableIntStateOf(0) }
    var particles by remember { mutableStateOf(emptyList<SimParticle>()) }
    var punch by remember { mutableFloatStateOf(0f) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    DisposableEffect(Unit) {
        onDispose { FoleySynth.stop() }
    }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            val now = withFrameMillis { it }
            val dt = if (last == 0L) 0.016f else ((now - last) / 1000f).coerceAtMost(0.05f)
            last = now
            if (particles.isNotEmpty()) particles = stepParticles(particles, dt)
            if (punch > 0.001f) punch *= 0.85f else punch = 0f
        }
    }

    fun knock(nx: Float, ny: Float) {
        count++
        punch = 1f
        particles = particles + SimParticle(
            x = nx, y = ny, vx = 0f, vy = 0f,
            life = 0.6f, maxLife = 0.6f, hue = 30f, radius = 8f, ring = true,
        )
        FoleySynth.play("wood")
        Haptic.vibrate(context, 40)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_sim_wood_title)) },
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
            SimHintCard(stringResource(R.string.tools_sim_wood_hint))
            Spacer(Modifier.height(Spacing.sm))
            SimStatCard(
                value = count.toString(),
                label = stringResource(R.string.tools_unit_times),
            )
            Spacer(Modifier.height(Spacing.md))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .height(320.dp)
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
                    val cy = h / 2f
                    // 敲击挤压：横向压扁 + 轻微下沉，弹簧回弹由 punch 衰减驱动
                    val sx = 1f - punch * 0.06f
                    val sy = 1f + punch * 0.05f
                    val bodyW = w * 0.56f * sx
                    val bodyH = h * 0.34f * sy
                    val bodyX = cx - bodyW / 2f
                    val bodyY = cy - bodyH / 2f + punch * bodyH * 0.04f

                    // 接触投影（二巡：软阴影，半影更自然）
                    drawSoftShadow(Offset(cx, cy + bodyH * 0.62f), bodyW * 0.5f, bodyH * 0.22f, maxAlpha = 0.32f)

                    // 木鱼主体：受光圆角矩形（木质暖棕渐变）
                    fillRoundRect3D(
                        topLeft = Offset(bodyX, bodyY),
                        size = Size(bodyW, bodyH),
                        cornerRadius = bodyH * 0.5f,
                        base = Color(0xFFB5835A),
                    )
                    // 木纹层次：两道内嵌暗木色描边，模拟雕刻木纹环
                    val grainColor = Color(0xFF8A5A36)
                    drawRoundRect(
                        color = grainColor.copy(alpha = 0.14f),
                        topLeft = Offset(bodyX + bodyW * 0.06f, bodyY + bodyH * 0.14f),
                        size = Size(bodyW * 0.88f, bodyH * 0.72f),
                        cornerRadius = CornerRadius(bodyH * 0.42f, bodyH * 0.42f),
                        style = Stroke(width = max(1f, bodyH * 0.025f)),
                    )
                    drawRoundRect(
                        color = grainColor.copy(alpha = 0.10f),
                        topLeft = Offset(bodyX + bodyW * 0.14f, bodyY + bodyH * 0.30f),
                        size = Size(bodyW * 0.72f, bodyH * 0.40f),
                        cornerRadius = CornerRadius(bodyH * 0.30f, bodyH * 0.30f),
                        style = Stroke(width = max(1f, bodyH * 0.02f)),
                    )
                    // 边缘辉光：浅木色描边，强化受光轮廓
                    drawRoundRect(
                        color = Color(0xFFE0B483).copy(alpha = 0.28f),
                        topLeft = Offset(bodyX, bodyY),
                        size = Size(bodyW, bodyH),
                        cornerRadius = CornerRadius(bodyH * 0.5f, bodyH * 0.5f),
                        style = Stroke(width = max(1f, bodyH * 0.03f)),
                    )
                    // 左侧圆头鼓包（关掉纯白 rim，改用木色辉光边）
                    val headCx = bodyX - bodyW * 0.10f
                    val headCy = bodyY + bodyH * 0.5f
                    val headR = bodyH * 0.42f
                    fillSphere(Offset(headCx, headCy), headR, Color(0xFFA9744C), rimLight = false)
                    drawRimLight(Offset(headCx, headCy), headR, Color(0xFFC89070), alpha = 0.32f)
                    // 顶部高光带
                    drawGloss(
                        Offset(bodyX + bodyW * 0.30f, bodyY + bodyH * 0.20f),
                        bodyW * 0.22f, bodyH * 0.10f, 0.45f,
                    )
                    // 雕刻开口（鱼嘴槽）：深色圆角矩形 + 内壁暗影
                    val slotW = bodyW * 0.34f
                    val slotH = bodyH * 0.16f
                    val slotX = bodyX + bodyW * 0.04f
                    val slotY = bodyY + bodyH * 0.40f
                    drawRoundRect(
                        color = Color(0xFF3A2414),
                        topLeft = Offset(slotX, slotY),
                        size = Size(slotW, slotH),
                        cornerRadius = CornerRadius(slotH / 2f, slotH / 2f),
                    )
                    drawRoundRect(
                        color = Color(0xFF5A3A22).copy(alpha = 0.6f),
                        topLeft = Offset(slotX, slotY + slotH * 0.5f),
                        size = Size(slotW, slotH * 0.5f),
                        cornerRadius = CornerRadius(slotH / 2f, slotH / 2f),
                    )
                    // 小眼点
                    drawCircle(
                        color = Color(0xFF3A2414),
                        radius = bodyH * 0.05f,
                        center = Offset(bodyX + bodyW * 0.20f, bodyY + bodyH * 0.30f),
                    )

                    // 敲击涟漪（复用 SimParticle ring）
                    for (pt in particles) {
                        val a = (pt.life / pt.maxLife).coerceIn(0f, 1f)
                        drawOval(
                            color = simColor(pt.hue, a * 0.8f),
                            topLeft = Offset(pt.x * w - pt.radius, pt.y * h - pt.radius),
                            size = Size(pt.radius * 2, pt.radius * 2),
                            style = Stroke(width = 3f * a + 1f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(Spacing.md))
        }
    }
}
