package com.tickclear.app.ui.tools

import android.Manifest
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.tickclear.app.R
import com.tickclear.app.data.local.entities.VoiceMemoEntity
import com.tickclear.app.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun VoiceMemoScreen(
    onBack: () -> Unit,
    viewModel: VoiceMemoViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val memos by viewModel.memos.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val recordElapsedMs by viewModel.recordElapsedMs.collectAsStateWithLifecycle()
    val recordTitle by viewModel.recordTitle.collectAsStateWithLifecycle()
    val activeId by viewModel.activeId.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val playPositionMs by viewModel.playPositionMs.collectAsStateWithLifecycle()
    val playDurationMs by viewModel.playDurationMs.collectAsStateWithLifecycle()
    val noiseReduction by viewModel.noiseReduction.collectAsStateWithLifecycle()
    val recordLevels by viewModel.recordLevels.collectAsStateWithLifecycle()

    val permissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    val snackbarHostState = remember { SnackbarHostState() }
    var permissionRequested by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<VoiceMemoEntity?>(null) }

    LaunchedEffect(permissionState.status) {
        if (permissionRequested && permissionState.status is PermissionStatus.Denied) {
            snackbarHostState.showSnackbar(context.getString(R.string.voice_permission_required))
        }
    }

    LaunchedEffect(Unit) {
        viewModel.errorEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    fun handleRecordClick() {
        when (permissionState.status) {
            is PermissionStatus.Granted -> viewModel.startRecording()
            is PermissionStatus.Denied -> {
                permissionRequested = true
                permissionState.launchPermissionRequest()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.voice_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            RecordBar(
                isRecording = isRecording,
                elapsedMs = recordElapsedMs,
                levels = recordLevels,
                onToggle = { if (isRecording) viewModel.stopRecording(save = true) else handleRecordClick() },
                onDiscard = { viewModel.stopRecording(save = false) },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (!isRecording) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.voice_noise_reduction),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = noiseReduction,
                        onCheckedChange = viewModel::setNoiseReduction,
                    )
                }
                OutlinedTextField(
                    value = recordTitle,
                    onValueChange = viewModel::onTitleChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    label = { Text(stringResource(R.string.voice_title_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
            }

            if (memos.isEmpty() && !isRecording) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.xl),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.voice_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(memos, key = { it.id }) { memo ->
                        VoiceMemoItem(
                            memo = memo,
                            isActive = activeId == memo.id,
                            isPlaying = isPlaying && activeId == memo.id,
                            positionMs = if (activeId == memo.id) playPositionMs else 0,
                            durationMs = if (activeId == memo.id && playDurationMs > 0) playDurationMs else memo.durationMs,
                            onTogglePlay = { viewModel.togglePlay(memo) },
                            onDelete = { pendingDelete = memo },
                        )
                    }
                }
            }
        }
    }
    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMemo(pendingDelete!!)
                    pendingDelete = null
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            },
            title = { Text(stringResource(R.string.voice_delete_title)) },
            text = { Text(stringResource(R.string.voice_delete_confirm)) },
        )
    }
}

@Composable
private fun RecordBar(
    isRecording: Boolean,
    elapsedMs: Long,
    levels: List<Float> = emptyList(),
    onToggle: () -> Unit,
    onDiscard: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (isRecording) {
            RecordingWaveform(
                levels = levels,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.xs),
            )
            Text(
                text = stringResource(R.string.voice_recording) + "  " + formatDuration(elapsedMs),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = Spacing.xs),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (isRecording) {
                TextButton(onClick = onDiscard) {
                    Text(stringResource(R.string.voice_discard))
                }
            }
            IconButton(
                onClick = onToggle,
                modifier = Modifier.size(64.dp),
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = stringResource(if (isRecording) R.string.voice_stop else R.string.voice_record),
                    modifier = Modifier.size(36.dp),
                    tint = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun VoiceMemoItem(
    memo: VoiceMemoEntity,
    isActive: Boolean,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onTogglePlay: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = memo.title.ifBlank { stringResource(R.string.voice_title) },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = formatDateTime(memo.createdAt) + "  ·  " + stringResource(R.string.voice_duration, formatDuration(memo.durationMs)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onTogglePlay) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(if (isPlaying) R.string.voice_pause else R.string.voice_play),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.voice_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (isActive && durationMs > 0) {
                Spacer(Modifier.height(Spacing.xs))
                val progress = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).toInt()
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

// 列表每条录音都要格式化时间。原来每次调用都 new 一个 SimpleDateFormat（内部还要解析
// pattern、拉 DateFormatSymbols），长列表滚动时是实打实的开销。改用不可变、线程安全的
// DateTimeFormatter 并按 Locale 缓存；系统语言切换时自动重建。
private var memoFormatterLocale: Locale? = null
private var memoFormatter: DateTimeFormatter? = null

private fun formatDateTime(ts: Long): String {
    val locale = Locale.getDefault()
    var f = memoFormatter
    if (f == null || memoFormatterLocale != locale) {
        f = DateTimeFormatter.ofPattern("M-d HH:mm", locale)
        memoFormatter = f
        memoFormatterLocale = locale
    }
    // 时区不进缓存：出行跨时区后立刻生效
    return f.format(Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()))
}

/**
 * 录制中的实时振幅波形：把最近最多 64 帧归一化振幅画成居中对称的竖条包络。
 * levels 为空时画一条基线，避免录制刚开始无采样时空白。纯 Canvas 静态绘制。
 */
@Composable
private fun RecordingWaveform(
    levels: List<Float>,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.error
    Canvas(modifier = modifier.height(56.dp)) {
        val w = size.width
        val h = size.height
        val mid = h / 2f
        val n = levels.size
        if (n == 0) {
            drawLine(
                color = color.copy(alpha = 0.5f),
                start = Offset(0f, mid),
                end = Offset(w, mid),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            return@Canvas
        }
        val barW = w / 64f
        val gap = barW * 0.35f
        for (i in 0 until 64) {
            val v = levels[if (i < n) i else n - 1]
            val bh = (h * 0.45f) * v.coerceAtLeast(0.04f)
            val x = i * barW + gap / 2f
            drawLine(
                color = color,
                start = Offset(x, mid - bh),
                end = Offset(x, mid + bh),
                strokeWidth = barW - gap,
                cap = StrokeCap.Round,
            )
        }
    }
}
