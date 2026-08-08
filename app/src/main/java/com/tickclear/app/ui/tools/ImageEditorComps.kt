package com.tickclear.app.ui.tools

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tickclear.app.R
import com.tickclear.app.ui.theme.Spacing

/**
 * 模拟解压类工具的说明卡片（统一外观：圆角 + 次级背景）。
 */
@Composable
fun SimHintCard(text: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(Spacing.md),
        )
    }
}

/**
 * 模拟解压类工具的醒目数据卡（计数 / 分数 / 压力等）。
 */
@Composable
fun SimStatCard(
    value: String,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (label != null) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

/**
 * 可折叠的「侧边工具面板」（图片编辑类工具通用）。
 * 展开时占满给定宽度并允许内部滚动；收起时仅留一个展开按钮，把空间让给图片。
 */
@Composable
fun ToolSidePanel(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconButton(onClick = onToggle) {
            Icon(
                imageVector = if (expanded) Icons.Filled.ChevronRight else Icons.Filled.Menu,
                contentDescription = stringResource(R.string.tools_panel_toggle),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                content = content,
            )
        }
    }
}

/**
 * 可缩放/平移的「绘制画布」（马赛克 / 去水印）。
 * - 图片按容器比例内接，避免竖图把控件挤出屏幕；
 * - scale 由父级按钮控制（1..4），offset 为归一化平移量（单位：图片宽/高的比例，
 *   由父级方向键控制），内层盒子先按中心缩放再平移，绘制坐标仍是归一化图片坐标，
 *   因此放大/平移后落点依旧精准（见 [toNormCoord] 反解算）；
 * - 拖拽手势交给父级做涂抹/框选，overlay 由父级以 Canvas 形式叠加（clip 裁掉溢出部分）。
 */
@Composable
fun ZoomableDrawCanvas(
    bitmap: Bitmap,
    scale: Float,
    offset: Offset,
    onOffsetChange: (Offset) -> Unit,
    modifier: Modifier = Modifier,
    onDrawStart: (Float, Float) -> Unit,
    onDrawMove: (Float, Float) -> Unit,
    onDrawEnd: () -> Unit,
    overlay: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val maxW = maxWidth
        val maxH = maxHeight
        val fw: Dp
        val fh: Dp
        if (maxW / ratio <= maxH) {
            fw = maxW
            fh = maxW / ratio
        } else {
            fh = maxH
            fw = maxH * ratio
        }
        val fwPx = with(density) { fw.toPx() }
        val fhPx = with(density) { fh.toPx() }
        // 最新值，供 pointerInput(Unit) 闭包实时读取，避免捕获陈旧的回调与参数
        val scaleState = rememberUpdatedState(scale)
        val offsetState = rememberUpdatedState(offset)
        val onStartState = rememberUpdatedState(onDrawStart)
        val onMoveState = rememberUpdatedState(onDrawMove)
        val onEndState = rememberUpdatedState(onDrawEnd)

        Box(
            modifier = Modifier
                .size(fw, fh)
                .clipToBounds(),
        ) {
            // 视觉层：图片与 overlay 一起被 graphicsLayer 缩放/平移，二者天然对齐
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scaleState.value
                        scaleY = scaleState.value
                        translationX = offsetState.value.x * fwPx
                        translationY = offsetState.value.y * fhPx
                    },
            ) {
                Image(
                    painter = BitmapPainter(bitmap.asImageBitmap()),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                )
                overlay()
            }
            // 手势层：透明、不缩放，落点直接是未变换的布局坐标；
            // 反解算由 toNormCoord 显式完成，彻底摆脱 graphicsLayer 指针语义的歧义
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { p ->
                                val (nx, ny) = toNormCoord(p, scaleState.value, offsetState.value, fwPx, fhPx)
                                onStartState.value(nx, ny)
                            },
                            onDrag = { change, _ ->
                                val (nx, ny) = toNormCoord(change.position, scaleState.value, offsetState.value, fwPx, fhPx)
                                onMoveState.value(nx, ny)
                            },
                            onDragEnd = { onEndState.value() },
                        )
                    },
            )
        }
    }
}

/**
 * 将手势层落点（未变换的布局坐标，px）反解算为归一化图片坐标。
 * 手势层不缩放，[p] 即视觉位置；视觉位置是「图片点以中心为轴缩放 [scale] 再平移
 * (offset*fwPx, offset*fhPx)」的结果，故逆推：
 * `imgLocal = (p - translation - center) / scale + center`，再除以图片尺寸归一化。
 */
private fun toNormCoord(
    p: Offset,
    scale: Float,
    offset: Offset,
    fwPx: Float,
    fhPx: Float,
): Pair<Float, Float> {
    val cx = fwPx / 2f
    val cy = fhPx / 2f
    val tx = offset.x * fwPx
    val ty = offset.y * fhPx
    val lx = (p.x - tx - cx) / scale + cx
    val ly = (p.y - ty - cy) / scale + cy
    return (lx / fwPx) to (ly / fhPx)
}

/**
 * 可缩放 / 平移的「预览画布」（图片压缩 / 黑白）。
 * - scale / offset 均由外部 state 控制：侧边面板的方向键、捏合手势、单指拖动都会改它们；
 * - 内部对平移做钳制，保证放大后图片始终至少部分可见，不会整张滑出视野；
 * - 回到 1× 时由调用方复位 offset（方向键在 scale<=1 时禁用）。
 */
@Composable
fun ZoomableImagePreview(
    bitmap: Bitmap,
    scale: Float,
    onScaleChange: (Float) -> Unit,
    offset: Offset,
    onOffsetChange: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 最新值，供 pointerInput(Unit) 闭包实时读取，避免捕获陈旧参数
    val scaleState = rememberUpdatedState(scale)
    val offsetState = rememberUpdatedState(offset)
    val onScaleState = rememberUpdatedState(onScaleChange)
    val onOffsetState = rememberUpdatedState(onOffsetChange)
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val maxW = maxWidth
        val maxH = maxHeight
        val fw: Dp
        val fh: Dp
        if (maxW / ratio <= maxH) {
            fw = maxW
            fh = maxW / ratio
        } else {
            fh = maxH
            fw = maxH * ratio
        }
        val fwPx = with(density) { fw.toPx() }
        val fhPx = with(density) { fh.toPx() }
        val maxWPx = with(density) { maxW.toPx() }
        val maxHPx = with(density) { maxH.toPx() }

        // 允许的最大归一化平移：放大后超出容器的部分（offset 为 0..1 归一化）。
        // 归一化 = 像素溢出 / (2 × 图片像素宽高)，确保图片不整张滑出视野。
        val maxTXNorm = if (fwPx > 0f) maxOf(0f, (fwPx * scale - maxWPx) / (2f * fwPx)) else 0f
        val maxTYNorm = if (fhPx > 0f) maxOf(0f, (fhPx * scale - maxHPx) / (2f * fhPx)) else 0f
        val clampedX = offset.x.coerceIn(-maxTXNorm, maxTXNorm)
        val clampedY = offset.y.coerceIn(-maxTYNorm, maxTYNorm)
        // 把钳制结果回写，使外部 state 始终合法（避免抖动循环）
        LaunchedEffect(clampedX, clampedY) {
            if (kotlin.math.abs(clampedX - offset.x) > 1e-4f || kotlin.math.abs(clampedY - offset.y) > 1e-4f) {
                onOffsetState.value(Offset(clampedX, clampedY))
            }
        }

        Box(
            modifier = Modifier
                .size(fw, fh)
                .clipToBounds(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scaleState.value
                        scaleY = scaleState.value
                        translationX = clampedX * fwPx
                        translationY = clampedY * fhPx
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val ns = (scaleState.value * zoom).coerceIn(1f, 4f)
                            onScaleState.value(ns)
                            // pan 是像素增量，转归一化后累加（与 offset 单位一致）
                            val base = if (ns <= 1f) Offset.Zero else offsetState.value
                            onOffsetState.value(base + Offset(pan.x / fwPx, pan.y / fhPx))
                        }
                    },
            ) {
                Image(
                    painter = BitmapPainter(bitmap.asImageBitmap()),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * 卡片式美化滑块：顶部一行「标签 + 数值徽章」，下方整条滑块使用主题强调色。
 * 用于图片压缩的质量、黑白的阈值 / 对比度等调节器。
 */
@Composable
fun ToolSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    displayValue: String,
    modifier: Modifier = Modifier,
    sliderModifier: Modifier = Modifier.fillMaxWidth(),
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    displayValue,
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                    modifier = Modifier
                        .background(accent.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                modifier = sliderModifier,
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    inactiveTrackColor = accent.copy(alpha = 0.25f),
                ),
            )
        }
    }
}

/**
 * 缩放 + 方向键平移控制台（行内卡片式，与马赛克/去水印侧栏布局一致）。
 * 放大（scale>1）后「上/下/左/右」方向键才可点击，通过小幅平移查看放大后看不见的区域；
 * 未加载图片或未放大时整体禁用并给出提示。
 */
@Composable
fun ZoomPanControls(
    scale: Float,
    onScaleChange: (Float) -> Unit,
    offset: Offset,
    onOffsetChange: (Offset) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    // offset 为归一化单位（0..1），方向键每按一次平移固定归一化步长
    val panStep = 0.12f
    val canPan = enabled && scale > 1f

    fun clampPan(o: Offset, s: Float): Offset {
        val m = (s - 1f) / 2f
        return Offset(o.x.coerceIn(-m, m), o.y.coerceIn(-m, m))
    }

    fun move(dx: Float, dy: Float) {
        if (!canPan) return
        onOffsetChange(clampPan(offset + Offset(dx * panStep, dy * panStep), scale))
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { onScaleChange((scale - 0.5f).coerceAtLeast(1f)) },
                enabled = enabled,
            ) { Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.tools_zoom_out)) }
            Text(
                "${scale.toInt()}×",
                style = MaterialTheme.typography.labelMedium,
            )
            IconButton(
                onClick = { onScaleChange((scale + 0.5f).coerceAtMost(4f)) },
                enabled = enabled,
            ) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.tools_zoom_in)) }
        }
        OutlinedButton(
            onClick = { onScaleChange(1f); onOffsetChange(Offset.Zero) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.tools_zoom_reset)) }

        Spacer(Modifier.height(Spacing.xs))
        Text(
            stringResource(R.string.tools_pan_hint),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            IconButton(
                onClick = { move(-1f, 0f) },
                enabled = canPan,
            ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.tools_pan_left)) }
            IconButton(
                onClick = { move(0f, -1f) },
                enabled = canPan,
            ) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.tools_pan_up)) }
            IconButton(
                onClick = { move(0f, 1f) },
                enabled = canPan,
            ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.tools_pan_down)) }
            IconButton(
                onClick = { move(1f, 0f) },
                enabled = canPan,
            ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.tools_pan_right)) }
        }
    }
}
