package com.tickclear.app.ui.tools

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.EmojiFoodBeverage
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tickclear.app.R
import com.tickclear.app.domain.tools.FoleySynth
import com.tickclear.app.ui.components.Haptic
import com.tickclear.app.ui.theme.Spacing
import kotlin.math.sqrt
import androidx.compose.runtime.withFrameMillis

private val CAN_HUES = listOf(195f, 205f, 215f)

/**
 * 模拟易拉罐摇晃（V2.9++ 模拟解压）。
 * 摇手机或点“摇晃”攒压力，点“开罐”触发喷溅动画 + 嘶嘶音效。
 * 压力越高喷得越猛；压力不足只滋一下。纯 Canvas + 加速度传感器 + AudioTrack 合成。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimCanShakeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary

    var pressure by remember { mutableFloatStateOf(0f) }   // 0..100
    var shake by remember { mutableFloatStateOf(0f) }      // 视觉抖动 0..1
    var particles by remember { mutableStateOf(emptyList<SimParticle>()) }
    var message by remember { mutableStateOf<String?>(null) }
    var canMouth by remember { mutableStateOf(Offset(0.5f, 0.25f)) }

    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    val listener = remember {
        object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val mag = sqrt(
                    e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2],
                )
                if (mag > 13f) {
                    pressure = (pressure + (mag - 13f) * 0.8f).coerceAtMost(100f)
                    shake = 1f
                }
            }

            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
    }

    DisposableEffect(Unit) {
        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        onDispose {
            sensorManager.unregisterListener(listener)
            FoleySynth.stop()
        }
    }

    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(1500)
            message = null
        }
    }

    // 粒子与抖动衰减的帧循环：只在「有喷溅粒子 或 仍在抖」时运行，静止后自动挂起省电
    val animating = particles.isNotEmpty() || shake > 0f
    LaunchedEffect(animating) {
        if (!animating) return@LaunchedEffect
        var last = 0L
        while (true) {
            val now = withFrameMillis { it }
            val dt = if (last == 0L) 0.016f else ((now - last) / 1000f).coerceAtMost(0.05f)
            last = now
            if (particles.isNotEmpty()) particles = stepParticles(particles, dt)
            if (shake > 0.001f) shake *= 0.9f else shake = 0f
        }
    }

    fun openCan() {
        val p = pressure
        pressure = 0f
        shake = 0f
        val strong = p >= 20f
        val mouth = canMouth
        val count = if (strong) 54 else 14
        val speed = if (strong) 0.95f else 0.45f
        val life = if (strong) 1.1f else 0.5f
        val spawned = burst(mouth.x, mouth.y, count, speed, life, CAN_HUES, 5f)
            .map { it.copy(vy = it.vy - 0.7f) } // 向上喷出
        particles = particles + spawned
        FoleySynth.play("can")
        Haptic.vibrate(context, if (strong) 90 else 30)
        if (!strong) message = context.getString(R.string.tools_sim_can_fizzle)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_sim_can_title)) },
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
            SimHintCard(stringResource(R.string.tools_sim_can_hint))
            Spacer(Modifier.height(Spacing.md))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .height(300.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val jx = (kotlin.random.Random.nextFloat() - 0.5f) * shake * 14f
                    val jy = (kotlin.random.Random.nextFloat() - 0.5f) * shake * 14f
                    // 压力越高罐身越鼓：给"要炸了"一个视觉预告
                    val canW = w * 0.34f * (1f + pressure / 100f * 0.05f)
                    val canH = h * 0.52f
                    val canX = (w - canW) / 2f + jx
                    val canY = h * 0.30f + jy
                    val cx = canX + canW / 2f
                    val rimH = canW * 0.20f
                    val silver = Color(0xFFD3D9DF)
                    canMouth = Offset(cx / w, canY / h)

                    drawContactShadow(
                        center = Offset(cx, canY + canH + rimH * 0.45f),
                        radiusX = canW * 0.75f,
                        radiusY = canH * 0.055f,
                        maxAlpha = 0.28f,
                    )
                    // 罐身：金属圆柱受光
                    fillCylinder(
                        topLeft = Offset(canX, canY),
                        size = Size(canW, canH),
                        base = silver,
                        cornerRadius = canW * 0.06f,
                    )
                    // 底部收口
                    fillOvoid(
                        topLeft = Offset(canX, canY + canH - rimH * 0.5f),
                        size = Size(canW, rimH),
                        base = silver.darken(0.30f),
                    )
                    // 彩色标签带（同样圆柱受光，才会"贴"在罐身上）
                    fillCylinder(
                        topLeft = Offset(canX, canY + canH * 0.32f),
                        size = Size(canW, canH * 0.28f),
                        base = primary,
                    )
                    drawLine(
                        color = silver.darken(0.35f),
                        start = Offset(canX, canY + canH * 0.32f),
                        end = Offset(canX + canW, canY + canH * 0.32f),
                        strokeWidth = canH * 0.008f,
                    )
                    drawLine(
                        color = silver.darken(0.35f),
                        start = Offset(canX, canY + canH * 0.60f),
                        end = Offset(canX + canW, canY + canH * 0.60f),
                        strokeWidth = canH * 0.008f,
                    )
                    // 冷凝水珠：确定性分布（逐帧随机会闪烁），用纯色圆保证低开销
                    for (i in 0 until 14) {
                        val fx = ((i * 37) % 100) / 100f
                        val fy = ((i * 53) % 100) / 100f
                        val r = canW * (0.011f + (i % 3) * 0.005f)
                        val dc = Offset(canX + canW * (0.12f + fx * 0.76f), canY + canH * (0.05f + fy * 0.88f))
                        drawCircle(color = Color.White.copy(alpha = 0.30f), radius = r, center = dc)
                        drawCircle(
                            color = Color.White.copy(alpha = 0.55f),
                            radius = r * 0.42f,
                            center = Offset(dc.x - r * 0.28f, dc.y - r * 0.3f),
                        )
                    }
                    // 罐顶：外沿 + 内凹罐面 + 拉环
                    fillOvoid(
                        topLeft = Offset(canX, canY - rimH * 0.5f),
                        size = Size(canW, rimH),
                        base = silver.darken(0.18f),
                    )
                    drawOval(
                        color = silver.darken(0.42f),
                        topLeft = Offset(canX + canW * 0.09f, canY - rimH * 0.32f),
                        size = Size(canW * 0.82f, rimH * 0.64f),
                    )
                    drawOval(
                        color = silver.lighten(0.25f),
                        topLeft = Offset(cx - canW * 0.22f, canY - rimH * 0.26f),
                        size = Size(canW * 0.30f, rimH * 0.42f),
                        style = Stroke(width = canW * 0.026f),
                    )
                    drawLine(
                        color = silver.darken(0.5f),
                        start = Offset(cx + canW * 0.09f, canY - rimH * 0.05f),
                        end = Offset(cx + canW * 0.24f, canY - rimH * 0.05f),
                        strokeWidth = canW * 0.02f,
                    )
                    // 罐身竖直高光
                    drawGloss(
                        center = Offset(canX + canW * 0.24f, canY + canH * 0.48f),
                        radiusX = canW * 0.07f,
                        radiusY = canH * 0.36f,
                        alpha = 0.38f,
                    )

                    // 喷溅液滴：带高光的球体，比原来的方块自然得多
                    for (pt in particles) {
                        val alpha = (pt.life / pt.maxLife).coerceIn(0f, 1f)
                        val px = pt.x * w
                        val py = pt.y * h
                        drawCircle(
                            color = simColor(pt.hue, alpha * 0.92f),
                            radius = pt.radius,
                            center = Offset(px, py),
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.5f * alpha),
                            radius = pt.radius * 0.34f,
                            center = Offset(px - pt.radius * 0.3f, py - pt.radius * 0.34f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.md))
            SimStatCard(
                value = "${pressure.toInt()}%",
                label = stringResource(R.string.tools_sim_can_pressure),
            )
            Spacer(Modifier.height(Spacing.sm))
            LinearProgressIndicator(
                progress = { pressure / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.xs),
            )
            message?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(Spacing.xs))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Button(
                    onClick = { pressure = (pressure + 16f).coerceAtMost(100f); shake = 1f },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.tools_sim_can_shake)) }
                Button(
                    onClick = { openCan() },
                    modifier = Modifier.weight(1f),
                    enabled = pressure >= 5f,
                ) { Text(stringResource(R.string.tools_sim_can_open)) }
            }
            Spacer(Modifier.height(Spacing.md))
        }
    }
}
