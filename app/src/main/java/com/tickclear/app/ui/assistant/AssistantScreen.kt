package com.tickclear.app.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.tickclear.app.R
import com.tickclear.app.domain.assistant.WakeWordBus
import com.tickclear.app.domain.model.Task
import com.tickclear.app.domain.model.RepeatType
import com.tickclear.app.ui.components.formatMinute
import com.tickclear.app.ui.components.EmptyStateGuide
import com.tickclear.app.ui.settings.SettingsViewModel
import android.Manifest

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun AssistantScreen(
    viewModel: AssistantViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    isWide: Boolean = false,
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val connected by viewModel.connected.collectAsStateWithLifecycle()
    val configured by viewModel.configured.collectAsStateWithLifecycle()
    val voiceSupported by viewModel.voiceSupported.collectAsStateWithLifecycle()
    val recording by viewModel.recording.collectAsStateWithLifecycle()
    val pendingDraft by viewModel.pendingDraft.collectAsStateWithLifecycle()
    val wakeWordActive by viewModel.wakeWordActive.collectAsStateWithLifecycle()
    val settingsVm: SettingsViewModel = hiltViewModel()
    val wakeEnabled by settingsVm.wakeWordEnabled.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    var showConfig by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var pendingVoiceStart by remember { mutableStateOf(false) }
    var pendingWakeStart by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val permissionDeniedMsg = stringResource(R.string.error_permission_record)

    val recordPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    LaunchedEffect(Unit) { viewModel.connect() }
    // 离开助手页（切到其它 Tab / 返回）即断开 WebSocket 并释放音频资源，避免连接与 IO 协程常驻泄露。
    DisposableEffect(Unit) {
        onDispose { viewModel.disconnect() }
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    // V2.66 常驻唤醒：被前台服务唤醒跳转到助手页后，消费待处理标记并监听后续唤醒事件，自动开始收音。
    LaunchedEffect(Unit) {
        if (WakeWordBus.consumePending()) {
            if (recordPermission.status is PermissionStatus.Granted) {
                viewModel.startVoice()
            } else {
                pendingVoiceStart = true
                recordPermission.launchPermissionRequest()
            }
        }
        WakeWordBus.events.collect {
            if (recordPermission.status is PermissionStatus.Granted) {
                viewModel.startVoice()
            } else {
                pendingVoiceStart = true
                recordPermission.launchPermissionRequest()
            }
        }
    }
    // 运行时授权结果：已授予且此前点了麦克风，则开始录音。
    LaunchedEffect(recordPermission.status) {
        if (recordPermission.status is PermissionStatus.Granted && pendingVoiceStart) {
            pendingVoiceStart = false
            viewModel.startVoice()
        }
        if (recordPermission.status is PermissionStatus.Granted && pendingWakeStart) {
            pendingWakeStart = false
            viewModel.startWakeWord()
        }
        if (recordPermission.status is PermissionStatus.Denied && (pendingVoiceStart || pendingWakeStart)) {
            pendingVoiceStart = false
            pendingWakeStart = false
            snackbarHostState.showSnackbar(message = permissionDeniedMsg)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.assistant_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    ConnectionChip(connected)
                    // V2.19 宽屏常驻配置侧栏，隐藏齿轮避免重复入口
                    if (!isWide) {
                        IconButton(onClick = { showConfig = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.assistant_config_desc))
                        }
                    }
                    if (wakeEnabled) {
                        IconButton(onClick = {
                            if (wakeWordActive) {
                                viewModel.stopWakeWord()
                            } else if (recordPermission.status is PermissionStatus.Granted) {
                                viewModel.startWakeWord()
                            } else {
                                pendingWakeStart = true
                                recordPermission.launchPermissionRequest()
                            }
                        }) {
                            Icon(
                                Icons.Filled.GraphicEq,
                                contentDescription = stringResource(
                                    if (wakeWordActive) R.string.assistant_wakeword_stop else R.string.assistant_wakeword_start,
                                ),
                                tint = if (wakeWordActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val micDesc = when {
                    !voiceSupported -> stringResource(R.string.assistant_voice_unsupported)
                    recording -> stringResource(R.string.assistant_voice_stop)
                    else -> stringResource(R.string.assistant_mic_desc)
                }
                IconButton(
                    onClick = {
                        if (!voiceSupported) return@IconButton
                        if (recording) {
                            viewModel.stopVoice()
                        } else if (recordPermission.status is PermissionStatus.Granted) {
                            viewModel.startVoice()
                        } else {
                            pendingVoiceStart = true
                            recordPermission.launchPermissionRequest()
                        }
                    },
                    enabled = voiceSupported,
                ) {
                    Icon(
                        if (recording) Icons.Filled.MicOff else Icons.Filled.Mic,
                        contentDescription = micDesc,
                        tint = if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.assistant_input_placeholder)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submit(input, viewModel) { input = it } }),
                )
                IconButton(onClick = { submit(input, viewModel) { input = it } }) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.assistant_send))
                }
            }
        },
    ) { padding ->
        if (isWide) {
            // V2.19 宽屏：对话与配置分栏；配置面板常驻右侧，无需弹窗。
            Row(Modifier.fillMaxSize().padding(padding)) {
                AssistantChatBody(
                    isWide = true,
                    configured = configured,
                    messages = messages,
                    pendingDraft = pendingDraft,
                    viewModel = viewModel,
                    listState = listState,
                    onConfigure = { showConfig = true },
                    modifier = Modifier.weight(1f).fillMaxSize(),
                )
                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp),
                )
                Column(
                    modifier = Modifier
                        .widthIn(max = 440.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    AssistantConfigContent(settingsViewModel = settingsVm, onDismiss = {})
                }
            }
        } else {
            AssistantChatBody(
                isWide = false,
                configured = configured,
                messages = messages,
                pendingDraft = pendingDraft,
                viewModel = viewModel,
                listState = listState,
                onConfigure = { showConfig = true },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }

    if (showConfig && !isWide) {
        AssistantConfigSheet(onDismiss = {
            showConfig = false
            viewModel.refreshConfigured()
        })
    }
}

private fun submit(
    text: String,
    viewModel: AssistantViewModel,
    clear: (String) -> Unit,
) {
    viewModel.sendText(text)
    clear("")
}

/**
 * 助手对话主体（窄屏整屏 / 宽屏左栏复用）：草稿确认卡 + 对话列表；
 * 未配置服务商且非宽屏时展示引导插画与「去配置」按钮（V2.20）。
 */
@Composable
private fun AssistantChatBody(
    isWide: Boolean,
    configured: Boolean,
    messages: List<ChatMessage>,
    pendingDraft: Task?,
    viewModel: AssistantViewModel,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onConfigure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        pendingDraft?.let { draft ->
            TaskDraftCard(
                draft = draft,
                onConfirm = { viewModel.confirmDraft() },
                onDismiss = { viewModel.dismissDraft() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        if (!isWide && !configured) {
            EmptyStateGuide(
                icon = "🤖",
                title = stringResource(R.string.assistant_unconfigured_title),
                message = stringResource(R.string.assistant_unconfigured_desc),
                actionLabel = stringResource(R.string.assistant_unconfigured_action),
                onAction = onConfigure,
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatBubble(msg)
                }
            }
        }
    }
}

@Composable
private fun ConnectionChip(connected: Boolean) {
    val statusText = stringResource(
        if (connected) R.string.assistant_connected else R.string.assistant_disconnected,
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(end = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = statusText },
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (connected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                ),
        )
        Text(
            text = statusText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun ChatBubble(msg: ChatMessage) {
    val isUser = msg.role == "user"
    val isSystem = msg.role == "system"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        if (isSystem) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    msg.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isUser) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    msg.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 语音/文本解析出的待确认草稿任务卡片。
 * 展示标题、时间、重复、提醒级别，提供「创建 / 取消」两个动作，避免误建任务。
 */
@Composable
private fun TaskDraftCard(
    draft: Task,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repeatLabel = when (RepeatType.fromCode(draft.repeatType)) {
        RepeatType.DAILY -> stringResource(R.string.repeat_daily)
        RepeatType.WEEKLY -> stringResource(R.string.repeat_weekly)
        RepeatType.MONTHLY -> stringResource(R.string.repeat_monthly)
        else -> stringResource(R.string.repeat_none)
    }
    val reminderLabel = if (draft.reminderEnabled) {
        when (draft.reminderLevel) {
            "high" -> stringResource(R.string.reminder_high)
            "low" -> stringResource(R.string.reminder_low)
            else -> stringResource(R.string.reminder_mid)
        }
    } else {
        stringResource(R.string.assistant_draft_anytime)
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.assistant_draft_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            text = draft.title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "${stringResource(R.string.assistant_draft_time)}：${formatMinute(draft.scheduledStartMin)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                "${stringResource(R.string.assistant_draft_repeat)}：$repeatLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text(
            "${stringResource(R.string.assistant_draft_reminder)}：$reminderLabel",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.assistant_draft_dismiss))
            }
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.assistant_draft_confirm))
            }
        }
    }
}
