package com.tickclear.app.ui.tools

import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.util.Locale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tickclear.app.R
import com.tickclear.app.ui.theme.Spacing
import kotlinx.coroutines.launch

private fun fmtTime(sec: Int): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.ROOT, "%02d:%02d", m, s)
}

/**
 * 烹饪计时列表项的环形进度尺寸。60.dp 显得偏小且难以容纳
 * `2:00:58` / `120:58:00` 这类 ≥ 6 字符时间（含冒号），提升到 80.dp
 * 给字号让出空间；与计时列表整体视觉权重也更协调。
 */
private val TimerRingSize = 80.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookingTimerScreen(
    vm: CookingTimerViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val timers by vm.timers.collectAsStateWithLifecycle()
    val defaultTimerName = stringResource(R.string.tools_cook_timer_default_name)

    var name by remember { mutableStateOf("") }
    var minStr by remember { mutableStateOf("") }
    var secStr by remember { mutableStateOf("") }

    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
    val ringtone = remember {
        try {
            RingtoneManager.getRingtone(
                context,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
            )
        } catch (_: Exception) {
            null
        }
    }

    fun feedback() {
        vibrator?.let { v ->
            if (v.hasVibrator()) {
                v.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 400, 180, 400),
                        -1,
                    ),
                )
            }
        }
        try {
            ringtone?.play()
        } catch (_: Exception) {
        }
    }

    LaunchedEffect(Unit) {
        vm.finished.collect { label ->
            feedback()
            snackbarHostState.showSnackbar(context.getString(R.string.tools_cook_timer_finished, label))
        }
    }

    val totalSec = (minStr.toIntOrNull() ?: 0) * 60 + (secStr.toIntOrNull() ?: 0)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_cook_timer_title)) },
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
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SimHintCard(stringResource(R.string.tools_cook_timer_hint))
            // 新建计时面板：去外框 Card、改用 Column 平铺；末尾的"+"按钮升级为 FilledIconButton（primaryContainer 填充色），
            // 比普通 IconButton 更醒目，与下方计时列表的视觉权重也对齐。
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.tools_cook_timer_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    OutlinedTextField(
                        value = minStr,
                        onValueChange = { minStr = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.tools_cook_timer_min)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = secStr,
                        onValueChange = { secStr = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.tools_cook_timer_sec)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    FilledIconButton(
                        onClick = {
                            if (totalSec > 0) {
                                vm.add(name.trim(), totalSec, defaultTimerName)
                                name = ""
                                minStr = ""
                                secStr = ""
                            }
                        },
                        enabled = totalSec > 0,
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = stringResource(R.string.tools_cook_timer_add),
                        )
                    }
                }
            }

            if (timers.isEmpty()) {
                Text(
                    stringResource(R.string.tools_cook_timer_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(timers, key = { it.id }) { t ->
                        TimerCard(
                            name = t.name,
                            remainSec = t.remainSec,
                            totalSec = t.totalSec,
                            running = t.running,
                            finished = t.finished,
                            onStart = { vm.start(t.id) },
                            onPause = { vm.pause(t.id) },
                            onReset = { vm.reset(t.id) },
                            onDelete = { vm.remove(t.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimerCard(
    name: String,
    remainSec: Int,
    totalSec: Int,
    running: Boolean,
    finished: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (finished) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.padding(Spacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (finished) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                IconButton(onClick = onDelete, modifier = Modifier.sizeIn(maxHeight = 32.dp)) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.tools_cook_timer_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Box(
                    modifier = Modifier.size(60.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    TimerRing(
                        remainSec = remainSec,
                        totalSec = totalSec,
                        finished = finished,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Text(
                        text = if (finished) "✓" else fmtTime(remainSec),
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        color = if (finished) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (finished) {
                            stringResource(R.string.tools_cook_timer_done)
                        } else {
                            stringResource(R.string.tools_cook_timer_remaining)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val pct = if (finished) 100 else if (totalSec > 0) (100 * remainSec / totalSec) else 0
                    Text(
                        stringResource(R.string.tools_cook_timer_progress, pct),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (finished) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (finished) {
                    Button(onClick = onReset, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.tools_cook_timer_reset))
                    }
                } else {
                    Button(
                        onClick = if (running) onPause else onStart,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(Spacing.xs))
                        Text(
                            if (running) {
                                stringResource(R.string.tools_cook_timer_pause)
                            } else {
                                stringResource(R.string.tools_cook_timer_start)
                            },
                        )
                    }
                    OutlinedButton(onClick = onReset) {
                        Icon(Icons.Filled.Replay, contentDescription = null)
                        Spacer(Modifier.width(Spacing.xs))
                        Text(stringResource(R.string.tools_cook_timer_reset))
                    }
                }
            }
        }
    }
}

/** 环形进度：底色整圈 + 进度弧（从 12 点顺时针扫过）。剩余比例为 0 时不画进度弧。 */
@Composable
private fun TimerRing(
    remainSec: Int,
    totalSec: Int,
    finished: Boolean,
    modifier: Modifier = Modifier,
) {
    val frac = when {
        finished -> 1f
        totalSec <= 0 -> 0f
        else -> (remainSec.toFloat() / totalSec).coerceIn(0f, 1f)
    }
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val progressColor = if (finished) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.tertiary
    }
    Canvas(modifier = modifier) {
        val strokeW = 6.dp.toPx()
        val inset = strokeW / 2f
        val arc = size.width - strokeW
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = strokeW),
            topLeft = Offset(inset, inset),
            size = Size(arc, arc),
        )
        if (frac > 0f) {
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * frac,
                useCenter = false,
                style = Stroke(width = strokeW, cap = StrokeCap.Round),
                topLeft = Offset(inset, inset),
                size = Size(arc, arc),
            )
        }
    }
}
