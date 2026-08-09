package com.tickclear.app.ui.tools

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tickclear.app.R
import com.tickclear.app.domain.tools.QrGenerator
import com.tickclear.app.ui.components.Haptic
import com.tickclear.app.ui.theme.Spacing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 手指涂鸦画板（V2.9++ 模拟解压）。
 * 选颜色 + 笔刷随手画，支持撤销、清空、保存图片到相册，可全屏沉浸。
 * 笔迹在 Compose Canvas 实时重绘；保存时将笔迹重绘到 Bitmap 经 MediaStore 落盘。
 */
private data class DoodleStroke(
    val points: List<Offset>,
    val color: Color,
    val width: Float,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoodleScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    // 保存动作不能随页面销毁被取消，否则用户点完保存立刻返回会静默丢图
    val saveScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }

    var fullscreen by remember { mutableStateOf(false) }
    var strokes by remember { mutableStateOf(listOf<DoodleStroke>()) }
    var current by remember { mutableStateOf<DoodleStroke?>(null) }
    var drawColor by remember { mutableStateOf(Color(0xFFE53935)) }
    var brushSize by remember { mutableFloatStateOf(8f) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val palette = listOf(
        Color(0xFFE53935), Color(0xFFFB8C00), Color(0xFFFDD835),
        Color(0xFF43A047), Color(0xFF1E88E5), Color(0xFF8E24AA),
        Color(0xFF000000), Color(0xFFFFFFFF),
    )

    fun saveImage() {
        val size = canvasSize
        if (size.width == 0 || size.height == 0) {
            Toast.makeText(context, R.string.doodle_save_fail, Toast.LENGTH_SHORT).show()
            return
        }
        val bmp = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        val ac = AndroidCanvas(bmp)
        ac.drawColor(AndroidColor.WHITE)
        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        (strokes + listOfNotNull(current)).forEach { s ->
            if (s.points.isEmpty()) return@forEach
            paint.color = s.color.toArgb()
            paint.strokeWidth = s.width
            val path = AndroidPath()
            path.moveTo(s.points.first().x, s.points.first().y)
            if (s.points.size == 1) {
                ac.drawCircle(s.points.first().x, s.points.first().y, s.width / 2f, paint)
            } else {
                for (p in s.points.drop(1)) path.lineTo(p.x, p.y)
                ac.drawPath(path, paint)
            }
        }
        saveScope.launch {
            val ok = runCatching { QrGenerator.saveToGallery(appContext, bmp, "TickClear_Doodle") }.getOrDefault(false)
            bmp.recycle()
            Toast.makeText(appContext, if (ok) R.string.doodle_saved else R.string.doodle_save_fail, Toast.LENGTH_SHORT).show()
        }
    }

    // 画布尺寸变化（全屏切换 / 旋转）时等比缩放已有笔迹，避免坐标错位
    fun onCanvasSized(newSize: IntSize) {
        val old = canvasSize
        if (old.width > 0 && old.height > 0 && newSize.width > 0 && newSize.height > 0 && old != newSize) {
            val sx = newSize.width.toFloat() / old.width
            val sy = newSize.height.toFloat() / old.height
            strokes = strokes.map { s -> s.copy(points = s.points.map { Offset(it.x * sx, it.y * sy) }) }
        }
        canvasSize = newSize
    }

    BackHandler(enabled = fullscreen) { fullscreen = false }

    DisposableEffect(fullscreen) {
        val activity = context as? Activity
        val controller = activity?.window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        if (fullscreen) {
            runCatching {
                controller?.hide(WindowInsetsCompat.Type.systemBars())
                controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            runCatching { controller?.show(WindowInsetsCompat.Type.systemBars()) }
        }
        onDispose { runCatching { controller?.show(WindowInsetsCompat.Type.systemBars()) } }
    }

    Scaffold(
        topBar = if (fullscreen) {
            { }
        } else {
            {
                TopAppBar(
                    title = { Text(stringResource(R.string.doodle_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                    actions = {
                        IconButton(onClick = { fullscreen = true }) {
                            Icon(Icons.Filled.Fullscreen, contentDescription = stringResource(R.string.doodle_fullscreen))
                        }
                        IconButton(onClick = { saveImage() }) {
                            Icon(Icons.Filled.Save, contentDescription = stringResource(R.string.doodle_save))
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        if (fullscreen) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.White)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset -> current = DoodleStroke(listOf(offset), drawColor, brushSize) },
                            onDrag = { change, _ ->
                                change.consume()
                                current = appendPoint(current, change.position)
                            },
                            onDragEnd = { current?.let { strokes = strokes + it }; current = null },
                            onDragCancel = { current = null },
                        )
                    }
                    .onSizeChanged { onCanvasSized(it) },
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    for (s in strokes) drawDoodleStroke(s)
                    current?.let { drawDoodleStroke(it) }
                }
                IconButton(
                    onClick = { fullscreen = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                ) {
                    // 画布固定白底，深色主题下默认 tint 为白色会导致按钮不可见
                    Icon(
                        Icons.Filled.FullscreenExit,
                        contentDescription = stringResource(R.string.doodle_exit_fullscreen),
                        tint = Color.Black,
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    stringResource(R.string.doodle_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    palette.forEach { c ->
                        val selected = drawColor == c
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .shadow(2.dp, CircleShape)
                                .clip(CircleShape)
                                .background(c)
                                .border(
                                    width = 3.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = CircleShape,
                                )
                                .clickable {
                                    drawColor = c
                                    Haptic.vibrate(context, 10)
                                },
                        )
                    }
                }
                // 笔刷调节器：标签 + 实时预览圆 + 滑杆（从细到粗，预览随动）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(stringResource(R.string.doodle_size), style = MaterialTheme.typography.labelMedium)
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .shadow(1.dp, CircleShape)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            // 实时笔刷预览：当前颜色 + 当前粗细；最小半径 1.5f 避免极小笔刷看不见
                            val r = (brushSize / 2f).coerceAtLeast(1.5f)
                            drawCircle(
                                color = drawColor,
                                radius = r,
                                center = Offset(size.width / 2f, size.height / 2f),
                            )
                        }
                    }
                    Slider(
                        value = brushSize,
                        onValueChange = { brushSize = it },
                        valueRange = 2f..40f,
                        modifier = Modifier.weight(1f),
                    )
                }
                // 操作按钮：撤销 2 / 撤销 / 清空（V2.11++ 新增撤销 2，三个等宽 OutlinedButton）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    OutlinedButton(
                        onClick = { strokes = strokes.dropLast(2); Haptic.vibrate(context, 14) },
                        modifier = Modifier.weight(1f),
                        enabled = strokes.size >= 2,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Undo,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.doodle_undo_2), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { strokes = strokes.dropLast(1); Haptic.vibrate(context, 14) },
                        modifier = Modifier.weight(1f),
                        enabled = strokes.isNotEmpty(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Undo,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.doodle_undo), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { strokes = emptyList(); Haptic.vibrate(context, 14) },
                        modifier = Modifier.weight(1f),
                        enabled = strokes.isNotEmpty(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.doodle_clear), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .shadow(4.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset -> current = DoodleStroke(listOf(offset), drawColor, brushSize) },
                                onDrag = { change, _ ->
                                    change.consume()
                                    current = appendPoint(current, change.position)
                                },
                                onDragEnd = { current?.let { strokes = strokes + it }; current = null },
                                onDragCancel = { current = null },
                            )
                        }
                        .onSizeChanged { onCanvasSized(it) },
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        for (s in strokes) drawDoodleStroke(s)
                        current?.let { drawDoodleStroke(it) }
                    }
                }
            }
        }
    }
}

/**
 * 追加笔迹采样点：小于 [MIN_SAMPLE_PX] 的抖动直接丢弃。
 * 高频 onDrag 每次都整表复制，过滤后可显著降低长笔迹的内存分配与重绘开销。
 */
private const val MIN_SAMPLE_PX = 2f

private fun appendPoint(stroke: DoodleStroke?, point: Offset): DoodleStroke? {
    if (stroke == null) return null
    val last = stroke.points.lastOrNull() ?: return stroke.copy(points = listOf(point))
    if (dist(last.x, last.y, point.x, point.y) < MIN_SAMPLE_PX) return stroke
    return stroke.copy(points = stroke.points + point)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDoodleStroke(s: DoodleStroke) {
    if (s.points.isEmpty()) return
    if (s.points.size == 1) {
        drawCircle(s.color, s.width / 2f, s.points.first())
        return
    }
    val path = Path().apply {
        moveTo(s.points.first().x, s.points.first().y)
        for (p in s.points.drop(1)) lineTo(p.x, p.y)
    }
    drawPath(
        path,
        s.color,
        style = Stroke(width = s.width, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}
