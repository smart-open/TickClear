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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageGrayscaleScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var mode by remember { mutableIntStateOf(0) } // 0 灰度, 1 黑白
    var threshold by remember { mutableIntStateOf(128) }

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
                Text(stringResource(R.string.tools_img_gray_pick))
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
                    stringResource(R.string.tools_img_gray_mode),
                    style = MaterialTheme.typography.labelMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FilterChip(
                        selected = mode == 0,
                        onClick = { mode = 0 },
                        label = { Text(stringResource(R.string.tools_img_gray_gray)) },
                    )
                    FilterChip(
                        selected = mode == 1,
                        onClick = { mode = 1 },
                        label = { Text(stringResource(R.string.tools_img_gray_bw)) },
                    )
                }

                if (mode == 1) {
                    Text(
                        stringResource(R.string.tools_img_gray_threshold),
                        style = MaterialTheme.typography.labelMedium,
                    )
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
            } ?: run {
                Text(
                    stringResource(R.string.tools_img_gray_noimg),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
