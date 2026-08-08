package com.tickclear.app.ui.tools

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.tickclear.app.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import com.tickclear.app.domain.tools.FoleySynth
import com.tickclear.app.ui.components.Haptic
import com.tickclear.app.ui.theme.Spacing
import androidx.compose.runtime.withFrameMillis

/**
 * 拟物敲木鱼（V2.9++ 模拟解压）。
 * 点一下敲一下、计数 + 木鱼「笃」声 + 触觉反馈；小木锤抬起→敲击的动画 + 浮起淡出的「+1」
 * 提供即时、拟真的视觉回报（功德+1）。纯 Canvas + 真实录音，零新依赖。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimWoodFishScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val motionReduced = remember { isMotionReduced(context) }
    var count by remember { mutableIntStateOf(0) }
    var particles by remember { mutableStateOf(emptyList<SimParticle>()) }
    var punch by remember { mutableFloatStateOf(0f) }
    var mallet by remember { mutableFloatStateOf(0f) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    var pluses by remember { mutableStateOf(emptyList<MeritPlus>()) }
    var nextPlusId by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        onDispose { FoleySynth.stop() }
    }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            val now = withFrameMillis { it }
            val dt = if (last == 0L) 0.016f else ((now - last) / 1000f).coerceAtMost(0.05f)
            last = now
            if (!motionReduced) {
                if (particles.isNotEmpty()) particles = stepParticles(particles, dt)
                if (punch > 0.001f) punch *= 0.85f else punch = 0f
                if (mallet > 0.001f) mallet *= 0.82f else mallet = 0f
            }
        }
    }

    fun knock(offset: Offset) {
        count++
        val xDp = (offset.x / canvasSize.width).coerceIn(0f, 1f)
        val yDp = (offset.y / canvasSize.height).coerceIn(0f, 1f)
        if (!motionReduced) {
            punch = 1f
            mallet = 1f
            particles = particles + SimParticle(
                x = xDp, y = yDp, vx = 0f, vy = 0f,
                life = 0.6f, maxLife = 0.6f, hue = 30f, radius = 8f, ring = true,
            )
        }
        val txDp = with(density) { offset.x.toDp() }
        val tyDp = with(density) { offset.y.toDp() }
        pluses = pluses + MeritPlus(nextPlusId++, txDp, tyDp)
        FoleySynth.playWood(context)
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
                horizontal = true,
            )
            Spacer(Modifier.height(Spacing.md))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { offset -> knock(offset) }
                    },
                contentAlignment = Alignment.TopStart,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    canvasSize = size
                    val w = size.width
                    val h = size.height
                    val cx = w / 2f
                    val cy = h / 2f
                    // 敲击挤压：横向压扁 + 轻微下沉，由 punch 衰减驱动
                    val sx = 1f - punch * 0.06f
                    val sy = 1f + punch * 0.05f
                    val bodyW = w * 0.58f * sx
                    val bodyH = h * 0.32f * sy
                    val bodyX = cx - bodyW / 2f
                    val bodyY = cy - bodyH / 2f + punch * bodyH * 0.04f

                    // 接触投影
                    drawSoftShadow(Offset(cx, cy + bodyH * 0.62f), bodyW * 0.5f, bodyH * 0.22f, maxAlpha = 0.32f)

                    // 木鱼主体：受光圆角矩形（木质暖棕渐变），更圆润像木鱼
                    fillRoundRect3D(
                        topLeft = Offset(bodyX, bodyY),
                        size = Size(bodyW, bodyH),
                        cornerRadius = bodyH * 0.92f,
                        base = Color(0xFFB5835A),
                    )
                    // 顶部受光高光带
                    drawGloss(
                        Offset(bodyX + bodyW * 0.32f, bodyY + bodyH * 0.18f),
                        bodyW * 0.24f, bodyH * 0.10f, 0.45f,
                    )
                    // 木纹雕刻：两道柔和暗色曲线
                    val grain1 = Path().apply {
                        moveTo(bodyX + bodyW * 0.16f, bodyY + bodyH * 0.60f)
                        quadraticTo(bodyX + bodyW * 0.5f, bodyY + bodyH * 0.80f, bodyX + bodyW * 0.84f, bodyY + bodyH * 0.60f)
                    }
                    drawPath(grain1, color = Color(0xFF8A5A36).copy(alpha = 0.18f), style = Stroke(width = max(1f, bodyH * 0.03f)))
                    val grain2 = Path().apply {
                        moveTo(bodyX + bodyW * 0.22f, bodyY + bodyH * 0.74f)
                        quadraticTo(bodyX + bodyW * 0.5f, bodyY + bodyH * 0.90f, bodyX + bodyW * 0.78f, bodyY + bodyH * 0.74f)
                    }
                    drawPath(grain2, color = Color(0xFF8A5A36).copy(alpha = 0.12f), style = Stroke(width = max(1f, bodyH * 0.022f)))
                    // 边缘辉光：浅木色描边，强化受光轮廓
                    drawRoundRect(
                        color = Color(0xFFE0B483).copy(alpha = 0.30f),
                        topLeft = Offset(bodyX, bodyY),
                        size = Size(bodyW, bodyH),
                        cornerRadius = CornerRadius(bodyH * 0.92f, bodyH * 0.92f),
                        style = Stroke(width = max(1f, bodyH * 0.03f)),
                    )
                    // 左侧圆头鼓包（木鱼头）
                    val headCx = bodyX - bodyW * 0.06f
                    val headCy = bodyY + bodyH * 0.5f
                    val headR = bodyH * 0.32f
                    fillSphere(Offset(headCx, headCy), headR, Color(0xFFA9744C), rimLight = false)
                    drawRimLight(Offset(headCx, headCy), headR, Color(0xFFC89070), alpha = 0.32f)
                    // 雕刻开口（鱼嘴槽）：深色圆角矩形 + 内壁暗影
                    val slotW = bodyW * 0.46f
                    val slotH = bodyH * 0.18f
                    val slotX = bodyX + bodyW * 0.06f
                    val slotY = bodyY + bodyH * 0.34f
                    drawRoundRect(
                        color = Color(0xFF3A2414),
                        topLeft = Offset(slotX, slotY),
                        size = Size(slotW, slotH),
                        cornerRadius = CornerRadius(slotH / 2f, slotH / 2f),
                    )
                    drawRoundRect(
                        color = Color(0xFF5A3A22).copy(alpha = 0.6f),
                        topLeft = Offset(slotX, slotY + slotH * 0.45f),
                        size = Size(slotW, slotH * 0.55f),
                        cornerRadius = CornerRadius(slotH / 2f, slotH / 2f),
                    )
                    // 小眼点（两颗，靠近鱼头）
                    drawCircle(
                        color = Color(0xFF3A2414),
                        radius = bodyH * 0.05f,
                        center = Offset(bodyX + bodyW * 0.16f, bodyY + bodyH * 0.30f),
                    )
                    drawCircle(
                        color = Color(0xFF3A2414),
                        radius = bodyH * 0.035f,
                        center = Offset(bodyX + bodyW * 0.22f, bodyY + bodyH * 0.26f),
                    )

                    // ---------- 小木锤（抬起→敲击） ----------
                    // pivot 在右上方，木锤沿「向左」方向伸出；角度为正时上扬（静止），为负时下落敲击
                    val restAngle = 22f
                    val strikeAngle = -26f
                    val e = (mallet * (2f - mallet)).coerceIn(0f, 1f) // easeOut，下落更利落
                    val angle = restAngle + (strikeAngle - restAngle) * e
                    val aRad = angle * PI.toFloat() / 180f
                    val pivot = Offset(cx + bodyW * 0.50f, cy - bodyH * 0.55f)
                    val L = bodyW * 0.60f
                    val dirX = -cos(aRad).toFloat()
                    val dirY = -sin(aRad).toFloat()
                    val tip = Offset(pivot.x + dirX * L, pivot.y + dirY * L)
                    val handleW = bodyH * 0.16f
                    val malletHeadR = bodyH * 0.24f
                    // 手柄
                    drawLine(
                        color = Color(0xFF9C6B3F),
                        start = pivot, end = tip,
                        strokeWidth = handleW, cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = Color(0xFF7A4F2C).copy(alpha = 0.5f),
                        start = pivot, end = tip,
                        strokeWidth = handleW * 0.4f, cap = StrokeCap.Round,
                    )
                    // 锤头
                    fillSphere(tip, malletHeadR, Color(0xFFC08A5A), rimLight = true)
                    drawRimLight(tip, malletHeadR, Color(0xFFE0B483), alpha = 0.30f)

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

                // 浮起淡出的「+1」（功德+1）
                pluses.forEach { p ->
                    key(p.id) {
                        MeritPlusText(p) { pluses = pluses - p }
                    }
                }
            }
            Spacer(Modifier.height(Spacing.md))
        }
    }
}

/** 一次「功德+1」浮字：出现在敲击点，上浮并淡出后自我移除。 */
private data class MeritPlus(val id: Int, val xDp: Dp, val yDp: Dp)

@Composable
private fun MeritPlusText(plus: MeritPlus, onDone: () -> Unit) {
    val plusAlpha = remember { Animatable(1f) }
    val move = remember { Animatable(0f) } // 像素，向上为负
    LaunchedEffect(Unit) {
        launch { plusAlpha.animateTo(0f, tween(850, easing = FastOutSlowInEasing)) }
        launch { move.animateTo(-72f, tween(850, easing = FastOutSlowInEasing)) }
        onDone()
    }
    Box(
        modifier = Modifier
            .offset(x = plus.xDp, y = plus.yDp)
            .graphicsLayer { alpha = plusAlpha.value; translationY = move.value },
    ) {
        Text(
            "+1",
            style = MaterialTheme.typography.headlineSmall,
            color = Color(0xFF3A2414).copy(alpha = 0.35f),
            modifier = Modifier.offset(1.dp, 1.dp),
        )
        Text(
            "+1",
            style = MaterialTheme.typography.headlineSmall,
            color = Color(0xFFFFD54F),
        )
    }
}
