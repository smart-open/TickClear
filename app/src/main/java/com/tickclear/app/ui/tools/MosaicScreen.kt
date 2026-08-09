package com.tickclear.app.ui.tools

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tickclear.app.R
import com.tickclear.app.domain.tools.ImageMasker
import com.tickclear.app.domain.tools.QrGenerator
import com.tickclear.app.ui.theme.Spacing
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.launch

/** 涂抹工具：矩形拖框 / 自由笔刷。 */
private enum class DrawMode { BRUSH, BOX }

/**
 * 马赛克 / 涂抹遮挡工具（V2.9++）：
 * - 选图后在图上自由笔刷涂抹（自由路径）或拖框选区域；
 * - 两种模式应用时均为「马赛克（像素块平均）」或「涂黑」；
 * - 应用后保存到相册。工具收进可折叠侧边面板，图片区放大并支持缩放。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MosaicScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var shapes by remember { mutableStateOf<List<ImageMasker.MaskShape>>(emptyList()) }
    var drawMode by remember { mutableStateOf(DrawMode.BRUSH) }
    var maskMode by remember { mutableStateOf(ImageMasker.MaskMode.MOSAIC) }
    var strength by remember { mutableIntStateOf(10) }
    var brushWidthRatio by remember { mutableFloatStateOf(0.04f) } // 笔刷宽度占短边比例
    var busy by remember { mutableStateOf(false) }

    // 实时拖拽状态：BRUSH 模式下累积点列，BOX 模式下记录矩形
    var currentStroke by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
    var dragBox by remember { mutableStateOf<ImageMasker.MaskShape.Box?>(null) }

    var panelExpanded by remember { mutableStateOf(true) }
    var scale by remember { mutableFloatStateOf(1f) }
    // 归一化平移量（单位：图片宽/高比例），由方向键控制，缩放/平移时与选区坐标同步
    var offset by remember { mutableStateOf(Offset.Zero) }
    val panStep = 0.12f
    fun clampPan(o: Offset, s: Float): Offset {
        val m = (s - 1f) / 2f
        return Offset(o.x.coerceIn(-m, m), o.y.coerceIn(-m, m))
    }

    // 应用并保存（移到右上角图标按钮复用）：选图与选区校验 + 马赛克/涂黑后存相册
    fun applyMosaic() {
        val bmp = bitmap ?: run {
            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.mosaic_hint_brush)) }
            return
        }
        if (shapes.isEmpty()) {
            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.mosaic_hint_brush)) }
            return
        }
        scope.launch {
            busy = true
            val out = ImageMasker.applyMaskWithShapes(bmp, shapes, maskMode, strength)
            val ok = QrGenerator.saveToGallery(context, out, "tickclear_mosaic")
            snackbarHostState.showSnackbar(
                context.getString(if (ok) R.string.mosaic_saved else R.string.mosaic_save_fail),
            )
            busy = false
        }
    }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val bmp = ImageMasker.loadBitmap(context, uri)
            if (bmp != null) {
                bitmap = bmp
                shapes = emptyList()
                currentStroke = emptyList()
                dragBox = null
                offset = Offset.Zero
            } else {
                snackbarHostState.showSnackbar(context.getString(R.string.mosaic_pick_hint))
            }
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_mosaic_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    // 应用并保存：右上角 FilledIconButton + Save 图标（primaryContainer 填充色，比原 IconButton + ✓ 更醒目，
                    // 视觉权重与[标 + 取消]等 M3 主操作一致；处理中显示进度圈）
                    FilledIconButton(
                        onClick = { applyMosaic() },
                        enabled = !busy,
                        modifier = Modifier.size(40.dp),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        } else {
                            Icon(
                                Icons.Filled.Save,
                                contentDescription = stringResource(R.string.mosaic_apply),
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Row(Modifier.fillMaxSize().padding(innerPadding)) {
            // 主区：选图 + 图片画布
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Button(
                    onClick = { pickLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(stringResource(R.string.mosaic_pick))
                }

                if (bitmap == null) {
                    Spacer(Modifier.height(Spacing.md))
                    Text(
                        stringResource(R.string.mosaic_pick_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                    ) {
                        val bmp = bitmap!!
                        ZoomableDrawCanvas(
                            bitmap = bmp,
                            scale = scale,
                            offset = offset,
                            onOffsetChange = { offset = it },
                            onDrawStart = { nx, ny ->
                                if (drawMode == DrawMode.BRUSH) {
                                    currentStroke = listOf(nx to ny)
                                } else {
                                    dragBox = ImageMasker.MaskShape.Box(nx, ny, nx, ny)
                                }
                            },
                            onDrawMove = { nx, ny ->
                                if (drawMode == DrawMode.BRUSH) {
                                    val last = currentStroke.lastOrNull()
                                    if (last == null || (last.first - nx) * (last.first - nx) +
                                        (last.second - ny) * (last.second - ny) > 0.00005f
                                    ) {
                                        currentStroke = currentStroke + (nx to ny)
                                    }
                                } else {
                                    dragBox?.let { box ->
                                        dragBox = box.copy(right = nx, bottom = ny)
                                    }
                                }
                            },
                            onDrawEnd = {
                                if (drawMode == DrawMode.BRUSH) {
                                    if (currentStroke.size >= 2) {
                                        shapes = shapes + ImageMasker.MaskShape.Stroke(
                                            points = currentStroke,
                                            widthRatio = brushWidthRatio,
                                        )
                                    }
                                    currentStroke = emptyList()
                                } else {
                                    dragBox?.let { box ->
                                        val l = min(box.left, box.right)
                                        val t = min(box.top, box.bottom)
                                        val r = max(box.left, box.right)
                                        val b = max(box.top, box.bottom)
                                        if (r - l > 0.005f && b - t > 0.005f) {
                                            shapes = shapes + ImageMasker.MaskShape.Box(l, t, r, b)
                                        }
                                    }
                                    dragBox = null
                                }
                            },
                            overlay = {
                                Canvas(Modifier.fillMaxSize()) {
                                    val strokeW = 2.dp.toPx()
                                    for (s in shapes) {
                                        when (s) {
                                            is ImageMasker.MaskShape.Box -> drawBoxStroke(this, s, primaryColor, strokeW)
                                            is ImageMasker.MaskShape.Stroke -> drawStrokePath(this, s, primaryColor, strokeW)
                                        }
                                    }
                                    if (drawMode == DrawMode.BRUSH && currentStroke.size >= 2) {
                                        drawStrokePath(
                                            this,
                                            ImageMasker.MaskShape.Stroke(currentStroke, brushWidthRatio),
                                            primaryColor,
                                            strokeW,
                                        )
                                    }
                                    dragBox?.let { drawBoxStroke(this, it, primaryColor, strokeW) }
                                }
                            },
                        )
                    }
                    Text(
                        stringResource(
                            if (drawMode == DrawMode.BRUSH) R.string.mosaic_hint_brush
                            else R.string.mosaic_hint_box,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 侧边工具面板（可折叠）
            ToolSidePanel(
                expanded = panelExpanded,
                onToggle = { panelExpanded = !panelExpanded },
                modifier = Modifier.width(if (panelExpanded) 148.dp else 52.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { scale = (scale - 0.5f).coerceAtLeast(1f); offset = clampPan(offset, scale) }) {
                        Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.tools_zoom_out))
                    }
                    Text("${scale.toInt()}×", style = MaterialTheme.typography.labelMedium)
                    IconButton(onClick = { scale = (scale + 0.5f).coerceAtMost(4f); offset = clampPan(offset, scale) }) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.tools_zoom_in))
                    }
                }
                OutlinedButton(
                    onClick = { scale = 1f; offset = Offset.Zero },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.tools_zoom_reset)) }

                Spacer(Modifier.height(Spacing.xs))
                Text(stringResource(R.string.tools_pan_hint), style = MaterialTheme.typography.labelMedium)
                // 方向键改常规十字 D-pad：↑ 在上、←→ 在中间、↓ 在下（共 3 行）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    IconButton(
                        onClick = { offset = clampPan(offset + Offset(0f, -panStep), scale) },
                        enabled = scale > 1f,
                    ) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.tools_pan_up)) }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    IconButton(
                        onClick = { offset = clampPan(offset + Offset(-panStep, 0f), scale) },
                        enabled = scale > 1f,
                    ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.tools_pan_left)) }
                    IconButton(
                        onClick = { offset = clampPan(offset + Offset(panStep, 0f), scale) },
                        enabled = scale > 1f,
                    ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.tools_pan_right)) }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    IconButton(
                        onClick = { offset = clampPan(offset + Offset(0f, panStep), scale) },
                        enabled = scale > 1f,
                    ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.tools_pan_down)) }
                }

                HorizontalDivider()

                FilterChip(
                    selected = drawMode == DrawMode.BRUSH,
                    onClick = { drawMode = DrawMode.BRUSH },
                    leadingIcon = { Icon(Icons.Filled.Brush, contentDescription = null) },
                    label = { Text(stringResource(R.string.mosaic_draw_brush)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                FilterChip(
                    selected = drawMode == DrawMode.BOX,
                    onClick = { drawMode = DrawMode.BOX },
                    leadingIcon = { Icon(Icons.Filled.CropSquare, contentDescription = null) },
                    label = { Text(stringResource(R.string.mosaic_draw_box)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                FilterChip(
                    selected = maskMode == ImageMasker.MaskMode.MOSAIC,
                    onClick = { maskMode = ImageMasker.MaskMode.MOSAIC },
                    label = { Text(stringResource(R.string.mosaic_mode_mosaic)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                FilterChip(
                    selected = maskMode == ImageMasker.MaskMode.BLACK,
                    onClick = { maskMode = ImageMasker.MaskMode.BLACK },
                    label = { Text(stringResource(R.string.mosaic_mode_black)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    OutlinedButton(
                        onClick = { shapes = shapes.dropLast(1) },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.mosaic_undo)) }
                    OutlinedButton(
                        onClick = { shapes = emptyList() },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.mosaic_clear)) }
                }

                if (drawMode == DrawMode.BRUSH) {
                    MiniHorizontalSlider(
                        label = stringResource(R.string.mosaic_brush_width),
                        value = brushWidthRatio,
                        onValueChange = { brushWidthRatio = it },
                        valueRange = 0.01f..0.15f,
                        steps = 0,
                        displayValue = "${(brushWidthRatio * 100).toInt()}%",
                    )
                }
                if (maskMode == ImageMasker.MaskMode.MOSAIC) {
                    MiniHorizontalSlider(
                        label = stringResource(R.string.mosaic_strength),
                        value = strength.toFloat(),
                        onValueChange = { strength = it.toInt() },
                        valueRange = 4f..24f,
                        steps = 19,
                        displayValue = "$strength",
                    )
                }

                // 应用并保存已移至右上角图标按钮（见 TopAppBar actions）
            }
        }
    }
}

private fun drawBoxStroke(
    scope: androidx.compose.ui.graphics.drawscope.DrawScope,
    box: ImageMasker.MaskShape.Box,
    color: Color,
    strokeW: Float,
) {
    val left = box.left * scope.size.width
    val top = box.top * scope.size.height
    val right = box.right * scope.size.width
    val bottom = box.bottom * scope.size.height
    scope.drawRect(
        color = color.copy(alpha = 0.35f),
        topLeft = Offset(left, top),
        size = Size(right - left, bottom - top),
    )
    scope.drawRect(
        color = color,
        topLeft = Offset(left, top),
        size = Size(right - left, bottom - top),
        style = Stroke(width = strokeW),
    )
}

private fun drawStrokePath(
    scope: androidx.compose.ui.graphics.drawscope.DrawScope,
    stroke: ImageMasker.MaskShape.Stroke,
    color: Color,
    strokeW: Float,
) {
    if (stroke.points.size < 2) return
    val path = Path()
    val pts = stroke.points.map { (x, y) -> Offset(x * scope.size.width, y * scope.size.height) }
    path.moveTo(pts[0].x, pts[0].y)
    for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
    val w = stroke.widthRatio * minOf(scope.size.width, scope.size.height)
    scope.drawIntoCanvas { canvas ->
        val androidPath = android.graphics.Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
        }
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            setColor(color.copy(alpha = 0.18f).toArgb())
            style = android.graphics.Paint.Style.STROKE
            setStrokeWidth(w)
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
        }
        canvas.nativeCanvas.drawPath(androidPath, paint)
        val paint2 = android.graphics.Paint(paint).apply {
            setColor(color.toArgb())
            setStrokeWidth(strokeW)
        }
        canvas.nativeCanvas.drawPath(androidPath, paint2)
    }
}
