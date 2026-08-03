package com.tickclear.app.ui.tools

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tickclear.app.R
import com.tickclear.app.domain.tools.QrGenerator
import kotlinx.coroutines.launch

private enum class QrType { TEXT, URL, CONTACT }

private const val QR_SIZE_PX = 512

/** 把任意内容收敛成文件名（去除路径/换行等非法字符）。 */
private fun sanitizeFileName(content: String): String {
    val base = content.lineSequence().firstOrNull().orEmpty().trim().take(24)
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
    return base.ifEmpty { "tickclear_qr" }
}

/**
 * 二维码工具（V2.9++）：支持文字 / 网址 / 联系人（vCard）三类输入，
 * 实时生成二维码；长按二维码图片保存到相册。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun QrScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var type by remember { mutableStateOf(QrType.TEXT) }
    var text by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var cName by remember { mutableStateOf("") }
    var cPhone by remember { mutableStateOf("") }
    var cEmail by remember { mutableStateOf("") }

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

    val bitmap = remember(content) { QrGenerator.generate(content, QR_SIZE_PX) }

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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.qr_type_label), style = MaterialTheme.typography.titleSmall)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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

            when (type) {
                QrType.TEXT -> OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.qr_input_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
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

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (bitmap != null) {
                    Icon(
                        painter = BitmapPainter(bitmap.asImageBitmap()),
                        contentDescription = stringResource(R.string.qr_image_desc),
                        modifier = Modifier
                            .size(220.dp)
                            .combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    scope.launch {
                                        val ok = QrGenerator.saveToGallery(context, bitmap, sanitizeFileName(content))
                                        snackbarHostState.showSnackbar(
                                            context.getString(
                                                if (ok) R.string.qr_save_success else R.string.qr_save_fail,
                                            ),
                                        )
                                    }
                                },
                            ),
                        tint = androidx.compose.ui.graphics.Color.Unspecified,
                    )
                    Text(
                        text = stringResource(R.string.qr_long_press_save),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.qr_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
