package com.tickclear.app.ui.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.tickclear.app.R
import com.tickclear.app.domain.tools.BarcodeTool
import com.tickclear.app.domain.tools.ImageMasker
import com.tickclear.app.domain.tools.QrGenerator
import com.tickclear.app.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.io.File

private enum class QrType { TEXT, URL, CONTACT }

private const val QR_SIZE_PX = 512

/** 把任意内容收敛成文件名（去除路径/换行等非法字符）。 */
private fun sanitizeFileName(content: String): String {
    val base = content.lineSequence().firstOrNull().orEmpty().trim().take(24)
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
    return base.ifEmpty { "tickclear_qr" }
}

/**
 * 二维码工具（V2.9++ 美化）：分为「生成」与「识别」两个区块。
 *  生成：文字 / 网址 / 联系人（vCard）三类输入，实时生成二维码；长按图片保存到相册。
 *  识别：拍照 / 相册选图 → ZXing MultiFormatReader 解码 → 显示内容 + 复制 / 打开链接。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun QrScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_qr_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
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
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                stringResource(R.string.qr_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionHeader(
                icon = Icons.Filled.QrCode2,
                title = stringResource(R.string.qr_section_generate),
            )
            GenerateSection(
                snackbarHostState = snackbarHostState,
                scope = scope,
            )

            SectionHeader(
                icon = Icons.Filled.CameraAlt,
                title = stringResource(R.string.qr_section_scan),
            )
            ScanSection(
                snackbarHostState = snackbarHostState,
                scope = scope,
            )
        }
    }
}

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = Modifier.padding(top = Spacing.xs),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** 生成二维码区块。 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun GenerateSection(
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val context = LocalContext.current
    var type by remember { mutableStateOf(QrType.TEXT) }
    var text by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var cName by remember { mutableStateOf("") }
    var cPhone by remember { mutableStateOf("") }
    var cEmail by remember { mutableStateOf("") }
    var ecLevel by remember { mutableStateOf(ErrorCorrectionLevel.M) }
    var logoCenter by remember { mutableStateOf(false) } // V2.9++ 二巡：居中 Logo 覆盖开关

    val content: String = remember(type, text, url, cName, cPhone, cEmail) {
        when (type) {
            QrType.TEXT -> text
            QrType.URL -> if (url.isBlank()) "" else if (url.startsWith("http", ignoreCase = true)) url else "https://$url"
            QrType.CONTACT -> buildString {
                appendLine("BEGIN:VCARD")
                appendLine("VERSION:3.0")
                if (cName.isNotBlank()) appendLine("N:$cName")
                if (cPhone.isNotBlank()) appendLine("TEL:$cPhone")
                if (cEmail.isNotBlank()) appendLine("EMAIL:$cEmail")
                appendLine("END:VCARD")
            }.trimEnd()
        }
    }

    // 开启居中 Logo 时强制 H 级纠错，否则遮挡后无法识别。
    val effectiveEc = if (logoCenter) ErrorCorrectionLevel.H else ecLevel
    val bitmap = remember(content, effectiveEc) { QrGenerator.generate(content, QR_SIZE_PX, effectiveEc) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                FilterChip(
                    selected = type == QrType.TEXT,
                    onClick = { type = QrType.TEXT },
                    label = { Text(stringResource(R.string.qr_type_text)) },
                )
                FilterChip(
                    selected = type == QrType.URL,
                    onClick = { type = QrType.URL },
                    label = { Text(stringResource(R.string.qr_type_url)) },
                )
                FilterChip(
                    selected = type == QrType.CONTACT,
                    onClick = { type = QrType.CONTACT },
                    label = { Text(stringResource(R.string.qr_type_contact)) },
                )
            }

            Text(
                stringResource(R.string.qr_ec_level_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
listOf(
                ErrorCorrectionLevel.L to R.string.qr_ec_l,
                ErrorCorrectionLevel.M to R.string.qr_ec_m,
                ErrorCorrectionLevel.Q to R.string.qr_ec_q,
                ErrorCorrectionLevel.H to R.string.qr_ec_h,
            ).forEach { (lvl, labelRes) ->
                FilterChip(
                    selected = !logoCenter && ecLevel == lvl,
                    onClick = { ecLevel = lvl },
                    enabled = !logoCenter,
                    label = { Text(stringResource(labelRes)) },
                )
            }
        }

        Text(
            stringResource(R.string.qr_ec_desc),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // 居中 Logo 开关（V2.9++ 二巡）：开启后在二维码中央叠加应用图标，提升品牌辨识。
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.qr_logo_center),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = logoCenter, onCheckedChange = { logoCenter = it })
        }
        Text(
            stringResource(R.string.qr_logo_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

            when (type) {
                QrType.TEXT -> OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.qr_input_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                QrType.URL -> OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.qr_url_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                QrType.CONTACT -> {
                    OutlinedTextField(
                        value = cName,
                        onValueChange = { cName = it },
                        label = { Text(stringResource(R.string.qr_contact_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = cPhone,
                        onValueChange = { cPhone = it },
                        label = { Text(stringResource(R.string.qr_contact_phone)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    )
                    OutlinedTextField(
                        value = cEmail,
                        onValueChange = { cEmail = it },
                        label = { Text(stringResource(R.string.qr_contact_email)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Box(
                            modifier = Modifier
                                .background(Color.White, RoundedCornerShape(12.dp))
                                .padding(Spacing.sm),
                        ) {
                            Icon(
                                painter = BitmapPainter(bitmap.asImageBitmap()),
                                contentDescription = stringResource(R.string.qr_image_desc),
                                modifier = Modifier
                                    .size(220.dp)
                                    .combinedClickable(
                                        onClick = {},
                                        onLongClick = {
                                            scope.launch {
                                                val ok = QrGenerator.saveToGallery(
                                                    context, bitmap, sanitizeFileName(content),
                                                )
                                                snackbarHostState.showSnackbar(
                                                    context.getString(
                                                        if (ok) R.string.qr_save_success
                                                        else R.string.qr_save_fail,
                                                    ),
                                                )
                                            }
                                        },
                                    ),
                                tint = Color.Unspecified,
                            )
                            if (logoCenter) {
                                // 居中 Logo（V2.9++ 二巡）：白底圆角 + 应用图标，靠 H 级纠错保证可扫。
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(56.dp)
                                        .background(Color.White, RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Image(
                                        painter = painterResource(R.drawable.ic_launcher),
                                        contentDescription = null,
                                        modifier = Modifier.size(44.dp),
                                    )
                                }
                            }
                        }
                        Text(
                            stringResource(R.string.qr_long_press_save),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Text(
                        stringResource(R.string.qr_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 识别二维码区块。 */
@Composable
private fun ScanSection(
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val context = LocalContext.current
    var result by remember { mutableStateOf<BarcodeTool.BarcodeResult?>(null) }
    var scanned by remember { mutableStateOf(false) }

    val photoUri = remember {
        FileProvider.getUriForFile(
            context,
            "com.tickclear.app.fileprovider",
            File(File(context.cacheDir, "share").apply { mkdirs() }, "qr_capture.jpg"),
        )
    }

    val decodeFromUri: (android.net.Uri) -> Unit = { uri ->
        scope.launch {
            val bmp = ImageMasker.loadBitmap(context, uri)
            if (bmp == null) {
                snackbarHostState.showSnackbar(context.getString(R.string.qr_scan_failed))
                scanned = false
                return@launch
            }
            val r = BarcodeTool.decode(bmp)
            if (r != null) {
                result = r
                scanned = true
            } else {
                snackbarHostState.showSnackbar(context.getString(R.string.qr_scan_empty))
            }
        }
    }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        decodeFromUri(uri)
    }
    val captureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (!ok) return@rememberLauncherForActivityResult
        decodeFromUri(photoUri)
    }
    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            captureLauncher.launch(photoUri)
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.qr_scan_camera_denied))
            }
            // 拒权后自动回退到相册选图
            pickLauncher.launch("image/*")
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Button(
                    onClick = { pickLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Filled.Image, contentDescription = null)
                    Spacer(Modifier.size(Spacing.xs))
                    Text(stringResource(R.string.qr_scan_pick))
                }
                OutlinedButton(
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(
                            context, android.Manifest.permission.CAMERA,
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        if (granted) captureLauncher.launch(photoUri)
                        else cameraPermLauncher.launch(android.Manifest.permission.CAMERA)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null)
                    Spacer(Modifier.size(Spacing.xs))
                    Text(stringResource(R.string.qr_scan_capture))
                }
            }

            if (scanned && result != null) {
                val r = result!!
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Text(
                            stringResource(R.string.qr_scan_result_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = r.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 16.sp,
                        )
                        Text(
                            stringResource(R.string.qr_scan_result_format, r.format),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("qr", r.text))
                                    Toast.makeText(context, R.string.qr_scan_copied, Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null)
                                Spacer(Modifier.size(Spacing.xs))
                                Text(stringResource(R.string.qr_scan_copy))
                            }
                            if (r.text.startsWith("http", ignoreCase = true)) {
                                Button(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(r.text))
                                        val ok = runCatching {
                                            context.startActivity(intent)
                                        }.isSuccess
                                        if (!ok) {
                                            Toast.makeText(
                                                context, R.string.qr_scan_open_fail, Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                                    Spacer(Modifier.size(Spacing.xs))
                                    Text(stringResource(R.string.qr_scan_open))
                                }
                            }
                            IconButton(onClick = { result = null; scanned = false }) {
                                Icon(
                                    Icons.Filled.Refresh,
                                    contentDescription = stringResource(R.string.qr_scan_pick),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
