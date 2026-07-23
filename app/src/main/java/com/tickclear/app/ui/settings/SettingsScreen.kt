package com.tickclear.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.tickclear.app.R
import com.tickclear.app.domain.scheduler.NotificationHelper
import com.tickclear.app.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToRecycleBin: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onNavigateToDebug: () -> Unit = {},
    onBack: () -> Unit = {},
    isWide: Boolean = false,
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val animationEnabled by viewModel.animationEnabled.collectAsStateWithLifecycle()
    val quietHoursEnabled by viewModel.quietHoursEnabled.collectAsStateWithLifecycle()
    val autoBackupEnabled by viewModel.autoBackupEnabled.collectAsStateWithLifecycle()
    val lastAutoBackupAt by viewModel.lastAutoBackupAt.collectAsStateWithLifecycle()
    val aiMode by viewModel.aiMode.collectAsStateWithLifecycle()
    val assistantMode by viewModel.assistantMode.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.backupToasts.collect { snackbarHostState.showSnackbar(it.message) }
    }

    val exportName = stringResource(R.string.backup_export_filename)
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> if (uri != null) viewModel.exportTo(uri) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.importFrom(uri) }

    // V2.11 通知与权限引导：跳转系统设置（纯 Intent，零依赖），规避 Android 14+ 全屏/精确闹钟限制。
    val context = LocalContext.current
    val openNotificationChannel: () -> Unit = {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startActivity(
                    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        putExtra(Settings.EXTRA_CHANNEL_ID, NotificationHelper.CHANNEL_REMINDER)
                    },
                )
            } else {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    },
                )
            }
        }
    }
    val openFullScreenPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    },
                )
            }
        }
    }
    val openExactAlarmPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    },
                )
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        // 左列：外观 / 提醒 / 助手
        val leftContent: @Composable ColumnScope.() -> Unit = {
            // ── 外观 ──
            SectionTitle(stringResource(R.string.settings_section_appearance))
            SettingRow(
                title = stringResource(R.string.settings_theme_title),
                subtitle = stringResource(R.string.settings_theme_subtitle),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = { Text(stringResource(themeLabelRes(mode))) },
                        )
                    }
                }
            }
            SettingRow(
                title = stringResource(R.string.settings_animation_title),
                subtitle = stringResource(R.string.settings_animation_subtitle),
            ) {
                Switch(
                    checked = animationEnabled,
                    onCheckedChange = { viewModel.setAnimationEnabled(it) },
                )
            }

            // ── 提醒 ──
            SectionTitle(stringResource(R.string.settings_section_reminder))
            SettingRow(
                title = stringResource(R.string.settings_quiet_title),
                subtitle = stringResource(R.string.settings_quiet_subtitle),
            ) {
                Switch(
                    checked = quietHoursEnabled,
                    onCheckedChange = { viewModel.setQuietHoursEnabled(it) },
                )
            }

            // ── 助手 ──
            SectionTitle(stringResource(R.string.settings_section_assistant))
            SettingRow(
                title = stringResource(R.string.settings_mode_title),
                subtitle = stringResource(R.string.settings_mode_subtitle),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = assistantMode == "MOCK",
                        onClick = { viewModel.setAssistantMode("MOCK") },
                        label = { Text(stringResource(R.string.settings_mode_mock)) },
                    )
                    FilterChip(
                        selected = assistantMode == "REAL",
                        onClick = { viewModel.setAssistantMode("REAL") },
                        label = { Text(stringResource(R.string.settings_mode_real)) },
                    )
                }
            }
            SettingRow(
                title = stringResource(R.string.settings_ai_engine_title),
                subtitle = if (aiMode == "LOCAL_NLU") {
                    stringResource(R.string.settings_ai_engine_local)
                } else {
                    stringResource(R.string.settings_ai_engine_cloud)
                },
            ) {
                FilterChip(
                    selected = true,
                    onClick = { viewModel.setAiMode("LOCAL_NLU") },
                    label = { Text(stringResource(R.string.settings_ai_local)) },
                )
            }
        }

        // 右列：数据与隐私 / 关于 / 后续能力
        val rightContent: @Composable ColumnScope.() -> Unit = {
            // ── 数据与隐私 ──
            SectionTitle(stringResource(R.string.settings_section_privacy))
            ClickableRow(
                icon = Icons.Filled.Delete,
                title = stringResource(R.string.settings_recycle_bin),
                subtitle = stringResource(R.string.settings_recycle_subtitle),
                onClick = onNavigateToRecycleBin,
            )
            ClickableRow(
                icon = Icons.Filled.Upload,
                title = stringResource(R.string.settings_export_title),
                subtitle = stringResource(R.string.settings_export_subtitle),
                onClick = { exportLauncher.launch(exportName) },
            )
            ClickableRow(
                icon = Icons.Filled.Download,
                title = stringResource(R.string.settings_import_title),
                subtitle = stringResource(R.string.settings_import_subtitle),
                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
            )

            // ── 自动备份（V2.5/V2.6）：每日加密备份到应用私有目录，可手动立即执行 ──
            SettingRow(
                title = stringResource(R.string.settings_auto_backup_title),
                subtitle = stringResource(R.string.settings_auto_backup_subtitle),
            ) {
                Switch(
                    checked = autoBackupEnabled,
                    onCheckedChange = { viewModel.setAutoBackupEnabled(it) },
                )
            }
            if (autoBackupEnabled) {
                val lastText = if (lastAutoBackupAt > 0L) {
                    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
                    stringResource(R.string.settings_auto_backup_last, fmt.format(java.util.Date(lastAutoBackupAt)))
                } else {
                    stringResource(R.string.settings_auto_backup_never)
                }
                ClickableRow(
                    icon = Icons.Filled.Upload,
                    title = stringResource(R.string.settings_auto_backup_now),
                    subtitle = lastText,
                    onClick = { viewModel.runAutoBackupNow() },
                )
            }

            // ── 通知与权限 ──
            SectionTitle(stringResource(R.string.settings_section_perms))
            ClickableRow(
                icon = Icons.Filled.Notifications,
                title = stringResource(R.string.settings_perms_notif),
                subtitle = stringResource(R.string.settings_perms_notif_sub),
                onClick = openNotificationChannel,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ClickableRow(
                    icon = Icons.Filled.Notifications,
                    title = stringResource(R.string.settings_perms_fullscreen),
                    subtitle = stringResource(R.string.settings_perms_fullscreen_sub),
                    onClick = openFullScreenPermission,
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ClickableRow(
                    icon = Icons.Filled.Notifications,
                    title = stringResource(R.string.settings_perms_exactalarm),
                    subtitle = stringResource(R.string.settings_perms_exactalarm_sub),
                    onClick = openExactAlarmPermission,
                )
            }

            // ── 关于 ──
            SectionTitle(stringResource(R.string.settings_section_about))
            ClickableRow(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.settings_about_title),
                subtitle = stringResource(R.string.settings_about_subtitle),
                onClick = onNavigateToAbout,
            )

            // ── 后续能力（未配置置灰）──
            SectionTitle(stringResource(R.string.settings_section_advanced))
            DisabledRow(
                icon = Icons.Filled.Settings,
                title = stringResource(R.string.settings_asr_row),
                tag = stringResource(R.string.settings_later_tag),
            )
            DisabledRow(
                icon = Icons.Filled.Settings,
                title = stringResource(R.string.settings_llm_row),
                tag = stringResource(R.string.settings_later_tag),
            )
            ClickableRow(
                icon = Icons.Filled.Settings,
                title = stringResource(R.string.settings_debug_row),
                subtitle = stringResource(R.string.settings_debug_subtitle),
                onClick = onNavigateToDebug,
            )
        }

        if (isWide) {
            // 宽屏：左右两列，各自独立滚动
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = leftContent,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = rightContent,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                leftContent()
                rightContent()
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
private fun SettingRow(
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing()
    }
    HorizontalDivider()
}

@Composable
private fun ClickableRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "$title，$subtitle"
            }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
    }
    HorizontalDivider()
}

@Composable
private fun DisabledRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    tag: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "$title，$tag" }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Text(
            tag,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
    HorizontalDivider()
}

private fun themeLabelRes(mode: ThemeMode): Int = when (mode) {
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
    ThemeMode.DYNAMIC -> R.string.settings_theme_dynamic_short
}
