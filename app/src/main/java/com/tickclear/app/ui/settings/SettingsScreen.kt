package com.tickclear.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import android.annotation.SuppressLint
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import com.tickclear.app.domain.assistant.WakeWordService
import com.tickclear.app.domain.backup.BackupHealth
import com.tickclear.app.domain.scheduler.NotificationHelper
import com.tickclear.app.ui.theme.ThemeMode
import com.tickclear.app.ui.theme.ThemeSkin
import com.tickclear.app.ui.theme.skinPreviewColor

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("InlinedApi")
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToRecycleBin: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onNavigateToDebug: () -> Unit = {},
    onNavigateToVoiceHistory: () -> Unit = {},
    onBack: () -> Unit = {},
    isWide: Boolean = false,
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val themeSkin by viewModel.themeSkin.collectAsStateWithLifecycle()
    val animationEnabled by viewModel.animationEnabled.collectAsStateWithLifecycle()
    val quietHoursEnabled by viewModel.quietHoursEnabled.collectAsStateWithLifecycle()
    val snoozeDefaultMin by viewModel.snoozeDefaultMin.collectAsStateWithLifecycle()
    val soundEnabled by viewModel.soundEnabled.collectAsStateWithLifecycle()
    val clearConfirmEnabled by viewModel.clearConfirmEnabled.collectAsStateWithLifecycle()
    val offlineCommandEnabled by viewModel.offlineCommandEnabled.collectAsStateWithLifecycle()
    val asrLanguage by viewModel.asrLanguage.collectAsStateWithLifecycle()
    val autoBackupEnabled by viewModel.autoBackupEnabled.collectAsStateWithLifecycle()
    val lastAutoBackupAt by viewModel.lastAutoBackupAt.collectAsStateWithLifecycle()
    val lastBackupHealth by viewModel.lastBackupHealth.collectAsStateWithLifecycle()
    // V2.71 分享任务组模板：活跃任务组列表 + 选择对话框状态。
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    var showGroupTemplateDialog by remember { mutableStateOf(false) }
    var selectedTemplateGroupId by remember { mutableStateOf<String?>(null) }
    val aiMode by viewModel.aiMode.collectAsStateWithLifecycle()
    val assistantMode by viewModel.assistantMode.collectAsStateWithLifecycle()
    // V2.65/V2.66 语音历史 + 常驻唤醒
    val voiceHistoryEnabled by viewModel.voiceHistoryEnabled.collectAsStateWithLifecycle()
    val wakeWordEnabled by viewModel.wakeWordEnabled.collectAsStateWithLifecycle()
    val wakeWord by viewModel.wakeWord.collectAsStateWithLifecycle()

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
    val icsExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/calendar"),
    ) { uri -> if (uri != null) viewModel.exportIcsTo(uri) }
    val icsImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.importIcsFrom(uri) }
    // V2.71 分享任务组模板：导出选中组为 JSON（先弹选择对话框，再启动 SAF）。
    val groupTemplateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> if (uri != null) selectedTemplateGroupId?.let { viewModel.exportGroupTemplateTo(uri, it) } }
    val icsExportName = "tickclear_${
        java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
    }.ics"
    // V2.11 通知与权限引导：跳转系统设置（纯 Intent，零依赖），规避 Android 14+ 全屏/精确闹钟限制。
    val context = LocalContext.current
    // V2.66 常驻唤醒：开启前必须先拿到 RECORD_AUDIO 运行时权限，
    // 否则 Android 14 上启动 type=microphone 前台服务会抛 SecurityException 崩溃。
    val micPermissionDeniedMsg = stringResource(R.string.error_permission_record)
    val wakeWordPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.setWakeWordEnabled(true)
            toggleWakeWordService(context, true)
        } else {
            viewModel.setWakeWordEnabled(false)
            viewModel.backupToasts.tryEmit(BackupToast(micPermissionDeniedMsg))
        }
    }
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
    // ⚠️ 这两个 Action 规范要求用 data URI（package:包名），EXTRA_APP_PACKAGE 在多数 ROM 上
    // 启动失败且被 runCatching 吞掉 → 点击无反应（问题10根因）。失败兜底跳应用详情页。
    val openFullScreenPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        android.net.Uri.parse("package:${context.packageName}"),
                    ),
                )
            }.recoverCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:${context.packageName}"),
                    ),
                )
            }
        }
    }
    val openExactAlarmPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        android.net.Uri.parse("package:${context.packageName}"),
                    ),
                )
            }.recoverCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:${context.packageName}"),
                    ),
                )
            }
        }
    }

    // V2.71 分享任务组模板：选择要导出的任务组，确认后启动 SAF 保存。
    if (showGroupTemplateDialog) {
        AlertDialog(
            onDismissRequest = { showGroupTemplateDialog = false },
            title = { Text(stringResource(R.string.settings_group_template_pick_title)) },
            text = {
                if (groups.isEmpty()) {
                    Text(stringResource(R.string.settings_group_template_pick_empty))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        groups.forEach { g ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedTemplateGroupId = g.id
                                        showGroupTemplateDialog = false
                                        val safe = g.name.trim()
                                            .replace(Regex("""[\\/:*?"<>|\s]+"""), "_")
                                            .take(40)
                                            .ifEmpty { "group" }
                                        groupTemplateLauncher.launch("group_${safe}_template.json")
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    g.name.ifBlank { stringResource(R.string.settings_group_template_untitled) },
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGroupTemplateDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // V2.8X 顶栏单行标题：Box 强制 48dp 高度下垂直居中。
            TopAppBar(
                modifier = Modifier.height(48.dp),
                title = {
                    Box(
                        modifier = Modifier.fillMaxHeight(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(stringResource(R.string.settings_title))
                    }
                },
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
            // 主题/皮肤区域极致紧凑化：绕过通用 SettingRow（其 padding+divider 累积导致红框大空白），
            // 用零外边距的专用布局直接渲染 chip 行。
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalMinimumInteractiveComponentSize provides 0.dp,
            ) {
                // 主题模式行
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_theme_title), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.settings_theme_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                label = { Text(stringResource(themeLabelRes(mode))) },
                            )
                        }
                    }
                }
                HorizontalDivider()
                // 皮肤行
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_skin_title), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.settings_skin_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(ThemeSkin.entries) { skin ->
                            FilterChip(
                                selected = themeSkin == skin,
                                onClick = { viewModel.setThemeSkin(skin) },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .background(skinPreviewColor(skin), CircleShape),
                                    )
                                },
                                label = { Text(stringResource(skinLabelRes(skin))) },
                            )
                        }
                    }
                }
                HorizontalDivider()
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
            // V2.30 稍后提醒默认时长：5/15/30 分钟分段选择。
            SettingRow(
                title = stringResource(R.string.settings_snooze_title),
                subtitle = stringResource(R.string.settings_snooze_subtitle),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.tickclear.app.domain.scheduler.ReminderPrefs.SNOOZE_OPTIONS.forEach { min ->
                        FilterChip(
                            selected = snoozeDefaultMin == min,
                            onClick = { viewModel.setSnoozeDefaultMin(min) },
                            label = { Text(stringResource(R.string.settings_snooze_option, min)) },
                        )
                    }
                }
            }
            // V2.31 提醒音效开关：关闭后高优先级提醒不发声/不震动。
            SettingRow(
                title = stringResource(R.string.settings_sound_title),
                subtitle = stringResource(R.string.settings_sound_subtitle),
            ) {
                Switch(
                    checked = soundEnabled,
                    onCheckedChange = { viewModel.setSoundEnabled(it) },
                )
            }
            // V2.40 清空前确认：关闭后「一键清空」直接执行。
            SettingRow(
                title = stringResource(R.string.settings_clear_confirm_title),
                subtitle = stringResource(R.string.settings_clear_confirm_subtitle),
            ) {
                Switch(
                    checked = clearConfirmEnabled,
                    onCheckedChange = { viewModel.setClearConfirmEnabled(it) },
                )
            }
            // V2.42 离线语音指令：开启后可用「暂停/启用/删除 + 任务名」热词直接操作，无需联网。
            SettingRow(
                title = stringResource(R.string.settings_offline_command_title),
                subtitle = stringResource(R.string.settings_offline_command_subtitle),
            ) {
                Switch(
                    checked = offlineCommandEnabled,
                    onCheckedChange = { viewModel.setOfflineCommandEnabled(it) },
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
            // V2.43 方言识别：选择系统 ASR 语言包（普通话/粤语/台湾/英语）。
            // 效果取决于设备是否装有对应语音包；未装则回退系统默认。
            SettingRow(
                title = stringResource(R.string.settings_asr_dialect_title),
                subtitle = stringResource(R.string.settings_asr_dialect_subtitle),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ASR_LANGUAGE_OPTIONS.forEach { (code, labelRes) ->
                        FilterChip(
                            selected = asrLanguage == code,
                            onClick = { viewModel.setAsrLanguage(code) },
                            label = { Text(stringResource(labelRes)) },
                        )
                    }
                }
            }
            // V2.65 语音对话历史：默认关闭；开启后助手对话保存本地，可随时清空。
            SettingRow(
                title = stringResource(R.string.settings_voice_history_title),
                subtitle = stringResource(R.string.settings_voice_history_subtitle),
            ) {
                Switch(
                    checked = voiceHistoryEnabled,
                    onCheckedChange = { viewModel.setVoiceHistoryEnabled(it) },
                )
            }
            if (voiceHistoryEnabled) {
                ClickableRow(
                    icon = Icons.Filled.Settings,
                    title = stringResource(R.string.settings_voice_history_view_title),
                    subtitle = stringResource(R.string.settings_voice_history_view_subtitle),
                    onClick = onNavigateToVoiceHistory,
                )
            }
            // V2.66 常驻语音唤醒：开启前台监听服务，说话即触发助手（能量级 VAD，本地处理）。
            SettingRow(
                title = stringResource(R.string.settings_wake_word_title),
                subtitle = stringResource(R.string.settings_wake_word_subtitle),
            ) {
                Switch(
                    checked = wakeWordEnabled,
                    onCheckedChange = { on ->
                        if (on && androidx.core.content.ContextCompat.checkSelfPermission(
                                context, android.Manifest.permission.RECORD_AUDIO,
                            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            // 未授权：先请求麦克风权限，授予后在回调中开启（拒绝则保持关闭并提示）。
                            wakeWordPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                        } else {
                            viewModel.setWakeWordEnabled(on)
                            toggleWakeWordService(context, on)
                        }
                    },
                )
            }
            if (wakeWordEnabled) {
                SettingRow(
                    title = stringResource(R.string.settings_wake_word_phrase_title),
                    subtitle = stringResource(R.string.settings_wake_word_phrase_subtitle, wakeWord),
                ) {}
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
                // V2.23 备份自愈校验回显：颜色区分健康/损坏，损坏提示用户重备。
                SettingRow(
                    title = stringResource(R.string.settings_backup_health_title),
                    subtitle = stringResource(R.string.settings_backup_health_subtitle),
                ) {
                    val (healthText, healthColor) = when (lastBackupHealth) {
                        BackupHealth.OK -> stringResource(R.string.settings_backup_health_ok) to MaterialTheme.colorScheme.primary
                        BackupHealth.CORRUPT -> stringResource(R.string.settings_backup_health_corrupt) to MaterialTheme.colorScheme.error
                        BackupHealth.EMPTY -> stringResource(R.string.settings_backup_health_empty) to MaterialTheme.colorScheme.onSurfaceVariant
                        BackupHealth.NONE -> stringResource(R.string.settings_backup_health_none) to MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(healthText, color = healthColor, style = MaterialTheme.typography.bodyMedium)
                }
            }

            // ── ICS 日历导入导出（V2.7）──
            ClickableRow(
                icon = Icons.Filled.Upload,
                title = stringResource(R.string.settings_ics_export_title),
                subtitle = stringResource(R.string.settings_ics_export_subtitle),
                onClick = { icsExportLauncher.launch(icsExportName) },
            )
            ClickableRow(
                icon = Icons.Filled.Download,
                title = stringResource(R.string.settings_ics_import_title),
                subtitle = stringResource(R.string.settings_ics_import_subtitle),
                onClick = { icsImportLauncher.launch(arrayOf("text/calendar", "application/octet-stream", "*/*")) },
            )

            // ── 分享任务组模板（V2.71）：本地导出单组+任务为可分享 JSON ──
            ClickableRow(
                icon = Icons.Filled.Upload,
                title = stringResource(R.string.settings_group_template_title),
                subtitle = stringResource(R.string.settings_group_template_subtitle),
                onClick = { showGroupTemplateDialog = true },
            )

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
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
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
            .padding(vertical = 3.dp),
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

/** V2.68 主题皮肤名称映射。 */
private fun skinLabelRes(skin: ThemeSkin): Int = when (skin) {
    ThemeSkin.BLUE -> R.string.settings_skin_blue
    ThemeSkin.GREEN -> R.string.settings_skin_green
    ThemeSkin.PURPLE -> R.string.settings_skin_purple
    ThemeSkin.ORANGE -> R.string.settings_skin_orange
    ThemeSkin.ROSE -> R.string.settings_skin_rose
    ThemeSkin.TEAL -> R.string.settings_skin_teal
}

/** V2.43 系统 ASR 方言选项：(语言代码, 标签资源)。效果取决于设备是否装有对应语音包。 */
private val ASR_LANGUAGE_OPTIONS = listOf(
    "zh-CN" to R.string.settings_dialect_mandarin,
    "yue-Hant" to R.string.settings_dialect_cantonese,
    "zh-TW" to R.string.settings_dialect_taiwan,
    "en-US" to R.string.settings_dialect_english,
)

/** V2.66 常驻唤醒：切换前台监听服务。开启用 startForegroundService，关闭用 stopService。 */
private fun toggleWakeWordService(context: android.content.Context, enabled: Boolean) {
    val intent = Intent(context, WakeWordService::class.java)
    runCatching {
        if (enabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } else {
            context.stopService(intent)
        }
    }
}
