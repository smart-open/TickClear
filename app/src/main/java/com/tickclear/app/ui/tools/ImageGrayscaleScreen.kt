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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageGrayscaleScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var mode by remember { mutableIntStateOf(0) } // 0 灰度, 1 黑白
    var threshold by remember { mutableIntStateOf(128) }
    var contrast by remember { mutableFloatStateOf(1f) } // 1f = 不变，>1 拉对比 <1 压平

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
            } else {
                snackbarHostState.showSnackbar(context.getString(R.string.tools_img_gray_noimg))
            }
        }
    }

    val processed = remember(bitmap, mode, threshold, contrast) {
        bitmap?.let {
            if (mode == 0) ImageProcessor.toGrayscale(it, contrast)
            else ImageProcessor.toBlackWhite(it, threshold, contrast)
        }
    }

    // 保存已移至右上角图标按钮（参考 MosaicScreen）：灰度/黑白结果写入相册（PNG 无损）
    fun saveGray() {
        val bmp = processed ?: return
        scope.launch {
            busy = true
            val name = "tickclear_gray_${System.currentTimeMillis()}"
            val saved = ImageProcessor.saveToGallery(context, bmp, CompressFormat.PNG, 100, name)
            snackbarHostState.showSnackbar(
                context.getString(
                    if (saved != null) {
                        R.string.tools_img_gray_saved
                    } else {
                        R.string.tools_img_gray_save_fail
                    },
                ),
            )
            busy = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_img_gray_title)) },
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
                        onClick = { saveGray() },
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
                                contentDescription = stringResource(R.string.tools_img_gray_save),
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
                    Text(stringResource(R.string.tools_img_gray_pick))
                }

                if (processed == null) {
                    Spacer(Modifier.height(Spacing.md))
                    Text(
                        stringResource(R.string.tools_img_gray_noimg),
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
                modifier = Modifier.width(if (panelExpanded) 220.dp else 52.dp),
            ) {
                ZoomPanControls(
                    scale = scale,
                    onScaleChange = { scale = it },
                    offset = offset,
                    onOffsetChange = { offset = it },
                    enabled = processed != null,
                )

                // 模式
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
                            stringResource(R.string.tools_img_gray_mode),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FilterChip(
                            selected = mode == 0,
                            onClick = { mode = 0 },
                            label = { Text(stringResource(R.string.tools_img_gray_gray)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        FilterChip(
                            selected = mode == 1,
                            onClick = { mode = 1 },
                            label = { Text(stringResource(R.string.tools_img_gray_bw)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                if (mode == 1) {
                    MiniHorizontalSlider(
                        label = stringResource(R.string.tools_img_gray_threshold),
                        value = threshold.toFloat(),
                        onValueChange = { threshold = it.toInt() },
                        valueRange = 0f..255f,
                        steps = 254,
                        displayValue = "$threshold",
                    )
                }

                MiniHorizontalSlider(
                    label = stringResource(R.string.tools_img_gray_contrast),
                    value = contrast,
                    onValueChange = { contrast = it.coerceIn(0.5f, 2.5f) },
                    valueRange = 0.5f..2.5f,
                    steps = 20,
                    displayValue = "${(contrast * 100).toInt()}%",
                )

            }
        }
    }
}
