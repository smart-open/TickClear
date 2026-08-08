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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tickclear.app.R
import com.tickclear.app.ui.theme.Spacing
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

/** 水平达标时的提示绿，深浅主题下都有足够对比度。 */
private val LevelOkColor = Color(0xFF43A047)

/** 判定「已水平」的角度容差（度）。 */
private const val LEVEL_TOLERANCE_DEG = 1f

/**
 * 水平仪：重力传感器解算左右倾（roll）与前后倾（pitch），
 * 圆形气泡盘用于平放校平，下方横向气泡管用于贴墙挂画找水平。
 * 传感器读数做低通滤波，避免气泡持续抖动。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LevelScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var gx by remember { mutableFloatStateOf(0f) }
    var gy by remember { mutableFloatStateOf(0f) }
    var gz by remember { mutableFloatStateOf(0f) }
    var hasSensor by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_GRAVITY ||
                    event.sensor.type == Sensor.TYPE_ACCELEROMETER
                ) {
                    // 一阶低通：保留 82% 历史值，抑制手持微抖导致的气泡跳动
                    gx = gx * 0.82f + event.values[0] * 0.18f
                    gy = gy * 0.82f + event.values[1] * 0.18f
                    gz = gz * 0.82f + event.values[2] * 0.18f
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        val gravity = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val sensor = gravity ?: accel
        hasSensor = sensor != null
        if (sensor != null) {
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose { sensorManager.unregisterListener(listener) }
    }

    // 组合上下文先捕获主题色，供下方 Canvas 绘制使用（DrawScope 非 @Composable 上下文）。
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    // roll=左右倾斜（绕 Y 轴），pitch=前后倾斜（绕 X 轴）。平放时 gz≈9.8，gx/gy≈0 → 角度≈0。
    val roll = Math.toDegrees(atan2(gx.toDouble(), gz.toDouble())).toFloat()
    val pitch = Math.toDegrees(atan2(gy.toDouble(), gz.toDouble())).toFloat()
    val maxAngle = max(abs(roll), abs(pitch))
    val isLevel = maxAngle < LEVEL_TOLERANCE_DEG

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
                .verticalScroll(rememberScrollState())
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

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawBubbleDial(
                        gx = gx,
                        gy = gy,
                        isLevel = isLevel,
                        okColor = LevelOkColor,
                        accentColor = primaryColor,
                        onSurface = onSurface,
                        surfaceColor = surfaceColor,
                        surfaceVariant = surfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.level_max_angle, maxAngle),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isLevel) LevelOkColor else onSurface,
                    )
                    Text(
                        if (isLevel) stringResource(R.string.level_flat) else stringResource(R.string.level_tilt),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isLevel) LevelOkColor else onSurfaceVariant,
                    )
                }
            }

            // 横向气泡管：贴墙 / 挂画时看这一条
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                drawTubeLevel(
                    angleDeg = roll,
                    isLevel = abs(roll) < LEVEL_TOLERANCE_DEG,
                    okColor = LevelOkColor,
                    accentColor = primaryColor,
                    onSurface = onSurface,
                    surfaceVariant = surfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                // weight 经 RowScope 接收器解析，不能按名 import（会解析到内部 ParentData 扩展）
                AngleCard(
                    label = stringResource(R.string.level_roll_label),
                    value = stringResource(R.string.level_angle_value, roll),
                    highlight = abs(roll) < LEVEL_TOLERANCE_DEG,
                    modifier = Modifier.weight(1f),
                )
                AngleCard(
                    label = stringResource(R.string.level_pitch_label),
                    value = stringResource(R.string.level_angle_value, pitch),
                    highlight = abs(pitch) < LEVEL_TOLERANCE_DEG,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(Spacing.xs))
            Text(
                stringResource(R.string.level_hint),
                style = MaterialTheme.typography.bodySmall,
                color = onSurfaceVariant,
            )
        }
    }
}

/** 单个角度读数卡片。 */
@Composable
private fun AngleCard(
    label: String,
    value: String,
    highlight: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) {
                LevelOkColor.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (highlight) LevelOkColor else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** 圆形气泡盘：3D 金属外圈 + 渐变底盘 + 刻度环 + 十字虚线 + 受光气泡。 */
private fun DrawScope.drawBubbleDial(
    gx: Float,
    gy: Float,
    isLevel: Boolean,
    okColor: Color,
    accentColor: Color,
    onSurface: Color,
    surfaceColor: Color,
    surfaceVariant: Color,
) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val center = Offset(cx, cy)
    val radius = (if (size.width < size.height) size.width else size.height) / 2f * 0.92f
    val bubbleR = radius * 0.16f
    val bubbleColor = if (isLevel) okColor else accentColor

    // 接地软阴影：椭圆收紧至表盘内、降透明度，避免达标变绿时顶部椭圆弧阴影刺眼
    drawSoftShadow(
        center = Offset(cx, cy + radius * 0.04f),
        radiusX = radius * 0.98f,
        radiusY = radius * 0.92f,
        maxAlpha = 0.10f,
    )

    // 底盘渐变，中心略暗形成凹槽感
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(surfaceVariant.copy(alpha = 0.65f), surfaceColor),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
    // 金属外圈环带：高光边 + 暗色内环，模拟仪表盘圆柱受光
    drawCircle(
        color = onSurface.copy(alpha = 0.22f),
        radius = radius * 0.985f,
        center = center,
        style = Stroke(width = 1.5.dp.toPx()),
    )
    drawCircle(
        color = onSurface.copy(alpha = 0.10f),
        radius = radius * 0.93f,
        center = center,
        style = Stroke(width = radius * 0.05f),
    )

    // 30° 一根的边缘刻度
    for (deg in 0 until 360 step 30) {
        val rad = Math.toRadians(deg.toDouble())
        val sinV = kotlin.math.sin(rad).toFloat()
        val cosV = kotlin.math.cos(rad).toFloat()
        val isMajor = deg % 90 == 0
        val inner = radius * if (isMajor) 0.88f else 0.92f
        drawLine(
            color = onSurface.copy(alpha = if (isMajor) 0.4f else 0.2f),
            start = Offset(cx + inner * sinV, cy - inner * cosV),
            end = Offset(cx + radius * 0.98f * sinV, cy - radius * 0.98f * cosV),
            strokeWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx(),
        )
    }

    // 十字虚线参考
    val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))
    drawLine(
        color = onSurface.copy(alpha = 0.18f),
        start = Offset(cx - radius * 0.85f, cy),
        end = Offset(cx + radius * 0.85f, cy),
        strokeWidth = 1.dp.toPx(),
        pathEffect = dash,
    )
    drawLine(
        color = onSurface.copy(alpha = 0.18f),
        start = Offset(cx, cy - radius * 0.85f),
        end = Offset(cx, cy + radius * 0.85f),
        strokeWidth = 1.dp.toPx(),
        pathEffect = dash,
    )

    // 中央容差环：气泡完全落入即视为水平；达标时加外发光
    if (isLevel) {
        drawCircle(color = okColor.copy(alpha = 0.16f), radius = bubbleR * 2.1f, center = center)
    }
    drawCircle(
        color = if (isLevel) okColor else onSurface.copy(alpha = 0.28f),
        radius = bubbleR * 1.45f,
        center = center,
        style = Stroke(width = if (isLevel) 2.5.dp.toPx() else 1.5.dp.toPx()),
    )

    // 气泡：偏移与重力分量反向（倾斜时气泡浮向高处）；用受光球体提升体积感
    val travel = radius - bubbleR * 1.2f
    val k = travel / 6f
    val offX = (gx * -k).coerceIn(-travel, travel)
    val offY = (gy * -k).coerceIn(-travel, travel)
    val bubbleCenter = Offset(cx + offX, cy + offY)
    drawCircle(color = bubbleColor.copy(alpha = 0.18f), radius = bubbleR * 1.6f, center = bubbleCenter)
    fillSphere(center = bubbleCenter, radius = bubbleR, base = bubbleColor)
}

/** 横向气泡管：贴墙找水平用，管体受光 + 气泡按左右倾角在管内移动。 */
private fun DrawScope.drawTubeLevel(
    angleDeg: Float,
    isLevel: Boolean,
    okColor: Color,
    accentColor: Color,
    onSurface: Color,
    surfaceVariant: Color,
) {
    val w = size.width
    val h = size.height
    val tubeH = h * 0.62f
    val top = (h - tubeH) / 2f
    val corner = tubeH / 2f
    val color = if (isLevel) okColor else accentColor
    val tubeSize = Size(w, tubeH)

    // 接地软阴影：让气泡管"放置"在画布上而非悬浮（二巡精修）
    drawSoftShadow(
        center = Offset(w / 2f, top + tubeH * 1.5f),
        radiusX = w * 0.48f,
        radiusY = tubeH * 0.5f,
        maxAlpha = 0.16f,
    )

    // 管体受光（圆角矩形圆柱感）
    fillRoundRect3D(topLeft = Offset(0f, top), size = tubeSize, cornerRadius = corner, base = surfaceVariant)
    drawRoundRect(
        color = onSurface.copy(alpha = 0.14f),
        topLeft = Offset(0f, top),
        size = tubeSize,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
        style = Stroke(width = 1.dp.toPx()),
    )

    // 中央容差窗（两条竖线）
    val bubbleR = tubeH * 0.36f
    val gate = bubbleR * 1.35f
    for (dx in listOf(-gate, gate)) {
        drawLine(
            color = onSurface.copy(alpha = 0.3f),
            start = Offset(w / 2f + dx, top + tubeH * 0.12f),
            end = Offset(w / 2f + dx, top + tubeH * 0.88f),
            strokeWidth = 1.5.dp.toPx(),
        )
    }

    // ±15° 映射到整条管长，超出则贴边
    val travel = w / 2f - bubbleR * 1.4f
    val offX = (angleDeg / 15f * travel).coerceIn(-travel, travel)
    val center = Offset(w / 2f + offX, top + tubeH / 2f)
    drawCircle(color = color.copy(alpha = 0.18f), radius = bubbleR * 1.4f, center = center)
    fillSphere(center = center, radius = bubbleR, base = color)
}
