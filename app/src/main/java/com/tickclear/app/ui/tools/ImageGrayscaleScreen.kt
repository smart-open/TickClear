package com.tickclear.app.ui.tools

import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    var panelExpanded by remember { mutableStateOf(true) }
    var scale by remember { mutableStateOf(1f) }

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

    val processed = remember(bitmap, mode, threshold) {
        bitmap?.let {
            if (mode == 0) ImageProcessor.toGrayscale(it)
            else ImageProcessor.toBlackWhite(it, threshold)
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
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            ToolSidePanel(
                expanded = panelExpanded,
                onToggle = { panelExpanded = !panelExpanded },
                modifier = Modifier.width(if (panelExpanded) 200.dp else 52.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { scale = (scale - 0.5f).coerceAtLeast(1f) }) {
                        Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.tools_zoom_out))
                    }
                    Text("${scale.toInt()}×", style = MaterialTheme.typography.labelMedium)
                    IconButton(onClick = { scale = (scale + 0.5f).coerceAtMost(4f) }) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.tools_zoom_in))
                    }
                }
                OutlinedButton(
                    onClick = { scale = 1f },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.tools_zoom_reset)) }

                HorizontalDivider()

                Text(stringResource(R.string.tools_img_gray_mode), style = MaterialTheme.typography.labelMedium)
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

                if (mode == 1) {
                    Text(stringResource(R.string.tools_img_gray_threshold), style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = threshold.toFloat(),
                            onValueChange = { threshold = it.toInt() },
                            valueRange = 0f..255f,
                            steps = 254,
                            modifier = Modifier.weight(1f),
                        )
                        Text("$threshold", fontSize = 14.sp)
                    }
                }

                Button(
                    onClick = {
                        val bmp = processed ?: return@Button
                        scope.launch {
                            val name = "tickclear_gray_${System.currentTimeMillis()}"
                            val saved = ImageProcessor.saveToGallery(
                                context,
                                bmp,
                                CompressFormat.PNG,
                                100,
                                name,
                            )
                            snackbarHostState.showSnackbar(
                                context.getString(
                                    if (saved != null) {
                                        R.string.tools_img_gray_saved
                                    } else {
                                        R.string.tools_img_gray_save_fail
                                    },
                                ),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.tools_img_gray_save))
                }
            }
        }
    }
}
