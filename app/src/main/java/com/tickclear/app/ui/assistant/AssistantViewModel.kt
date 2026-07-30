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
import com.tickclear.app.domain.assistant.MessageTextFilter
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
import com.tickclear.app.domain.log.AppLogger
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

    /** V2.8X++：启动期连接诊断（握手超时/ws-rejected 等），顶栏 banner 展示，不进消息流。 */
    private val _connectionBanner = MutableStateFlow<String?>(null)
    val connectionBanner: StateFlow<String?> = _connectionBanner.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), null,
    )

    fun dismissConnectionBanner() {
        _connectionBanner.value = null
    }

    /** V2.8X++：重连进度文本，仅供顶部状态栏展示，绝不进入消息流（避免重连刷屏）。 */
    private val _reconnectingStatus = MutableStateFlow<String?>(null)
    val reconnectingStatus: StateFlow<String?> = _reconnectingStatus.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), null,
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

    /**
     * V2.8X++：本轮已展示的用户文本（用于来自发送路径的 `sendText`）。
     * - 本地 ASR / 云 ASR / 文本输入：走 `sendText` → 已展示为 user 气泡；服务端回 stt 时按此 dedup。
     * - 真·Opus 语音流：跳过 `sendText`，flag 保持 null，stt 帧来时直接落用户气泡。
     * 每次新轮开始（`startVoice` 进入）+ LlmText 首 token + SttText 处理完成 均清空，避免跨轮污染。
     */
    private var userSaidThisTurn: String? = null

    /** V2.8X++：麦克风按下的即时反馈（避免「按下去没反应」的哑失败）。 */
    private val _micToast = MutableStateFlow<String?>(null)
    val micToast: StateFlow<String?> = _micToast.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), null,
    )

    /**
     * V2.8X++：语音「准备中」状态（用户已点击但还未真正进入聆听）。UI 用内联指示器呈现，
     * 取代原先的 Snackbar 占位——旧实现把「正在打开麦克风…」当 Short Snackbar 弹出，
     * 与后续的「开始聆听」 snackbar 排队，导致"正在打开"至少挂 4 秒的体感卡顿。
     */
    private val _voiceOpening = MutableStateFlow(false)
    val voiceOpening: StateFlow<Boolean> = _voiceOpening.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false,
    )

    fun consumeMicToast() {
        _micToast.value = null
    }

    init {
        viewModelScope.launch {
            settingsRepository.voiceHistoryEnabled.collect { voiceHistoryOn.value = it }
        }
        // V2.8X++：从 voice_history 库加载历史对话作为 _messages 初值 —— 之前只存内存，
        // 切到其他 tab / 杀进程后消息全丢。加载完后正向时间排序（dao.observeAll 按 createdAt DESC，
        // 内存展示需 ASC；reversed() 在小数据集上 OK，未来量大再换 SQL ASC）。
        viewModelScope.launch {
            val initial = voiceHistoryRepository.observeAll().first().reversed()
            // V2.8X++：清洗历史数据 —
            //   ① 重连提示残留（旧版本曾插入消息流，已在 onEvent 修源头为仅更新 _reconnectingStatus，
            //     但 DB 里老消息还在），按前缀识别丢弃；
            //   ② 多模态 token（@image#xxx）残留（旧消息写入时尚未加 MessageTextFilter），
            //     加载时再 strip 一次与新消息处理一致。
            val cleaned = initial
                .filterNot { isReconnectNoise(it.text) }
                .map { it.copy(text = MessageTextFilter.strip(it.text)) }
                .filter { it.text.isNotBlank() }
            _messages.value = cleaned.toChatMessages()
            AppLogger.d(
                TAG,
                "init 从 voice_history 加载历史消息 ${cleaned.size} 条（原始 ${initial.size} 条，丢弃重连/空串）",
            )
        }
        viewModelScope.launch {
            transport.events.collect { onEvent(it) }
        }
        viewModelScope.launch {
            val mode = settingsRepository.assistantMode.first()
            val llm = settingsRepository.llmProvider.first()
            val asr = settingsRepository.asrProvider.first()
            val cloudAsrReady = (llm != LlmProviderCatalog.XIAOZHI) && asrSupportsVoice(asr) && asrCredentialsPresent(asr)
            // V2.8X：小智 REAL 模式语音输入扩展为「系统 ASR 或 任一可用云 ASR 取文本」，
            // 解决 localRecognizer.isAvailable=false 时麦克风按钮永久 disabled 的问题。
            // 取到的文本走 sendText() → listen 帧，与文本输入框等价，无需 Opus 编码。
            val xzReal = (llm == LlmProviderCatalog.XIAOZHI) && (mode == "REAL")
            val xzAsrReady = xzReal && (
                localRecognizer.isAvailable ||
                opusCodec.isEncoderAvailable() ||
                (asrSupportsVoice(asr) && asrCredentialsPresent(asr))
            )
            _voiceSupported.value = xzAsrReady || cloudAsrReady
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
            val configured = computeConfigured()
            _configured.value = configured
            val llm = settingsRepository.llmProvider.first()
            AppLogger.d(TAG, "connect() llm=$llm configured=$configured")
            if (llm == LlmProviderCatalog.XIAOZHI) {
                val prompt = settingsRepository.assistantPrompt.first()
                val mode = settingsRepository.assistantMode.first()
                AppLogger.d(TAG, "connect() 小智模式=$mode 发起 transport.connect（prompt.len=${prompt.length}）")
                transport.connect(prompt)
            } else {
                // OpenAI 兼容系（含豆包/通义千问）为请求/响应文本通道，无长连接。
                AppLogger.d(TAG, "connect() 非小智（${llm}），置为已连接文本通道")
                _connected.value = true
            }
        }
    }

    fun disconnect() {
        if (_recording.value) stopVoice()
        stopWakeWord()
        viewModelScope.launch {
            val llm = settingsRepository.llmProvider.first()
            AppLogger.d(TAG, "disconnect() llm=$llm")
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

    /**
     * V2.8X：配置页保存后调用 —— 串行写完所有 settings 后，断开旧 transport 再用新配置重连，
     * 解决「保存 token/endpoint 仍显示未连接、回答还是本地（mock）」问题。
     * 同时重新计算 voiceSupported，使麦克风按钮 enable 状态与新 ASR 配置一致。
     */
    fun reconnectAfterConfig() {
        viewModelScope.launch {
            // 先释放旧连接：若继续用旧 transport 会复用 cached ws，新建连接配置不会生效。
            if (_recording.value) stopVoice()
            stopWakeWord()
            val llm = settingsRepository.llmProvider.first()
            if (llm == LlmProviderCatalog.XIAOZHI) {
                transport.disconnect()
            }
            _connected.value = false
            // 重新计算语音可用性。
            val mode = settingsRepository.assistantMode.first()
            val asr = settingsRepository.asrProvider.first()
            val cloudAsrReady = (llm != LlmProviderCatalog.XIAOZHI) && asrSupportsVoice(asr) && asrCredentialsPresent(asr)
            val xzReal = (llm == LlmProviderCatalog.XIAOZHI) && (mode == "REAL")
            val xzAsrReady = xzReal && (
                localRecognizer.isAvailable ||
                opusCodec.isEncoderAvailable() ||
                (asrSupportsVoice(asr) && asrCredentialsPresent(asr))
            )
            _voiceSupported.value = xzAsrReady || cloudAsrReady
            _configured.value = computeConfigured()
            // 触发新连接。
            connect()
        }
    }

    /**
     * 开始语音采集：根据 ASR 服务商路由到系统实时识别或整段 PCM 累积（云 ASR）。
     *
     * V2.8X 调整：不再以 `_voiceSupported` 早退（UI 已移除按钮 enabled 门控，避免哑失败）。
     * 改为在协程内**动态**重新计算 ASR 能力并明确提示：
     * - 系统 ASR / Opus 编码器 / 文件式云 ASR 三者皆不可用时，弹一条 system message 解释，
     *   并把 `_voiceSupported` 同步置为 false（让 UI 灰显）。
     */
    fun startVoice() {
        // V2.8X++：新一轮语音开始时清空"本轮已展示用户文本"标记，避免上一轮的 user 气泡
        // 误 dedup 本轮的 stt 帧（仅 Opus 路径依赖此 flag 落用户气泡）。
        userSaidThisTurn = null
        // V2.8X++：先同步置「准备中」内联状态，UI 立即给出反馈（旋转环），不再用 Snackbar 占位，
        // 避免「正在打开麦克风…」与后续「开始聆听」 snackbar 排队导致挂 4 秒的卡顿体感。
        _voiceOpening.value = true
        viewModelScope.launch {
            // 动态计算（不等 init 阶段的异步快照）—— 进页面后用户可能改了 ASR 设置。
            val mode = settingsRepository.assistantMode.first()
            val llm = settingsRepository.llmProvider.first()
            val asr = settingsRepository.asrProvider.first()
            val cloudAsrReady = (llm != LlmProviderCatalog.XIAOZHI) && asrSupportsVoice(asr) && asrCredentialsPresent(asr)
            val xzReal = (llm == LlmProviderCatalog.XIAOZHI) && (mode == "REAL")
            val xzAsrReady = xzReal && (
                localRecognizer.isAvailable ||
                    opusCodec.isEncoderAvailable() ||
                    (asrSupportsVoice(asr) && asrCredentialsPresent(asr))
            )
            val actuallySupported = xzAsrReady || cloudAsrReady
            AppLogger.d(TAG, "startVoice mode=$mode llm=$llm asr=$asr sysAsr=${localRecognizer.isAvailable} opusEnc=${opusCodec.isEncoderAvailable()} cloudReady=$cloudAsrReady xzReal=$xzReal → supported=$actuallySupported")
            _voiceSupported.value = actuallySupported
            if (!actuallySupported) {
                // 给用户明确反馈（而不是按钮按了无反应）。文案走 strings.xml（红线② 禁止硬编码中文）。
                val msg = if (xzReal) {
                    appContext.getString(R.string.assistant_voice_no_system_asr)
                } else {
                    appContext.getString(R.string.assistant_voice_no_asr_configured)
                }
                append(ChatMessage(nextId(), "system", msg))
                _micToast.value = msg
                _voiceOpening.value = false
                return@launch
            }
            if (_recording.value) {
                _micToast.value = appContext.getString(R.string.assistant_mic_already_open)
                return@launch
            }
            // V2.8X++：成功路径不再弹 Snackbar——"准备中→聆听"由 UI 内联状态（voiceOpening/recording）呈现，
            // 避免 snackbar 排队导致的 lingering 卡顿。仅错误/冲突信息走 micToast。

            // 小智 REAL 模式：麦克风经系统 ASR 取文本后送 listen 帧（无 Opus 依赖）。
            if (llm == LlmProviderCatalog.XIAOZHI) {
                AppLogger.d(TAG, "startVoice → 路由到小智 REAL 语音")
                startXiaozhiVoice()
                return@launch
            }
            if (asr == AsrProviderCatalog.SYSTEM) {
                AppLogger.d(TAG, "startVoice → 路由到系统 ASR 实时识别")
                _recording.value = true
                _voiceOpening.value = false
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
                AppLogger.d(TAG, "startVoice → 路由到云 ASR($asr) 边录边传 PCM")
                val pcmFile = File(appContext.cacheDir, "asr_upload.pcm")
                val ok = capture.startAccumulate(pcmFile = pcmFile) { handleCloudAsr(it) }
                if (!ok) {
                    AppLogger.e(TAG, "startVoice 云 ASR 采集启动失败（capture.startAccumulate 返回 false）")
                    runCatching { pcmFile.delete() }
                    _voiceSupported.value = false
                    return@launch
                }
                _recording.value = true
                _voiceOpening.value = false
                AppLogger.d(TAG, "startVoice 云 ASR 采集已启动 pcmFile=${pcmFile.absolutePath}")
                if (llm == LlmProviderCatalog.XIAOZHI) {
                    transport.abortTts()       // 录音开始即静音，避免录到小智回声
                    transport.sendListenStart()
                }
            }
        }
    }

    /**
     * 小智 REAL 模式语音输入：经系统 SpeechRecognizer 取文本，再走 [sendText]（listen 帧）上送。
     * 零依赖方案，规避设备无 Opus 编码器导致麦克风被禁用；与文本输入框输入等价。
     */
    private fun startXiaozhiVoice() {
        viewModelScope.launch {
            // 小智 REAL 语音输入：优先系统 SpeechRecognizer（转文字→listen 帧，零依赖）；
            // 无系统识别器但有 Opus 编码器时，走真·语音流（mic PCM→Opus 二进制帧），由服务端完成 ASR。
            if (localRecognizer.isAvailable) {
                AppLogger.d(TAG, "startXiaozhiVoice → 系统 SpeechRecognizer 路径")
                _recording.value = true
                _voiceOpening.value = false
                // V2.8X++：系统 ASR 路径此前漏发 listen start，导致用户开始说话时小智仍在播放上一轮 TTS，
                // 麦克风把小智自己的声音录进去当作用户输入发出去（"发送的信息是小智自己说的话"）。
                // 这里与 Opus/云 ASR 路径一致：录音开始即中断小智——abortTts 停设备侧外放 + sendListenStart
                // 通知服务端停止生成，双管齐下消除回声。
                transport.abortTts()
                transport.sendListenStart()
                val lang = settingsRepository.asrLanguage.first()
                localRecognizer.start(
                    continuous = false,
                    language = lang,
                    onPartial = { /* 系统识别仅以终句为准提交 */ },
                    onFinal = { text ->
                        AppLogger.d(TAG, "startXiaozhiVoice 系统识别终句 len=${text.length}")
                        _recording.value = false
                        if (text.isNotBlank()) sendText(text) else append(
                            ChatMessage(nextId(), "system", appContext.getString(R.string.assistant_asr_empty)),
                        )
                    },
                )
            } else if (opusCodec.isEncoderAvailable()) {
                AppLogger.d(TAG, "startXiaozhiVoice → Opus 编码语音流路径（服务端 ASR）")
                startXiaozhiOpusVoice()
            } else {
                AppLogger.e(TAG, "startXiaozhiVoice 无可用语音通道（系统 ASR 与 Opus 编码器均不可用）")
                _recording.value = false
                _voiceSupported.value = false
                // 一次性 Snackbar 提示，不污染聊天消息流。
                _micToast.value = appContext.getString(R.string.assistant_voice_no_system_asr)
            }
        }
    }

    /** 小智 REAL 真·语音流：麦克风采集 16kHz PCM → Opus 编码 → 二进制帧上送，由服务端 ASR。 */
    private fun startXiaozhiOpusVoice() {
        viewModelScope.launch {
            _recording.value = true
            _voiceOpening.value = false
            // 通知服务端进入实时聆听（realtime 模式，由服务端 VAD 判定语句边界）。
            // V2.8X++：listen(state="start") 即服务端「打断」信号——用户开始说话时中止对方当前 TTS，
            // 因此紧跟 _recording 翻转后第一时间发出，确保「录音即打断对方」即时生效。
            // 同时 abortTts 立即停掉设备侧 TTS 外放，避免麦克风录入小智上一轮回复的尾音（回声）。
            AppLogger.d(TAG, "startXiaozhiOpusVoice → abortTts + sendListenStart + capture.start(16000)")
            transport.abortTts()
            transport.sendListenStart()
            val ok = capture.start(16000) { pcm ->
                // V2.8X+：补诊断日志链路，定位"语音不能用"是录音没起、编码失败还是 sendAudio 失败。
                // 三处日志分布在 AudioCapture/OpusCodec/XzTransport，结合 frameCount 即可定位。
                AppLogger.v(TAG, "startXiaozhiOpusVoice → 收到 pcm ${pcm.size}B，开始编码")
                val opus = opusCodec.encodeFrame(pcm)
                if (opus != null) {
                    AppLogger.v(TAG, "startXiaozhiOpusVoice → Opus 编码完成 ${opus.size}B，调用 sendAudio")
                    transport.sendAudio(opus)
                } else {
                    // OpusCodec 自身已 w 级日志 encodeFrame 失败原因，此处不再重复
                    AppLogger.w(TAG, "startXiaozhiOpusVoice Opus 编码返回 null（详见 OpusCodec 标签）")
                }
            }
            if (!ok) {
                AppLogger.e(TAG, "startXiaozhiOpusVoice 麦克风采集启动失败（capture.start 返回 false）")
                _recording.value = false
                _voiceOpening.value = false
                transport.sendListenStop()
                append(ChatMessage(nextId(), "system", appContext.getString(R.string.assistant_asr_empty)))
            }
        }
    }

    /** 停止语音采集。云 ASR 路径下，停止会触发转写；系统路径下停止实时识别。 */
    fun stopVoice() {
        // V2.8X++：无论是否真在录音，点「停止」即退出「准备中」态，避免 UI 卡在旋转环。
        _voiceOpening.value = false
        if (!_recording.value) {
            AppLogger.d(TAG, "stopVoice 被忽略：当前未在录音")
            return
        }
        viewModelScope.launch {
            val isXz = settingsRepository.llmProvider.first() == LlmProviderCatalog.XIAOZHI
            AppLogger.d(TAG, "stopVoice isXz=$isXz")
            if (isXz) {
                // 小智 REAL 模式语音走系统 ASR（或 Opus 流）：停止识别器/采集并通知服务端结束聆听。
                localRecognizer.stop()
                capture.stop()
                transport.sendListenStop()
                _recording.value = false
                return@launch
            }
            val isSystem = settingsRepository.asrProvider.first() == AsrProviderCatalog.SYSTEM
            if (isSystem) localRecognizer.stop() else capture.stop()
            _recording.value = false
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
        // V2.8X++：记录本轮用户文本，stt 帧到达时用于 dedup（与服务端回传的相同文本不再追加气泡）。
        userSaidThisTurn = t
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
            is XiaozhiEvent.Connected -> {
                _reconnectingStatus.value = null
                _connected.value = true
            }
            // V2.8X++：重连提示仅在顶部状态栏展示（_reconnectingStatus），不再插入聊天气泡，
            // 杜绝「重连 1/10…10/10…又从 1/10」刷屏；同一字段被连续覆盖即天然去重。
            is XiaozhiEvent.Reconnecting -> _reconnectingStatus.value =
                appContext.getString(R.string.assistant_reconnecting, ev.attempt, ev.max)
            is XiaozhiEvent.Disconnected -> {
                _reconnectingStatus.value = null
                _connected.value = false
            }
            is XiaozhiEvent.ConnectionIssue -> {
                // V2.8X++：启动期连接诊断只走 banner，不污染消息流。
                // 用户进 tab 默认空白；连接问题由顶栏红/黄条展示。
                AppLogger.w(TAG, "ConnectionIssue 启动期连接诊断: ${ev.detail.take(80)}…")
                _connected.value = false
                _connectionBanner.value = ev.detail
            }
            is XiaozhiEvent.Error -> {
                _reconnectingStatus.value = null
                _connected.value = false
                append(ChatMessage(nextId(), "system", appContext.getString(R.string.assistant_connect_error, ev.detail)))
            }
            is XiaozhiEvent.SttText -> {
                // V2.8X++：Opus 语音流路径下没有 `sendText`，服务端回 stt 是用户文本的唯一来源；
                // 之前这里直接 Unit 导致「消息面板未展示我发送的消息」，现按 dedup 选择性落为用户气泡。
                // - 本轮已 sendText 且文本相同：dedup skip（避免本地 ASR/文本输入时与服务端 ASR 各产生一条相同气泡）。
                // - 本轮未 sendText 或文本不同：作为用户气泡追加（Opus 语音直送场景；或 ASR 转写与本地不一致时如实呈现）。
                val incoming = ev.text
                val cleaned = MessageTextFilter.strip(incoming)
                if (cleaned.isNotBlank() && userSaidThisTurn != cleaned) {
                    append(ChatMessage(nextId(), "user", cleaned))
                }
                userSaidThisTurn = null
            }
            is XiaozhiEvent.TtsText -> Unit // 界面无需展示 TTS
            is XiaozhiEvent.LlmText -> {
                // V2.8X++：服务端未必每轮都回 stt（仅 LLM token），此场景下同样清掉本轮标记避免跨轮污染。
                userSaidThisTurn = null
                // V2.8X+ 防御：即便传输层未 strip（如 Mock/未来其他来源），UI 层再 strip 一次
                // 多模态资源 token（@image#xxx），避免技术串进入消息列表；空串则不 append。
                val cleaned = MessageTextFilter.strip(ev.text)
                if (cleaned.isNotBlank()) append(ChatMessage(nextId(), "assistant", cleaned))
            }
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

    /** V2.57：长会话消息上限，超过则丢弃最旧，避免 messages 随会话无限增长导致内存单调上升。
     *  V2.8X++：从 100 提到 2000 — voice_history 持久化后用户期望"消息不要丢失"，
     *  硬截断会让用户疑惑（库里存着但 UI 不显示）。2000 条基本覆盖 1~2 个月的日常对话，
     *  且按文本大小通常 < 1MB 内存可承受。超此上限才截断首部（防 OOM）。 */
    private companion object {
        const val TAG = "AssistantVM"
        private const val MAX_MESSAGES = 2000
    }

    private fun append(msg: ChatMessage) {
        // 去重：小智服务端常在 llm 与 tts.sentence_start 各下发一遍相同回复文本，
        // 若直接追加会出现两条一模一样的助手消息；此处跳过与末条（同角色同文本）的连续重复。
        val last = _messages.value.lastOrNull()
        if (last != null && last.role == msg.role && last.text == msg.text && msg.role != "user") {
            recordVoiceHistory(msg)
            return
        }
        _messages.update { list ->
            val next = list + msg
            if (next.size > MAX_MESSAGES) next.takeLast(MAX_MESSAGES) else next
        }
        recordVoiceHistory(msg)
    }

    /** V2.8X++：识别"重连提示"残留消息。旧版本 onEvent(Reconnecting) 曾把这些字符串 append 到消息流并落库，
     *  现已在源头改为仅更新 _reconnectingStatus（顶栏状态栏展示，不进聊天），但 DB 里仍残留旧条目。
     *  加载历史时按字符串前缀丢弃，避免「顶部已显示已连接/未连接，但聊天列表却刷满连接中断...」的体验割裂。 */
    private fun isReconnectNoise(text: String): Boolean {
        // assistant_reconnecting = "连接中断，正在重连（第 %1$d/%2$d 次）…"
        // assistant_connect_error = "连接失败：%1$s" —— 一次性错误，仍保留给用户排查，故不丢
        return text.startsWith("连接中断，正在重连")
    }

    /**
     * VoiceHistoryEntity → ChatMessage 转换（V2.8X++）。
     * - id: 用 DB 自增 id（保证与 DB 同步，UI 删除/刷新能正确命中）；
     * - role: DB 已规范为 "user" | "assistant" | "system" 三种；
     * - taskCreated: 仅 assistant + kind=result 时为 true（任务创建回执）。
     */
    private fun List<VoiceHistoryEntity>.toChatMessages(): List<ChatMessage> = map { e ->
        ChatMessage(
            id = e.id,
            role = e.role,
            text = e.text,
            taskCreated = e.role == "assistant" && e.kind == "result",
        )
    }

    /** V2.8X++ 助手消息：左滑/长按删除单条。同步删库（system 也走相同路径）。 */
    fun removeMessage(id: Long) {
        AppLogger.d(TAG, "removeMessage id=$id")
        var removed: VoiceHistoryEntity? = null
        _messages.update { list ->
            removed = list.firstOrNull { it.id == id }?.let { msg ->
                VoiceHistoryEntity(
                    id = msg.id,
                    createdAt = 0L, // 仅用于占位匹配，下面会按 id 真删
                    role = msg.role,
                    text = msg.text,
                )
            }
            list.filterNot { it.id == id }
        }
        // 同步清理 voice_history 库对应记录（按 id 主键精确删除；id=0 表示内存临时项，无库可删）。
        val target = removed
        if (target != null && target.id > 0L) {
            viewModelScope.launch {
                runCatching { voiceHistoryRepository.deleteById(target.id) }
                    .onFailure { AppLogger.e(TAG, "removeMessage 删库失败 id=${target.id}", it) }
            }
        }
    }

    /** V2.8X++ 助手消息：一键清空整段对话（保留「草稿」状态）。同步清空 voice_history 库。 */
    fun clearAllMessages() {
        AppLogger.d(TAG, "clearAllMessages 清空整段对话（库+内存）")
        _messages.value = emptyList()
        viewModelScope.launch {
            runCatching { voiceHistoryRepository.clearAll() }
                .onFailure { AppLogger.e(TAG, "clearAllMessages 清库失败", it) }
        }
    }

    /**
     * V2.8X++ 消息落库：assistant tab 的对话**主存储**就是 voice_history，
     * 不再受 voiceHistoryOn 守门——用户要求「消息不要丢失，除非用户清除」，
     * 所以每次 append 都落库，按 createdAt 升序展示。
     * `voiceHistoryOn`（设置页开关）仅控制"语音历史页面"是否可访问，不再影响主对话存留。
     * 失败静默（落库非核心链路，不应阻断对话）。
     */
    private fun recordVoiceHistory(msg: ChatMessage) {
        val now = System.currentTimeMillis()
        val kind = if (msg.role == "assistant" && msg.taskCreated) "result" else "utterance"
        viewModelScope.launch {
            runCatching {
                voiceHistoryRepository.insert(
                    VoiceHistoryEntity(
                        createdAt = now,
                        role = msg.role,
                        text = msg.text,
                        kind = kind,
                    ),
                )
            }.onFailure { AppLogger.e(TAG, "recordVoiceHistory 落库失败 role=${msg.role}", it) }
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
