package com.tickclear.app.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tickclear.app.R
import com.tickclear.app.ui.theme.Spacing

private val WEAR_OPTIONS = listOf(15, 30, 45, 60, 90, 120)

/**
 * 听力保护（V2.9++）：设置总开关、音量安全阈值、建议最大佩戴时长。
 * 实际监测在 [com.tickclear.app.domain.hearing.HearingMonitor]（App 运行时生效）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HearingScreen(
    vm: HearingViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val enabled by vm.enabled.collectAsStateWithLifecycle()
    val volume by vm.volumeThreshold.collectAsStateWithLifecycle()
    val wear by vm.maxWearMin.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_hearing_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
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
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.hearing_enable_label), style = MaterialTheme.typography.titleSmall)
                Switch(checked = enabled, onCheckedChange = { vm.setEnabled(it) })
            }

            Text(
                text = stringResource(R.string.hearing_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (enabled) {
                Text(stringResource(R.string.hearing_volume_label), style = MaterialTheme.typography.titleSmall)
                Text(
                    text = stringResource(R.string.hearing_volume_current, volume),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = volume.toFloat(),
                    onValueChange = { vm.setVolumeThreshold(it.toInt()) },
                    valueRange = 0f..100f,
                    steps = 0,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(stringResource(R.string.hearing_wear_label), style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    WEAR_OPTIONS.forEach { min ->
                        FilterChip(
                            selected = wear == min,
                            onClick = { vm.setMaxWearMin(min) },
                            label = { Text(stringResource(R.string.hearing_wear_min, min)) },
                        )
                    }
                }
            }
        }
    }
}
