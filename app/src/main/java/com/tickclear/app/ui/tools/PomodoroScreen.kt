package com.tickclear.app.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import com.tickclear.app.R
import com.tickclear.app.ui.theme.Spacing
import java.util.Locale

private val FOCUS_OPTIONS = listOf(15, 25, 30, 45, 52)
private val BREAK_OPTIONS = listOf(3, 5, 10, 15, 30)

/** 右上角说明弹窗里的「时长建议表」数据（文案全部在 strings.xml）。 */
private data class PomoPreset(val focusRes: Int, val breakRes: Int, val sceneRes: Int)

private val POMO_PRESETS = listOf(
    PomoPreset(R.string.pomodoro_tip_f1, R.string.pomodoro_tip_b1, R.string.pomodoro_tip_s1),
    PomoPreset(R.string.pomodoro_tip_f2, R.string.pomodoro_tip_b2, R.string.pomodoro_tip_s2),
    PomoPreset(R.string.pomodoro_tip_f3, R.string.pomodoro_tip_b3, R.string.pomodoro_tip_s3),
    PomoPreset(R.string.pomodoro_tip_f4, R.string.pomodoro_tip_b4, R.string.pomodoro_tip_s4),
    PomoPreset(R.string.pomodoro_tip_f5, R.string.pomodoro_tip_b5, R.string.pomodoro_tip_s5),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PomodoroScreen(
    vm: PomodoroViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val focusLabel = stringResource(R.string.pomodoro_focus)
    val breakLabel = stringResource(R.string.pomodoro_break)
    val startLabel = stringResource(R.string.pomodoro_start)
    val pauseLabel = stringResource(R.string.pomodoro_pause)
    val resetLabel = stringResource(R.string.pomodoro_reset)
    val focusLenLabel = stringResource(R.string.pomodoro_focus_len)
    val breakLenLabel = stringResource(R.string.pomodoro_break_len)
    val completedFmt = stringResource(R.string.pomodoro_completed, vm.completed)
    val hint = stringResource(R.string.pomodoro_hint)
    val minFmt = stringResource(R.string.interval_min_label)
    val tipTitle = stringResource(R.string.pomodoro_tip_title)
    val colFocus = stringResource(R.string.pomodoro_tip_col_focus)
    val colBreak = stringResource(R.string.pomodoro_tip_col_break)
    val colScene = stringResource(R.string.pomodoro_tip_col_scene)

    var showTip by remember { mutableStateOf(false) }

    val mm = vm.remainingSec / 60
    val ss = vm.remainingSec % 60
    val timeText = String.format(Locale.ROOT, "%02d:%02d", mm, ss)
    val total = (if (vm.phase == "focus") vm.focusMin else vm.breakMin) * 60
    val progress = if (total > 0) 1f - vm.remainingSec.toFloat() / total else 0f

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_pomodoro_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showTip = true }) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = tipTitle,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (showTip) {
            AlertDialog(
                onDismissRequest = { showTip = false },
                confirmButton = {
                    TextButton(onClick = { showTip = false }) {
                        Text(stringResource(R.string.action_confirm))
                    }
                },
                title = { Text(tipTitle) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                colFocus,
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                colBreak,
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                colScene,
                                Modifier.weight(1.5f),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        HorizontalDivider(Modifier.padding(vertical = Spacing.xs))
                        POMO_PRESETS.forEach { p ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Spacing.xs),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Text(
                                    stringResource(p.focusRes),
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    stringResource(p.breakRes),
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    stringResource(p.sceneRes),
                                    Modifier.weight(1.5f),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                },
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (vm.phase == "focus") focusLabel else breakLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                timeText,
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
            )
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )

            if (vm.isRunning) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Button(
                    onClick = { if (vm.isRunning) vm.pause() else vm.start() },
                    modifier = Modifier.weight(1f),
                ) { Text(if (vm.isRunning) pauseLabel else startLabel) }
                Button(
                    onClick = { vm.reset() },
                    modifier = Modifier.weight(1f),
                ) { Text(resetLabel) }
            }

            Spacer(Modifier.height(Spacing.sm))
            Text(focusLenLabel, style = MaterialTheme.typography.titleSmall)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                FOCUS_OPTIONS.forEach { min ->
                    FilterChip(
                        selected = vm.focusMin == min,
                        onClick = { vm.setFocus(min) },
                        enabled = !vm.isRunning,
                        label = { Text(String.format(minFmt, min)) },
                    )
                }
            }

            Text(breakLenLabel, style = MaterialTheme.typography.titleSmall)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                BREAK_OPTIONS.forEach { min ->
                    FilterChip(
                        selected = vm.breakMin == min,
                        onClick = { vm.setBreak(min) },
                        enabled = !vm.isRunning,
                        label = { Text(String.format(minFmt, min)) },
                    )
                }
            }

            Spacer(Modifier.height(Spacing.sm))
            Text(completedFmt, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
