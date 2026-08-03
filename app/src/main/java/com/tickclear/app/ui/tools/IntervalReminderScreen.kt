package com.tickclear.app.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tickclear.app.R
import com.tickclear.app.domain.scheduler.IntervalType
import com.tickclear.app.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val INTERVAL_OPTIONS = listOf(15, 30, 45, 60, 90, 120)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntervalReminderScreen(
    vm: IntervalReminderViewModel,
    onBack: () -> Unit,
    isWide: Boolean = false,
) {
    val enabled by vm.enabled.collectAsStateWithLifecycle()
    val intervalMin by vm.intervalMin.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val titleRes = if (vm.type == IntervalType.WATER) R.string.water_title else R.string.rest_title
    val enableLabelRes = if (vm.type == IntervalType.WATER) R.string.water_enable else R.string.rest_enable
    val nextRes = if (vm.type == IntervalType.WATER) R.string.water_next else R.string.rest_next
    val testRes = if (vm.type == IntervalType.WATER) R.string.water_test else R.string.rest_test
    val testToastRes = if (vm.type == IntervalType.WATER) R.string.water_test_toast else R.string.rest_test_toast
    val intervalLabelRes = if (vm.type == IntervalType.WATER) R.string.water_interval else R.string.rest_interval

    val nextTime = remember(enabled, intervalMin) {
        if (enabled) {
            LocalTime.now().plusMinutes(intervalMin.toLong()).format(DateTimeFormatter.ofPattern("HH:mm"))
        } else {
            ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleRes)) },
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(enableLabelRes), style = MaterialTheme.typography.titleMedium)
                Switch(checked = enabled, onCheckedChange = vm::setEnabled)
            }

            Text(stringResource(intervalLabelRes), style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                INTERVAL_OPTIONS.forEach { min ->
                    FilterChip(
                        selected = intervalMin == min,
                        onClick = { vm.setIntervalMin(min) },
                        label = {
                            Text(stringResource(R.string.interval_min_label, min))
                        },
                    )
                }
            }

            if (enabled) {
                Text(
                    text = stringResource(nextRes, nextTime),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // stringResource 必须在组合上下文取值，onClick 内是普通 lambda。
            val testToast = stringResource(testToastRes)
            Button(
                onClick = {
                    vm.testNotify()
                    scope.launch { snackbarHostState.showSnackbar(testToast) }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(testRes))
            }
        }
    }
}
