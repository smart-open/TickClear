package com.tickclear.app.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tickclear.app.R
import com.tickclear.app.domain.repository.SettingsRepository
import com.tickclear.app.domain.assistant.AsrProviderCatalog
import com.tickclear.app.domain.assistant.AsrProviderResolver
import com.tickclear.app.domain.assistant.AudioCapture
import com.tickclear.app.domain.assistant.LocalSpeechRecognizer
import com.tickclear.app.domain.assistant.OfflineAction
import com.tickclear.app.domain.assistant.OfflineCommand
import com.tickclear.app.domain.assistant.OfflineCommandRecognizer
import com.tickclear.app.domain.assistant.LlmProviderCatalog
import com.tickclear.app.domain.assistant.LlmProviderResolver
import com.tickclear.app.domain.assistant.OpusCodec
import com.tickclear.app.domain.assistant.WakeWordManager
import com.tickclear.app.domain.assistant.WavUtil
import com.tickclear.app.domain.assistant.XiaozhiEvent
import com.tickclear.app.domain.assistant.XiaozhiMcpTools
import com.tickclear.app.domain.assistant.XiaozhiTransport
import com.tickclear.app.domain.assistant.TaskIntentParser
import com.tickclear.app.domain.model.AppException
import com.tickclear.app.domain.model.ErrorCode
import com.tickclear.app.data.local.entities.VoiceHistoryEntity
import com.tickclear.app.domain.model.Task
import com.tickclear.app.domain.repository.TaskRepository
import com.tickclear.app.domain.repository.VoiceHistoryRepository
import com.tickclear.app.domain.usecase.SoftDeleteTaskUseCase
import com.tickclear.app.domain.usecase.UpdateTaskUseCase
import com.tickclear.app.domain.usecase.ApplyOfflineCommandUseCase
import com.tickclear.app.domain.usecase.OfflineCommandResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatMessage(
    val id: Long,
    val role: String, // "user" | "assistant" | "system"
    val text: String,
    val taskCreated: Boolean = false,
)

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val transport: XiaozhiTransport,
    private val mcpTools: XiaozhiMcpTools,
    private val settingsRepository: SettingsRepository,
    private val opusCodec: OpusCodec,
    private val llmResolver: LlmProviderResolver,
    private val asrResolver: AsrProviderResolver,
    private val taskRepository: TaskRepository,
    private val updateTaskUseCase: UpdateTaskUseCase,
    private val softDeleteTaskUseCase: SoftDeleteTaskUseCase,
    private val applyOfflineCommand: ApplyOfflineCommandUseCase,
    private val voiceHistoryRepository: VoiceHistoryRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    /** V2.18：最近一次经助手创建的任务 id，作为「改时间/改重复/取消」多轮编辑的目标。 */
    @Volatile private var lastCreatedTaskId: String? = null

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList(),
    )

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false,
    )

    /** V2.20：助手是否已配置可用（据此展示未配置引导）。 */
    private val _configured = MutableStateFlow(false)
    val configured: StateFlow<Boolean> = _configured.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false,
    )

    /** 是否支持语音输入：真实小智(Opus) 或 任一文件式云 ASR 或 系统本地识别。 */
    private val _voiceSupported = MutableStateFlow(false)
    val voiceSupported: StateFlow<Boolean> = _voiceSupported.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false,
    )

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false,
    )

    /** 唤醒词监听是否激活。 */
    private val _wakeWordActive = MutableStateFlow(false)
    val wakeWordActive: StateFlow<Boolean> = _wakeWordActive.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false,
    )

    /** 语音/文本解析出的待确认草稿任务（确认卡）。null 表示无待确认项。 */
    private val _pendingDraft = MutableStateFlow<Task?>(null)
    val pendingDraft: StateFlow<Task?> = _pendingDraft.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), null,
    )

    private val capture = AudioCapture()
    private val localRecognizer = LocalSpeechRecognizer(appContext)
    private val wakeWordManager = WakeWordManager(appContext, settingsRepository)
    private var seq = 0L
    private fun nextId() = ++seq

    /** 语音历史开关缓存：避免每条消息都读一次 DataStore（V2.65 优化）。 */
    private val voiceHistoryOn = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            settingsRepository.voiceHistoryEnabled.collect { voiceHistoryOn.value = it }
        }
        viewModelScope.launch {
            transport.events.collect { onEvent(it) }
        }
        viewModelScope.launch {
            val mode = settingsRepository.assistantMode.first()
            val llm = settingsRepository.llmProvider.first()
            val asr = settingsRepository.asrProvider.first()
            val cloudAsrReady = (llm != LlmProviderCatalog.XIAOZHI) && asrSupportsVoice(asr) && asrCredentialsPresent(asr)
            _voiceSupported.value = ((llm == LlmProviderCatalog.XIAOZHI) && (mode == "REAL") && opusCodec.isEncoderAvailable())
                || cloudAsrReady
        }
    }

    private fun asrSupportsVoice(asr: String): Boolean = when (asr) {
        AsrProviderCatalog.SYSTEM -> localRecognizer.isAvailable
        AsrProviderCatalog.XIAOZHI -> false
        else -> true // openai / tencent / aliyun 均为文件式上传
    }

    /** 文件式云 ASR 的凭据是否齐备（§7.5.9 门控表：无凭据不应展示可用麦克风）。 */
    private suspend fun asrCredentialsPresent(asr: String): Boolean = when (asr) {
        AsrProviderCatalog.OPENAI -> !settingsRepository.getAsrApiKey().isNullOrBlank()
        AsrProviderCatalog.TENCENT ->
            !settingsRepository.getTencentSecretId().isNullOrBlank() && !settingsRepository.getTencentSecretKey().isNullOrBlank()
        AsrProviderCatalog.ALIYUN ->
            !settingsRepository.getAliyunAccessKeyId().isNullOrBlank()
                && !settingsRepository.getAliyunAccessKeySecret().isNullOrBlank()
                && !settingsRepository.getAliyunAppKey().isNullOrBlank()
        else -> true
    }

    fun connect() {
        viewModelScope.launch {
            _configured.value = computeConfigured()
            val llm = settingsRepository.llmProvider.first()
            if (llm == LlmProviderCatalog.XIAOZHI) {
                val prompt = settingsRepository.assistantPrompt.first()
                transport.connect(prompt)
            } else {
                // OpenAI 兼容系（含豆包/通义千问）为请求/响应文本通道，无长连接。
                _connected.value = true
            }
        }
    }

    fun disconnect() {
        if (_recording.value) stopVoice()
        stopWakeWord()
        viewModelScope.launch {
            val llm = settingsRepository.llmProvider.first()
            if (llm == LlmProviderCatalog.XIAOZHI) transport.disconnect()
            _connected.value = false
        }
    }

    /** V2.20：依据当前设置判断助手是否可正常工作（未配置时展示引导）。 */
    private suspend fun computeConfigured(): Boolean {
        val llm = settingsRepository.llmProvider.first()
        return if (llm == LlmProviderCatalog.XIAOZHI) {
            val mode = settingsRepository.assistantMode.first()
            mode == "MOCK" || settingsRepository.assistantEndpoint.first().isNotBlank()
        } else {
            settingsRepository.llmBaseUrl.first().isNotBlank()
                && !settingsRepository.getLlmApiKey(llm).isNullOrBlank()
        }
    }

    /** V2.20：配置面板保存后刷新「已配置」状态，及时收起未配置引导。 */
    fun refreshConfigured() {
        viewModelScope.launch { _configured.value = computeConfigured() }
    }

    /** 开始语音采集：根据 ASR 服务商路由到系统实时识别或整段 PCM 累积（云 ASR）。 */
    fun startVoice() {
        if (!_voiceSupported.value || _recording.value) return
        viewModelScope.launch {
            val asr = settingsRepository.asrProvider.first()
            if (asr == AsrProviderCatalog.SYSTEM) {
                _recording.value = true
                val lang = settingsRepository.asrLanguage.first()
                localRecognizer.start(
                    continuous = false,
                    language = lang,
                    onPartial = { /* 系统识别仅以终句为准提交 */ },
                    onFinal = { text ->
                        _recording.value = false
                        if (text.isNotBlank()) sendText(text) else append(
                            ChatMessage(nextId(), "system", appContext.getString(R.string.assistant_asr_empty)),
                        )
                    },
                )
            } else {
                // V2.58：云 ASR 路径先边录边写裸 PCM 临时文件，停止时回调该文件，避免整段驻留内存。
                val pcmFile = File(appContext.cacheDir, "asr_upload.pcm")
                val ok = capture.startAccumulate(pcmFile = pcmFile) { handleCloudAsr(it) }
                if (!ok) {
                    runCatching { pcmFile.delete() }
                    _voiceSupported.value = false
                    return@launch
                }
                _recording.value = true
                if (settingsRepository.llmProvider.first() == LlmProviderCatalog.XIAOZHI) transport.sendListenStart()
            }
        }
    }

    /** 停止语音采集。云 ASR 路径下，停止会触发转写；系统路径下停止实时识别。 */
    fun stopVoice() {
        if (!_recording.value) return
        viewModelScope.launch {
            val isSystem = settingsRepository.asrProvider.first() == AsrProviderCatalog.SYSTEM
            if (isSystem) localRecognizer.stop() else capture.stop()
            _recording.value = false
            if (settingsRepository.llmProvider.first() == LlmProviderCatalog.XIAOZHI) transport.sendListenStop()
        }
    }

    /** 云 ASR 路径：裸 PCM 文件 → 流式封装 WAV → 转写 → 作为文本发送；失败回显错误。 */
    private fun handleCloudAsr(pcmFile: File) {
        viewModelScope.launch {
            val wav = File(appContext.cacheDir, "asr_upload.wav")
            runCatching {
                WavUtil.writePcmFromFile(pcmFile, wav)
                val provider = asrResolver.resolve()
                    ?: throw AppException(ErrorCode.ASSISTANT_NOT_CONFIGURED, detail = "ASR 服务商未配置")
                provider.transcribe(wav)
            }.onSuccess { text ->
                if (text.isNotBlank()) sendText(text) else append(
                    ChatMessage(nextId(), "system", appContext.getString(R.string.assistant_asr_empty)),
                )
            }.onFailure { e ->
                append(
                    ChatMessage(
                        nextId(), "system",
                        AppException.from(e, ErrorCode.ASSISTANT_CONNECT_FAILED).userMessage(appContext),
                    ),
                )
            }
            runCatching { wav.delete() }
            runCatching { pcmFile.delete() }
        }
    }

    fun sendText(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        append(ChatMessage(nextId(), "user", t))
        viewModelScope.launch {
            // V2.42：离线热词指令（暂停/启用/删除 + 任务名）。开启且识别为已知指令即本地闭环执行，
            // 不再送 LLM；任务名需匹配真实任务，删除仅命中才生效，避免误删。
            if (settingsRepository.offlineCommandEnabled.first()) {
                val cmd = OfflineCommandRecognizer.parse(t)
                if (cmd !is OfflineCommand.Unknown) {
                    val tasks = taskRepository.observeAll().first()
                    val result = applyOfflineCommand(cmd, tasks)
                    val msg = when (result) {
                        is OfflineCommandResult.Applied ->
                            appContext.getString(
                                R.string.offline_cmd_applied,
                                when (result.action) {
                                    OfflineAction.PAUSE -> appContext.getString(R.string.offline_action_pause)
                                    OfflineAction.RESUME -> appContext.getString(R.string.offline_action_resume)
                                    OfflineAction.DELETE -> appContext.getString(R.string.offline_action_delete)
                                },
                                result.task.title,
                            )
                        is OfflineCommandResult.NotFound -> {
                            val kw = when (cmd) {
                                is OfflineCommand.Pause -> cmd.keyword
                                is OfflineCommand.Resume -> cmd.keyword
                                is OfflineCommand.Delete -> cmd.keyword
                                is OfflineCommand.Unknown -> null
                            }
                            appContext.getString(R.string.offline_cmd_not_found, kw.orEmpty())
                        }
                        is OfflineCommandResult.NoTarget ->
                            appContext.getString(R.string.offline_cmd_no_target)
                        // 不可达：外层已用 cmd !is Unknown 守卫，applyOfflineCommand 不会返回 Unknown；
                        // 即便理论到达也直接结束本次处理，避免追加空消息触发无意义重组。
                        is OfflineCommandResult.Unknown -> return@launch
                    }
                    append(ChatMessage(nextId(), "system", msg))
                    return@launch
                }
            }
            // V2.18 多轮编辑：若存在「刚创建的任务」且本句是编辑指令，则本地闭环处理，不再送 LLM。
            if (lastCreatedTaskId != null && tryHandleEdit(t)) return@launch
            val llm = settingsRepository.llmProvider.first()
            if (llm == LlmProviderCatalog.XIAOZHI) {
                transport.sendText(t)
                return@launch
            }
            val provider = llmResolver.resolve()
            if (provider == null) {
                append(ChatMessage(nextId(), "system", appContext.getString(R.string.assistant_provider_unknown)))
                return@launch
            }
            runCatching {
                provider.chat(settingsRepository.assistantPrompt.first(), t)
            }.onSuccess { reply ->
                append(ChatMessage(nextId(), "assistant", reply))
            }.onFailure { e ->
                append(
                    ChatMessage(
                        nextId(),
                        "system",
                        AppException.from(e, ErrorCode.ASSISTANT_CONNECT_FAILED).userMessage(appContext),
                    ),
                )
            }
        }
    }

    /** 启动唤醒词持续监听（命中后自动开始语音输入）。 */
    fun startWakeWord() {
        viewModelScope.launch {
            val enabled = settingsRepository.wakeWordEnabled.first()
            if (!enabled) return@launch
            if (!wakeWordManager.isAvailable) {
                append(ChatMessage(nextId(), "system", appContext.getString(R.string.assistant_wakeword_unavailable)))
                return@launch
            }
            _wakeWordActive.value = true
            wakeWordManager.start {
                _wakeWordActive.value = false
                append(ChatMessage(nextId(), "system", appContext.getString(R.string.assistant_wakeword_triggered)))
                startVoice()
            }
        }
    }

    /** 停止唤醒词监听。 */
    fun stopWakeWord() {
        if (!_wakeWordActive.value) return
        wakeWordManager.stop()
        _wakeWordActive.value = false
    }

    private fun onEvent(ev: XiaozhiEvent) {
        when (ev) {
            is XiaozhiEvent.Connected -> _connected.value = true
            is XiaozhiEvent.Reconnecting -> append(
                ChatMessage(nextId(), "system", appContext.getString(R.string.assistant_reconnecting, ev.attempt, ev.max)),
            )
            is XiaozhiEvent.Disconnected -> _connected.value = false
            is XiaozhiEvent.Error -> {
                _connected.value = false
                append(ChatMessage(nextId(), "system", appContext.getString(R.string.assistant_connect_error, ev.detail)))
            }
            is XiaozhiEvent.SttText -> Unit // 用户输入已在 sendText 体现
            is XiaozhiEvent.TtsText -> Unit // 界面无需展示 TTS
            is XiaozhiEvent.LlmText -> append(ChatMessage(nextId(), "assistant", ev.text))
            is XiaozhiEvent.McpToolCall -> handleTool(ev)
            is XiaozhiEvent.McpToolResult -> append(
                ChatMessage(nextId(), "system", ev.message, taskCreated = ev.taskCreated),
            )
        }
    }

    private fun handleTool(call: XiaozhiEvent.McpToolCall) {
        viewModelScope.launch {
            val draft = mcpTools.handle(call)
            if (draft == null) {
                append(ChatMessage(nextId(), "system", appContext.getString(R.string.assistant_draft_unknown)))
                return@launch
            }
            // 信任模式（PRD D20）：非危险操作（建任务）免去确认卡直接落库；
            // 删除/暂停等危险操作无论是否信任都强制二次确认，避免误删。
            if (settingsRepository.trustMode.first() && !isDangerousTool(call.tool)) {
                val res = mcpTools.commit(draft)
                if (res.ok) lastCreatedTaskId = draft.id // V2.18：记录多轮编辑目标
                append(ChatMessage(nextId(), "system", res.message, taskCreated = res.ok))
            } else {
                _pendingDraft.value = draft
                append(ChatMessage(nextId(), "system", appContext.getString(R.string.assistant_draft_pending)))
            }
        }
    }

    /** 危险操作（删除/暂停/恢复/更新等）即便在信任模式下也强制确认。 */
    private fun isDangerousTool(tool: String): Boolean =
        tool in setOf("delete_task", "pause_task", "resume_task", "update_task", "remove_task")

    /** 确认创建草稿任务（语音解析确认卡「确认」）。 */
    fun confirmDraft() {
        val draft = _pendingDraft.value ?: return
        viewModelScope.launch {
            val res = mcpTools.commit(draft)
            if (res.ok) lastCreatedTaskId = draft.id // V2.18：记录多轮编辑目标
            append(ChatMessage(nextId(), "system", res.message, taskCreated = res.ok))
            _pendingDraft.value = null
        }
    }

    /**
     * V2.18 多轮任务编辑：尝试把 [text] 解析为对最近创建任务的「改时间/改重复/取消」指令。
     * 返回 true 表示已本地闭环处理（调用方不再路由到 LLM）。
     */
    private suspend fun tryHandleEdit(text: String): Boolean {
        val edit = TaskIntentParser.parseEdit(text) ?: return false
        val id = lastCreatedTaskId ?: return false
        val task = taskRepository.getById(id)
        if (task == null) {
            lastCreatedTaskId = null
            return false
        }
        when (edit) {
            is TaskIntentParser.ParsedEdit.Cancel -> {
                softDeleteTaskUseCase(id)
                lastCreatedTaskId = null
                append(ChatMessage(nextId(), "system", appContext.getString(R.string.assistant_edit_cancelled, task.title)))
            }
            is TaskIntentParser.ParsedEdit.ChangeTime -> {
                val updated = task.copy(
                    scheduledDate = edit.dateStr ?: task.scheduledDate,
                    scheduledStartMin = edit.minute ?: task.scheduledStartMin,
                )
                val conflicts = updateTaskUseCase(updated)
                val note = if (conflicts.isNotEmpty()) appContext.getString(R.string.assistant_edit_conflict_note) else ""
                append(ChatMessage(nextId(), "system", appContext.getString(R.string.assistant_edit_time_ok, task.title) + note))
            }
            is TaskIntentParser.ParsedEdit.ChangeRepeat -> {
                val updated = task.copy(
                    repeatType = edit.repeatType,
                    repeatWeekdays = edit.weekdays,
                    // 重复任务不留一次性日期；改回不重复时若原无日期则回落到今天。
                    scheduledDate = if (edit.repeatType == "NONE") {
                        task.scheduledDate ?: java.time.LocalDate.now().toString()
                    } else {
                        null
                    },
                )
                val conflicts = updateTaskUseCase(updated)
                val note = if (conflicts.isNotEmpty()) appContext.getString(R.string.assistant_edit_conflict_note) else ""
                append(ChatMessage(nextId(), "system", appContext.getString(R.string.assistant_edit_repeat_ok, task.title) + note))
            }
        }
        return true
    }

    /** 取消草稿任务（语音解析确认卡「取消」）。 */
    fun dismissDraft() {
        if (_pendingDraft.value == null) return
        _pendingDraft.value = null
        append(ChatMessage(nextId(), "system", appContext.getString(R.string.assistant_draft_cancelled)))
    }

    /** V2.57：长会话消息上限，超过则丢弃最旧，避免 messages 随会话无限增长导致内存单调上升。 */
    private companion object {
        private const val MAX_MESSAGES = 100
    }

    private fun append(msg: ChatMessage) {
        _messages.update { list ->
            val next = list + msg
            if (next.size > MAX_MESSAGES) next.takeLast(MAX_MESSAGES) else next
        }
        recordVoiceHistory(msg)
    }

    /**
     * V2.65 语音历史：仅当设置开启（默认关闭）时，将用户/助手对话文本落库；系统提示不记录。
     * 失败静默（历史保存非核心链路，不应阻断对话）。
     */
    private fun recordVoiceHistory(msg: ChatMessage) {
        if (msg.role != "user" && msg.role != "assistant") return
        // 复用缓存开关，避免每条消息都触发一次 DataStore 读取（最多 100 条/会话）。
        if (!voiceHistoryOn.value) return
        viewModelScope.launch {
            runCatching {
                voiceHistoryRepository.insert(
                    VoiceHistoryEntity(
                        createdAt = System.currentTimeMillis(),
                        role = msg.role,
                        text = msg.text,
                    ),
                )
            }
        }
    }

    override fun onCleared() {
        // capture.stop() 内含 thread.join()，onCleared 运行于主线程，移出到独立线程避免阻塞 UI（ANR 风险）。
        val capturing = _recording.value
        Thread {
            if (capturing) capture.stop()
            wakeWordManager.stop()
            localRecognizer.stop()
        }.apply { isDaemon = true }.start()
        // opusCodec 为 @Singleton 共享资源，随进程存活并跨会话复用，不由屏幕级 VM 释放（生命周期归属修正）。
        // transport 同为 @Singleton，生命周期长于本屏幕级 VM；disconnect 必须跑在与 viewModelScope 无关的作用域，
        // 否则 super.onCleared() 取消 viewModelScope 后该协程永不执行，导致 WebSocket/AudioTrack/scope 泄露，
        // 且再次进入助手时 transport.connect() 因 connected==true 直接 return（连接/重连失效）。
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch { transport.disconnect() }
        super.onCleared()
    }
}
