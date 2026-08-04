package com.tickclear.app.ui.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.tickclear.app.R
import com.tickclear.app.domain.tools.ImageMasker
import com.tickclear.app.domain.tools.PhotoRuler
import com.tickclear.app.ui.theme.Spacing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs

/** 测距模式：纯屏幕刻度 or 拍照参照物比例换算。 */
private enum class RulerMode { SCREEN, PHOTO }

/** 拍照测距四个落点状态机：先点参照物两点，再点目标物两点。 */
private enum class TapStage { REF1, REF2, TARGET1, TARGET2 }

private val ReferenceColor = Color(0xFF1E88E5)
private val TargetColor = Color(0xFFE53935)

/**
 * 测距仪：
 * - 屏幕刻度：基于屏幕 xdpi 的简单标尺，用于屏内可量物体（约值）。
 * - 拍照测距：在照片中先标定一个已知尺寸的参照物两端、再标目标物两端，
 *   按像素比例换算得到目标实际长度。误差受拍摄角度与共面性影响。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(RulerMode.SCREEN) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_ruler_title)) },
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
                .padding(innerPadding)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        ) {
            ModeSelector(mode, onChange = { mode = it })
            Spacer(Modifier.height(Spacing.sm))
            when (mode) {
                RulerMode.SCREEN -> ScreenRuler()
                RulerMode.PHOTO -> PhotoRulerPanel(scope = scope, context = context)
            }
        }
    }
}

@Composable
private fun ModeSelector(mode: RulerMode, onChange: (RulerMode) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        FilterChip(
            selected = mode == RulerMode.SCREEN,
            onClick = { onChange(RulerMode.SCREEN) },
            label = { Text(stringResource(R.string.ruler_mode_screen)) },
            modifier = Modifier.weight(1f),
        )
        FilterChip(
            selected = mode == RulerMode.PHOTO,
            onClick = { onChange(RulerMode.PHOTO) },
            label = { Text(stringResource(R.string.ruler_mode_photo)) },
            modifier = Modifier.weight(1f),
        )
    }
}

/** 屏幕刻度模式。 */
@Composable
private fun ScreenRuler() {
    val context = LocalContext.current
    val pxPerMm = context.resources.displayMetrics.xdpi / 25.4f
    val primaryColor = MaterialTheme.colorScheme.primary

    var startX by remember { mutableStateOf(0f) }
    var endX by remember { mutableStateOf(0f) }
    var initialized by remember { mutableStateOf(false) }
    var endSet by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            stringResource(R.string.ruler_hint),
            style = MaterialTheme.typography.bodyMedium,
        )
        val lengthCm = if (initialized) abs(endX - startX) / pxPerMm / 10f else 0f
        Text(
            stringResource(R.string.ruler_length, lengthCm),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            stringResource(R.string.ruler_calibrated),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        if (!initialized) {
                            startX = offset.x
                            endX = offset.x
                            initialized = true
                            endSet = false
                        } else if (!endSet) {
                            endX = offset.x
                            endSet = true
                        } else {
                            startX = offset.x
                            endX = offset.x
                            endSet = false
                        }
                    }
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val baseY = h * 0.55f
                val primary = Color.Gray
                drawLine(primary, Offset(0f, baseY), Offset(w, baseY), strokeWidth = 2f)
                val totalMm = (w / pxPerMm).toInt()
                for (i in 0..totalMm) {
                    val x = i * pxPerMm
                    val tickH = when {
                        i % 10 == 0 -> 28f
                        i % 5 == 0 -> 16f
                        else -> 9f
                    }
                    drawLine(primary, Offset(x, baseY - tickH), Offset(x, baseY), strokeWidth = 1.5f)
                }
                if (initialized) {
                    val left = minOf(startX, endX).coerceIn(0f, w)
                    val right = maxOf(startX, endX).coerceIn(0f, w)
                    drawLine(primaryColor, Offset(left, baseY - 36f), Offset(right, baseY - 36f), strokeWidth = 4f)
                    if (endSet && right > left) {
                        drawLine(
                            primaryColor.copy(alpha = 0.25f),
                            Offset(left, 0f),
                            Offset(right, h),
                            strokeWidth = 1f,
                        )
                    }
                    drawCircle(primaryColor, radius = 8f, center = Offset(left, baseY - 36f))
                    drawCircle(primaryColor, radius = 8f, center = Offset(right, baseY - 36f))
                }
            }
        }
        if (initialized && !endSet) {
            Text(
                stringResource(R.string.ruler_tap_second),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 拍照测距模式。 */
@Composable
private fun PhotoRulerPanel(
    scope: CoroutineScope,
    context: Context,
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    var stage by remember { mutableStateOf(TapStage.REF1) }
    var ref1 by remember { mutableStateOf<Offset?>(null) }
    var ref2 by remember { mutableStateOf<Offset?>(null) }
    var target1 by remember { mutableStateOf<Offset?>(null) }
    var target2 by remember { mutableStateOf<Offset?>(null) }

    var selectedPreset by remember { mutableStateOf(PhotoRuler.PRESETS[0]) }
    var customMm by remember { mutableStateOf("") }
    val refMm: Float? = customMm.toFloatOrNull()?.takeIf { it > 0 } ?: selectedPreset.mm

    // 局部函数必须先声明再被 SAM lambda 引用，编译器在 lambda 处静态解析标识符，前向引用不接受
    fun resetPoints() {
        stage = TapStage.REF1
        ref1 = null
        ref2 = null
        target1 = null
        target2 = null
    }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            bitmap = ImageMasker.loadBitmap(context, uri)
            resetPoints()
        }
    }
    val photoUri = remember {
        FileProvider.getUriForFile(
            context,
            "com.tickclear.app.fileprovider",
            File(File(context.cacheDir, "share").apply { mkdirs() }, "ruler_capture.jpg"),
        )
    }
    val captureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (!ok) return@rememberLauncherForActivityResult
        scope.launch {
            bitmap = ImageMasker.loadBitmap(context, photoUri)
            resetPoints()
        }
    }
    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) captureLauncher.launch(photoUri)
    }

    val readyForTarget = stage == TapStage.TARGET1 || stage == TapStage.TARGET2
    val allSet = stage == TapStage.TARGET2 && ref2 != null && target2 != null

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            stringResource(R.string.ruler_photo_intro),
            style = MaterialTheme.typography.bodyMedium,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Button(
                onClick = {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) captureLauncher.launch(photoUri)
                    else cameraPermLauncher.launch(Manifest.permission.CAMERA)
                },
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.ruler_capture)) }
            OutlinedButton(
                onClick = { pickLauncher.launch("image/*") },
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.ruler_pick)) }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    stringResource(R.string.ruler_reference_title),
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    PhotoRuler.PRESETS.forEach { preset ->
                        FilterChip(
                            selected = selectedPreset.key == preset.key && customMm.isBlank(),
                            onClick = {
                                selectedPreset = preset
                                customMm = ""
                            },
                            label = { Text(preset.label) },
                        )
                    }
                }
                OutlinedTextField(
                    value = customMm,
                    onValueChange = { v -> customMm = v.filter { it.isDigit() || it == '.' } },
                    label = { Text(stringResource(R.string.ruler_reference_custom)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        val hintRes = when (stage) {
            TapStage.REF1 -> R.string.ruler_tap_ref1
            TapStage.REF2 -> R.string.ruler_tap_ref2
            TapStage.TARGET1 -> R.string.ruler_tap_target1
            TapStage.TARGET2 -> R.string.ruler_tap_target2
        }
        Text(
            stringResource(hintRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (bitmap == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.ruler_no_image),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val bmp = bitmap!!
            val ratio = bmp.width.toFloat() / bmp.height.toFloat()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio)
                    .pointerInput(bmp) {
                        detectTapGestures { offset ->
                            when (stage) {
                                TapStage.REF1 -> { ref1 = offset; stage = TapStage.REF2 }
                                TapStage.REF2 -> { ref2 = offset; stage = TapStage.TARGET1 }
                                TapStage.TARGET1 -> { target1 = offset; stage = TapStage.TARGET2 }
                                TapStage.TARGET2 -> { target2 = offset }
                            }
                        }
                    },
            ) {
                Image(
                    painter = BitmapPainter(bmp.asImageBitmap()),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
                Canvas(modifier = Modifier.fillMaxSize()) {
                    ref1?.let { drawMarker(it, ReferenceColor, "R1") }
                    ref2?.let { drawMarker(it, ReferenceColor, "R2") }
                    if (ref1 != null && ref2 != null) {
                        drawSegment(ref1!!, ref2!!, ReferenceColor)
                    }
                    target1?.let { drawMarker(it, TargetColor, "T1") }
                    target2?.let { drawMarker(it, TargetColor, "T2") }
                    if (target1 != null && target2 != null) {
                        drawSegment(target1!!, target2!!, TargetColor)
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            OutlinedButton(
                onClick = ::resetPoints,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.ruler_reset)) }
            OutlinedButton(
                onClick = {
                    when (stage) {
                        TapStage.REF1 -> Unit
                        TapStage.REF2 -> { ref2 = null; stage = TapStage.REF1 }
                        TapStage.TARGET1 -> { target1 = null; stage = TapStage.REF2 }
                        TapStage.TARGET2 -> { target1 = null; stage = TapStage.TARGET1 }
                    }
                },
                enabled = stage != TapStage.REF1,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.ruler_undo)) }
        }

        val resultMm: Float? = if (allSet && ref1 != null && ref2 != null && target1 != null && target2 != null) {
            val refPx = PhotoRuler.pixelDistance(ref1!!.x, ref1!!.y, ref2!!.x, ref2!!.y)
            val tgtPx = PhotoRuler.pixelDistance(target1!!.x, target1!!.y, target2!!.x, target2!!.y)
            PhotoRuler.measure(refMm ?: 0f, refPx, tgtPx)
        } else null

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                when {
                    resultMm != null -> {
                        val cm = resultMm / 10f
                        Text(
                            stringResource(R.string.ruler_result, cm, resultMm),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            stringResource(R.string.ruler_accuracy_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    readyForTarget -> Text(
                        stringResource(R.string.ruler_result_pending),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> Text(
                        stringResource(R.string.ruler_result_idle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(Spacing.sm))
    }
}

private fun DrawScope.drawMarker(p: Offset, color: Color, label: String) {
    val r = 10f
    drawCircle(color.copy(alpha = 0.25f), radius = r * 2f, center = p)
    drawCircle(Color.White, radius = r + 2f, center = p)
    drawCircle(color, radius = r, center = p)
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        // 参数 `color: Color` 会遮盖 Paint.color 的 setter 解析，改用具名 setColor 显式赋 Int
        setColor(android.graphics.Color.WHITE)
        textSize = r * 1.1f
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    drawContext.canvas.nativeCanvas.drawText(label, p.x, p.y + r * 0.4f, paint)
}

private fun DrawScope.drawSegment(a: Offset, b: Offset, color: Color) {
    drawLine(color.copy(alpha = 0.6f), a, b, strokeWidth = 3f)
    drawLine(color, a, b, strokeWidth = 2f)
}
