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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tickclear.app.R
import com.tickclear.app.ui.theme.Spacing
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** 指北端配色（正红），与主题色无关，保证任何主题下都能一眼认出北方。 */
private val CompassNorthColor = Color(0xFFE53935)

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
 * 指南针工具：融合加速度计与磁力计，经旋转矩阵解算方位角，表盘随设备朝向旋转，
 * 顶部固定指针指示当前面对方向。无 ViewModel，传感器在 DisposableEffect 中注册/注销。
 *
 * 方位角做了跨 360° 的最短路径低通平滑，避免读数在 0/359 之间来回跳。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompassScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var azimuth by remember { mutableFloatStateOf(0f) }
    var hasSensor by remember { mutableStateOf(true) }
    var needCalibrate by remember { mutableStateOf(false) }

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
                    val target = (az + 360f) % 360f
                    // 取 [-180,180) 内的最短角差再插值，否则 359°→1° 会反向转一整圈
                    val delta = ((target - azimuth + 540f) % 360f) - 180f
                    azimuth = (azimuth + delta * 0.18f + 360f) % 360f
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) {
                    needCalibrate = accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW
                }
            }
        }
        accel?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        mag?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        onDispose { sensorManager.unregisterListener(listener) }
    }

    // DrawScope 非 @Composable 上下文，先在组合上下文捕获主题色
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceColor = MaterialTheme.colorScheme.surface
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
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
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!hasSensor) {
                Text(
                    stringResource(R.string.compass_no_sensor),
                    style = MaterialTheme.typography.bodyMedium,
                    color = onSurfaceVariant,
                )
            }
            if (needCalibrate) {
                Text(
                    stringResource(R.string.compass_calibrate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(Spacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCompassDial(
                        azimuth = azimuth,
                        northColor = CompassNorthColor,
                        accentColor = primaryColor,
                        onSurface = onSurface,
                        surfaceColor = surfaceColor,
                        surfaceVariant = surfaceVariant,
                    )
                }
                // 中心读数用 Compose 文本渲染，字体与主题一致，比 nativeCanvas 画字更清晰
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.compass_degree, azimuth.toInt()),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        dirLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.xs))
            Text(
                stringResource(R.string.compass_hint),
                style = MaterialTheme.typography.bodySmall,
                color = onSurfaceVariant,
            )
        }
    }
}

/** 绘制指南针表盘：底盘渐变 + 分级刻度 + 方位标签 + 双色指针 + 顶部固定指示。 */
private fun DrawScope.drawCompassDial(
    azimuth: Float,
    northColor: Color,
    accentColor: Color,
    onSurface: Color,
    surfaceColor: Color,
    surfaceVariant: Color,
) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val center = Offset(cx, cy)
    val radius = min(size.width, size.height) / 2f * 0.90f

    // 底盘：中心稍亮的径向渐变，营造凹陷金属盘质感
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(surfaceVariant.copy(alpha = 0.55f), surfaceColor),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
    drawCircle(
        color = onSurface.copy(alpha = 0.14f),
        radius = radius,
        center = center,
        style = Stroke(width = 1.5.dp.toPx()),
    )
    drawCircle(
        color = onSurface.copy(alpha = 0.08f),
        radius = radius * 0.70f,
        center = center,
        style = Stroke(width = 1.dp.toPx()),
    )

    val labelPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
    }

    // 表盘整体按 -azimuth 旋转，使 N 始终指向真实北方
    rotate(degrees = -azimuth, pivot = center) {
        // 分级刻度：2° 细刻度 / 10° 中刻度 / 30° 主刻度
        for (deg in 0 until 360 step 2) {
            val isMajor = deg % 30 == 0
            val isMedium = deg % 10 == 0
            val len = when {
                isMajor -> radius * 0.11f
                isMedium -> radius * 0.07f
                else -> radius * 0.035f
            }
            val alpha = when {
                isMajor -> 0.75f
                isMedium -> 0.42f
                else -> 0.20f
            }
            val width = if (isMajor) 2.dp.toPx() else 1.dp.toPx()
            val rad = Math.toRadians(deg.toDouble())
            val sinV = sin(rad).toFloat()
            val cosV = cos(rad).toFloat()
            val outer = radius * 0.96f
            drawLine(
                color = if (deg == 0) northColor else onSurface.copy(alpha = alpha),
                start = Offset(cx + (outer - len) * sinV, cy - (outer - len) * cosV),
                end = Offset(cx + outer * sinV, cy - outer * cosV),
                strokeWidth = if (deg == 0) 3.dp.toPx() else width,
            )
        }

        // 30° 数字刻度（避开四正方位，那里放 N/E/S/W 字母）
        labelPaint.textSize = radius * 0.10f
        labelPaint.color = onSurface.copy(alpha = 0.55f).toArgb()
        labelPaint.typeface = android.graphics.Typeface.DEFAULT
        for (deg in 0 until 360 step 30) {
            if (deg % 90 == 0) continue
            val rad = Math.toRadians(deg.toDouble())
            val r = radius * 0.77f
            val x = cx + r * sin(rad).toFloat()
            val y = cy - r * cos(rad).toFloat()
            drawContext.canvas.nativeCanvas.drawText(
                deg.toString(),
                x,
                y + labelPaint.textSize / 3f,
                labelPaint,
            )
        }

        // 四正方位字母
        labelPaint.textSize = radius * 0.17f
        labelPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        val cardinals = listOf(
            Triple("N", 0f, northColor),
            Triple("E", 90f, onSurface),
            Triple("S", 180f, onSurface),
            Triple("W", 270f, onSurface),
        )
        for ((label, deg, col) in cardinals) {
            val rad = Math.toRadians(deg.toDouble())
            val r = radius * 0.77f
            val x = cx + r * sin(rad).toFloat()
            val y = cy - r * cos(rad).toFloat()
            labelPaint.color = col.toArgb()
            drawContext.canvas.nativeCanvas.drawText(label, x, y + labelPaint.textSize / 3f, labelPaint)
        }

        // 双色指针：北红南灰。中段留空给中央读数盘，只画两片箭头
        val tip = radius * 0.66f
        val tail = radius * 0.40f
        val half = radius * 0.07f
        val north = Path().apply {
            moveTo(cx, cy - tip)
            lineTo(cx - half, cy - tail)
            lineTo(cx + half, cy - tail)
            close()
        }
        val south = Path().apply {
            moveTo(cx, cy + tip)
            lineTo(cx - half, cy + tail)
            lineTo(cx + half, cy + tail)
            close()
        }
        drawPath(north, northColor)
        drawPath(south, onSurface.copy(alpha = 0.30f))
    }

    // 顶部固定指示（机头方向），不随表盘旋转
    val markerTop = cy - radius * 1.0f
    val marker = Path().apply {
        moveTo(cx, markerTop + 14.dp.toPx())
        lineTo(cx - 9.dp.toPx(), markerTop - 2.dp.toPx())
        lineTo(cx + 9.dp.toPx(), markerTop - 2.dp.toPx())
        close()
    }
    drawPath(marker, accentColor)

    // 中央读数盘：不透明底 + 细描边，压住指针尾部，给度数文字留出干净背景
    drawCircle(color = surfaceColor, radius = radius * 0.38f, center = center)
    drawCircle(
        color = onSurface.copy(alpha = 0.12f),
        radius = radius * 0.38f,
        center = center,
        style = Stroke(width = 1.dp.toPx()),
    )
}
