package com.tickclear.app.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.tickclear.app.R
import com.tickclear.app.ui.theme.Spacing

private val FOCUS_OPTIONS = listOf(15, 25, 45)
private val BREAK_OPTIONS = listOf(5, 10)

@OptIn(ExperimentalMaterial3Api::class)
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

    val mm = vm.remainingSec / 60
    val ss = vm.remainingSec % 60
    val timeText = String.format("%02d:%02d", mm, ss)
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
            )
        },
    ) { innerPadding ->
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
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
