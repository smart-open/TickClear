package com.tickclear.app.ui.settings

import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import com.tickclear.app.R
import com.tickclear.app.domain.log.AppLogger
import com.tickclear.app.domain.log.LogLevel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    viewModel: DebugViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val info by viewModel.info.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val rescheduleResult by viewModel.rescheduleResult.collectAsStateWithLifecycle()
    LaunchedEffect(rescheduleResult) {
        rescheduleResult?.let { n ->
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.debug_reschedule_done, n),
                android.widget.Toast.LENGTH_SHORT,
            ).show()
            viewModel.clearRescheduleResult()
        }
    }

    val logExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri -> if (uri != null) viewModel.exportLogs(uri) }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.height(48.dp),
                title = {
                    Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                        Text(stringResource(R.string.debug_title))
                    }
                },
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
                .widthIn(max = 720.dp)
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
            InfoRow(stringResource(R.string.debug_battery_optimized), yesNo(info.batteryOptimized))
            if (info.batteryOptimized) {
                Text(
                    stringResource(R.string.debug_battery_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            InfoRow(stringResource(R.string.debug_quiet_hours), yesNo(info.quietHoursEnabled))
            InfoRow(stringResource(R.string.debug_notifications), yesNo(info.notificationsEnabled))
            InfoRow(stringResource(R.string.debug_channels), info.channelCount.toString())
            InfoRow(stringResource(R.string.debug_dnd_access), yesNo(info.dndAccessGranted))
            if (!info.dndAccessGranted) {
                Text(
                    stringResource(R.string.debug_dnd_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            SectionTitle(stringResource(R.string.debug_section_data))
            InfoRow(stringResource(R.string.debug_tasks), info.taskCount.toString())
            InfoRow(stringResource(R.string.debug_groups), info.groupCount.toString())
            InfoRow(stringResource(R.string.debug_completions), info.completionCount.toString())
            InfoRow(stringResource(R.string.debug_checkins), info.checkInCount.toString())
            InfoRow(stringResource(R.string.debug_medals), info.medalCount.toString())

            SectionTitle(stringResource(R.string.debug_logs))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp),
            ) {
                if (logs.isEmpty()) {
                    Text(
                        stringResource(R.string.debug_logs_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    SelectionContainer {
                        Column {
                            logs.takeLast(200).forEach { e ->
                                val ts = DateFormat.format("HH:mm:ss", e.timeMillis).toString()
                                val color = when (e.level) {
                                    LogLevel.ERROR -> MaterialTheme.colorScheme.error
                                    LogLevel.WARN -> MaterialTheme.colorScheme.error.copy(alpha = 0.75f)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                Text(
                                    text = "$ts ${e.level.letter}/${e.tag}: ${e.message}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = color,
                                    modifier = Modifier.padding(vertical = 1.dp),
                                )
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {
                        val text = AppLogger.formatPlain(logs.takeLast(200))
                        clipboard.setPrimaryClip(ClipData.newPlainText("TickClear Logs", text))
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.assistant_msg_copied),
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.action_copy))
                }
                Button(
                    onClick = { viewModel.clearLogs() },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.debug_clear_logs))
                }
                Button(
                    onClick = { logExportLauncher.launch("tickclear_logs.txt") },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.debug_export_logs))
                }
            }

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
            // 排查「时好时坏」：一键跳转系统设置，授予精确闹钟 / 关闭电池优化（Doze 会延迟非精确闹钟）。
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {
                        runCatching {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                context.startActivity(
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                        .setData(android.net.Uri.parse("package:${context.packageName}")),
                                )
                            } else {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                        .setData(android.net.Uri.parse("package:${context.packageName}")),
                                )
                            }
                        }.onFailure {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                        .setData(android.net.Uri.parse("package:${context.packageName}")),
                                )
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.debug_open_exact_alarm))
                }
                Button(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.debug_open_battery))
                }
            }
            // 排查「勿扰模式下不响铃」：一键跳转系统设置，授予「通知优先级访问」权限（高优先级渠道 setBypassDnd 才生效）。
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.debug_open_dnd))
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

@Composable
private fun yesNo(v: Boolean): String =
    stringResource(if (v) R.string.common_yes else R.string.common_no)
