package com.tickclear.app.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tickclear.app.R
import com.tickclear.app.ui.theme.Spacing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info

/**
 * 剪贴板防窃取界面（V2.9++）。开关 + 延迟 + 安全复制 + 立即清除 + 实时状态。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipboardGuardScreen(
    onBack: () -> Unit,
    viewModel: ClipboardGuardViewModel = hiltViewModel(),
) {
    val autoClear by viewModel.autoClear.collectAsStateWithLifecycle()
    val delaySec by viewModel.delaySec.collectAsStateWithLifecycle()
    val clipPreview by viewModel.clipPreview.collectAsStateWithLifecycle()
    val readable by viewModel.readable.collectAsStateWithLifecycle()
    val countdownMs by viewModel.countdownMs.collectAsStateWithLifecycle()
    val lastEvent by viewModel.lastEvent.collectAsStateWithLifecycle()

    var safeText by remember { mutableStateOf("") }

    // 自动清除反馈（V2.9++ 二巡）：当 lastEvent 变化时弹一条 Snackbar，避免错过「已自动清除」。
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(lastEvent) {
        val msg = lastEvent
        if (msg.isNotEmpty()) snackbarHostState.showSnackbar(msg)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_clipboard_guard_title)) },
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
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // 自动清除开关 + 延迟
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.clip_guard_auto_clear),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = autoClear, onCheckedChange = viewModel::setAutoClear)
                    }
                    if (autoClear) {
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            stringResource(R.string.clip_guard_delay, delaySec),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Slider(
                            value = delaySec.toFloat(),
                            onValueChange = { viewModel.setDelaySec(it.toInt()) },
                            valueRange = 5f..120f,
                            steps = 115,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // 安全复制
            OutlinedTextField(
                value = safeText,
                onValueChange = { safeText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.clip_guard_input_hint)) },
                singleLine = false,
                minLines = 2,
            )
            Button(
                onClick = {
                    viewModel.copyProtect(safeText)
                    safeText = ""
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = safeText.isNotBlank(),
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null)
                Spacer(Modifier.size(Spacing.xs))
                Text(stringResource(R.string.clip_guard_copy))
            }

            // 立即清除
            OutlinedButton(
                onClick = { viewModel.clearNow() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = null)
                Spacer(Modifier.size(Spacing.xs))
                Text(stringResource(R.string.clip_guard_clear))
            }

            // 实时状态
            Text(
                stringResource(R.string.clip_guard_status),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (countdownMs > 0) {
                val remaining = (countdownMs / 1000f).coerceAtLeast(0f)
                val progress = if (delaySec > 0) (countdownMs / (delaySec * 1000f)).coerceIn(0f, 1f) else 0f
                Text(
                    stringResource(R.string.clip_guard_countdown, remaining.toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                )
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (readable && clipPreview.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        clipPreview,
                        modifier = Modifier.padding(Spacing.md),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    stringResource(R.string.clip_guard_empty_or_hidden),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (lastEvent.isNotEmpty()) {
                Text(
                    lastEvent,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // 说明
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Row(modifier = Modifier.padding(Spacing.md)) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(Spacing.sm))
                    Text(
                        stringResource(R.string.clip_guard_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
