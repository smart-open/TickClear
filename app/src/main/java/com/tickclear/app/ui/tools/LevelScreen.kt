package com.tickclear.app.ui.tools

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tickclear.app.R
import com.tickclear.app.ui.theme.Spacing
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.minOf
import kotlin.math.toDegrees
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LevelScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var gx by remember { mutableStateOf(0f) }
    var gy by remember { mutableStateOf(0f) }
    var gz by remember { mutableStateOf(0f) }
    var hasSensor by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_GRAVITY ||
                    event.sensor.type == Sensor.TYPE_ACCELEROMETER
                ) {
                    gx = event.values[0]
                    gy = event.values[1]
                    gz = event.values[2]
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        val gravity = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val sensor = gravity ?: accel
        hasSensor = sensor != null
        if (sensor != null) {
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { sensorManager.unregisterListener(listener) }
    }

    // 组合上下文先捕获主题色，供下方 Canvas 绘制使用（DrawScope 非 @Composable 上下文）。
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    // roll=左右倾斜（绕 Y 轴），pitch=前后倾斜（绕 X 轴）。平放时 gz≈9.8，gx/gy≈0 → 角度≈0。
    val roll = toDegrees(atan2(gx.toDouble(), gz.toDouble())).toFloat()
    val pitch = toDegrees(atan2(gy.toDouble(), gz.toDouble())).toFloat()
    val maxAngle = max(abs(roll), abs(pitch))
    val isLevel = maxAngle < 1f

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_level_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
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
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!hasSensor) {
                Text(
                    stringResource(R.string.level_no_sensor),
                    style = MaterialTheme.typography.bodyMedium,
                    color = onSurfaceVariant,
                )
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val radius = minOf(size.width, size.height) / 3f
                val bubbleR = radius * 0.18f
                // 气泡偏移与重力分量反向：平放(gx,gy≈0)→居中；倾斜→气泡移向高处。
                val k = (radius - bubbleR) / 6f
                val offX = (gx * -k).coerceIn(-(radius - bubbleR), radius - bubbleR)
                val offY = (gy * -k).coerceIn(-(radius - bubbleR), radius - bubbleR)

                // 外圈
                drawCircle(
                    color = onSurface.copy(alpha = 0.15f),
                    radius = radius,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2.dp.toPx()),
                )
                // 中心十字参考线
                drawLine(
                    color = onSurface.copy(alpha = 0.25f),
                    start = Offset(cx - radius, cy),
                    end = Offset(cx + radius, cy),
                    strokeWidth = 1.dp.toPx(),
                )
                drawLine(
                    color = onSurface.copy(alpha = 0.25f),
                    start = Offset(cx, cy - radius),
                    end = Offset(cx, cy + radius),
                    strokeWidth = 1.dp.toPx(),
                )
                // 水平判定环（水平时变绿）
                drawCircle(
                    color = if (isLevel) Color(0xFF4CAF50) else onSurface.copy(alpha = 0.3f),
                    radius = radius * 0.5f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2.dp.toPx()),
                )
                // 气泡
                drawCircle(
                    color = if (isLevel) Color(0xFF4CAF50) else primaryColor,
                    radius = bubbleR,
                    center = Offset(cx + offX, cy + offY),
                )
            }

            Text(
                if (isLevel) stringResource(R.string.level_flat) else stringResource(R.string.level_tilt),
                style = MaterialTheme.typography.headlineSmall,
                color = if (isLevel) Color(0xFF4CAF50) else onSurface,
            )
            Text(
                stringResource(R.string.level_roll, roll),
                style = MaterialTheme.typography.bodyMedium,
                color = onSurfaceVariant,
            )
            Text(
                stringResource(R.string.level_pitch, pitch),
                style = MaterialTheme.typography.bodyMedium,
                color = onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                stringResource(R.string.level_hint),
                style = MaterialTheme.typography.bodySmall,
                color = onSurfaceVariant,
            )
        }
    }
}
