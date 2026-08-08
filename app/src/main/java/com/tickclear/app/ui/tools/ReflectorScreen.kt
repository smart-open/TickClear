package com.tickclear.app.ui.tools

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tickclear.app.R
import com.tickclear.app.ui.theme.Spacing
import kotlin.math.max
import kotlin.math.roundToInt

/** 反光板色温选项：白光 / 暖光 / 冷光。 */
private val TINTS = listOf(
    R.string.reflector_white to Color.White,
    R.string.reflector_warm to Color(0xFFFFE0B2),
    R.string.reflector_cool to Color(0xFFB3E5FC),
)

/**
 * 简易反光板（临时补光小灯）：把屏幕背光亮到最高并铺满纯色，当临时补光板用。
 * 支持亮度滑杆与白/暖/冷三档色温；控制区可向下折叠 / 向上展开，折叠后补光区域自动铺满。
 * 退出自动恢复原有屏幕亮度。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReflectorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var brightness by remember { mutableFloatStateOf(1f) }
    var tintRes by remember { mutableIntStateOf(TINTS[0].first) }
    var zoom by remember { mutableFloatStateOf(0.6f) } // 聚光缩放：0.1 小光斑 ~ 1 大铺光
    var collapsed by remember { mutableStateOf(false) }
    val tintColor = TINTS.first { it.first == tintRes }.second

    // 进入即拉满亮度当补光；退出恢复原有亮度
    DisposableEffect(Unit) {
        val act = context.findActivity()
        val original = act?.window?.attributes?.screenBrightness ?: -1f
        act?.window?.let { w ->
            val a = w.attributes
            a.screenBrightness = 1f
            w.attributes = a
        }
        onDispose {
            act?.window?.let { w ->
                val a = w.attributes
                a.screenBrightness = original
                w.attributes = a
            }
        }
    }

    // 亮度滑杆变化时实时写入系统背光
    LaunchedEffect(brightness) {
        context.findActivity()?.let { act ->
            val a = act.window.attributes
            a.screenBrightness = brightness.coerceIn(0f, 1f)
            act.window.attributes = a
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_reflector_title)) },
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
                .padding(innerPadding),
        ) {
            // 补光区：铺满屏幕，色温由 tint 决定；zoom 控制中心聚光亮斑大小。
            // 用 weight(1f)，控制区折叠时此处自动增大铺满，实现「增大补光/反光区域」。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val maxR = max(size.width, size.height) / 2f
                    val r = (zoom.coerceIn(0.1f, 1f) * maxR * 0.9f) + maxR * 0.1f
                    // 底色铺满
                    drawRect(color = tintColor)
                    // 中心聚光：从更亮的中心渐隐回底色，形成可调大小的光斑
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(tintColor.lighten(0.92f), tintColor),
                            center = Offset(cx, cy),
                            radius = r,
                        ),
                        radius = r,
                        center = Offset(cx, cy),
                    )
                }
                if (collapsed) {
                    Text(
                        stringResource(R.string.tools_reflector_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Black.copy(alpha = 0.55f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(Spacing.lg),
                    )
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                // 控制区头部：当前色温 + 亮度摘要 + 折叠/展开按钮（向下折叠 / 向上展开）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { collapsed = !collapsed }
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(tintColor)
                                .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
                        )
                        val pct = (brightness * 100).roundToInt()
                        Text(
                            stringResource(R.string.reflector_panel_title, pct),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    IconButton(onClick = { collapsed = !collapsed }) {
                        Icon(
                            if (collapsed) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = stringResource(
                                if (collapsed) R.string.reflector_expand else R.string.reflector_collapse,
                            ),
                        )
                    }
                }

                // 展开时显示完整调节区
                AnimatedVisibility(visible = !collapsed) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md)
                            .padding(bottom = Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        ToolSlider(
                            label = stringResource(R.string.reflector_brightness),
                            value = brightness,
                            onValueChange = { brightness = it.coerceIn(0f, 1f) },
                            valueRange = 0.1f..1f,
                            steps = 18,
                            displayValue = "${(brightness * 100).roundToInt()}%",
                        )

                        // 色温：白 / 暖 / 冷 圆形样本选择器
                        Text(
                            stringResource(R.string.reflector_color_temp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            TINTS.forEach { (labelRes, color) ->
                                val selected = tintRes == labelRes
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .border(
                                                width = if (selected) 3.dp else 1.5.dp,
                                                color = if (selected) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                                },
                                                shape = CircleShape,
                                            )
                                            .clickable { tintRes = labelRes },
                                    )
                                    Text(
                                        stringResource(labelRes),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                            }
                        }

                        ToolSlider(
                            label = stringResource(R.string.reflector_zoom),
                            value = zoom,
                            onValueChange = { zoom = it.coerceIn(0.1f, 1f) },
                            valueRange = 0.1f..1f,
                            steps = 18,
                            displayValue = "${(zoom * 100).roundToInt()}%",
                        )

                        Text(
                            stringResource(R.string.reflector_restore_note),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(Spacing.sm))
        }
    }
}

/** 从任意 Context 向上回溯找到宿主 Activity。 */
private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
