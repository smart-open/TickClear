package com.tickclear.app.ui.tools

import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tickclear.app.R
import com.tickclear.app.domain.tools.ImageProcessor
import com.tickclear.app.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val MAX_DIMS = listOf(0, 1920, 1280, 800)

/**
 * 参数变化到重算的防抖窗口。质量滑杆 steps=89，一次拖动会触发数十次
 * 「缩放 + 整图编码」；不防抖会逐帧编码大图，主线程直接卡死。
 */
private const val COMPRESS_DEBOUNCE_MS = 180L

private fun formatBytes(b: Long?): String {
    if (b == null) return "—"
    return if (b >= 1024 * 1024) {
        String.format(java.util.Locale.US, "%.2f MB", b / 1048576.0)
    } else {
        String.format(java.util.Locale.US, "%.1f KB", b / 1024.0)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageCompressScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var originalSize by remember { mutableStateOf<Long?>(null) }
    var quality by remember { mutableFloatStateOf(80f) }
    var maxDim by remember { mutableIntStateOf(1920) } // 0 = 原始
    var format by remember { mutableStateOf(CompressFormat.JPEG) }

    var panelExpanded by remember { mutableStateOf(true) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var busy by remember { mutableStateOf(false) }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val bmp = ImageProcessor.loadBitmap(context, uri)
            if (bmp != null) {
                bitmap = bmp
                originalSize = ImageProcessor.fileSizeFromUri(context, uri)
            } else {
                snackbarHostState.showSnackbar(context.getString(R.string.tools_img_compress_noimg))
            }
        }
    }

    var processed by remember { mutableStateOf<Bitmap?>(null) }
    // 只保留字节数：整张编码结果没有别的用途，常驻内存等于白白多占一份图片大小
    var compressedSize by remember { mutableStateOf<Long?>(null) }
    // gate 串行化后台运算：新结果就绪时上一次运算必已结束，此刻回收上一张缩放图不会被读到
    val gate = remember { Mutex() }

    // 原实现在 remember 里同步做「缩放 + 整图编码」：拖质量滑杆时主线程逐帧编码，既卡顿，
    // 又会不断产出新的缩放位图且从不回收（Native 堆，GC 跟不上）→ 大图必 OOM。
    LaunchedEffect(bitmap, maxDim, format, quality) {
        val src = bitmap
        if (src == null) {
            processed = null
            compressedSize = null
            return@LaunchedEffect
        }
        delay(COMPRESS_DEBOUNCE_MS)
        val q = quality.toInt()
        val dim = maxDim
        val fmt = format
        gate.withLock {
            // NonCancellable：编码/缩放打不断，被取消也要把结果挂上去，否则缩放图成为无人回收的孤儿
            withContext(NonCancellable) {
                val result = withContext(Dispatchers.Default) {
                    val scaled = if (dim > 0) ImageProcessor.scaleToMaxSide(src, dim) else src
                    scaled to runCatching { ImageProcessor.compress(scaled, fmt, q).size.toLong() }
                        .getOrNull()
                }
                val old = processed
                processed = result.first
                compressedSize = result.second
                // 与原图同一个对象（未触发缩放）时不能回收，否则会连原图一起废掉
                if (old !== result.first && old !== src && !busy) old?.recycle()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val p = processed
            // 未缩放时 processed 就是原图，交给 GC；saveCompressed 进行中同理，避免写盘途中被回收
            if (p != null && p !== bitmap && !busy) p.recycle()
        }
    }

    // 保存已移至右上角图标按钮（参考 MosaicScreen）：校验选图后压缩并写入相册
    fun saveCompressed() {
        val bmp = processed ?: return
        scope.launch {
            busy = true
            val name = "tickclear_compress_${System.currentTimeMillis()}"
            val saved = ImageProcessor.saveToGallery(context, bmp, format, quality.toInt(), name)
            snackbarHostState.showSnackbar(
                context.getString(
                    if (saved != null) {
                        R.string.tools_img_compress_saved
                    } else {
                        R.string.tools_img_compress_save_fail
                    },
                ),
            )
            busy = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_img_compress_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    // 保存：右上角 FilledIconButton + Save 图标（primaryContainer 填充色，比原 IconButton + ✓ 更醒目）；处理中显示进度圈
                    FilledIconButton(
                        onClick = { saveCompressed() },
                        enabled = processed != null && !busy,
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
                                contentDescription = stringResource(R.string.tools_img_compress_save),
                            )
                        }
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
                    Text(stringResource(R.string.tools_img_compress_pick))
                }

                if (processed == null) {
                    Spacer(Modifier.height(Spacing.md))
                    Text(
                        stringResource(R.string.tools_img_compress_noimg),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                    ) {
                        ZoomableImagePreview(
                            bitmap = processed!!,
                            scale = scale,
                            onScaleChange = { scale = it },
                            offset = offset,
                            onOffsetChange = { offset = it },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            ToolSidePanel(
                expanded = panelExpanded,
                onToggle = { panelExpanded = !panelExpanded },
                modifier = Modifier.width(if (panelExpanded) 148.dp else 52.dp),
            ) {
                ZoomPanControls(
                    scale = scale,
                    onScaleChange = { scale = it },
                    offset = offset,
                    onOffsetChange = { offset = it },
                    enabled = processed != null,
                    compact = true,
                )

                ToolHorizontalSlider(
                    label = stringResource(R.string.tools_img_compress_quality),
                    value = quality,
                    onValueChange = { quality = it },
                    valueRange = 10f..100f,
                    steps = 89,
                    displayValue = "${quality.toInt()}%",
                )

                // 最大边长
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Text(
                            stringResource(R.string.tools_img_compress_maxdim),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        MAX_DIMS.forEach { dim ->
                            FilterChip(
                                selected = maxDim == dim,
                                onClick = { maxDim = dim },
                                label = {
                                    Text(
                                        if (dim == 0) {
                                            stringResource(R.string.tools_img_compress_dim_orig)
                                        } else {
                                            dim.toString()
                                        },
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                // 格式
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Text(
                            stringResource(R.string.tools_img_compress_format),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FilterChip(
                            selected = format == CompressFormat.JPEG,
                            onClick = { format = CompressFormat.JPEG },
                            label = { Text(stringResource(R.string.tools_img_compress_format_jpeg)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        FilterChip(
                            selected = format == CompressFormat.WEBP,
                            onClick = { format = CompressFormat.WEBP },
                            label = { Text(stringResource(R.string.tools_img_compress_format_webp)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                if (processed != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        // 「压缩前」必须显示原图尺寸；processed 已是缩放后的结果
                        InfoChip(
                            stringResource(R.string.tools_img_compress_before),
                            "${bitmap!!.width}×${bitmap!!.height}",
                            formatBytes(originalSize),
                            modifier = Modifier.weight(1f),
                        )
                        InfoChip(
                            stringResource(R.string.tools_img_compress_after),
                            "${processed!!.width}×${processed!!.height}",
                            formatBytes(compressedSize),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoChip(label: String, dim: String, size: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(Spacing.sm)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.tools_img_compress_dim) + "：$dim",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                stringResource(R.string.tools_img_compress_size) + "：$size",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
