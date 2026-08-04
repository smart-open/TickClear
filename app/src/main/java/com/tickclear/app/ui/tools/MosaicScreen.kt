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
import com.tickclear.app.domain.tools.QrGenerator
import com.tickclear.app.ui.theme.Spacing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/**
 * 马赛克 / 涂抹遮挡工具（V2.9++）：选图后在图上拖动框选敏感区域，
 * 支持「马赛克（像素块）」与「涂黑」两种方式，应用后保存到相册。纯 Compose + Bitmap 处理。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MosaicScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var rects by remember { mutableStateOf<List<RectF>>(emptyList()) }
    var mode by remember { mutableStateOf(ImageMasker.MaskMode.MOSAIC) }
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
                        .aspectRatio(ratio)
                        .padding(Spacing.xs),
                ) {
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
                Text(
                    stringResource(R.string.mosaic_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    FilterChip(
                        selected = mode == ImageMasker.MaskMode.MOSAIC,
                        onClick = { mode = ImageMasker.MaskMode.MOSAIC },
                        label = { Text(stringResource(R.string.mosaic_mode_mosaic)) },
                    )
                    FilterChip(
                        selected = mode == ImageMasker.MaskMode.BLACK,
                        onClick = { mode = ImageMasker.MaskMode.BLACK },
                        label = { Text(stringResource(R.string.mosaic_mode_black)) },
                    )
                    OutlinedButton(onClick = { rects = rects.dropLast(1) }) {
                        Text(stringResource(R.string.mosaic_undo))
                    }
                    OutlinedButton(onClick = { rects = emptyList() }) {
                        Text(stringResource(R.string.mosaic_clear))
                    }
                }

                if (mode == ImageMasker.MaskMode.MOSAIC) {
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
                        if (rects.isEmpty()) {
                            scope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.mosaic_hint))
                            }
                            return@Button
                        }
                        scope.launch {
                            busy = true
                            val out = ImageMasker.applyMask(bmp, rects, mode, strength)
                            val ok = QrGenerator.saveToGallery(context, out, "tickclear_mosaic")
                            snackbarHostState.showSnackbar(
                                context.getString(if (ok) R.string.mosaic_saved else R.string.mosaic_save_fail),
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
                    Text(stringResource(R.string.mosaic_apply))
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
