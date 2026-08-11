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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tickclear.app.R
import com.tickclear.app.domain.permission.PermissionStatus

/** 总步数（4 步：通知 / 闹钟 / 后台 / 可选增强）。 */
private const val TOTAL_STEPS = 4

/**
 * 「首次启动权限引导」主屏 + 「设置 → 高级 → 权限配置」复访页（共用）。
 *
 * 启动方式：
 * - 首次安装：MainActivity.onCreate 检测 introDone=false，在启动动画结束后叠加此屏；
 *   用户点「完成」或「跳过」→ markDone() 写盘并 onClose。
 * - 复访入口：设置 → 高级 → 助手配置后 → 「权限配置」行导航至此屏（onClose 返回）。
 *
 * 设计：4 步分页通过 [currentStep] 索引切换（rememberSaveable 跨旋转保留）；
 * 状态数据由 [PermissionIntroViewModel] 持有（现场扫描不缓存）；用户从系统设置
 * 返回时由 [LifecycleResumeEffect] 自动 refresh。
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

    // POST_NOTIFICATIONS 单权限申请（API33+）。
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refresh() }
    // 定位（粗+细）批量申请。
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.refresh() }

    // 从系统设置返回时（含 onResume）刷新权限状态。
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            IntroTopBar(
                currentStep = currentStep,
                totalSteps = TOTAL_STEPS,
                onClose = {
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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

// ── 顶栏（进度 + 跳过） ────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntroTopBar(
    currentStep: Int,
    totalSteps: Int,
    onClose: () -> Unit,
    onSkip: () -> Unit,
) {
    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.intro_step_indicator, currentStep + 1, totalSteps))
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.intro_back_btn))
                    }
                },
                actions = {
                    TextButton(onClick = onSkip) {
                        Text(stringResource(R.string.intro_skip_btn))
                    }
                },
            )
            LinearProgressIndicator(
                progress = { (currentStep + 1).toFloat() / totalSteps },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ── 底部导航（上一步 / 下一步 / 完成） ──────────────────────────────────

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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (currentStep > 0) {
                OutlinedButton(
                    onClick = onPrev,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.intro_prev_btn)) }
            }
            if (currentStep < totalSteps - 1) {
                Button(
                    onClick = onNext,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.intro_next_btn)) }
            } else {
                Button(
                    onClick = onDone,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(),
                ) { Text(stringResource(R.string.intro_done_btn)) }
            }
        }
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
        title = stringResource(R.string.intro_step1_title),
        subtitle = stringResource(R.string.intro_step1_subtitle),
    )
    // 状态卡：通知权限。
    StatusCard(
        title = stringResource(R.string.intro_status_notification_granted)
            .substringBefore(" · "),
        granted = status.notification,
        onAction = onGrant,
        actionLabel = if (status.notification) {
            stringResource(R.string.intro_status_check_again)
        } else {
            stringResource(R.string.intro_step1_grant_btn)
        },
    )
    // 渠道清单（只读展示）。
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
        modifier = Modifier.fillMaxWidth(),
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

// ── 步骤 3：后台与电池 ───────────────────────────────────────────────

@Composable
private fun Step3Background(
    status: PermissionStatus,
    onOpenBattery: () -> Unit,
) {
    StepHeader(
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
private fun StepHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusCard(
    title: String,
    subtitle: String? = null,
    granted: Boolean,
    onAction: () -> Unit,
    actionLabel: String,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (granted) 0.5f else 0.85f),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (granted) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(stringResource(R.string.intro_status_granted), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(stringResource(R.string.intro_status_denied), color = MaterialTheme.colorScheme.error)
                }
            }
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = onAction,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(actionLabel) }
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
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ChannelList(items: List<String>) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEach { line ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .padding(top = 7.dp)
                            .size(6.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)),
                    )
                    Text(line, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

// ── Intent 助手：跳系统设置 ──────────────────────────────────────────

/** 跳本应用通知设置（API26+）。 */
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

/** 跳精确闹钟设置（API31+；API30- 无此权限，无需跳）。 */
private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    runCatching { context.startActivity(intent) }
        .onFailure { openAppDetailsSettings(context) }
}

/** 跳全屏通知意图设置（API34+；以下无此权限）。 */
private fun openFullScreenIntentSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
    val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    runCatching { context.startActivity(intent) }
        .onFailure { openAppDetailsSettings(context) }
}

/** 跳电池优化白名单申请（API23+；鸿蒙/部分定制 ROM 可能无 ACTION，则降级到应用详情）。 */
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

/** 跳应用详情（最后的兜底入口）。 */
private fun openAppDetailsSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    runCatching { context.startActivity(intent) }
}