package com.tickclear.app.ui.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tickclear.app.R
import com.tickclear.app.domain.tools.ImageMasker
import com.tickclear.app.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * 条码识别工具（V2.9++）：从相册选图 → ZXing 解码条码 → 展示条码号，
 * 并可联网查询商品基础信息（无相机实时扫码，规避新增 CameraX 依赖）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScreen(
    vm: BarcodeViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val state by vm.state.collectAsStateWithLifecycle()

    var preview by remember { mutableStateOf<Bitmap?>(null) }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val bmp = ImageMasker.loadBitmap(context, uri)
            if (bmp != null) {
                preview = bmp
                vm.decode(bmp)
            } else {
                snackbarHostState.showSnackbar(context.getString(R.string.barcode_pick_hint))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_barcode_title)) },
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
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(onClick = { pickLauncher.launch("image/*") }) {
                Text(stringResource(R.string.barcode_pick))
            }

            preview?.let {
                Image(
                    painter = BitmapPainter(it.asImageBitmap()),
                    contentDescription = null,
                    modifier = Modifier.size(200.dp),
                )
            }

            when (val s = state) {
                BarcodeUiState.Idle -> {
                    Text(
                        stringResource(R.string.barcode_pick_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                BarcodeUiState.Decoding -> {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.barcode_decoding))
                }

                BarcodeUiState.NoBarcode -> {
                    Text(
                        stringResource(R.string.barcode_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is BarcodeUiState.Decoded -> {
                    Text(
                        stringResource(R.string.barcode_format, s.result.format),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(R.string.barcode_value, s.result.text),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    OutlinedButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("barcode", s.result.text))
                        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.barcode_copied)) }
                    }) {
                        Text(stringResource(R.string.barcode_copy))
                    }

                    if (s.querying) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.barcode_querying))
                    } else {
                        Button(onClick = { vm.query() }) {
                            Text(stringResource(R.string.barcode_query))
                        }
                        if (s.queried) {
                            Spacer(Modifier.height(Spacing.sm))
                            if (s.product != null) {
                                s.product.name?.let {
                                    Text(stringResource(R.string.barcode_product_name, it))
                                }
                                s.product.brand?.let {
                                    Text(stringResource(R.string.barcode_product_brand, it))
                                }
                            } else {
                                Text(
                                    stringResource(R.string.barcode_not_found),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
