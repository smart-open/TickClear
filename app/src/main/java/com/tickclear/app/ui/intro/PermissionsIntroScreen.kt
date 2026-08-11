@file:OptIn(ExperimentalMaterial3Api::class)

package com.tickclear.app.ui.intro

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tickclear.app.R
import com.tickclear.app.domain.permission.PermissionStatus
import androidx.compose.material3.ExperimentalMaterial3Api

/** 总步数（4 步：通知 / 闹钟 / 后台 / 可选增强）。 */
private const val TOTAL_STEPS = 4

/**
 * 「首次启动权限引导」主屏 + 「设置 → 高级 → 权限配置」复访页（共用）。
 *
 * 视觉设计要点（V2.13.2 美化）：
 * - 顶栏：4 圆点指示器（当前步大圆高亮 + 主色，已完成实心圆，未到灰空心）+ 步骤文字 + 跳过
 * - 内容：每步大圆形图标（80dp 容器 + 40dp 图标）+ HeadlineMedium 标题 + 副标题
 * - 状态卡：16dp 圆角 + 2dp elevation + 主色 tinted surface（granted）/ error tinted（denied）
 * - 底栏：上一步/下一步/完成 圆角按钮（28dp 圆角 + 主色填充）
 *
 * 启动方式：
 * - 首次安装：MainActivity 在 splash 退出之后叠加此屏；markDone → introDone=true → 自动消失
 * - 复访入口：设置 → 高级 → 权限配置 → 此屏（onClose = popBackStack 回到设置页）
 *
 * 状态数据由 [PermissionIntroViewModel] 持有（现场扫描不缓存）；onResume 时 refresh。
 */
@Composable
fun PermissionsIntroScreen(
    onClose: () -> Unit,
    startStep: Int = 0,
    viewModel: PermissionIntroViewModel = hiltViewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var currentStep by rememberSaveable { mutableIntStateOf(startStep.coerceIn(0, TOTAL_STEPS - 1)) }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refresh() }
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.refresh() }

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            IntroTopBar(
                currentStep = currentStep,
                totalSteps = TOTAL_STEPS,
                onBack = {
                    if (currentStep > 0) currentStep--
                    else onClose()
                },
                onSkip = {
                    viewModel.markDone()
                    onClose()
                },
            )
        },
        bottomBar = {
            IntroNavBar(
                currentStep = currentStep,
                totalSteps = TOTAL_STEPS,
                onPrev = { if (currentStep > 0) currentStep-- },
                onNext = { if (currentStep < TOTAL_STEPS - 1) currentStep++ },
                onDone = {
                    viewModel.markDone()
                    onClose()
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (currentStep) {
                0 -> Step1Notification(
                    status = status,
                    onGrant = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onOpenSettings = { openAppNotificationSettings(context) },
                )
                1 -> Step2Alarm(
                    status = status,
                    onOpenExactAlarm = { openExactAlarmSettings(context) },
                    onOpenFullScreen = { openFullScreenIntentSettings(context) },
                )
                2 -> Step3Background(
                    status = status,
                    onOpenBattery = { openBatteryOptimizationSettings(context) },
                )
                3 -> Step4Optional(
                    status = status,
                    onGrantLocation = {
                        locationLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            )
                        )
                    },
                    onOpenAppSettings = { openAppDetailsSettings(context) },
                )
            }
        }
    }
}

// ── 顶栏（4 圆点指示器 + 步骤文字 + 跳过） ────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntroTopBar(
    currentStep: Int,
    totalSteps: Int,
    onBack: () -> Unit,
    onSkip: () -> Unit,
) {
    Surface(
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.intro_back_btn),
                )
            }
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.intro_step_indicator, currentStep + 1, totalSteps),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                StepDots(currentStep = currentStep, totalSteps = totalSteps)
            }
            TextButton(onClick = onSkip) {
                Text(
                    text = stringResource(R.string.intro_skip_btn),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/** 4 圆点指示器：当前步大圆主色 + 已完成步实心主色 + 未到步灰空心。 */
@Composable
private fun StepDots(currentStep: Int, totalSteps: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(totalSteps) { idx ->
            val isCurrent = idx == currentStep
            val isDone = idx < currentStep
            Box(
                modifier = Modifier
                    .size(if (isCurrent) 14.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isCurrent -> MaterialTheme.colorScheme.primary
                            isDone -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outlineVariant
                        }
                    ),
            )
        }
    }
}

// ── 底栏（上一步 / 下一步 / 完成） ──────────────────────────────────────

@Composable
private fun IntroNavBar(
    currentStep: Int,
    totalSteps: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onDone: () -> Unit,
) {
    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (currentStep > 0) {
                OutlinedButton(
                    onClick = onPrev,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                ) { Text(stringResource(R.string.intro_prev_btn), style = MaterialTheme.typography.labelLarge) }
            }
            if (currentStep < totalSteps - 1) {
                Button(
                    onClick = onNext,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                ) { Text(stringResource(R.string.intro_next_btn), style = MaterialTheme.typography.labelLarge) }
            } else {
                Button(
                    onClick = onDone,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) { Text(stringResource(R.string.intro_done_btn), style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

// ── 步骤通用：图标头部 + 标题 + 副标题 ────────────────────────────────

@Composable
private fun StepHeader(
    icon: ImageVector,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    title: String,
    subtitle: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(40.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── 步骤 1：通知 ───────────────────────────────────────────────────────

@Composable
private fun Step1Notification(
    status: PermissionStatus,
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    StepHeader(
        icon = Icons.Filled.NotificationsActive,
        title = stringResource(R.string.intro_step1_title),
        subtitle = stringResource(R.string.intro_step1_subtitle),
    )
    StatusCard(
        title = stringResource(R.string.intro_status_notification_granted).substringBefore(" · "),
        granted = status.notification,
        onAction = onGrant,
        actionLabel = if (status.notification) {
            stringResource(R.string.intro_status_check_again)
        } else {
            stringResource(R.string.intro_step1_grant_btn)
        },
    )
    SectionTitle(stringResource(R.string.intro_step1_channels_title))
    ChannelList(
        items = listOf(
            stringResource(R.string.intro_step1_channel_high),
            stringResource(R.string.intro_step1_channel_default),
            stringResource(R.string.intro_step1_channel_low),
            stringResource(R.string.intro_step1_channel_min),
        ),
    )
    OutlinedButton(
        onClick = onOpenSettings,
        modifier = Modifier.fillMaxWidth().height(44.dp),
        shape = RoundedCornerShape(22.dp),
    ) { Text(stringResource(R.string.intro_step1_open_settings_btn)) }
}

// ── 步骤 2：闹钟 + 全屏通知 ───────────────────────────────────────────

@Composable
private fun Step2Alarm(
    status: PermissionStatus,
    onOpenExactAlarm: () -> Unit,
    onOpenFullScreen: () -> Unit,
) {
    StepHeader(
        icon = Icons.Filled.Alarm,
        title = stringResource(R.string.intro_step2_title),
        subtitle = stringResource(R.string.intro_step2_subtitle),
    )
    StatusCard(
        title = stringResource(R.string.intro_step2_open_settings_btn),
        granted = status.exactAlarm,
        onAction = onOpenExactAlarm,
        actionLabel = stringResource(R.string.intro_step2_open_settings_btn),
    )
    SectionTitle(stringResource(R.string.intro_step2_full_screen_title))
    Text(
        text = stringResource(R.string.intro_step2_full_screen_desc),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    StatusCard(
        title = stringResource(R.string.intro_step2_full_screen_btn),
        granted = status.fullScreenIntent,
        onAction = onOpenFullScreen,
        actionLabel = stringResource(R.string.intro_step2_full_screen_btn),
    )
}

// ── 步骤 3：后台与电池 ────────────────────────────────────────────────

@Composable
private fun Step3Background(
    status: PermissionStatus,
    onOpenBattery: () -> Unit,
) {
    StepHeader(
        icon = Icons.Filled.BatteryFull,
        title = stringResource(R.string.intro_step3_title),
        subtitle = stringResource(R.string.intro_step3_subtitle),
    )
    StatusCard(
        title = stringResource(R.string.intro_step3_open_battery_btn),
        granted = status.batteryOptimization,
        onAction = onOpenBattery,
        actionLabel = stringResource(R.string.intro_step3_open_battery_btn),
    )
    SectionTitle(stringResource(R.string.intro_step3_boot_title))
    Text(
        text = stringResource(R.string.intro_step3_boot_desc),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ── 步骤 4：可选增强 ────────────────────────────────────────────────

@Composable
private fun Step4Optional(
    status: PermissionStatus,
    onGrantLocation: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    StepHeader(
        icon = Icons.Filled.Security,
        title = stringResource(R.string.intro_step4_title),
        subtitle = stringResource(R.string.intro_step4_subtitle),
    )
    StatusCard(
        title = stringResource(R.string.intro_step4_location_title),
        subtitle = stringResource(R.string.intro_step4_location_desc),
        granted = status.location,
        onAction = onGrantLocation,
        actionLabel = if (status.location) {
            stringResource(R.string.intro_status_check_again)
        } else {
            stringResource(R.string.intro_step4_location_title)
        },
    )
    StatusCard(
        title = stringResource(R.string.intro_step4_microphone_title),
        subtitle = stringResource(R.string.intro_step4_microphone_desc),
        granted = status.microphone,
        onAction = onOpenAppSettings,
        actionLabel = stringResource(R.string.intro_status_check_again),
    )
    StatusCard(
        title = stringResource(R.string.intro_step4_camera_title),
        subtitle = stringResource(R.string.intro_step4_camera_desc),
        granted = status.camera,
        onAction = onOpenAppSettings,
        actionLabel = stringResource(R.string.intro_status_check_again),
    )
    StatusCard(
        title = stringResource(R.string.intro_step4_overlay_title),
        subtitle = stringResource(R.string.intro_step4_overlay_desc),
        granted = status.drawOverlays,
        onAction = onOpenAppSettings,
        actionLabel = stringResource(R.string.intro_status_check_again),
    )
}

// ── 通用小组件 ───────────────────────────────────────────────────────

@Composable
private fun StatusCard(
    title: String,
    subtitle: String? = null,
    granted: Boolean,
    onAction: () -> Unit,
    actionLabel: String,
) {
    val accent = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = accent.copy(alpha = 0.08f),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (granted) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = stringResource(R.string.intro_status_granted),
                        color = accent,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelLarge,
                    )
                } else {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = stringResource(R.string.intro_status_denied),
                        color = accent,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = onAction,
                modifier = Modifier.fillMaxWidth().height(40.dp),
                shape = RoundedCornerShape(20.dp),
            ) { Text(actionLabel, style = MaterialTheme.typography.labelMedium) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun ChannelList(items: List<String>) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { line ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .padding(top = 7.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Text(line, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

// ── Intent 助手：跳系统设置 ──────────────────────────────────────────

private fun openAppNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    }
    runCatching { context.startActivity(intent) }.onFailure { openAppDetailsSettings(context) }
}

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    runCatching { context.startActivity(intent) }
        .onFailure { openAppDetailsSettings(context) }
}

private fun openFullScreenIntentSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
    val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    runCatching { context.startActivity(intent) }
        .onFailure { openAppDetailsSettings(context) }
}

private fun openBatteryOptimizationSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    if (pm.isIgnoringBatteryOptimizations(context.packageName)) return
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    runCatching { context.startActivity(intent) }
        .onFailure { openAppDetailsSettings(context) }
}

private fun openAppDetailsSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    runCatching { context.startActivity(intent) }
}