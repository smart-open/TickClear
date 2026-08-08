package com.tickclear.app.ui.tools

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
        // 最新 scale / offset，供手势回调实时读取，避免 pointerInput(Unit) 闭包捕获陈旧值
        val scaleState = rememberUpdatedState(scale)
        val offsetState = rememberUpdatedState(offset)

        Box(
            modifier = Modifier
                .size(fw, fh)
                .clipToBounds(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x * fwPx
                        translationY = offset.y * fhPx
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { p ->
                                val (nx, ny) = toNormCoord(p, scaleState.value, offsetState.value, fwPx, fhPx)
                                onDrawStart(nx, ny)
                            },
                            onDrag = { change, _ ->
                                val (nx, ny) = toNormCoord(change.position, scaleState.value, offsetState.value, fwPx, fhPx)
                                onDrawMove(nx, ny)
                            },
                            onDragEnd = onDrawEnd,
                        )
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
        }
    }
}

/**
 * 将指针落点（内层 Box 布局坐标，px）反解算为归一化图片坐标。
 * graphicsLayer 真实变换顺序：**先平移 (offset*fwPx, offset*fhPx)，再以中心为轴缩放 [scale]**，
 * 因此平移量在最终屏幕上被 [scale] 放大，逆向必须减去 `scale * offset * fwPx`，
 * 否则放大+平移后笔迹会整体偏移到下方（偏移量被少减一截）。
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
    val nx = (cx + (p.x - cx - scale * offset.x * fwPx) / scale) / fwPx
    val ny = (cy + (p.y - cy - scale * offset.y * fhPx) / scale) / fhPx
    return nx to ny
}

/**
 * 可缩放的「预览画布」（图片压缩 / 黑白）。
 * 支持双指捏合缩放 + 单指拖动平移；scale 由外部 state 控制（侧边面板按钮与捏合手势都改它），
 * 回到 1× 时自动复位平移。
 */
@Composable
fun ZoomableImagePreview(
    bitmap: Bitmap,
    scale: Float,
    onScaleChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var offset by remember { mutableStateOf(Offset.Zero) }

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
        Box(
            modifier = Modifier
                .size(fw, fh)
                .clipToBounds(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val ns = (scale * zoom).coerceIn(1f, 4f)
                            onScaleChange(ns)
                            offset = if (ns == 1f) Offset.Zero else offset + pan
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
