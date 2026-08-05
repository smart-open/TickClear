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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
    val scope = rememberCoroutineScope()
    val primary = MaterialTheme.colorScheme.primary

    var pressure by remember { mutableStateOf(0f) }   // 0..100
    var shake by remember { mutableStateOf(0f) }      // 视觉抖动 0..1
    var particles by remember { mutableStateOf(emptyList<SimParticle>()) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }
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

    // 粒子与抖动衰减的帧循环
    LaunchedEffect(Unit) {
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
            Text(
                stringResource(R.string.tools_sim_can_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.md))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .height(300.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    canvasSize = size
                    val w = size.width
                    val h = size.height
                    val jx = (kotlin.random.Random.nextFloat() - 0.5f) * shake * 14f
                    val jy = (kotlin.random.Random.nextFloat() - 0.5f) * shake * 14f
                    val canW = w * 0.34f
                    val canH = h * 0.52f
                    val canX = (w - canW) / 2f + jx
                    val canY = h * 0.30f + jy
                    canMouth = Offset((canX + canW / 2f) / w, canY / h)

                    drawRoundRect(
                        color = Color(0xFFBFC6CE),
                        topLeft = Offset(canX, canY),
                        size = Size(canW, canH),
                    )
                    drawRoundRect(
                        color = Color(0xFF9AA1A9),
                        topLeft = Offset(canX, canY - canH * 0.04f),
                        size = Size(canW, canH * 0.06f),
                    )
                    drawRoundRect(
                        color = Color(0xFF6E747B),
                        topLeft = Offset(canX + canW * 0.38f, canY - canH * 0.06f),
                        size = Size(canW * 0.24f, canH * 0.03f),
                    )
                    drawRoundRect(
                        color = primary,
                        topLeft = Offset(canX, canY + canH * 0.34f),
                        size = Size(canW, canH * 0.22f),
                    )

                    for (pt in particles) {
                        val alpha = (pt.life / pt.maxLife).coerceIn(0f, 1f)
                        val cx = pt.x * w
                        val cy = pt.y * h
                        drawRoundRect(
                            color = simColor(pt.hue, alpha),
                            topLeft = Offset(cx - pt.radius, cy - pt.radius),
                            size = Size(pt.radius * 2, pt.radius * 2),
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.md))
            Text(
                "压力 ${pressure.toInt()}%",
                style = MaterialTheme.typography.titleSmall,
            )
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
