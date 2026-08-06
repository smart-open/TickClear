package com.tickclear.app.ui.tools

import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.util.Locale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (v.hasVibrator()) {
                    v.vibrate(
                        VibrationEffect.createWaveform(
                            longArrayOf(0, 400, 180, 400),
                            -1,
                        ),
                    )
                }
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(500)
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
                Button(
                    onClick = {
                        if (totalSec > 0) {
                            vm.add(name.trim(), totalSec, defaultTimerName)
                            name = ""
                            minStr = ""
                            secStr = ""
                        }
                    },
                    enabled = totalSec > 0,
                ) {
                    Text(stringResource(R.string.tools_cook_timer_add))
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
        colors = CardDefaults.cardColors(
            containerColor = if (finished) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
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
            Spacer(Modifier.height(4.dp))
            Text(
                if (finished) stringResource(R.string.tools_cook_timer_done) else fmtTime(remainSec),
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(Spacing.xs))
            if (!finished && totalSec > 0) {
                LinearProgressIndicator(
                    progress = { remainSec.toFloat() / totalSec },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
