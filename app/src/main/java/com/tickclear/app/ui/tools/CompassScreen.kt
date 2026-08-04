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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tickclear.app.R
import com.tickclear.app.ui.theme.Spacing
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** 方位标签资源（按 8 向映射）。 */
private fun directionRes(azimuth: Float): Int {
    val idx = ((((azimuth + 22.5f) % 360f) + 360f) % 360f / 45f).toInt()
    return when (idx) {
        0 -> R.string.compass_north
        1 -> R.string.compass_northeast
        2 -> R.string.compass_east
        3 -> R.string.compass_southeast
        4 -> R.string.compass_south
        5 -> R.string.compass_southwest
        6 -> R.string.compass_west
        else -> R.string.compass_northwest
    }
}

/**
 * 指南针工具（V2.9++）：融合加速度计与磁力计，经旋转矩阵解算方位角，
 * 表盘随设备朝向旋转，顶部三角指示当前面对方向。无 ViewModel，传感器在 DisposableEffect 中注册/注销。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompassScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var azimuth by remember { mutableStateOf(0f) }
    var hasSensor by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val mag = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        hasSensor = accel != null && mag != null
        val gravity = FloatArray(3)
        val geomag = FloatArray(3)
        val rMat = FloatArray(9)
        val iMat = FloatArray(9)
        val orientation = FloatArray(3)
        var accelSet = false
        var magSet = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        System.arraycopy(event.values, 0, gravity, 0, 3)
                        accelSet = true
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        System.arraycopy(event.values, 0, geomag, 0, 3)
                        magSet = true
                    }
                }
                if (accelSet && magSet && SensorManager.getRotationMatrix(rMat, iMat, gravity, geomag)) {
                    SensorManager.getOrientation(rMat, orientation)
                    val az = Math.toDegrees(orientation[0].toDouble()).toFloat()
                    azimuth = (az + 360f) % 360f
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        accel?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        mag?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        onDispose { sensorManager.unregisterListener(listener) }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val dirLabel = stringResource(directionRes(azimuth))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_compass_title)) },
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
                    stringResource(R.string.compass_no_sensor),
                    style = MaterialTheme.typography.bodyMedium,
                    color = onSurfaceVariant,
                )
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(Spacing.sm),
            ) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val radius = min(size.width, size.height) / 2f * 0.92f
                val textPaint = android.graphics.Paint().apply {
                    textAlign = android.graphics.Paint.Align.CENTER
                    textSize = radius * 0.16f
                }

                // 外环
                drawCircle(
                    color = onSurface.copy(alpha = 0.2f),
                    radius = radius,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2.dp.toPx()),
                )

                // 旋转的方位盘：N 固定在顶部，整体按 -azimuth 旋转，使 N 指向真实北方
                rotate(degrees = -azimuth, pivot = Offset(cx, cy)) {
                    val rLabel = radius * 0.78f
                    val labels = listOf(
                        Triple("N", 0f, primaryColor),
                        Triple("E", 90f, onSurface),
                        Triple("S", 180f, onSurface),
                        Triple("W", 270f, onSurface),
                    )
                    for ((label, deg, col) in labels) {
                        val rad = Math.toRadians(deg.toDouble())
                        val x = cx + rLabel * sin(rad).toFloat()
                        val y = cy - rLabel * cos(rad).toFloat()
                        textPaint.color = col.toArgb()
                        drawContext.canvas.nativeCanvas.drawText(
                            label,
                            x,
                            y + textPaint.textSize / 3,
                            textPaint,
                        )
                    }
                    for (deg in 0 until 360 step 30) {
                        val rad = Math.toRadians(deg.toDouble())
                        val x1 = cx + radius * 0.86f * sin(rad).toFloat()
                        val y1 = cy - radius * 0.86f * cos(rad).toFloat()
                        val x2 = cx + radius * 0.94f * sin(rad).toFloat()
                        val y2 = cy - radius * 0.94f * cos(rad).toFloat()
                        drawLine(
                            color = onSurface.copy(alpha = 0.4f),
                            start = Offset(x1, y1),
                            end = Offset(x2, y2),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                }

                // 固定顶部朝向指示三角（你正面对的方向）
                val tri = Path().apply {
                    val ty = cy - radius * 0.82f
                    moveTo(cx, ty)
                    lineTo(cx - 12.dp.toPx(), ty - 16.dp.toPx())
                    lineTo(cx + 12.dp.toPx(), ty - 16.dp.toPx())
                    close()
                }
                drawPath(tri, primaryColor)

                // 中心圆点
                drawCircle(
                    color = primaryColor,
                    radius = radius * 0.06f,
                    center = Offset(cx, cy),
                )
            }

            Text(
                stringResource(R.string.compass_degree, kotlin.math.abs(azimuth.toInt())),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                stringResource(R.string.compass_direction, dirLabel),
                style = MaterialTheme.typography.bodyMedium,
                color = onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                stringResource(R.string.compass_hint),
                style = MaterialTheme.typography.bodySmall,
                color = onSurfaceVariant,
            )
        }
    }
}
