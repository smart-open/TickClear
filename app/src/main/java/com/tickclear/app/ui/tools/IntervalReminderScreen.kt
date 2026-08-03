package com.tickclear.app.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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

private val INTERVAL_OPTIONS = listOf(15, 30, 45, 60, 90, 120, 150, 180, 240)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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

    val titleRes = when (vm.type) {
        IntervalType.WATER -> R.string.water_title
        IntervalType.REST -> R.string.rest_title
        IntervalType.EYECARE -> R.string.eyecare_title
    }
    val enableLabelRes = when (vm.type) {
        IntervalType.WATER -> R.string.water_enable
        IntervalType.REST -> R.string.rest_enable
        IntervalType.EYECARE -> R.string.eyecare_enable
    }
    val nextRes = when (vm.type) {
        IntervalType.WATER -> R.string.water_next
        IntervalType.REST -> R.string.rest_next
        IntervalType.EYECARE -> R.string.eyecare_next
    }
    val testRes = when (vm.type) {
        IntervalType.WATER -> R.string.water_test
        IntervalType.REST -> R.string.rest_test
        IntervalType.EYECARE -> R.string.eyecare_test
    }
    val testToastRes = when (vm.type) {
        IntervalType.WATER -> R.string.water_test_toast
        IntervalType.REST -> R.string.rest_test_toast
        IntervalType.EYECARE -> R.string.eyecare_test_toast
    }
    val intervalLabelRes = when (vm.type) {
        IntervalType.WATER -> R.string.water_interval
        IntervalType.REST -> R.string.rest_interval
        IntervalType.EYECARE -> R.string.eyecare_interval
    }

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
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
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
