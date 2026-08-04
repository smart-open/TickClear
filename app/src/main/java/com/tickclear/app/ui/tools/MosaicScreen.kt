package com.tickclear.app.ui.tools

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.tickclear.app.R
import com.tickclear.app.domain.tools.ImageMasker
import com.tickclear.app.domain.tools.QrGenerator
import com.tickclear.app.ui.theme.Spacing
import kotlinx.coroutines.launch

/** 涂抹工具：矩形拖框 / 自由笔刷。 */
private enum class DrawMode { BRUSH, BOX }

/**
 * 马赛克 / 涂抹遮挡工具（V2.9++）：
 * - 选图后在图上自由笔刷涂抹（自由路径）或拖框选区域；
 * - 两种模式应用时均为「马赛克（像素块平均）」或「涂黑」；
 * - 应用后保存到相册。纯 Compose + Bitmap 处理。
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
    var strength by remember { mutableStateOf(10) }
    var brushWidthRatio by remember { mutableStateOf(0.04f) } // 笔刷宽度占短边比例
    var busy by remember { mutableStateOf(false) }

    // 实时拖拽状态：BRUSH 模式下累积点列，BOX 模式下记录矩形
    var overlaySize by remember { mutableStateOf(IntSize.Zero) }
    var currentStroke by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
    var dragBox by remember { mutableStateOf<ImageMasker.MaskShape.Box?>(null) }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val bmp = ImageMasker.loadBitmap(context, uri)
            if (bmp != null) {
                bitmap = bmp
                shapes = emptyList()
                currentStroke = emptyList()
                dragBox = null
            } else {
                snackbarHostState.showSnackbar(context.getString(R.string.mosaic_pick_hint))
            }
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary

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
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                val bmp = bitmap!!
                val ratio = bmp.width.toFloat() / bmp.height.toFloat()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .padding(Spacing.xs),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(ratio)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .onSizeChanged { overlaySize = it }
                            .pointerInput(drawMode, bmp) {
                                detectDragGestures(
                                    onDragStart = { start ->
                                        if (drawMode == DrawMode.BRUSH) {
                                            currentStroke = listOf(
                                                start.x / size.width.toFloat() to
                                                    start.y / size.height.toFloat(),
                                            )
                                        } else {
                                            dragBox = ImageMasker.MaskShape.Box(
                                                left = start.x / size.width.toFloat(),
                                                top = start.y / size.height.toFloat(),
                                                right = start.x / size.width.toFloat(),
                                                bottom = start.y / size.height.toFloat(),
                                            )
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        if (drawMode == DrawMode.BRUSH) {
                                            val nx = change.position.x / size.width.toFloat()
                                            val ny = change.position.y / size.height.toFloat()
                                            // 距离上一次点过近则跳过，密集点只是浪费内存
                                            val last = currentStroke.lastOrNull()
                                            if (last == null || (last.first - nx) * (last.first - nx) +
                                                (last.second - ny) * (last.second - ny) > 0.00005f
                                            ) {
                                                currentStroke = currentStroke + (nx to ny)
                                            }
                                        } else {
                                            dragBox?.let { box ->
                                                dragBox = box.copy(
                                                    right = change.position.x / size.width.toFloat(),
                                                    bottom = change.position.y / size.height.toFloat(),
                                                )
                                            }
                                        }
                                    },
                                    onDragEnd = {
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
                                                val l = minOf(box.left, box.right)
                                                val t = minOf(box.top, box.bottom)
                                                val r = maxOf(box.left, box.right)
                                                val b = maxOf(box.top, box.bottom)
                                                if (r - l > 0.005f && b - t > 0.005f) {
                                                    shapes = shapes + ImageMasker.MaskShape.Box(l, t, r, b)
                                                }
                                            }
                                            dragBox = null
                                        }
                                    },
                                )
                            },
                    ) {
                        Image(
                            painter = BitmapPainter(bmp.asImageBitmap()),
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeW = 2.dp.toPx()
                            // 已有 shapes 描边：区分 BRUSH 路径 vs BOX 矩形
                            for (s in shapes) {
                                when (s) {
                                    is ImageMasker.MaskShape.Box -> drawBoxStroke(this, s, primaryColor, strokeW)
                                    is ImageMasker.MaskShape.Stroke -> drawStrokePath(this, s, primaryColor, strokeW)
                                }
                            }
                            // 实时拖拽态
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
                    }
                }
                Text(
                    stringResource(
                        if (drawMode == DrawMode.BRUSH) R.string.mosaic_hint_brush
                        else R.string.mosaic_hint_box,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    FilterChip(
                        selected = drawMode == DrawMode.BRUSH,
                        onClick = { drawMode = DrawMode.BRUSH },
                        leadingIcon = { Icon(Icons.Filled.Brush, contentDescription = null) },
                        label = { Text(stringResource(R.string.mosaic_draw_brush)) },
                    )
                    FilterChip(
                        selected = drawMode == DrawMode.BOX,
                        onClick = { drawMode = DrawMode.BOX },
                        leadingIcon = { Icon(Icons.Filled.CropSquare, contentDescription = null) },
                        label = { Text(stringResource(R.string.mosaic_draw_box)) },
                    )
                    FilterChip(
                        selected = maskMode == ImageMasker.MaskMode.MOSAIC,
                        onClick = { maskMode = ImageMasker.MaskMode.MOSAIC },
                        label = { Text(stringResource(R.string.mosaic_mode_mosaic)) },
                    )
                    FilterChip(
                        selected = maskMode == ImageMasker.MaskMode.BLACK,
                        onClick = { maskMode = ImageMasker.MaskMode.BLACK },
                        label = { Text(stringResource(R.string.mosaic_mode_black)) },
                    )
                    OutlinedButton(onClick = { shapes = shapes.dropLast(1) }) {
                        Text(stringResource(R.string.mosaic_undo))
                    }
                    OutlinedButton(onClick = { shapes = emptyList() }) {
                        Text(stringResource(R.string.mosaic_clear))
                    }
                }

                if (drawMode == DrawMode.BRUSH) {
                    Text(stringResource(R.string.mosaic_brush_width))
                    Slider(
                        value = brushWidthRatio,
                        onValueChange = { brushWidthRatio = it },
                        valueRange = 0.01f..0.15f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (maskMode == ImageMasker.MaskMode.MOSAIC) {
                    Text(stringResource(R.string.mosaic_strength))
                    Slider(
                        value = strength.toFloat(),
                        onValueChange = { strength = it.toInt() },
                        valueRange = 4f..24f,
                        steps = 19,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Button(
                    onClick = {
                        if (shapes.isEmpty()) {
                            scope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.mosaic_hint_brush))
                            }
                            return@Button
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
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(20.dp),
                            strokeWidth = 2.dp,
                            color = onPrimaryColor,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.mosaic_apply))
                }
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
    // 半透明填充 + 主色描边
    scope.drawIntoCanvas { canvas ->
        val androidPath = android.graphics.Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
        }
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            // `color` / `strokeWidth` 与外层 Compose Color 参数冲突，改用具名 setter
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
