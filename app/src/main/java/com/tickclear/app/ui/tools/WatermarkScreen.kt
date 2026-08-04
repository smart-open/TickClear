package com.tickclear.app.ui.tools

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.tickclear.app.domain.tools.ImageRepair
import com.tickclear.app.domain.tools.QrGenerator
import com.tickclear.app.ui.theme.Spacing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/**
 * 去水印工具（V2.9++，简易版）：选图后在图上拖动框选水印区域，
 * 支持「色彩修复」（四周取色覆盖，适合纯色背景水印/AI 文字水印）与「模糊柔化」（轻度模糊），
 * 应用后保存到相册。纯 Compose + Bitmap 处理，零新依赖。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WatermarkScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var rects by remember { mutableStateOf<List<RectF>>(emptyList()) }
    var mode by remember { mutableStateOf(ImageRepair.RepairMode.REPAIR) }
    var strength by remember { mutableStateOf(10) }
    var busy by remember { mutableStateOf(false) }

    var overlaySize by remember { mutableStateOf(IntSize.Zero) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }
    val currentRect: RectF? = remember(dragStart, dragCurrent, overlaySize) {
        if (dragStart != null && dragCurrent != null && overlaySize.width > 0) {
            val w = overlaySize.width.toFloat()
            val h = overlaySize.height.toFloat()
            RectF(
                min(dragStart!!.x, dragCurrent!!.x) / w,
                min(dragStart!!.y, dragCurrent!!.y) / h,
                max(dragStart!!.x, dragCurrent!!.x) / w,
                max(dragStart!!.y, dragCurrent!!.y) / h,
            )
        } else {
            null
        }
    }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val bmp = ImageMasker.loadBitmap(context, uri)
            if (bmp != null) {
                bitmap = bmp
                rects = emptyList()
            } else {
                snackbarHostState.showSnackbar(context.getString(R.string.watermark_pick_hint))
            }
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_watermark_title)) },
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
            OutlinedButton(onClick = { pickLauncher.launch("image/*") }) {
                Text(stringResource(R.string.watermark_pick))
            }

            if (bitmap == null) {
                Spacer(Modifier.height(Spacing.md))
                Text(
                    stringResource(R.string.watermark_pick_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val bmp = bitmap!!
                val ratio = bmp.width.toFloat() / bmp.height.toFloat()
                // 预览区只占「扣掉下方控件后的剩余空间」。原先直接 fillMaxWidth().aspectRatio()，
                // 竖图高度 = 屏宽 / ratio（3:4 图约为屏宽的 1.33 倍），会把模式选择、强度滑杆、
                // 应用按钮整体挤出屏幕外，且 Column 不可滚动 → 用户只看得到图，看不到任何操作按钮。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .padding(Spacing.xs),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(modifier = Modifier.aspectRatio(ratio)) {
                        androidx.compose.foundation.Image(
                            painter = BitmapPainter(bmp.asImageBitmap()),
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize(),
                        )
                        androidx.compose.foundation.Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = { dragStart = it },
                                        onDrag = { change, _ -> dragCurrent = change.position },
                                        onDragEnd = {
                                            currentRect?.let { rects = rects + it }
                                            dragStart = null
                                            dragCurrent = null
                                        },
                                    )
                                }
                                .onSizeChanged { overlaySize = it },
                        ) {
                            val strokeW = 2.dp.toPx()
                            for (r in rects) {
                                drawOverlayRect(r, size, primaryColor, strokeW)
                            }
                            currentRect?.let { drawOverlayRect(it, size, primaryColor, strokeW) }
                        }
                    }
                }
                Text(
                    stringResource(R.string.watermark_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    FilterChip(
                        selected = mode == ImageRepair.RepairMode.REPAIR,
                        onClick = { mode = ImageRepair.RepairMode.REPAIR },
                        label = { Text(stringResource(R.string.watermark_mode_repair)) },
                    )
                    FilterChip(
                        selected = mode == ImageRepair.RepairMode.BLUR,
                        onClick = { mode = ImageRepair.RepairMode.BLUR },
                        label = { Text(stringResource(R.string.watermark_mode_blur)) },
                    )
                    OutlinedButton(onClick = { rects = rects.dropLast(1) }) {
                        Text(stringResource(R.string.watermark_undo))
                    }
                    OutlinedButton(onClick = { rects = emptyList() }) {
                        Text(stringResource(R.string.watermark_clear))
                    }
                }

                Text(stringResource(R.string.watermark_strength))
                Slider(
                    value = strength.toFloat(),
                    onValueChange = { strength = it.toInt() },
                    valueRange = 4f..16f,
                    steps = 11,
                    modifier = Modifier.fillMaxWidth(),
                )

                Button(
                    onClick = {
                        if (rects.isEmpty()) {
                            scope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.watermark_hint))
                            }
                            return@Button
                        }
                        scope.launch {
                            busy = true
                            val out = ImageRepair.applyRepair(bmp, rects, mode, strength)
                            val ok = QrGenerator.saveToGallery(context, out, "tickclear_watermark")
                            snackbarHostState.showSnackbar(
                                context.getString(if (ok) R.string.watermark_saved else R.string.watermark_save_fail),
                            )
                            busy = false
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.watermark_apply))
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawOverlayRect(
    r: RectF,
    size: Size,
    color: Color,
    strokeW: Float,
) {
    val left = r.left * size.width
    val top = r.top * size.height
    val right = r.right * size.width
    val bottom = r.bottom * size.height
    drawRect(
        color = color.copy(alpha = 0.35f),
        topLeft = Offset(left, top),
        size = Size(right - left, bottom - top),
    )
    drawRect(
        color = color,
        topLeft = Offset(left, top),
        size = Size(right - left, bottom - top),
        style = Stroke(width = strokeW),
    )
}
