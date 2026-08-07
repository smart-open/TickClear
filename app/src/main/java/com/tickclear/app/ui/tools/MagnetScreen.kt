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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tickclear.app.R
import com.tickclear.app.ui.theme.Spacing
import kotlin.math.absoluteValue
import kotlin.math.sqrt

/**
 * 地磁场观测（V2.9++ 实用工具）。
 * 注册磁力传感器，实时展示 X/Y/Z 三轴与合成磁场强度(µT)，并在画布上以三色条可视化变化。
 * 无 ViewModel（零新依赖）。
 *
 * 采样受生命周期约束：原实现只在 DisposableEffect 注册/注销，而 Compose 不会因为
 * 应用退到后台就把这个界面移出组合，于是磁力计会以游戏级频率在后台持续采样耗电。
 * 现改为 ON_START 注册 / ON_STOP 注销，并把频率降到 SENSOR_DELAY_UI
 * （约 60ms 一次，人眼读数完全够用，而 SENSOR_DELAY_GAME 每秒会触发 50 次全屏重组）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MagnetScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var x by remember { mutableFloatStateOf(0f) }
    var y by remember { mutableFloatStateOf(0f) }
    var z by remember { mutableFloatStateOf(0f) }
    var hasSensor by remember { mutableStateOf(true) }
    // 磁力计易受机身/环境干扰，精度不足时读数无意义，需提示用户画「8」字校准。
    var lowAccuracy by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val mag = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        hasSensor = mag != null
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                    x = event.values[0]
                    y = event.values[1]
                    z = event.values[2]
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                lowAccuracy = accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START ->
                    mag?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
                Lifecycle.Event.ON_STOP -> sensorManager.unregisterListener(listener)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            sensorManager.unregisterListener(listener)
        }
    }

    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val total = sqrt(x * x + y * y + z * z)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.magnet_title)) },
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
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SimHintCard(stringResource(R.string.magnet_hint))
            if (!hasSensor) {
                Text(
                    stringResource(R.string.magnet_no_sensor),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (lowAccuracy) {
                Text(
                    stringResource(R.string.magnet_low_accuracy),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                SimStatCard("%.1f".format(x), modifier = Modifier.weight(1f), label = stringResource(R.string.magnet_x))
                SimStatCard("%.1f".format(y), modifier = Modifier.weight(1f), label = stringResource(R.string.magnet_y))
                SimStatCard("%.1f".format(z), modifier = Modifier.weight(1f), label = stringResource(R.string.magnet_z))
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(Spacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawMagBars(x, y, z, primary, onSurface)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "%.1f".format(total),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        stringResource(R.string.magnet_total) + " (" + stringResource(R.string.magnet_unit) + ")",
                        style = MaterialTheme.typography.labelMedium,
                        color = onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xs))
        }
    }
}

/** 三色条可视化：X 红 / Y 绿 / Z 蓝，正负分别向左右延伸，长度按 |值|/100µT 归一。 */
private fun DrawScope.drawMagBars(x: Float, y: Float, z: Float, primary: Color, onSurface: Color) {
    val max = 100f
    val barW = size.width * 0.7f
    val cx = size.width / 2f
    val colors = listOf(Color(0xFFE53935), Color(0xFF43A047), Color(0xFF1E88E5))
    val vals = listOf(x, y, z)
    val gap = size.height / 4f
    val h = 10.dp.toPx()
    // 接地软阴影：让三色条"贴"在面板上而非悬浮（二巡精修）
    val trackCenterY = gap * 2f
    drawSoftShadow(
        center = Offset(cx, trackCenterY + h * 0.9f),
        radiusX = barW * 0.5f,
        radiusY = h * 0.8f,
        maxAlpha = 0.12f,
    )
    for (i in 0..2) {
        val cy = gap * (i + 1)
        drawLine(
            color = onSurface.copy(alpha = 0.15f),
            start = Offset(cx - barW / 2, cy),
            end = Offset(cx + barW / 2, cy),
            strokeWidth = 1.dp.toPx(),
        )
        val len = (vals[i].absoluteValue / max).coerceAtMost(1f) * barW / 2
        val col = colors[i]
        if (len > 0f) {
            val topLeft = if (vals[i] >= 0f) Offset(cx, cy - h / 2) else Offset(cx - len, cy - h / 2)
            fillRoundRect3D(topLeft = topLeft, size = Size(len, h), cornerRadius = h / 2, base = col)
        }
    }
}
