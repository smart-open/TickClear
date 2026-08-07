package com.tickclear.app.ui.tools

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tickclear.app.R
import com.tickclear.app.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 摄像头/麦克风隐私检测界面（V2.9++）。实时指示灯 + 应用权限审计 + 事件日志。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraDetectScreen(
    onBack: () -> Unit,
    viewModel: PrivacyDetectViewModel = hiltViewModel(),
) {
    val monitoring by viewModel.monitoring.collectAsStateWithLifecycle()
    val cameraInUse by viewModel.cameraInUse.collectAsStateWithLifecycle()
    val micInUse by viewModel.micInUse.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val appList by viewModel.appList.collectAsStateWithLifecycle()

    // 事件流持续追加时列表重组频繁，格式化器与倒序视图都必须提到组合体外缓存。
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val reversedEvents = remember(events) { events.asReversed() }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.errorEvents.collect { msg -> snackbarHostState.showSnackbar(msg) }
    }
    LaunchedEffect(Unit) {
        viewModel.scanApps()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_camera_detect_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.scanApps() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.cam_detect_rescan))
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
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Button(
                onClick = { if (monitoring) viewModel.stop() else viewModel.start() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (monitoring) R.string.cam_detect_stop else R.string.cam_detect_start,
                    ),
                )
            }

            val alertActive = cameraInUse || micInUse
            if (alertActive) {
                AlertBanner(cameraInUse = cameraInUse, micInUse = micInUse)
            }

            StatusCard(
                icon = Icons.Filled.CameraAlt,
                label = stringResource(R.string.cam_detect_camera),
                inUse = cameraInUse,
            )
            StatusCard(
                icon = Icons.Filled.Mic,
                label = stringResource(R.string.cam_detect_mic),
                inUse = micInUse,
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.size(Spacing.sm))
                    Text(
                        stringResource(R.string.cam_detect_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (events.isNotEmpty()) {
                Text(
                    stringResource(R.string.cam_detect_events),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    // SimpleDateFormat 构造需解析 locale 数据，属重量级对象，不可每项每次重组新建；
                    // asReversed 返回视图不复制列表（reversed() 每次组合都会整表拷贝）。
                    itemsIndexed(reversedEvents, key = { _, ev -> ev.id }) { index, ev ->
                        val time = timeFormatter.format(Date(ev.time))
                        val isNewest = index == 0 && alertActive
                        Surface(
                            color = if (isNewest) {
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f)
                            } else {
                                Color.Transparent
                            },
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                text = (if (isNewest) "● " else "") + "$time  ${ev.text}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (ev.kind == "mic" || isNewest) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 2.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.cam_detect_apps_title, appList.total),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                OutlinedButton(onClick = { viewModel.scanApps() }) {
                    Text(stringResource(R.string.cam_detect_rescan))
                }
            }
            Text(
                stringResource(R.string.cam_detect_apps_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 系统应用
            if (appList.systemApps.isNotEmpty()) {
                SectionLabel(
                    label = stringResource(R.string.cam_detect_section_system),
                    count = appList.systemApps.size,
                    bg = MaterialTheme.colorScheme.tertiaryContainer,
                    fg = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                AppCardList(appList.systemApps)
            }
            // 安装的应用
            if (appList.installedApps.isNotEmpty()) {
                SectionLabel(
                    label = stringResource(R.string.cam_detect_section_installed),
                    count = appList.installedApps.size,
                    bg = MaterialTheme.colorScheme.secondaryContainer,
                    fg = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                AppCardList(appList.installedApps)
            }
        }
    }
}

@Composable
private fun SectionLabel(
    label: String,
    count: Int,
    bg: Color,
    fg: Color,
) {
    Surface(color = bg, shape = RoundedCornerShape(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = fg,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(fg.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = fg,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun AppCardList(apps: List<PrivacyDetectViewModel.SensitiveApp>) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        for (app in apps) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(app.appName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            app.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val perms = buildString {
                        if (app.hasCamera) append(stringResource(R.string.cam_detect_perm_camera))
                        if (app.hasCamera && app.hasMic) append(" / ")
                        if (app.hasMic) append(stringResource(R.string.cam_detect_perm_mic))
                    }
                    Text(
                        perms,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    inUse: Boolean,
) {
    val context = LocalContext.current
    val motionReduced = remember { isMotionReduced(context) }
    val infinite = rememberInfiniteTransition(label = "camStatusPulse")
    val pulse by infinite.animateFloat(
        0.35f, 1f,
        infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse",
    )
    // 被调用时红色脉冲（呼吸光圈）；减少动态下冻结为常亮，避免晕动
    val glow = if (inUse) (if (motionReduced) 0.6f else pulse) else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (inUse) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 状态指示灯：inUse 时红色呼吸脉冲，强化"正在被访问"的警觉反馈
            Box(
                modifier = Modifier.size(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (glow > 0f) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(
                                MaterialTheme.colorScheme.error.copy(alpha = glow * 0.45f),
                                CircleShape,
                            ),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            if (inUse) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            CircleShape,
                        ),
                )
            }
            Spacer(Modifier.size(Spacing.sm))
            Icon(
                icon,
                contentDescription = null,
                tint = if (inUse) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.size(Spacing.sm))
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                if (inUse) stringResource(R.string.cam_detect_status_used) else stringResource(R.string.cam_detect_status_idle),
                style = MaterialTheme.typography.bodyMedium,
                color = if (inUse) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 异常占用告警横幅：摄像头/麦克风被占用时置顶显示，呼吸光晕吸引注意。
 * 减少动态（isMotionReduced）下冻结为常亮半透，避免晕动并省电。
 */
@Composable
private fun AlertBanner(
    cameraInUse: Boolean,
    micInUse: Boolean,
) {
    val context = LocalContext.current
    val motionReduced = remember { isMotionReduced(context) }
    val infinite = rememberInfiniteTransition(label = "camAlertPulse")
    val pulse by infinite.animateFloat(
        0.35f, 1f,
        infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "camAlertAlpha",
    )
    val t = if (motionReduced) 0.6f else pulse
    val glow = MaterialTheme.colorScheme.error

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f + 0.5f * t),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(glow.copy(alpha = 0.16f + 0.34f * t), CircleShape),
                )
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = glow,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.size(Spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.cam_detect_alert_heading),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = when {
                        cameraInUse && micInUse -> stringResource(R.string.cam_detect_alert_both)
                        cameraInUse -> stringResource(R.string.cam_detect_alert_camera)
                        else -> stringResource(R.string.cam_detect_alert_mic)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}
