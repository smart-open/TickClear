package com.tickclear.app.ui.tools

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tickclear.app.R
import com.tickclear.app.domain.tools.FoleySynth
import com.tickclear.app.ui.components.Haptic
import com.tickclear.app.ui.theme.Spacing
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch

/**
 * 虚拟打火机（V2.9++ 模拟解压）。
 * 向上滑动开盖（火石轮打火）→ 火焰动画 + “咔哒”音效；可再次滑动或点“收起”熄灭火焰。
 * 纯 Canvas + 拖拽手势 + AudioTrack 合成，零新依赖。
 *
 * V2.9++ 美化：机身改为金属圆柱受光 + 竖直高光 + 齿纹火石轮 + 接触投影；
 * 盖子松手后走弹簧动画（原本是瞬间吸附，很硬）；火焰改用共享 [drawFlame]（光晕/内外焰/蓝焰根）。
 * 性能：帧循环仅在点燃时运行，熄灭后自动挂起（原实现常驻空转 + withFrameMillis 与 delay 双重节流）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimLighterScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var lidProgress by remember { mutableFloatStateOf(0f) } // 0 关盖 / 1 开盖
    var lit by remember { mutableStateOf(false) }
    var flicker by remember { mutableFloatStateOf(1f) }
    var lean by remember { mutableFloatStateOf(0f) }
    // 无障碍：系统关闭动画时不做火焰摇曳
    val motionReduced = remember(context) { isMotionReduced(context) }

    DisposableEffect(Unit) {
        onDispose { FoleySynth.stop() }
    }

    fun animateLid(target: Float) {
        scope.launch {
            animate(
                initialValue = lidProgress,
                targetValue = target,
                animationSpec = spring(dampingRatio = 0.58f, stiffness = 520f),
            ) { v, _ -> lidProgress = v }
        }
    }

    fun ignite() {
        animateLid(1f)
        lit = true
        FoleySynth.play("lighter")
        Haptic.vibrate(context, 50)
    }

    fun closeLid() {
        animateLid(0f)
        lit = false
    }

    // 火焰摇曳：只在点燃时驱动，熄灭即挂起（省电）
    LaunchedEffect(lit, motionReduced) {
        if (!lit || motionReduced) {
            flicker = 1f
            lean = 0f
            return@LaunchedEffect
        }
        var last = 0L
        var t = 0f
        while (true) {
            val now = withFrameMillis { it }
            val dt = if (last == 0L) 0.016f else ((now - last) / 1000f).coerceAtMost(0.05f)
            last = now
            t += dt
            // 双频正弦叠加：比逐帧随机自然，不会抖成噪点
            flicker = 0.88f + sin(t * 11f) * 0.07f + sin(t * 27f) * 0.05f
            lean = sin(t * 6f) * 0.20f + sin(t * 15f) * 0.09f
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_sim_lighter_title)) },
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
            SimHintCard(
                if (lit) {
                    stringResource(R.string.tools_sim_lighter_lit)
                } else {
                    stringResource(R.string.tools_sim_lighter_open_hint)
                },
            )
            Spacer(Modifier.height(Spacing.md))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .height(340.dp)
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            // 向上拖（delta<0）开盖，向下拖收盖
                            lidProgress = (lidProgress - delta / 240f).coerceIn(0f, 1f)
                        },
                        onDragStopped = {
                            if (lidProgress > 0.55f) {
                                if (lit) animateLid(1f) else ignite()
                            } else {
                                closeLid()
                            }
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val bodyW = w * 0.30f
                    val bodyH = h * 0.5f
                    val cx = w / 2f
                    val bodyBottom = h * 0.86f
                    val bodyTop = bodyBottom - bodyH
                    val bodyX = cx - bodyW / 2f
                    val chrome = Color(0xFFCBD2D9)

                    drawSoftShadow(
                        center = Offset(cx, bodyBottom + bodyH * 0.05f),
                        radiusX = bodyW * 0.95f,
                        radiusY = bodyH * 0.05f,
                        maxAlpha = 0.32f,
                    )
                    // 机身：金属圆柱受光
                    fillCylinder(
                        topLeft = Offset(bodyX, bodyTop),
                        size = Size(bodyW, bodyH),
                        base = chrome,
                        cornerRadius = bodyW * 0.14f,
                    )
                    // 二巡：机身一道锐利金属反光条
                    drawLine(
                        color = Color.White.copy(alpha = 0.40f),
                        start = Offset(bodyX + bodyW * 0.18f, bodyTop + bodyH * 0.05f),
                        end = Offset(bodyX + bodyW * 0.18f, bodyTop + bodyH * 0.92f),
                        strokeWidth = bodyW * 0.02f,
                    )
                    // 底部包边
                    fillCylinder(
                        topLeft = Offset(bodyX, bodyBottom - bodyH * 0.11f),
                        size = Size(bodyW, bodyH * 0.11f),
                        base = chrome.darken(0.30f),
                        cornerRadius = bodyW * 0.10f,
                    )
                    // 竖直高光条：金属质感的关键
                    drawGloss(
                        center = Offset(bodyX + bodyW * 0.27f, bodyTop + bodyH * 0.46f),
                        radiusX = bodyW * 0.10f,
                        radiusY = bodyH * 0.34f,
                        alpha = 0.42f,
                    )
                    // 铰链分缝
                    drawLine(
                        color = chrome.darken(0.45f),
                        start = Offset(bodyX + bodyW * 0.06f, bodyTop + bodyH * 0.02f),
                        end = Offset(bodyX + bodyW * 0.94f, bodyTop + bodyH * 0.02f),
                        strokeWidth = bodyW * 0.02f,
                    )
                    // 出气口
                    fillRoundRect3D(
                        topLeft = Offset(cx - bodyW * 0.11f, bodyTop - bodyH * 0.035f),
                        size = Size(bodyW * 0.22f, bodyH * 0.06f),
                        cornerRadius = bodyW * 0.03f,
                        base = chrome.darken(0.42f),
                    )
                    // 火石轮：开盖进度驱动转动，带齿纹
                    val wheelC = Offset(cx + bodyW * 0.26f, bodyTop + bodyH * 0.06f)
                    val wheelR = bodyW * 0.15f
                    fillSphere(center = wheelC, radius = wheelR, base = Color(0xFF8D9298), rimLight = false)
                    // 二巡：火石轮辉光边
                    drawRimLight(wheelC, wheelR, Color(0xFFBFC4CA), alpha = 0.40f)
                    val spin = lidProgress * 220f
                    for (i in 0 until 14) {
                        val ang = Math.toRadians((spin + i * (360f / 14)).toDouble())
                        val c = cos(ang).toFloat()
                        val s = sin(ang).toFloat()
                        drawLine(
                            color = Color(0xFF5A5F65),
                            start = Offset(wheelC.x + c * wheelR * 0.55f, wheelC.y + s * wheelR * 0.55f),
                            end = Offset(wheelC.x + c * wheelR * 0.95f, wheelC.y + s * wheelR * 0.95f),
                            strokeWidth = bodyW * 0.015f,
                        )
                    }
                    drawCircle(
                        color = Color.White.copy(alpha = 0.35f),
                        radius = wheelR * 0.95f,
                        center = wheelC,
                        style = Stroke(width = bodyW * 0.012f),
                    )

                    // 火焰（画在盖子之前，关盖时被盖住；开盖进度同时控制高度与透明度）
                    if (lit) {
                        val flameHalfW = bodyW * 0.20f * flicker
                        drawFlame(
                            baseX = cx,
                            baseY = bodyTop - bodyH * 0.025f,
                            halfWidth = flameHalfW,
                            height = bodyH * 0.34f * flicker * lidProgress,
                            leanX = lean * flameHalfW,
                            alpha = lidProgress,
                        )
                    }

                    // 盖子：绕铰链翻起
                    val lift = lidProgress * bodyH * 0.30f
                    rotate(-lidProgress * 46f, pivot = Offset(bodyX, bodyTop)) {
                        fillCylinder(
                            topLeft = Offset(bodyX, bodyTop - bodyH * 0.18f - lift),
                            size = Size(bodyW, bodyH * 0.18f),
                            base = chrome.lighten(0.08f),
                            cornerRadius = bodyW * 0.12f,
                        )
                        drawGloss(
                            center = Offset(bodyX + bodyW * 0.30f, bodyTop - bodyH * 0.12f - lift),
                            radiusX = bodyW * 0.12f,
                            radiusY = bodyH * 0.04f,
                            alpha = 0.45f,
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Button(
                    onClick = { if (lit) closeLid() else ignite() },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        Icons.Filled.LocalFireDepartment,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Text(if (lit) stringResource(R.string.tools_sim_lighter_close) else stringResource(R.string.tools_sim_lighter_ignite))
                }
            }
            Spacer(Modifier.height(Spacing.md))
        }
    }
}
