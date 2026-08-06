package com.tickclear.app.ui.assistant

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import com.tickclear.app.ui.components.showTimedSnackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.roundToInt
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.tickclear.app.R
import com.tickclear.app.domain.assistant.WakeWordBus
import com.tickclear.app.domain.log.AppLogger
import com.tickclear.app.domain.model.Task
import com.tickclear.app.domain.model.RepeatType
import com.tickclear.app.ui.components.formatMinute
import com.tickclear.app.ui.components.EmptyStateGuide
import com.tickclear.app.ui.settings.SettingsViewModel
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun AssistantScreen(
    viewModel: AssistantViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    isWide: Boolean = false,
    // V2.8X++：设置 Tab「助手配置」直达入口 —— 进入即弹配置面板（宽屏配置面板常驻，无需弹）。
    initialOpenConfig: Boolean = false,
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val connected by viewModel.connected.collectAsStateWithLifecycle()
    val configured by viewModel.configured.collectAsStateWithLifecycle()
    val voiceSupported by viewModel.voiceSupported.collectAsStateWithLifecycle()
    val recording by viewModel.recording.collectAsStateWithLifecycle()
    val pendingDraft by viewModel.pendingDraft.collectAsStateWithLifecycle()
    val wakeWordActive by viewModel.wakeWordActive.collectAsStateWithLifecycle()
    // V2.8X++：麦克风按下即时反馈 → 短 toast（连接诊断已不再顶栏 banner 展示）
    val micToast by viewModel.micToast.collectAsStateWithLifecycle()
    // V2.8X++：重连进度仅在顶部状态栏芯片展示（由 ViewModel 更新，不进消息流）。
    val reconnectingStatus by viewModel.reconnectingStatus.collectAsStateWithLifecycle()
    // V2.8X++：语音「准备中」内联态（点击麦克风到真正聆听之间），驱动旋转环，避免 linger toast。
    val voiceOpening by viewModel.voiceOpening.collectAsStateWithLifecycle()
    val settingsVm: SettingsViewModel = hiltViewModel()
    val wakeEnabled by settingsVm.wakeWordEnabled.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    // V2.8X++：输入框聚焦态——获焦(键盘起)时隐藏话筒(纯打字)，失焦时话筒重现(微信式切换)。
    var isFocused by remember { mutableStateOf(false) }
    var showConfig by remember { mutableStateOf(initialOpenConfig) }
    // V2.8X++：切回助手页时初始位置直接落在末条（避免从顶部 animateScrollToItem 造成的跳动卡顿）。
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = if (messages.isNotEmpty()) messages.lastIndex else 0,
    )
    var pendingVoiceStart by remember { mutableStateOf(false) }
    var pendingWakeStart by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val appContext = androidx.compose.ui.platform.LocalContext.current
    // V2.8X++：剪贴板与「已复制」提示在顶层提前取值，供长按菜单与多选底栏共用（onClick 非 @Composable 上下文，项目红线）。
    val clipboard = LocalClipboardManager.current
    val copiedTip = stringResource(R.string.assistant_msg_copied)
    // V2.8X++：焦点管理——发送后 / 点击消息列表等输入与键盘以外区域时收起键盘并失焦（微信式）。
    val focusManager = LocalFocusManager.current
    // V2.8X++：长按进入多选模式（参考微信：长按消息→多选→顶部固定「删除」按钮）。
    // 状态在 AssistantScreen 顶层，跨 LazyColumn item 共享。messageMenuFor：长按单条弹菜单时锁定。
    val selectedIds = remember { mutableStateOf<Set<Long>>(emptySet()) }
    val selectionMode = selectedIds.value.isNotEmpty()
    // V2.8X++：记录每条消息在窗口中的包围盒，供长按弹框锚定到「选中行下方」（非底部弹层）。
    val messageRects = remember { mutableMapOf<Long, Rect>() }
    var messageMenuFor by remember { mutableStateOf<Long?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }
    val permissionDeniedMsg = stringResource(R.string.error_permission_record)

    val recordPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    // V2.8X++：连接生命周期已下沉到 ViewModel（跟随应用前后台，见 AssistantViewModel），
    // 切 tab 不再断连；此处仅负责「切离助手页」时停麦/停唤醒词，保留 WebSocket 连接避免重连卡顿。
    DisposableEffect(Unit) {
        onDispose { viewModel.onScreenHidden() }
    }
    // V2.8X++：仅当「新消息到达（size 增大）」才平滑动画；切回 tab 时不触发动画（初始位置已到底）。
    val prevMsgCount = remember { mutableIntStateOf(messages.size) }
    LaunchedEffect(messages.size) {
        if (messages.size > prevMsgCount.intValue) {
            listState.animateScrollToItem(messages.lastIndex)
        }
        prevMsgCount.intValue = messages.size
    }
    // V2.8X++：麦克风按下即时反馈 → 短 toast。
    LaunchedEffect(micToast) {
        micToast?.let {
            scope.launch {
                snackbarHostState.showTimedSnackbar(it)
                viewModel.consumeMicToast()
            }
        }
    }
    // V2.66 常驻唤醒：被前台服务唤醒跳转到助手页后，消费待处理标记并监听后续唤醒事件，自动开始收音。
    // V2.8X 修复：原来直接在 LaunchedEffect(Unit) 里 collect，只要助手页还在返回栈上（哪怕 App 已退到后台
    // 或屏幕已锁），唤醒事件仍会触发 startVoice —— 在后台静默开麦，既是隐私问题，
    // 也会在 Android 12+ 因无前台权限抛 SecurityException。现用 repeatOnLifecycle 绑定到 STARTED。
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
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
            snackbarHostState.showTimedSnackbar(message = permissionDeniedMsg)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // V2.8X++：连接状态仅靠顶部「未连接 / 重连中…」芯片呈现，不再单独弹红 banner，
        // 避免一进 tab 就被大块错误信息糊一脸（用户反馈：顶部有提示这个要去掉）。
        // 详细诊断信息仍走 AppLogger，可在「设置 → 诊断日志」中查看。
        topBar = {
            // V2.8X 顶栏单行标题：用 Box 强制 48dp 高度下垂直居中显示。
            // V2.8X++：多选模式下替换为「已选 N 项」+ 全选/取消/删除 顶栏（参考微信）。
            TopAppBar(
                modifier = Modifier.height(48.dp),
                title = {
                    Box(
                        modifier = Modifier.fillMaxHeight(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (selectionMode) {
                            Text(stringResource(R.string.assistant_selected_count, selectedIds.value.size))
                        } else {
                            Text(stringResource(R.string.assistant_screen_title))
                        }
                    }
                },
                navigationIcon = {
                    if (selectionMode) {
                        // 多选模式下返回按钮 = 退出多选
                        IconButton(onClick = { selectedIds.value = emptySet() }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_cancel))
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                },
                actions = {
                    if (selectionMode) {
                        // 全选 / 删除
                        val allSelected = selectedIds.value.size == messages.size && messages.isNotEmpty()
                        IconButton(onClick = {
                            selectedIds.value = if (allSelected) emptySet() else messages.map { it.id }.toSet()
                        }) {
                            Icon(
                                if (allSelected) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                                contentDescription = stringResource(R.string.action_select_all),
                            )
                        }
                    } else {
                        ConnectionChip(connected, reconnectingStatus)
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
                    }
                },
            )
        },
        bottomBar = {
            // V2.8X++：多选模式下不显示输入栏（避免与微信行为一致：多选时仅可删除，不能继续输入/录音）
            if (!selectionMode) {
                // V2.8X++：助手输入栏（参考微信风格）——单栏浅底 + 圆角文本输入框 + 右侧「麦克风 / 发送」二选一。
                // 输入为空显示麦克风按钮（点击录音、再点停止并发送）；输入非空显示蓝色「发送」胶囊按钮。
                // 高度收敛（约 52dp），比旧版全宽胶囊更轻巧；右侧二选一避免两个按钮挤占。
                // V2.8X++：单个圆角输入框（surfaceVariant 浅底），话筒内嵌于输入框内右侧。
                // 交互（微信式强交互，根治"回声"）：
                //  - 输入框获焦(键盘起) → 话筒隐藏，纯打字；失焦 → 话筒重现；
                //  - 未聚焦且未录音 → 右侧显示话筒：点一下开始持续聆听，AI 说话时点击即打断
                //    （startVoice 内含 abortTts + sendListenStart，服务器与设备双侧静音）；
                //  - 录音中 → 话筒变红色脉冲，再次点击停止并发送(stopVoice → 系统 ASR 终句/Opus 停听即上送)；
                //  - 聚焦且已输入文字 → 右侧显示蓝色发送图标(IME 回车亦可发送)。
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        enabled = !recording,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .height(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .padding(horizontal = 12.dp)
                            .onFocusChanged { isFocused = it.isFocused },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { submit(input, viewModel, { input = it }) { focusManager.clearFocus() } }),
                        decorationBox = { innerTextField ->
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    if (input.isEmpty()) {
                                        val hint = when {
                                            recording -> stringResource(R.string.assistant_voice_listening_hint)
                                            voiceOpening -> stringResource(R.string.assistant_mic_opening)
                                            else -> stringResource(R.string.assistant_input_placeholder)
                                        }
                                        Text(
                                            hint,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                    innerTextField()
                                }
                                // 右侧控制：录音中=停止并发送；聚焦且非空=发送图标；未聚焦=话筒(开始/打断)。
                                when {
                                    recording -> VoiceMicButton(
                                        opening = voiceOpening,
                                        recording = true,
                                        voiceSupported = voiceSupported,
                                        onClick = { viewModel.stopVoice() },
                                    )
                                    isFocused && input.isNotBlank() -> IconButton(
                                        onClick = { submit(input, viewModel, { input = it }) { focusManager.clearFocus() } },
                                        modifier = Modifier.size(36.dp),
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Send,
                                            contentDescription = stringResource(R.string.assistant_send),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    !isFocused -> VoiceMicButton(
                                        opening = voiceOpening,
                                        recording = false,
                                        voiceSupported = voiceSupported,
                                        onClick = {
                                            AppLogger.d(
                                                "AssistantScreen",
                                                "话筒点击 recording=$recording opening=$voiceOpening permStatus=${recordPermission.status.javaClass.simpleName}",
                                            )
                                            if (voiceOpening) return@VoiceMicButton
                                            val granted = ContextCompat.checkSelfPermission(
                                                appContext,
                                                Manifest.permission.RECORD_AUDIO,
                                            ) == PackageManager.PERMISSION_GRANTED ||
                                                recordPermission.status is PermissionStatus.Granted
                                            if (granted) {
                                                viewModel.startVoice()
                                            } else {
                                                pendingVoiceStart = true
                                                recordPermission.launchPermissionRequest()
                                            }
                                        },
                                    )
                                    // 聚焦且空：话筒隐藏（按需求），仅占位保持布局稳定。
                                    else -> Spacer(Modifier.size(36.dp))
                                }
                            }
                        },
                    )
                }
            } else {
                // V2.8X++：多选模式底部动作栏——显式「复制 / 删除」，解决此前仅有顶栏删除图标、无复制入口的问题。
                Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    ) {
                        // 复制：将选中消息文本按时间顺序拼接后写入剪贴板
                        TextButton(onClick = {
                            val sel = selectedIds.value
                            if (sel.isNotEmpty()) {
                                // 直接沿用 messages 的展示顺序（已按时间升序）；
                                // 不能再按 id 排序 —— 未落库的新消息 ID 为负（见 nextId），排序会错乱。
                                val texts = messages
                                    .filter { sel.contains(it.id) }
                                    .joinToString("\n\n") { it.text }
                                clipboard.setText(AnnotatedString(texts))
                                scope.launch { snackbarHostState.showTimedSnackbar(copiedTip) }
                            }
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_copy))
                        }
                        // 删除：复用二次确认弹窗（confirmClearAll）
                        TextButton(
                            onClick = { confirmClearAll = true },
                            enabled = selectedIds.value.isNotEmpty(),
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.assistant_msg_delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
    ) { padding ->
        // V2.8X++：点击消息列表等「输入与键盘以外」区域即收起键盘并失焦（微信式）。
        // 输入框在 bottomBar（本 Box 之外），点它不会触发此 clickable，故打字态不被误清。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { focusManager.clearFocus() },
        ) {
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
                    snackbarHostState = snackbarHostState,
                    selectionMode = selectionMode,
                    selectedIds = selectedIds.value,
                    onToggleSelect = { id ->
                        selectedIds.value = if (selectedIds.value.contains(id))
                            selectedIds.value - id else selectedIds.value + id
                    },
                    onLongPressMessage = { id -> messageMenuFor = id },
                    onMeasureRect = { id, rect -> messageRects[id] = rect },
                    modifier = Modifier.weight(1f).fillMaxSize(),
                )
                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp),
                )
                Column(
                    // 滚动交由 AssistantConfigContent 内部处理，外层不再嵌套同向 verticalScroll
                    modifier = Modifier
                        .widthIn(max = 440.dp)
                        .fillMaxHeight(),
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
                snackbarHostState = snackbarHostState,
                selectionMode = selectionMode,
                selectedIds = selectedIds.value,
                onToggleSelect = { id ->
                    selectedIds.value = if (selectedIds.value.contains(id))
                        selectedIds.value - id else selectedIds.value + id
                },
                    onLongPressMessage = { id -> messageMenuFor = id },
                    onMeasureRect = { id, rect -> messageRects[id] = rect },
                    modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
        } // Box close（点击失焦包裹层）
    }

    if (showConfig && !isWide) {
        AssistantConfigSheet(onDismiss = {
            showConfig = false
            viewModel.refreshConfigured()
        })
    }

    // V2.8X++：单条长按弹「复制/多选/删除」动作卡（系统消息不弹）。
    // 改为锚定在「选中行下方」的紧凑下拉（Popup + PopupPositionProvider），跟随主题色，
    // 高度较旧版再减约 1/3；下方空间不足时自动翻到行上方。Popup 自带点外部/返回键关闭。
    messageMenuFor?.let { id ->
        val msg = messages.firstOrNull { it.id == id }
        if (msg != null && msg.role != "system") {
            val copy = stringResource(R.string.action_copy)
            val selectMore = stringResource(R.string.assistant_msg_select_more)
            val deleteOne = stringResource(R.string.assistant_msg_delete)
            // clipboard / copiedTip 已在 AssistantScreen 顶层取值（onClick 非 @Composable 上下文，项目红线）。
            val deletedTip = stringResource(R.string.assistant_msg_deleted)
            val rect = messageRects[id]
            Popup(
                onDismissRequest = { messageMenuFor = null },
                popupPositionProvider = object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect,
                        windowSize: IntSize,
                        layoutDirection: LayoutDirection,
                        popupContentSize: IntSize,
                    ): IntOffset {
                        if (rect == null) return IntOffset.Zero
                        val x = rect.left.roundToInt()
                        var y = rect.bottom.roundToInt()
                        // 下方空间不足则翻到选中行上方
                        if (y + popupContentSize.height > windowSize.height) {
                            y = (rect.top - popupContentSize.height).roundToInt()
                        }
                        return IntOffset(x.coerceAtLeast(0), y.coerceAtLeast(0))
                    }
                },
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 3.dp,
                    shadowElevation = 2.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 4.dp, vertical = 3.dp)
                            .navigationBarsPadding(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        CompactMsgAction(
                            icon = Icons.Filled.Share,
                            label = copy,
                            onClick = {
                                clipboard.setText(AnnotatedString(msg.text))
                                messageMenuFor = null
                                scope.launch { snackbarHostState.showTimedSnackbar(copiedTip) }
                            },
                        )
                        CompactMsgAction(
                            icon = Icons.Filled.CheckBoxOutlineBlank,
                            label = selectMore,
                            onClick = {
                                selectedIds.value = selectedIds.value + msg.id
                                messageMenuFor = null
                            },
                        )
                        CompactMsgAction(
                            icon = Icons.Filled.Delete,
                            label = deleteOne,
                            tint = MaterialTheme.colorScheme.error,
                            onClick = {
                                viewModel.removeMessage(msg.id)
                                messageMenuFor = null
                                scope.launch { snackbarHostState.showTimedSnackbar(deletedTip) }
                            },
                        )
                    }
                }
            }
        } else {
            // 已被删除或为 system，直接清理菜单状态
            messageMenuFor = null
        }
    }

    // V2.8X++：多选模式下点删除按钮 → 二次确认 → 批量删
    if (confirmClearAll) {
        val count = selectedIds.value.size
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text(stringResource(R.string.assistant_msg_delete_confirm_title)) },
            text = { Text(stringResource(R.string.assistant_msg_delete_confirm_body, count)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearAll = false
                        val toDelete = selectedIds.value.toList()
                        toDelete.forEach { viewModel.removeMessage(it) }
                        selectedIds.value = emptySet()
                        scope.launch {
                            snackbarHostState.showTimedSnackbar(
                                appContext.getString(R.string.assistant_msg_deleted_count, toDelete.size),
                            )
                        }
                    },
                ) {
                    Text(stringResource(R.string.action_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/**
 * V2.8X++：语音麦克风按钮（FAB 风格）。三态视觉：
 * - 准备中（opening）：主色旋转环，提示「正在聆听…」，即时反馈避免卡顿体感；
 * - 录音中（recording）：红色容器 + 白色麦克风脉冲动画，文案「停止并发送」；
 * - 空闲：主色容器 + 麦克风；voiceSupported=false 时图标降透明度但仍可点（点击后由 ViewModel 给明确提示）。
 * 所有状态 44dp 触控目标，颜色取自 theme token 满足 WCAG AA 对比。
 */
@Composable
private fun VoiceMicButton(
    opening: Boolean,
    recording: Boolean,
    voiceSupported: Boolean,
    onClick: () -> Unit,
) {
    val infinite = rememberInfiniteTransition(label = "micPulse")
    val pulse by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "micPulseScale",
    )
    // V2.8X++：内嵌于输入框右侧——仅图标(无填充圆)，录音态转红脉冲，空闲态主色；
    // voiceSupported=false 时降透明度但仍可点（点击后由 ViewModel 给明确提示）。
    val tint = when {
        recording -> MaterialTheme.colorScheme.error
        !voiceSupported -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }
    val desc = when {
        recording -> stringResource(R.string.assistant_voice_stop)
        opening -> stringResource(R.string.assistant_mic_opening)
        else -> stringResource(R.string.assistant_mic_desc)
    }
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp),
    ) {
        when {
            opening -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            recording -> Icon(
                Icons.Filled.Mic,
                contentDescription = desc,
                tint = tint,
                modifier = Modifier.scale(pulse),
            )
            else -> Icon(
                Icons.Filled.Mic,
                contentDescription = desc,
                tint = tint,
            )
        }
    }
}

private fun submit(
    text: String,
    viewModel: AssistantViewModel,
    clear: (String) -> Unit,
    clearFocus: () -> Unit,
) {
    viewModel.sendText(text)
    clear("")
    // 发送后收起键盘并失去焦点（微信式：输入栏回到「话筒态」）。
    clearFocus()
}

/**
 * 助手对话主体（窄屏整屏 / 宽屏左栏复用）：草稿确认卡 + 对话列表；
 * 未配置服务商且非宽屏时展示引导插画与「去配置」按钮（V2.20）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AssistantChatBody(
    isWide: Boolean,
    configured: Boolean,
    messages: List<ChatMessage>,
    pendingDraft: Task?,
    viewModel: AssistantViewModel,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onConfigure: () -> Unit,
    snackbarHostState: SnackbarHostState,
    selectionMode: Boolean,
    selectedIds: Set<Long>,
    onToggleSelect: (Long) -> Unit,
    onLongPressMessage: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onMeasureRect: (Long, Rect) -> Unit = { _, _ -> },
) {
    // V2.8X++ 闪退兜底：LazyColumn 的 item key 一旦重复会直接抛 IllegalArgumentException，
    // 且异常在 Compose 组合阶段抛出，ViewModel 侧的 runCatching 一概拦不住 → 整个 App 闪退。
    // ID 已在 ViewModel 按「内存负数 / 库正数」分区隔离（见 AssistantViewModel.nextId），
    // 此处再按 id 去重做最后一道防线：任何数据异常最多少展示一条，绝不闪退。
    val renderMessages = remember(messages) { messages.distinctBy { it.id } }
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
        } else if (renderMessages.isEmpty()) {
            // V2.8X++：已配置但消息列表为空时的引导 —— 之前直接渲染空 LazyColumn，
            // 新用户进 tab 一片空白不知从哪下手。宽屏亦展示，避免双栏右侧留白。
            EmptyStateGuide(
                icon = "💬",
                title = stringResource(R.string.assistant_empty_title),
                message = stringResource(R.string.assistant_empty_desc),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(renderMessages, key = { it.id }) { msg ->
                    MessageRow(
                        msg = msg,
                        selectionMode = selectionMode,
                        selected = selectedIds.contains(msg.id),
                        onClick = {
                            // 多选模式下：点击切换选中（参考微信）
                            if (selectionMode && msg.role != "system") onToggleSelect(msg.id)
                        },
                        onLongClick = {
                            if (msg.role == "system") return@MessageRow
                            if (!selectionMode) onLongPressMessage(msg.id)
                            else onToggleSelect(msg.id)
                        },
                        onMeasureRect = onMeasureRect,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionChip(connected: Boolean, reconnecting: String? = null) {
    val statusText = if (reconnecting != null) reconnecting
    else stringResource(
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
                    when {
                        reconnecting != null -> MaterialTheme.colorScheme.tertiary
                        connected -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outline
                    },
                ),
        )
        Text(
            text = statusText,
            style = MaterialTheme.typography.labelSmall,
            color = if (reconnecting != null) MaterialTheme.colorScheme.tertiary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatBubble(
    msg: ChatMessage,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onMeasureRect: (Long, Rect) -> Unit = { _, _ -> },
) {
    val isUser = msg.role == "user"
    val isSystem = msg.role == "system"
    // V2.8X++：长按（多选/单条菜单）+ 可选点击（多选模式切换选中）。系统消息不可点击/长按。
    val clickModifier = when {
        isSystem -> Modifier
        onClick != null || onLongClick != null -> Modifier.combinedClickable(onClick = onClick ?: {}, onLongClick = onLongClick)
        else -> Modifier
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                val pos = coords.localToWindow(Offset.Zero)
                val size = coords.size
                onMeasureRect(
                    msg.id,
                    Rect(pos.x, pos.y, pos.x + size.width.toFloat(), pos.y + size.height.toFloat()),
                )
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        // V2.8X++：多选模式在每条左侧加 checkbox 指示（用户/助手可选，系统不可选）
        if (selectionMode && !isSystem) {
            Icon(
                if (selected) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp).size(20.dp),
            )
        }
        if (isSystem) {
            Box(
                modifier = clickModifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                // V2.8X++：系统消息文字也支持直接选择复制（与用户/助手一致）。
                SelectionContainer { Text(msg.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer) }
            }
        } else {
            Box(
                modifier = clickModifier
                    .fillMaxWidth(0.8f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        when {
                            selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                            isUser -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                // V2.8X++：把 Text 包进 SelectionContainer —— 长按文字直接走系统文本选择菜单
                // （复制/全选），与外部气泡 onLongClick 的「复制/删除/多选」菜单并存：
                //   - 长按文字本身 → 系统文本选择（复制更快）
                //   - 长按气泡留白 → 自定义菜单（含复制/删除/多选，保留历史行为）
                SelectionContainer {
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
}

/**
 * V2.8X++：单条消息容器。**去除 SwipeToDismissBox 左滑删除**（参考微信），
 * 改为长按单条弹「复制/多选/删除」菜单，长按多选进入选中模式。
 * 系统消息不暴露任何手势（避免误删服务端状态回执）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(
    msg: ChatMessage,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMeasureRect: (Long, Rect) -> Unit = { _, _ -> },
) {
    ChatBubble(
        msg = msg,
        selectionMode = selectionMode,
        selected = selected,
        onClick = onClick,
        onLongClick = onLongClick,
        onMeasureRect = onMeasureRect,
    )
}

/**
 * V2.8X++：长按弹框内单个动作项（微信风格：图标在上、标签在下，56dp 触控目标，胶囊形涟漪）。
 * tint 不传则用 inverseOnSurface（白色），传了则用其值（删除用 error 红色）。
 * Row 内以 Column 形态居中展示，列内 padding 给出点击命中区。
 */
@Composable
private fun CompactMsgAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = LocalContentColor.current,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .widthIn(min = 44.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
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
