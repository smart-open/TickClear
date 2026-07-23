package com.tickclear.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    viewModel: DebugViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val info by viewModel.info.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.debug_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.load() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionTitle(stringResource(R.string.debug_section_app))
            InfoRow(stringResource(R.string.debug_app_version), info.appVersion)
            InfoRow(stringResource(R.string.debug_ai_mode), info.aiMode)
            InfoRow(stringResource(R.string.debug_assistant_mode), info.assistantMode)
            InfoRow(stringResource(R.string.debug_assistant_endpoint), info.assistantEndpoint)
            InfoRow(stringResource(R.string.debug_voice_supported), yesNo(info.voiceSupported))

            SectionTitle(stringResource(R.string.debug_section_reminder))
            InfoRow(stringResource(R.string.debug_exact_alarm), yesNo(info.canScheduleExact))
            if (!info.canScheduleExact) {
                Text(
                    stringResource(R.string.debug_exact_alarm_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            InfoRow(stringResource(R.string.debug_quiet_hours), yesNo(info.quietHoursEnabled))
            InfoRow(stringResource(R.string.debug_notifications), yesNo(info.notificationsEnabled))
            InfoRow(stringResource(R.string.debug_channels), info.channelCount.toString())

            SectionTitle(stringResource(R.string.debug_section_data))
            InfoRow(stringResource(R.string.debug_tasks), info.taskCount.toString())
            InfoRow(stringResource(R.string.debug_groups), info.groupCount.toString())
            InfoRow(stringResource(R.string.debug_completions), info.completionCount.toString())
            InfoRow(stringResource(R.string.debug_checkins), info.checkInCount.toString())
            InfoRow(stringResource(R.string.debug_medals), info.medalCount.toString())

            SectionTitle(stringResource(R.string.debug_section_actions))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { viewModel.testNotification() },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.debug_test_notification))
                }
                Button(
                    onClick = { viewModel.reschedule() },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.debug_reschedule))
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider()
}

private fun yesNo(v: Boolean): String = if (v) "是" else "否"
