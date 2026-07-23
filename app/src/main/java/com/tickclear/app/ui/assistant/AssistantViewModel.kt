package com.tickclear.app.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tickclear.app.R
import com.tickclear.app.data.repositories.SettingsRepository
import com.tickclear.app.domain.assistant.AsrProviderCatalog
import com.tickclear.app.domain.assistant.AsrProviderResolver
import com.tickclear.app.domain.assistant.AudioCapture
import com.tickclear.app.domain.assistant.LocalSpeechRecognizer
import com.tickclear.app.domain.assistant.LlmProviderCatalog
import com.tickclear.app.domain.assistant.LlmProviderResolver
import com.tickclear.app.domain.assistant.OpusCodec
import com.tickclear.app.domain.assistant.WakeWordManager
import com.tickclear.app.domain.assistant.WavUtil
import com.tickclear.app.domain.assistant.XiaozhiEvent
import com.tickclear.app.domain.assistant.XiaozhiMcpTools
import com.tickclear.app.domain.assistant.XiaozhiTransport
import com.tickclear.app.domain.model.AppException
import com.tickclear.app.domain.model.ErrorCode
import com.tickclear.app.data.local.entities.TaskEntity
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
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList(),
    )

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.stateIn(
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
    private val _pendingDraft = MutableStateFlow<TaskEntity?>(null)
    val pendingDraft: StateFlow<TaskEntity?> = _pendingDraft.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), null,
    )

    private val capture = AudioCapture()
    private val localRecognizer = LocalSpeechRecognizer(appContext)
    private val wakeWordManager = WakeWordManager(appContext, settingsRepository)
    private var seq = 0L
    private fun nextId() = ++seq

    init {
        viewModelScope.launch {
            transport.events.collect { onEvent(it) }
        }
        viewModelScope.launch {
            val mode = settingsRepository.assistantMode.first()
            val llm = settingsRepository.llmProvider.first()
            val asr = settingsRepository.asrProvider.first()
            _voiceSupported.value = ((llm == LlmProviderCatalog.XIAOZHI) && (mode == "REAL") && opusCodec.isEncoderAvailable())
                || ((llm != LlmProviderCatalog.XIAOZHI) && asrSupportsVoice(asr))
        }
    }

    private fun asrSupportsVoice(asr: String): Boolean = when (asr) {
        AsrProviderCatalog.SYSTEM -> localRecognizer.isAvailable
        AsrProviderCatalog.XIAOZHI -> false
        else -> true // openai / tencent / aliyun 均为文件式上传
    }

    fun connect() {
        viewModelScope.launch {
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

    /** 开始语音采集：根据 ASR 服务商路由到系统实时识别或整段 PCM 累积（云 ASR）。 */
    fun startVoice() {
        if (!_voiceSupported.value || _recording.value) return
        viewModelScope.launch {
            val asr = settingsRepository.asrProvider.first()
            if (asr == AsrProviderCatalog.SYSTEM) {
                _recording.value = true
                localRecognizer.start(
                    continuous = false,
                    onPartial = { /* 系统识别仅以终句为准提交 */ },
                    onFinal = { text ->
                        _recording.value = false
                        if (text.isNotBlank()) sendText(text) else append(
                            ChatMessage(nextId(), "system", appContext.getString(R.string.assistant_asr_empty)),
                        )
                    },
                )
            } else {
                val ok = capture.startAccumulate { pcm -> handleCloudAsr(pcm) }
                if (!ok) {
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
        if (settingsRepository.asrProvider.first() == AsrProviderCatalog.SYSTEM) {
            localRecognizer.stop()
        } else {
            capture.stop()
        }
        _recording.value = false
        viewModelScope.launch {
            if (settingsRepository.llmProvider.first() == LlmProviderCatalog.XIAOZHI) transport.sendListenStop()
        }
    }

    /** 云 ASR 路径：整段 PCM → WAV 临时文件 → 转写 → 作为文本发送；失败回显错误。 */
    private fun handleCloudAsr(pcm: ByteArray) {
        viewModelScope.launch {
            val wav = File(appContext.cacheDir, "asr_upload.wav")
            runCatching {
                WavUtil.writePcm(pcm, wav)
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
        }
    }

    fun sendText(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        append(ChatMessage(nextId(), "user", t))
        viewModelScope.launch {
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
            is XiaozhiEvent.Disconnected -> _connected.value = false
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
            if (draft != null) {
                _pendingDraft.value = draft
                append(ChatMessage(nextId(), "system", appContext.getString(R.string.assistant_draft_pending)))
            } else {
                append(ChatMessage(nextId(), "system", appContext.getString(R.string.assistant_draft_unknown)))
            }
        }
    }

    /** 确认创建草稿任务（语音解析确认卡「确认」）。 */
    fun confirmDraft() {
        val draft = _pendingDraft.value ?: return
        viewModelScope.launch {
            val res = mcpTools.commit(draft)
            append(ChatMessage(nextId(), "system", res.message, taskCreated = res.ok))
            _pendingDraft.value = null
        }
    }

    /** 取消草稿任务（语音解析确认卡「取消」）。 */
    fun dismissDraft() {
        if (_pendingDraft.value == null) return
        _pendingDraft.value = null
        append(ChatMessage(nextId(), "system", appContext.getString(R.string.assistant_draft_cancelled)))
    }

    private fun append(msg: ChatMessage) {
        _messages.update { it + msg }
    }

    override fun onCleared() {
        if (_recording.value) capture.stop()
        wakeWordManager.stop()
        opusCodec.release()
        viewModelScope.launch { transport.disconnect() }
        super.onCleared()
    }
}
