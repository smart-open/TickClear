package com.tickclear.app.ui.tools

import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tickclear.app.R
import com.tickclear.app.domain.tools.ImageProcessor
import com.tickclear.app.ui.theme.Spacing
import kotlinx.coroutines.launch

private val MAX_DIMS = listOf(0, 1920, 1280, 800)

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

    val processed = remember(bitmap, maxDim) {
        bitmap?.let { if (maxDim > 0) ImageProcessor.scaleToMaxSide(it, maxDim) else it }
    }
    val compressed = remember(processed, format, quality) {
        processed?.let { ImageProcessor.compress(it, format, quality.toInt()) }
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
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            OutlinedButton(
                onClick = { pickLauncher.launch("image/*") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.tools_img_compress_pick))
            }

            processed?.let { bmp ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.sm),
                    )
                }

                Text(
                    stringResource(R.string.tools_img_compress_quality),
                    style = MaterialTheme.typography.labelMedium,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = quality,
                        onValueChange = { quality = it },
                        valueRange = 10f..100f,
                        steps = 89,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${quality.toInt()}%", fontSize = 14.sp)
                }

                Text(
                    stringResource(R.string.tools_img_compress_maxdim),
                    style = MaterialTheme.typography.labelMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
                        )
                    }
                }

                Text(
                    stringResource(R.string.tools_img_compress_format),
                    style = MaterialTheme.typography.labelMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FilterChip(
                        selected = format == CompressFormat.JPEG,
                        onClick = { format = CompressFormat.JPEG },
                        label = { Text(stringResource(R.string.tools_img_compress_format_jpeg)) },
                    )
                    FilterChip(
                        selected = format == CompressFormat.WEBP,
                        onClick = { format = CompressFormat.WEBP },
                        label = { Text(stringResource(R.string.tools_img_compress_format_webp)) },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    InfoChip(
                        stringResource(R.string.tools_img_compress_before),
                        "${bmp.width}×${bmp.height}",
                        formatBytes(originalSize),
                        modifier = Modifier.weight(1f),
                    )
                    InfoChip(
                        stringResource(R.string.tools_img_compress_after),
                        "${bmp.width}×${bmp.height}",
                        formatBytes(compressed?.size?.toLong()),
                        modifier = Modifier.weight(1f),
                    )
                }

                Button(
                    onClick = {
                        scope.launch {
                            val name = "tickclear_compress_${System.currentTimeMillis()}"
                            val saved = ImageProcessor.saveToGallery(
                                context,
                                bmp,
                                format,
                                quality.toInt(),
                                name,
                            )
                            snackbarHostState.showSnackbar(
                                context.getString(
                                    if (saved != null) {
                                        R.string.tools_img_compress_saved
                                    } else {
                                        R.string.tools_img_compress_save_fail
                                    },
                                ),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.tools_img_compress_save))
                }
            } ?: run {
                Text(
                    stringResource(R.string.tools_img_compress_noimg),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
