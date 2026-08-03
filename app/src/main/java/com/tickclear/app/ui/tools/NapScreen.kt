package com.tickclear.app.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tickclear.app.R
import com.tickclear.app.domain.scheduler.NapScheduler
import com.tickclear.app.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import androidx.hilt.navigation.compose.hiltViewModel

private val NAP_OPTIONS = listOf(20, 30, 45, 60, 90)

/**
 * 午休小憩（V2.9++）：选择时长 → 一次性精确闹钟唤醒。
 * 20–30 分钟落在浅睡窗口，醒后不易昏沉（智能短午休）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NapScreen(
    vm: NapViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val durationMin by vm.durationMin.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val wakeTime = remember(durationMin) {
        LocalTime.now().plusMinutes(durationMin.toLong()).format(DateTimeFormatter.ofPattern("HH:mm"))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_nap_title)) },
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
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(stringResource(R.string.nap_duration_label), style = MaterialTheme.typography.titleSmall)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                NAP_OPTIONS.forEach { min ->
                    FilterChip(
                        selected = durationMin == min,
                        onClick = { vm.setDuration(min) },
                        label = { Text(stringResource(R.string.nap_min_label, min)) },
                    )
                }
            }

            Text(
                text = stringResource(R.string.nap_smart_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val startToast = stringResource(R.string.nap_start_toast, wakeTime)
            Button(
                onClick = {
                    NapScheduler.schedule(context, durationMin)
                    scope.launch { snackbarHostState.showSnackbar(startToast) }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.nap_start))
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.nap_wake_at, wakeTime),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
