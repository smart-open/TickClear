package com.tickclear.app.ui.tools

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.draw.clip
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

/** 三轴可视化配色：X 红 / Y 绿 / Z 蓝。 */
private val AXIS_COLORS = listOf(
    Color(0xFFE53935),
    Color(0xFF43A047),
    Color(0xFF1E88E5),
)

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

    val total = sqrt(x * x + y * y + z * z)
    val status = magnetStatus(total)

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
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SimHintCard(stringResource(R.string.magnet_hint))

            // 无传感器 / 低精度提示
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

            // 人性化磁场科普卡
            MagInfoCard()

            // 三轴数据卡（红 / 绿 / 蓝 与可视化色条一一对应）
            if (hasSensor) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    AxisStatCard(
                        value = "%.1f".format(x),
                        label = stringResource(R.string.magnet_x),
                        color = AXIS_COLORS[0],
                        modifier = Modifier.weight(1f),
                    )
                    AxisStatCard(
                        value = "%.1f".format(y),
                        label = stringResource(R.string.magnet_y),
                        color = AXIS_COLORS[1],
                        modifier = Modifier.weight(1f),
                    )
                    AxisStatCard(
                        value = "%.1f".format(z),
                        label = stringResource(R.string.magnet_z),
                        color = AXIS_COLORS[2],
                        modifier = Modifier.weight(1f),
                    )
                }

                // 合成强度大卡 + 状态解读
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.lg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "%.1f".format(total),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            stringResource(R.string.magnet_total) + " (" + stringResource(R.string.magnet_unit) + ")",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        // 状态解读胶囊
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(status.color.copy(alpha = 0.18f))
                                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(status.color, CircleShape),
                            )
                            Spacer(Modifier.width(Spacing.xs))
                            Text(
                                stringResource(R.string.magnet_status_label) + "：" + stringResource(status.textRes),
                                style = MaterialTheme.typography.labelMedium,
                                color = status.color,
                            )
                        }
                    }
                }

                // 三色条可视化 + 图例
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.md),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.6f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawMagBars(x, y, z, AXIS_COLORS)
                            }
                        }
                        Spacer(Modifier.height(Spacing.sm))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterHorizontally),
                        ) {
                            LegendDot(AXIS_COLORS[0], stringResource(R.string.magnet_x))
                            LegendDot(AXIS_COLORS[1], stringResource(R.string.magnet_y))
                            LegendDot(AXIS_COLORS[2], stringResource(R.string.magnet_z))
                        }
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            stringResource(R.string.magnet_legend),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

/** 三轴数据卡：轴色高亮，与可视化色条对应。 */
@Composable
private fun AxisStatCard(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(color, CircleShape),
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                color = color,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 磁场科普卡：图标标题 + 四条人性化说明。 */
@Composable
private fun MagInfoCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    stringResource(R.string.magnet_info_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            Text("• " + stringResource(R.string.magnet_info_1), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.height(Spacing.xs))
            Text("• " + stringResource(R.string.magnet_info_2), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.height(Spacing.xs))
            Text("• " + stringResource(R.string.magnet_info_3), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.height(Spacing.xs))
            Text("• " + stringResource(R.string.magnet_info_4), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

/** 图例小圆点 + 标签。 */
@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape),
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 按合成强度给出人性化状态（纯判断，无副作用）。 */
private data class MagStatus(val textRes: Int, val color: Color)

private fun magnetStatus(total: Float): MagStatus {
    return when {
        total < 20f -> MagStatus(R.string.magnet_status_weak, Color(0xFF9E9E9E))
        total <= 70f -> MagStatus(R.string.magnet_status_normal, Color(0xFF43A047))
        total <= 150f -> MagStatus(R.string.magnet_status_mild, Color(0xFFFF9800))
        else -> MagStatus(R.string.magnet_status_strong, Color(0xFFE53935))
    }
}

/** 三色条可视化：X 红 / Y 绿 / Z 蓝，正负分别向左右延伸，长度按 |值|/100µT 归一。 */
private fun DrawScope.drawMagBars(x: Float, y: Float, z: Float, colors: List<Color>) {
    val max = 100f
    val barW = size.width * 0.7f
    val cx = size.width / 2f
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
            color = Color.Black.copy(alpha = 0.12f),
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
