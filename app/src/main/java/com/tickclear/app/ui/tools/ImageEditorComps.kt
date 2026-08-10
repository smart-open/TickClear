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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
/**
 * 模拟解压类工具的醒目数据卡（计数 / 分数 / 压力等）。
 * [horizontal]=true 时数值与单位同行不换行（如「123下」），用于横向空间紧张的场景。
 */
@Composable
fun SimStatCard(
    value: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    horizontal: Boolean = false,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(16.dp),
    ) {
        if (horizontal && label != null) {
            Row(
                modifier = Modifier.padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    value,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    softWrap = false,
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        } else {
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
 * 竖向滑块（竖条）：标签在上、竖向轨道居中、数值徽章在下。
 * 用于图片编辑类工具的调节器（强度 / 对比度 / 质量 / 笔刷宽度等），比横向滑块更省横向空间。
 * [barHeight] 默认 96dp —— 取侧栏展开宽度的约一半，即「竖条长度减半」。
 */
@Composable
fun ToolVerticalSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    displayValue: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    barHeight: Dp = 96.dp,
) {
    val density = LocalDensity.current
    val barHeightPx = with(density) { barHeight.toPx() }
    val trackW = 8.dp
    val thumbR = with(density) { 11.dp.toPx() }
    val onValueChangeState = rememberUpdatedState(onValueChange)
    // Canvas 绘制 lambda 非 @Composable 上下文，颜色需在此捕获
    val surfaceColor = MaterialTheme.colorScheme.surface

    // 屏幕落点 y（相对竖条顶部，px）→ 归一化分数 → 实际值；steps>0 时吸附到离散档位
    fun valueFromY(yPx: Float): Float {
        val frac = (1f - (yPx / barHeightPx)).coerceIn(0f, 1f)
        var v = valueRange.start + frac * (valueRange.endInclusive - valueRange.start)
        if (steps > 0) {
            val stepSize = (valueRange.endInclusive - valueRange.start) / (steps + 1)
            v = (kotlin.math.round((v - valueRange.start) / stepSize) * stepSize + valueRange.start).toFloat()
        }
        return v
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Canvas(
                modifier = Modifier
                    .width(48.dp)
                    .height(barHeight)
                    .pointerInput(valueRange, steps) {
                        detectDragGestures(
                            onDragStart = { onValueChangeState.value(valueFromY(it.y)) },
                            onDrag = { change, _ -> onValueChangeState.value(valueFromY(change.position.y)) },
                        )
                    },
            ) {
                val cx = size.width / 2f
                val trackWpx = with(density) { trackW.toPx() }
                val frac = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
                val fillH = size.height * frac
                // 轨道
                drawRoundRect(
                    color = accent.copy(alpha = 0.25f),
                    topLeft = Offset(cx - trackWpx / 2f, 0f),
                    size = Size(trackWpx, size.height),
                    cornerRadius = CornerRadius(trackWpx / 2f),
                )
                // 已填充
                if (fillH > 1f) {
                    drawRoundRect(
                        color = accent,
                        topLeft = Offset(cx - trackWpx / 2f, size.height - fillH),
                        size = Size(trackWpx, fillH),
                        cornerRadius = CornerRadius(trackWpx / 2f),
                    )
                }
                // 滑块（带内圈，便于辨识）
                val ty = size.height - fillH
                drawCircle(color = accent, radius = thumbR, center = Offset(cx, ty))
                drawCircle(
                    color = surfaceColor,
                    radius = thumbR * 0.42f,
                    center = Offset(cx, ty),
                )
            }
            Text(
                displayValue,
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                modifier = Modifier
                    .background(accent.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

/**
 * 单行紧凑横向调节器（V2.9++）：label / Slider / display-value 三段在同一行 Row 内，
 * label 自然宽度、Slider 占 weight(1f) 弹性空间、display-value 自然宽度，整体 1 行不占两行竖向空间。
 * 与 [ToolSlider]（顶部 label + 下方整条 Slider 的卡片样式）和 [ToolVerticalSlider]（左 label + 右侧竖条）互补——
 * **工具箱侧边面板专用** 紧凑型，横排节省 portrait 屏幕的纵向空间。
 *
 * 用于马赛克 / 去水印等工具的强度 / 笔刷宽度调节器。
 */
@Composable
fun MiniHorizontalSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    displayValue: String,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = accent.copy(alpha = 0.25f),
            ),
        )
        Text(
            displayValue,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            maxLines = 1,
        )
    }
}

/**
 * 双行横向调节器：标题在上、自定义 Canvas 横条 + 圆球滑块在下。
 * 用于马赛克 / 去水印侧边面板的强度 / 笔刷宽度调节，比 [MiniHorizontalSlider] 更直观，
 * 且不受其它工具（压缩 / 黑白）的单行布局影响。
 */
@Composable
fun ToolHorizontalSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    displayValue: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    barHeight: Dp = 24.dp,
) {
    val density = LocalDensity.current
    val barHeightPx = with(density) { barHeight.toPx() }
    val trackH = 6.dp
    val thumbR = with(density) { 7.dp.toPx() }
    val onValueChangeState = rememberUpdatedState(onValueChange)
    val surfaceColor = MaterialTheme.colorScheme.surface

    fun valueFromX(xPx: Float, widthPx: Float): Float {
        val frac = (xPx / widthPx).coerceIn(0f, 1f)
        var v = valueRange.start + frac * (valueRange.endInclusive - valueRange.start)
        if (steps > 0) {
            val stepSize = (valueRange.endInclusive - valueRange.start) / (steps + 1)
            v = (kotlin.math.round((v - valueRange.start) / stepSize) * stepSize + valueRange.start).toFloat()
        }
        return v
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
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
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    displayValue,
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                    modifier = Modifier
                        .background(accent.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    maxLines = 1,
                )
            }
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .pointerInput(valueRange, steps) {
                        detectDragGestures(
                            onDragStart = { onValueChangeState.value(valueFromX(it.x, size.width.toFloat())) },
                            onDrag = { change, _ -> onValueChangeState.value(valueFromX(change.position.x, size.width.toFloat())) },
                        )
                    },
            ) {
                val trackHpx = with(density) { trackH.toPx() }
                val frac = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
                val fillW = size.width * frac
                val cy = size.height / 2f
                // 轨道
                drawRoundRect(
                    color = accent.copy(alpha = 0.25f),
                    topLeft = Offset(0f, cy - trackHpx / 2f),
                    size = Size(size.width, trackHpx),
                    cornerRadius = CornerRadius(trackHpx / 2f),
                )
                // 已填充
                if (fillW > 1f) {
                    drawRoundRect(
                        color = accent,
                        topLeft = Offset(0f, cy - trackHpx / 2f),
                        size = Size(fillW, trackHpx),
                        cornerRadius = CornerRadius(trackHpx / 2f),
                    )
                }
                // 圆球滑块（带内圈，便于辨识）
                val tx = fillW
                drawCircle(color = accent, radius = thumbR, center = Offset(tx, cy))
                drawCircle(color = surfaceColor, radius = thumbR * 0.42f, center = Offset(tx, cy))
            }
        }
    }
}

/**
 * 缩放 + 方向键平移控制台（行内卡片式，与马赛克/去水印侧栏布局一致）。
 * 放大（scale>1）后「上/下/左/右」方向键才可点击，通过小幅平移查看放大后看不见的区域；
 * 未加载图片或未放大时整体禁用并给出提示。
 *
 * [compact]=true 时缩小图标与按钮尺寸、收紧行距，用于侧边面板空间紧张的 Mosaic/Watermark。
 */
@Composable
fun ZoomPanControls(
    scale: Float,
    onScaleChange: (Float) -> Unit,
    offset: Offset,
    onOffsetChange: (Offset) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    // offset 为归一化单位（0..1），方向键每按一次平移固定归一化步长
    val panStep = 0.12f
    val canPan = enabled && scale > 1f
    // 复位仅在已放大或已偏移时可用（默认 scale=1 且 offset=0 时置灰）
    val canReset = enabled && (scale != 1f || offset != Offset.Zero)

    fun clampPan(o: Offset, s: Float): Offset {
        val m = (s - 1f) / 2f
        return Offset(o.x.coerceIn(-m, m), o.y.coerceIn(-m, m))
    }

    fun move(dx: Float, dy: Float) {
        if (!canPan) return
        onOffsetChange(clampPan(offset + Offset(dx * panStep, dy * panStep), scale))
    }

    fun setScale(newScale: Float) {
        val ns = newScale.coerceIn(1f, 4f)
        onScaleChange(ns)
        onOffsetChange(clampPan(offset, ns))
    }

    val iconSize = if (compact) 18.dp else 24.dp
    val buttonSize = if (compact) 32.dp else 40.dp
    val dpadSpacing = if (compact) 0.dp else Spacing.xs
    val labelStyle = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) Spacing.xs else Spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { setScale(scale - 0.5f) },
                enabled = enabled,
                modifier = Modifier.size(buttonSize),
            ) { Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.tools_zoom_out), modifier = Modifier.size(iconSize)) }
            Text(
                "${scale.toInt()}×",
                style = labelStyle,
            )
            IconButton(
                onClick = { setScale(scale + 0.5f) },
                enabled = enabled,
                modifier = Modifier.size(buttonSize),
            ) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.tools_zoom_in), modifier = Modifier.size(iconSize)) }
        }
        OutlinedButton(
            onClick = { setScale(1f); onOffsetChange(Offset.Zero) },
            enabled = canReset,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.tools_zoom_reset), style = labelStyle) }

        if (!compact) Spacer(Modifier.height(Spacing.xs))
        Text(
            stringResource(R.string.tools_pan_hint),
            style = labelStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // 常规十字 D-pad：↑ 在上、←→ 在中间、↓ 在下（共 3 行）
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(dpadSpacing),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                IconButton(
                    onClick = { move(0f, -1f) },
                    enabled = canPan,
                    modifier = Modifier.size(buttonSize),
                ) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.tools_pan_up), modifier = Modifier.size(iconSize)) }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                IconButton(
                    onClick = { move(-1f, 0f) },
                    enabled = canPan,
                    modifier = Modifier.size(buttonSize),
                ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.tools_pan_left), modifier = Modifier.size(iconSize)) }
                IconButton(
                    onClick = { move(1f, 0f) },
                    enabled = canPan,
                    modifier = Modifier.size(buttonSize),
                ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.tools_pan_right), modifier = Modifier.size(iconSize)) }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                IconButton(
                    onClick = { move(0f, 1f) },
                    enabled = canPan,
                    modifier = Modifier.size(buttonSize),
                ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.tools_pan_down), modifier = Modifier.size(iconSize)) }
            }
        }
    }
}
