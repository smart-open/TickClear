package com.tickclear.app.ui.tools

import android.graphics.Bitmap
import android.graphics.RectF
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tickclear.app.R
import com.tickclear.app.domain.tools.ImageMasker
import com.tickclear.app.domain.tools.ImageRepair
import com.tickclear.app.domain.tools.QrGenerator
import com.tickclear.app.ui.theme.Spacing
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.launch

/**
 * 去水印工具（V2.9++，简易版）：选图后在图上拖动框选水印区域，
 * 支持「色彩修复」与「模糊柔化」，应用后保存到相册。工具收进可折叠侧边面板，图片区放大并支持缩放。
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
    var strength by remember { mutableIntStateOf(10) }
    var busy by remember { mutableStateOf(false) }

    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }

    var panelExpanded by remember { mutableStateOf(true) }
    var scale by remember { mutableFloatStateOf(1f) }
    // 归一化平移量（单位：图片宽/高比例），由方向键控制，缩放/平移时与选区坐标同步
    var offset by remember { mutableStateOf(Offset.Zero) }
    val panStep = 0.12f
    fun clampPan(o: Offset, s: Float): Offset {
        val m = (s - 1f) / 2f
        return Offset(o.x.coerceIn(-m, m), o.y.coerceIn(-m, m))
    }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val bmp = ImageMasker.loadBitmap(context, uri)
            if (bmp != null) {
                bitmap = bmp
                rects = emptyList()
                offset = Offset.Zero
            } else {
                snackbarHostState.showSnackbar(context.getString(R.string.watermark_pick_hint))
            }
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary

    val currentRect: RectF? = remember(dragStart, dragCurrent) {
        if (dragStart != null && dragCurrent != null) {
            RectF(
                min(dragStart!!.x, dragCurrent!!.x),
                min(dragStart!!.y, dragCurrent!!.y),
                max(dragStart!!.x, dragCurrent!!.x),
                max(dragStart!!.y, dragCurrent!!.y),
            )
        } else {
            null
        }
    }

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
        Row(Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                OutlinedButton(
                    onClick = { pickLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
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
                            onDrawStart = { nx, ny -> dragStart = Offset(nx, ny) },
                            onDrawMove = { nx, ny -> dragCurrent = Offset(nx, ny) },
                            onDrawEnd = {
                                val s = dragStart
                                val c = dragCurrent
                                if (s != null && c != null) {
                                    val l = min(s.x, c.x)
                                    val t = min(s.y, c.y)
                                    val r = max(s.x, c.x)
                                    val b = max(s.y, c.y)
                                    if (r - l > 0.005f && b - t > 0.005f) {
                                        rects = rects + RectF(l, t, r, b)
                                    }
                                }
                                dragStart = null
                                dragCurrent = null
                            },
                            overlay = {
                                Canvas(Modifier.fillMaxSize()) {
                                    val strokeW = 3.dp.toPx()
                                    for (r in rects) drawOverlayRect(r, size, primaryColor, strokeW)
                                    currentRect?.let { drawOverlayRect(it, size, primaryColor, strokeW) }
                                }
                            },
                        )
                    }
                    Text(
                        stringResource(R.string.watermark_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            ToolSidePanel(
                expanded = panelExpanded,
                onToggle = { panelExpanded = !panelExpanded },
                modifier = Modifier.width(if (panelExpanded) 196.dp else 52.dp),
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    IconButton(
                        onClick = { offset = clampPan(offset + Offset(-panStep, 0f), scale) },
                        enabled = scale > 1f,
                    ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.tools_pan_left)) }
                    IconButton(
                        onClick = { offset = clampPan(offset + Offset(0f, -panStep), scale) },
                        enabled = scale > 1f,
                    ) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.tools_pan_up)) }
                    IconButton(
                        onClick = { offset = clampPan(offset + Offset(0f, panStep), scale) },
                        enabled = scale > 1f,
                    ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.tools_pan_down)) }
                    IconButton(
                        onClick = { offset = clampPan(offset + Offset(panStep, 0f), scale) },
                        enabled = scale > 1f,
                    ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.tools_pan_right)) }
                }

                HorizontalDivider()

                FilterChip(
                    selected = mode == ImageRepair.RepairMode.REPAIR,
                    onClick = { mode = ImageRepair.RepairMode.REPAIR },
                    label = { Text(stringResource(R.string.watermark_mode_repair)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                FilterChip(
                    selected = mode == ImageRepair.RepairMode.BLUR,
                    onClick = { mode = ImageRepair.RepairMode.BLUR },
                    label = { Text(stringResource(R.string.watermark_mode_blur)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    OutlinedButton(
                        onClick = { rects = rects.dropLast(1) },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.watermark_undo)) }
                    OutlinedButton(
                        onClick = { rects = emptyList() },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.watermark_clear)) }
                }

                Text(stringResource(R.string.watermark_strength), style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = strength.toFloat(),
                    onValueChange = { strength = it.toInt() },
                    valueRange = 4f..16f,
                    steps = 11,
                    modifier = Modifier.fillMaxWidth(),
                )

                Button(
                    onClick = {
                        val bmp = bitmap ?: run {
                            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.watermark_hint)) }
                            return@Button
                        }
                        if (rects.isEmpty()) {
                            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.watermark_hint)) }
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
