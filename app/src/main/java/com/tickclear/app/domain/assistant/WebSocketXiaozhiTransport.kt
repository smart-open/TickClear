package com.tickclear.app.domain.assistant

import android.content.Context
import com.tickclear.app.R
import com.tickclear.app.data.SecureStore
import com.tickclear.app.domain.log.AppLogger
import com.tickclear.app.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit

/**
 * 真实小智传输：基于 OkHttp WebSocket 对接小智服务端（wss）。
 * 协议要点：
 * - 握手 hello 携带 audio_params + 自定义 prompt（人设）+ 可选 token；
 * - 服务端下发 hello.message（欢迎）/ stt / llm / tts / mcp；
 * - 真实模式下 MCP 工具调用由本传输层执行并回执 result（JSON 字符串），再推送 UI 事件；
 * - 文本输入以 listen(start/stop+text) 模拟 ASR 结果；
 * - 语音模式经二进制帧收发 Opus：上行麦克风由 martoreto/opuscodec 软件 libopus 编码帧（V2.8X++ 替代
 *   MediaCodec，规避机型碎片化导致零字节上行），下行服务端 TTS 同样经该库软件解码后 AudioTrack 播放（见 OpusCodec）。
 */
class WebSocketXiaozhiTransport(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val mcpTools: XiaozhiMcpTools,
    private val codec: OpusCodec,
) : XiaozhiTransport {

    private val _events = MutableSharedFlow<XiaozhiEvent>(extraBufferCapacity = 64)
    override val events: Flow<XiaozhiEvent> = _events.asSharedFlow()

    private val json = Json { ignoreUnknownKeys = true }

    // 服务端 TTS 下行：解码 Opus → PCM → AudioTrack 播放（best-effort，失败静默丢弃）。
    private val player = AudioPlayer()
    /** 录音/打断期间为 true：丢弃所有 TTS 音频帧，彻底消除小智回声（流式帧会不断重建播放器）。 */
    @Volatile private var ttsSuppressed = false

    // 心跳保活（V2.17）：WebSocket 协议层 ping/pong，20s 间隔；pong 超时即触发 onFailure → 退避重连，
    // 兼作 NAT/代理空闲链路保活与死链检测，无需应用层自定义心跳报文。
    // V2.8X+ 第二目的：阻止网关/反代 idle timeout（CloudFront 默认 10s / ALB 60s）
    // 在握手完成前提前 RST 关闭（表现为 close code=1005 网关裸关闭）。
    // V2.8X++：从 20s 缩到 15s —— 对端官方云（xiaozhi.me）在对话链结束
    // （LLM/TTS 收尾一段时间无新活动）后约 30~60s 主动 close；更短的 ping 间隔
    // 让 NAT/反代 idle timeout 在对话链关闭前被持续抑制，对话间隙不掉线。
    private val client = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private companion object {
        const val TAG = "XzTransport"
        /** 意外断线最大自动重连次数（1s/2s/4s/8s/16s/30s 指数退避）。
         *  V2.8X++：5 → 10，对话间隙反复断连/重连时给足退避次数，
         *  否则 MAX_RECONNECT 用尽后彻底掉线要等用户切走重进助手才恢复。 */
        const val MAX_RECONNECT = 10
        /** token 仅记录前 4 位 + 长度，避免明文泄漏到 Logcat。 */
        fun mask(s: String?): String = if (s.isNullOrEmpty()) "∅" else s.take(4) + "…(len=${s.length})"
    }

    @Volatile private var ws: WebSocket? = null
    @Volatile private var sessionId: String? = null
    @Volatile private var connected = false

    /** 设备标识缓存（握手头 + hello.device_id 共用），openSocket 时刷新。 */
    @Volatile private var deviceIdCache: String? = null
    @Volatile private var clientIdCache: String? = null

    /** V2.8X：hello 握手是否完成（用于超时守卫，避免设备未绑定时无限「连接中」）。 */
    @Volatile private var handshakeDone = false
    /** V2.8X：WebSocket 是否真正 onOpen（升级 101 成功）。用于区分「连都连不上」(DNS/TLS)
     *  与「升级成功但握手前被服务端无声关闭」(设备/服务端校验失败)，给不同报错。 */
    @Volatile private var opened = false

    /** 服务端声明的 TTS 采样率（来自 hello/tts-start 的 audio_params），用于 Opus 解码与播放对齐。 */
    private var currentSampleRate = 16000

    // V2.17 重连韧性：用户主动断开时不重连；意外断线按指数退避重试，握手成功后计数清零。
    @Volatile private var userDisconnect = false
    @Volatile private var reconnectAttempt = 0

    private var pendingPrompt: String = ""
    private var pendingToken: String? = null

    // MCP 工具调用在 WebSocket 回调线程上触发，用独立作用域切到协程执行挂起逻辑。
    private var scope: CoroutineScope? = null

    override suspend fun connect(prompt: String) {
        if (connected && handshakeDone) return
        pendingPrompt = prompt
        userDisconnect = false
        reconnectAttempt = 0
        handshakeDone = false
        AppLogger.d(TAG, "connect() prompt.len=${prompt.length} handshakeDone(was)=${connected && handshakeDone}")
        // 若重连进行中，先取消旧作用域，避免旧延迟重连与新连接竞建重复 WebSocket（P2）。
        scope?.cancel()
        scope = null
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        openSocket()
        // V2.8X：握手超时守卫（10s）。小智官方服务要求设备先在 xiaozhi.me 控制台绑定，
        // 未绑定/绑定失效时服务端不会响应 hello，连接会无限「连接中」且毫无报错。
        // 超时给出明确错误（含当前 Device-Id 便于用户对照「我到底绑了哪个」），并按 OTA 通道给出修复路径。
        scope?.launch {
            // V2.8X+：握手超时从 10s 提到 25s。原因：服务端 connection.py 源码显示收到 hello 后
            // 会异步调 get_private_config_from_api 校验 device-id/client-id，并启动 _initialize_components
            // 加载 TTS/ASR（默认 FunASR/EdgeTTS 模型加载要几秒），等所有组件就绪才会发 welcome。
            // 给服务端留足初始化时间，避免误判"被拒"。12s 内网关 idle timeout 也会在 XzTransport 这层
            // 由 OkHttp pingInterval(20s) 显式 ping 抑制，不必再额外顾虑。
            delay(25_000)
            if (!handshakeDone && !userDisconnect && ws != null) {
                val did = deviceIdCache ?: "（未生成）"
                AppLogger.w(TAG, "握手超时 25s 未收到服务端 welcome。deviceId=$did，注意 close code（1005=网关裸关闭/1006=连接异常/1011=服务端内部错误）")
                // V2.8X++：启动期握手超时走 ConnectionIssue，不污染消息流；UI 顶栏 banner 展示。
                _events.tryEmit(
                    XiaozhiEvent.ConnectionIssue(
                        context.getString(
                            R.string.assistant_handshake_timeout,
                            did,
                            25,
                        ),
                    ),
                )
                runCatching { ws?.close(1000, "handshake timeout") }
                connected = false
                ws = null
            }
        }
    }

    /**
     * 建立（或重建）WebSocket。每次都重新读取 endpoint 与 token —— 外部 token 刷新场景下，
     * 用户在设置页更新 token 后无需重启应用，下一次（重）连接即生效。
     * V2.8：同时读取设备标识（Device-Id / Client-Id），模拟 ESP32 设备接入官方 xiaozhi.me。
     */
    private suspend fun openSocket() {
        if (ws != null) return // 已有连接，避免重复建连（与 connect 重建作用域配合消除竞态）
        handshakeDone = false
        opened = false
        // P0：端点非法 / 建连异常必须捕获 —— 否则异常冒泡出 connect() 协程导致崩溃，
        // 且原实现先置 connected=true 再建连，异常后 connected 卡死使后续 connect() 永远早退。
        val request = try {
            pendingToken = settings.getAssistantToken()
            // 纠正遗留错误主机：早期版本误用 wss://api.xiaozhi.me/ws（该主机不存在），
            // 导致一直「未连接」；官方 WS 地址固定为 wss://api.tenclass.net/xiaozhi/v1/。
            val rawEndpoint = settings.assistantEndpoint.first()
            val endpoint = when {
                rawEndpoint.isBlank() -> "wss://api.tenclass.net/xiaozhi/v1/"
                rawEndpoint == "wss://api.xiaozhi.me/ws" -> "wss://api.tenclass.net/xiaozhi/v1/"
                else -> rawEndpoint
            }
            AppLogger.d(TAG, "openSocket 端点归一化: raw='$rawEndpoint' → '$endpoint'")
            // 读取设备标识（模拟 ESP32），用于官方服务端设备认证
            // 关键修复（2026-07-29 协议实证）：xiaozhi.me 对 Device-Id(MAC) 大小写敏感，
            // 必须以绑定时的小写原样发送（如 e8:06:90:98:6c:d4），大写会被静默拒→1005。
            // 此处统一转小写，覆盖"历史已存大写 MAC"的情况（用户无需手动改）。
            val deviceId = settings.xzDeviceId.first().takeIf { it.isNotEmpty() }?.lowercase()
            val clientId = settings.xzClientId.first().takeIf { it.isNotEmpty() }
            deviceIdCache = deviceId
            clientIdCache = clientId
            AppLogger.d(TAG, "openSocket 设备标识 deviceId=${deviceId ?: "∅"} clientId=${clientId ?: "∅"} token=${mask(pendingToken)}")
            Request.Builder().url(endpoint).apply {
                // 设备认证头：小智服务端据此识别虚拟设备（与 OTA 激活一致）。
                // Protocol-Version:1 为 ESP32 官方客户端固定握手头，官方网关依赖/忽略该头，
                // 补上以最大化与真实设备行为的一致性。
                addHeader("Protocol-Version", "1")
                pendingToken?.takeIf { it.isNotBlank() }?.let { addHeader("Authorization", "Bearer $it") }
                // 模拟 ESP32 设备认证头：官方 xiaozhi.me 需要这两个头来识别设备
                deviceId?.let { addHeader("Device-Id", it) }
                clientId?.let { addHeader("Client-Id", it) }
            }.build()
        } catch (e: Exception) {
            connected = false
            ws = null
            AppLogger.e(TAG, "openSocket 异常：建连前准备失败", e)
            _events.tryEmit(XiaozhiEvent.Error(e.message ?: "unknown"))
            return
        }
        try {
            ws = client.newWebSocket(request, listener)
            connected = true
            AppLogger.d(TAG, "openSocket newWebSocket 已发起（等待 onOpen）")
        } catch (e: Exception) {
            connected = false
            ws = null
            AppLogger.e(TAG, "openSocket 异常：newWebSocket 失败", e)
            _events.tryEmit(XiaozhiEvent.Error(e.message ?: "unknown"))
        }
    }

    /** 意外断线处理：未超上限则退避重连，否则最终失联。 */
    private fun onUnexpectedDrop() {
        connected = false
        ws = null
        if (userDisconnect || reconnectAttempt >= MAX_RECONNECT) {
            _events.tryEmit(XiaozhiEvent.Disconnected)
            return
        }
        reconnectAttempt++
        val attempt = reconnectAttempt
        _events.tryEmit(XiaozhiEvent.Reconnecting(attempt, MAX_RECONNECT))
        scope?.launch {
            // 指数退避：1s/2s/4s/8s/16s，上限 30s。
            delay(minOf(1000L shl (attempt - 1), 30_000L))
            if (!userDisconnect) openSocket()
        }
    }

    override suspend fun sendText(text: String) {
        val sock = ws ?: run {
            AppLogger.w(TAG, "sendText 丢弃：ws=null（可能尚未握手完成或已断开）text.len=${text.length}")
            return
        }
        if (!handshakeDone) {
            AppLogger.w(TAG, "sendText 丢弃：握手未完成（handshakeDone=false）text.len=${text.length}")
            return
        }
        // 文本输入：先发 listen start（开始捕获），再以 detect+text 携带识别文本，模拟"用户说了这句话"。
        // ⚠️ 关键修复（2026-07-29 官方协议文档 + 实证探针）：服务端仅在 listen 的 state="detect"
        // 且携带 text 时才把文本当作用户输入处理；旧实现用 state="stop"+text，服务端会静默忽略
        // → 表现为"测试连接正常（握手通过）但发消息无回复"。改为 detect+text 后整条 STT→LLM→TTS
        // 对话链正常触发（已用 e8:06 真机 MAC 实证完整天气回复 + MCP 工具调用）。
        val startFrame = buildJsonObject {
            put("type", "listen")
            put("mode", "manual")
            put("state", "start")
            sessionId?.let { put("session_id", it) }
        }.toString()
        val detectFrame = buildJsonObject {
            put("type", "listen")
            put("mode", "manual")
            put("state", "detect")
            put("text", text)
            sessionId?.let { put("session_id", it) }
        }.toString()
        AppLogger.d(TAG, "→ sendText 发送 listen(start) session=${sessionId ?: "∅"} text.len=${text.length}")
        val sentStart = runCatching { sock.send(startFrame); true }.onFailure { AppLogger.e(TAG, "发送 listen(start) 失败", it) }.getOrDefault(false)
        if (!sentStart) return
        // 极短间隔模拟真实「说完一段」时序，避免服务端把两条压成一条。
        delay(50)
        AppLogger.d(TAG, "→ sendText 发送 listen(detect+text) text='${text.take(80)}' session=${sessionId ?: "∅"}")
        runCatching { sock.send(detectFrame) }.onFailure { AppLogger.e(TAG, "发送 listen(detect) 失败", it) }
    }

    override suspend fun sendListenStart() {
        val sock = ws ?: run {
            AppLogger.w(TAG, "sendListenStart 丢弃：ws=null")
            return
        }
        val frame = buildJsonObject {
            put("type", "listen")
            put("mode", "realtime")
            put("state", "start")
            sessionId?.let { put("session_id", it) }
        }.toString()
        AppLogger.d(TAG, "→ sendListenStart 发送 listen(realtime/start) session=${sessionId ?: "∅"}")
        runCatching { sock.send(frame) }.onFailure { AppLogger.e(TAG, "发送 listen(realtime/start) 失败", it) }
    }

    override suspend fun sendListenStop() {
        val sock = ws ?: run {
            AppLogger.w(TAG, "sendListenStop 丢弃：ws=null")
            return
        }
        val frame = buildJsonObject {
            put("type", "listen")
            put("mode", "realtime")
            put("state", "stop")
            sessionId?.let { put("session_id", it) }
        }.toString()
        AppLogger.d(TAG, "→ sendListenStop 发送 listen(realtime/stop) session=${sessionId ?: "∅"}")
        resumeTts()
        runCatching { sock.send(frame) }.onFailure { AppLogger.e(TAG, "发送 listen(realtime/stop) 失败", it) }
    }

    override fun abortTts() {
        // 立即停掉设备侧 TTS 外放，避免用户开始录音时麦克风录入小智上一轮回复的尾音（回声）。
        // player.release() 内部对 track=null / 未播放均做了 runCatching 保护，安全可重复调用。
        val released = runCatching { player.release() }.isSuccess
        ttsSuppressed = true
        AppLogger.d(TAG, "abortTts 停止本地 TTS 外放 released=$released ttsSuppressed=true")
    }

    /** 停止录音/打断结束后调用：允许 TTS 音频帧正常播放（小智的回答恢复出声）。 */
    override fun resumeTts() {
        ttsSuppressed = false
        AppLogger.d(TAG, "resumeTts 恢复 TTS 外放 ttsSuppressed=false")
    }

    /**
     * 客户端 hello 握手：严格按官方 78/xiaozhi-esp32 v1.9+ 固件源码 `WebsocketProtocol::GetHelloMessage()` 实现。
     *
     * 字段（v2.8X 修正）：
     * - 必发：type / version / features / transport / audio_params
     * - features.mcp=true 是 v1.6+ 协议层必填字段（v1.0 老固件漏发会被服务端协议层拒绝 → 立即关连接）
     * - device_id / client_id / token / prompt 全部走 HTTP 头（Device-Id / Client-Id / Authorization），
     *   服务端不读 hello JSON 里的这些字段
     *
     * 修复历史：v2.8X 之前实现为严格 5 字段（漏 features），官方 78/xiaozhi-esp32 源码明确有
     * `cJSON_AddBoolToObject(features, "mcp", true)`，服务端 helloHandle 在 hello 解析阶段校验 features
     * 失败时直接关闭连接（close frame，无 reason），表现为 0.001s 立刻断开。
     */
    private fun sendClientHello() {
        val sock = ws ?: return
        val hello = buildJsonObject {
            put("type", "hello")
            put("version", 1)
            // V2.8X 关键修复：补 features.mcp=true（v1.6+ 协议必填）。
            // 漏发此字段时服务端 hello 协议层解析失败 → 立即关连接，UI 表现为「连接中」几秒后
            // 收到 onFailure 而非握手超时。详见 GitHub 78/xiaozhi-esp32 GetHelloMessage()。
            put(
                "features",
                buildJsonObject {
                    put("mcp", true)
                },
            )
            put("transport", "websocket")
            put(
                "audio_params",
                buildJsonObject {
                    put("format", "opus")
                    put("sample_rate", 16000)
                    put("channels", 1)
                    put("frame_duration", 60)
                },
            )
        }
        val json = hello.toString()
        AppLogger.d(TAG, "→ 发送 client hello（v1.6+ 协议 6 字段，含 features.mcp）")
        AppLogger.d(TAG, "→ 发送 hello json（6字段，供核对格式）: $json")
        runCatching { sock.send(json) }.onFailure { AppLogger.e(TAG, "发送 hello 失败", it) }
    }

    override fun sendAudio(bytes: ByteArray) {
        // WebSocket 二进制帧承载 Opus 音频；OkHttp send 线程安全，可在采集线程直接调用。
        // V2.8X+：补诊断日志 —— 之前 0 日志，"语音不能用" 时无法判断是 capture 没起 / 编码失败
        // 还是 send 失败。v 级避免日志爆炸，错误路径 e 级。
        val sock = ws ?: run {
            AppLogger.w(TAG, "sendAudio 丢弃：ws=null（可能未握手完成或已断开）len=${bytes.size}")
            return
        }
        runCatching { sock.send(ByteString.of(*bytes)) }
            .onSuccess { AppLogger.v(TAG, "→ sendAudio Opus 帧 len=${bytes.size}") }
            .onFailure { AppLogger.e(TAG, "sendAudio 发送失败 len=${bytes.size}", it) }
    }

    override suspend fun disconnect() {
        userDisconnect = true
        // 无论如何都释放资源：旧实现在「重连中」(connected=false) 时早退，
        // 导致 AudioTrack / OpusCodec 未释放、声音残留，且作用域/WS 滞留（P2）。
        connected = false
        handshakeDone = false
        opened = false
        runCatching { player.release() }
        ttsSuppressed = false
        // 会话结束释放 OpusCodec，避免编解码器终生死占（下次 encode/decode 惰性重建）。
        runCatching { codec.release() }
        scope?.cancel()
        scope = null
        runCatching { ws?.close(1000, "bye") }
        ws = null
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            opened = true
            AppLogger.i(TAG, "onOpen WebSocket 已建立（响应码 ${response.code}）。按官方协议客户端先发 hello")
            // 官方协议（xiaozhi.me / xiaozhi.dev / ESP32 固件三方一致）：连接建立后【客户端先发 hello】，
            // 服务端随后回 hello 完成握手。此前实现错误地等待服务端先发 hello，导致握手永不完成、
            // 一直「未连接」且无任何报错。修正：onOpen 立即发送客户端 hello。
            sendClientHello()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            // 断开过程中（connected=false）可能仍有排队的文本帧到达，跳过解码避免触碰已释放的 codec/player（L2）。
            if (!connected) return
            AppLogger.v(TAG, "← 收到文本帧: ${text.take(400)}")
            runCatching { handleServerMessage(text) }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            // 断开过程中（connected=false）可能仍有排队的帧到达，跳过解码避免触碰已释放的 codec/player（L2）。
            if (!connected) return
            AppLogger.v(TAG, "← 收到二进制 Opus 帧 len=${bytes.size}")
            // 录音/打断静音期：直接丢弃 TTS 音频帧，避免流式帧不断重建播放器让小智继续外放（回声根因）。
            if (ttsSuppressed) {
                AppLogger.v(TAG, "← 丢弃 TTS 帧（ttsSuppressed=true，录音静音期）")
                return
            }
            // 服务端 TTS 二进制 Opus 帧：按服务端声明的采样率解码后播放；任一环节失败则静默丢弃。
            runCatching {
                val pcm = codec.decodeFrame(bytes.toByteArray()) ?: return@runCatching
                if (player.init(currentSampleRate)) player.play(pcm)
            }.onFailure { AppLogger.e(TAG, "解码/播放 Opus 帧失败（已丢弃）", it) }
        }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // V2.8X+：抓服务端响应体（401/403/400 通常回 JSON 解释），原先被吞了只留笼统「网络失败」，
                // 真实服务端在「设备未绑定」时可能返回 4xx + `{message:"device not activated"}` 体，便于排查。
                val bodySnippet = response?.let { r ->
                    runCatching { r.body?.string() }.getOrNull()
                }?.take(512)
                val http = response?.code
                // 失败分类（详见下方 phase 计算）：以「是否已完成握手(handshakeDone)」为首要判据，
                // 仅握手前被无声关闭才判设备被拒、不重连；握手成功后的链路死亡一律走退避重连。
                // V2.8X++ 修正：失败分类必须以「是否已完成握手」为首要判据，而非只看 opened。
                // 旧逻辑把「opened 后、response==null 的失败」一律判为 ws-rejected（设备被拒），
                // 导致握手成功后两种常见掉线被误判、不重连、还弹「设备被拒」误导用户：
                //  ① ping/pong 超时（"sent ping but didn't receive pong"）—— 链路死亡，应重连；
                //  ② 对话中 "Software caused connection abort"（服务端/网关 RST）—— 链路死亡，应重连。
                // 这两种都发生在 handshakeDone=true 之后，应走 onUnexpectedDrop 退避重连，
                // 否则表现就是「聊几句就断、要手动重连」（见用户日志 09:29:07 / 09:31:47）。
                val handshakeOk = handshakeDone
                val phase = when {
                    response != null -> "http"      // HTTP 升级被拒（401/403/426 凭据或端点错误）→ 不重连
                    handshakeOk -> "dead"           // 已握手成功、后续链路死亡（ping 超时/连接中止）→ 重连
                    opened -> "ws-rejected"         // 升级成功但握手前被无声关闭 = 设备未激活/令牌无效 → 不重连
                    else -> "connect"               // 连 WS 都没建起来（DNS/TLS/网络）→ 重连
                }
                val msg = buildString {
                    append("阶段=").append(phase)
                    append(" http=").append(http?.toString() ?: "∅")
                    append(" msg=").append(t.message ?: t.javaClass.simpleName)
                    if (!bodySnippet.isNullOrBlank()) append('\n').append("响应体: ").append(bodySnippet)
                }
                AppLogger.e(TAG, "onFailure WebSocket 失败 $msg", t)

                when (phase) {
                    "ws-rejected" -> {
                        // 升级成功但握手前被无声关闭：设备未激活 / 令牌无效 / 固件版本不被接受。
                        // 属设备/服务端侧问题，重连必同样被拒，给明确报错 + deviceId 便于对照控制台。
                        // V2.8X++：启动期诊断走 ConnectionIssue，不污染消息流；UI 顶栏 banner 展示。
                        val did = deviceIdCache ?: "（未生成）"
                        _events.tryEmit(
                            XiaozhiEvent.ConnectionIssue(
                                context.getString(R.string.assistant_connection_rejected, did),
                            ),
                        )
                        connected = false
                        ws = null
                    }
                    "http" -> {
                        // HTTP 层拒绝（401/403 等凭据/端点问题）：重连必同样失败，给诊断报错不重连。
                        // V2.8X++：同上走 ConnectionIssue（启动期诊断）。
                        _events.tryEmit(XiaozhiEvent.ConnectionIssue(msg))
                        connected = false
                        ws = null
                    }
                    else -> {
                        // "connect"（网络不通）或 "dead"（已握手但链路死亡）：退避重连（V2.17）。
                        onUnexpectedDrop()
                    }
                }
            }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            AppLogger.d(TAG, "onClosed 服务端关闭 code=$code reason='$reason'")
            connected = false
            ws = null
            // 升级成功、但握手尚未完成就被服务端以 0/空原因关闭 —— 属「被拒绝」语义，给明确报错，不重连。
            // V2.8X++：启动期诊断走 ConnectionIssue，不污染消息流。
            if (!handshakeDone && opened) {
                val did = deviceIdCache ?: "（未生成）"
                _events.tryEmit(
                    XiaozhiEvent.ConnectionIssue(
                        context.getString(R.string.assistant_connection_rejected, did),
                    ),
                )
                return
            }
            // V2.8X++：握手成功后的任何关闭（含 1000 正常）均视为「对话链结束」，
            // 统一走退避重连：① 避免 5 次重试用尽后彻底掉线；② 缩短对话间隙断连黑屏窗口。
            // 主动 disconnect（userDisconnect=true）由 onUnexpectedDrop 早退。
            onUnexpectedDrop()
        }
    }

    private fun handleServerMessage(text: String) {
        val root = (json.parseToJsonElement(text) as? JsonObject) ?: return
        val type = root["type"]?.jsonPrimitive?.content
        AppLogger.d(TAG, "← 处理消息 type=$type")
        when (type) {
            "hello" -> {
                handshakeDone = true
                sessionId = root["session_id"]?.jsonPrimitive?.content
                AppLogger.i(TAG, "✅ 握手成功 session_id=$sessionId")
                // 服务端 hello 响应：携带其音频参数（采样率），用于后续 TTS 解码/播放对齐。
                val ap = root["audio_params"] as? JsonObject
                val sr = ap?.get("sample_rate")?.jsonPrimitive?.content?.toIntOrNull()
                if (sr != null && sr > 0) {
                    currentSampleRate = sr
                    codec.preferredDecodeRate = sr
                }
                // 握手成功：重连计数清零并广播连接恢复（V2.17）。
                reconnectAttempt = 0
                _events.tryEmit(XiaozhiEvent.Connected)
                // V2.8X++：去掉 hello 响应的 message 字段自动展示——
                // 「默认进来是空白的」，不主动 seed 任何助手消息。
                // 用户首条消息送达后才会出现第一条助手回复。
            }
            "welcome" -> {
                // 按落地文档 4.1，拦截官方默认欢迎话术；人设已在 hello.prompt 中配置。
            }
            "stt" -> {
                val t = root["text"]?.jsonPrimitive?.content
                if (!t.isNullOrBlank()) _events.tryEmit(XiaozhiEvent.SttText(t))
            }
            "llm" -> {
                val raw = root["text"]?.jsonPrimitive?.content
                // V2.8X+：剥离多模态资源 token（@image#xxx），避免技术串污染消息列表；
                // 整句被过滤为空则不 emit（避免空气泡）；TTS 二进制帧不受影响。
                val t = raw?.let { MessageTextFilter.strip(it) }
                if (!t.isNullOrBlank()) _events.tryEmit(XiaozhiEvent.LlmText(t))
            }
            "tts" -> {
                // 服务端 TTS：sentence_start 携带助手回复文本（界面展示），二进制帧携带 Opus 音频（见
                // onMessage）。start 携带采样率用于初始化播放器，stop 结束播放。
                val state = root["state"]?.jsonPrimitive?.content
                val text = root["text"]?.jsonPrimitive?.content
                val sr = root["sample_rate"]?.jsonPrimitive?.content?.toIntOrNull()
                when (state) {
                    "start" -> {
                        if (sr != null && sr > 0) {
                            currentSampleRate = sr
                            codec.preferredDecodeRate = sr
                            AppLogger.d(TAG, "tts.start 服务端采样率=${sr}Hz，初始化播放器")
                        }
                        if (!ttsSuppressed) runCatching { player.init(currentSampleRate) }
                            .onFailure { AppLogger.e(TAG, "初始化 AudioPlayer 失败", it) }
                    }
                    "sentence_start" -> {
                        // 仅以 sentence_start 的文本作为可见回复，避免 sentence_end 重复追加。
                        // V2.8X+：剥离多模态资源 token（@image#xxx），整句被过滤为空则不 emit
                        // （避免空气泡）。TTS 音频路径不受影响。
                        val cleaned = text?.let { MessageTextFilter.strip(it) }
                        if (!cleaned.isNullOrBlank()) _events.tryEmit(XiaozhiEvent.LlmText(cleaned))
                    }
                    "stop" -> {
                        runCatching { player.release() }
                    }
                    else -> Unit // sentence_end 等无需处理
                }
            }
            "mcp" -> {
                // V2.8X+ 关键修复：服务端用的是 MCP 协议（JSON-RPC 2.0 信封），不是旧的"tool 顶层字段"格式。
                // 旧实现只读 root["tool"]，遇到 {"method":"initialize"} 的握手消息就静默丢弃 →
                // 服务端等 25s 收不到 initialize 响应 → 整体超时关闭。修复：识别 payload.method 分支处理。
                val payload = root["payload"] as? JsonObject
                if (payload != null) {
                    handleMcpJsonRpc(payload)
                    return
                }
                // 旧格式（无 payload 信封）：{type:"mcp", tool, arguments, result}，保持兼容
                val tool = root["tool"]?.jsonPrimitive?.content ?: return
                val argsElement = root["arguments"]
                val argsMap = jsonElementToMap(argsElement)
                scope?.launch {
                    // 真实模式下传输层需代表服务端执行工具并回执结果：先解析草稿，再提交落库。
                    val draft = runCatching { mcpTools.handle(XiaozhiEvent.McpToolCall(tool, argsMap)) }.getOrNull()
                    val res = if (draft != null) {
                        runCatching { mcpTools.commit(draft) }
                            .getOrElse { XiaozhiMcpTools.ToolResult(false, it.message ?: "error", null) }
                    } else {
                        XiaozhiMcpTools.ToolResult(false, mcpTools.unknownToolMessage(tool), null)
                    }
                    val resultJson = buildJsonObject {
                        put("success", JsonPrimitive(res.ok))
                        put("message", JsonPrimitive(res.message))
                    }.toString()
                    val reply = buildJsonObject {
                        put("type", "mcp")
                        put("tool", tool)
                        if (argsElement != null) put("arguments", argsElement)
                        put("result", JsonPrimitive(resultJson))
                    }
                    ws?.send(reply.toString())
                    _events.tryEmit(XiaozhiEvent.McpToolResult(res.message, res.ok))
                }
            }
            "goodbye", "abort" -> {
                connected = false
                ws = null
                _events.tryEmit(XiaozhiEvent.Disconnected)
            }
            else -> Unit
        }
    }

    /**
     * V2.8X+ MCP JSON-RPC 2.0 协议处理。
     *
     * 背景：xiaozhi.me 官方云从 v1.6+ 起在握手后用标准 MCP 协议（JSON-RPC 2.0 信封）
     * 与客户端做初始化/工具调用协商。服务端握手成功后会**先**发 `method=initialize` 请求，
     * 客户端**必须**回应一个 `result` 才会进入正常 listen 流程。漏回 initialize 是上一版
     * 「25 秒后服务端关连接」的根因。
     *
     * 处理三种方法：
     *  - `initialize`        → 回 `{protocolVersion, capabilities, clientInfo}` result
     *  - `notifications/initialized` → 服务端广播"已就绪"，**不回**（标准 JSON-RPC 通知语义）
     *  - `tools/list`        → 回 `result.tools = [create_task schema]`
     *  - `tools/call`        → 转发给 mcpTools 执行，回 `result.content[].text`
     *  - 其他 method         → 回 JSON-RPC error（method_not_found），不静默
     */
    private fun handleMcpJsonRpc(payload: JsonObject) {
        val sock = ws ?: return
        val method = payload["method"]?.jsonPrimitive?.content
        val id = payload["id"]?.jsonPrimitive?.content
        AppLogger.d(TAG, "mcp JSON-RPC method=$method id=$id")
        when (method) {
            "initialize" -> {
                // 协议版本与能力集必须回显服务端给的；capabilities 我们上报空集（不主动启用 vision 等服务端能力）
                val srvParams = payload["params"] as? JsonObject
                val protocolVersion = srvParams?.get("protocolVersion")?.jsonPrimitive?.content ?: "2024-11-05"
                val reply = buildJsonObject {
                    put("type", "mcp")
                    put("payload", buildJsonObject {
                        put("jsonrpc", "2.0")
                        put("id", JsonPrimitive(id ?: "1"))
                        put("result", buildJsonObject {
                            put("protocolVersion", JsonPrimitive(protocolVersion))
                            put("capabilities", buildJsonObject {})
                            put("clientInfo", buildJsonObject {
                                put("name", JsonPrimitive("TickClear"))
                                put("version", JsonPrimitive("1.0"))
                            })
                        })
                    })
                }
                runCatching { sock.send(reply.toString()) }
                    .onSuccess { AppLogger.d(TAG, "→ mcp initialize response sent (protocolVersion=$protocolVersion)") }
                    .onFailure { AppLogger.e(TAG, "发送 mcp initialize 失败", it) }
            }
            "notifications/initialized" -> {
                // 服务端"已就绪"广播，标准 JSON-RPC 通知语义：无需回执
                AppLogger.d(TAG, "← mcp notifications/initialized（服务端就绪）")
            }
            "tools/list" -> {
                // ⚠️ MCP 规范要求 result.tools 是 JSON 数组 [{...}]，不是 {"0":{...}} 对象。
                // 旧实现误用对象导致服务端解析失败、listen 路径被静默中断。
                val createTaskSchema = buildJsonObject {
                    put("name", JsonPrimitive("create_task"))
                    put("description", JsonPrimitive("创建一个待办任务/提醒"))
                    put("inputSchema", buildJsonObject {
                        put("type", "object")
                        put("properties", buildJsonObject {
                            put("title", buildJsonObject {
                                put("type", "string")
                                put("description", "任务标题")
                            })
                            put("date", buildJsonObject {
                                put("type", "string")
                                put("description", "日期 YYYY-MM-DD")
                            })
                            put("minute", buildJsonObject {
                                put("type", "integer")
                                put("minimum", 0); put("maximum", 1439)
                                put("description", "当天分钟 0-1439")
                            })
                            put("repeatType", buildJsonObject {
                                put("type", "string")
                                // enum 必须是字符串数组
                                put("enum", buildJsonArray {
                                    add(JsonPrimitive("NONE"))
                                    add(JsonPrimitive("DAILY"))
                                    add(JsonPrimitive("WEEKLY"))
                                    add(JsonPrimitive("MONTHLY"))
                                })
                                put("description", "重复类型 NONE/DAILY/WEEKLY/MONTHLY")
                            })
                            put("weekdays", buildJsonObject {
                                put("type", "string")
                                put("description", "WEEKLY 时使用，1-7 逗号分隔（周一=1）")
                            })
                            put("reminderOffset", buildJsonObject {
                                put("type", "integer")
                                put("description", "提前多少分钟提醒（>0）")
                            })
                        })
                        // required 必须是字符串数组
                        put("required", buildJsonArray {
                            add(JsonPrimitive("title"))
                            add(JsonPrimitive("date"))
                        })
                    })
                }
                val createHabitSchema = buildJsonObject {
                    put("name", JsonPrimitive("create_habit"))
                    put("description", JsonPrimitive(context.getString(R.string.xiaozhi_habit_desc)))
                    put("inputSchema", buildJsonObject {
                        put("type", "object")
                        put("properties", buildJsonObject {
                            put("title", buildJsonObject {
                                put("type", "string")
                                put("description", "习惯名称")
                            })
                            put("emoji", buildJsonObject {
                                put("type", "string")
                                put("description", "习惯图标 emoji，可选")
                            })
                            put("repeatType", buildJsonObject {
                                put("type", "string")
                                put("enum", buildJsonArray {
                                    add(JsonPrimitive("NONE"))
                                    add(JsonPrimitive("DAILY"))
                                    add(JsonPrimitive("WEEKLY"))
                                })
                                put("description", "重复类型 NONE/DAILY/WEEKLY，缺省每天")
                            })
                            put("weekdays", buildJsonObject {
                                put("type", "string")
                                put("description", "WEEKLY 时使用，1-7 逗号分隔（周一=1）")
                            })
                            put("reminderMin", buildJsonObject {
                                put("type", "integer")
                                put("description", "每日提醒分钟 0-1439，-1 或不传表示不提醒")
                            })
                        })
                        put("required", buildJsonArray { add(JsonPrimitive("title")) })
                    })
                }
                val reply = buildJsonObject {
                    put("type", "mcp")
                    put("payload", buildJsonObject {
                        put("jsonrpc", "2.0")
                        put("id", JsonPrimitive(id ?: "2"))
                        put("result", buildJsonObject {
                            put("tools", buildJsonArray {
                                add(createTaskSchema)
                                add(createHabitSchema)
                            })
                        })
                    })
                }
                runCatching { sock.send(reply.toString()) }
                    .onSuccess { AppLogger.d(TAG, "→ mcp tools/list response sent (1 tool)") }
                    .onFailure { AppLogger.e(TAG, "发送 mcp tools/list 失败", it) }
            }
            "tools/call" -> {
                // 真正的工具调用，结构：params.name / params.arguments
                val params = payload["params"] as? JsonObject
                val tool = params?.get("name")?.jsonPrimitive?.content
                val argsElement = params?.get("arguments")
                if (tool == null) {
                    sendMcpError(id, -32602, "params.name 缺失")
                    return
                }
                val argsMap = jsonElementToMap(argsElement)
                scope?.launch {
                    val draft = runCatching { mcpTools.handle(XiaozhiEvent.McpToolCall(tool, argsMap)) }.getOrNull()
                    val res = if (draft != null) {
                        runCatching { mcpTools.commit(draft) }
                            .getOrElse { XiaozhiMcpTools.ToolResult(false, it.message ?: "error", null) }
                    } else {
                        XiaozhiMcpTools.ToolResult(false, mcpTools.unknownToolMessage(tool), null)
                    }
                    // MCP 标准回执：result.content 必须是 JSON 数组 [{type:"text", text:"..."}]
                    val reply = buildJsonObject {
                        put("type", "mcp")
                        put("payload", buildJsonObject {
                            put("jsonrpc", "2.0")
                            put("id", JsonPrimitive(id ?: "3"))
                            put("result", buildJsonObject {
                                put("content", buildJsonArray {
                                    add(buildJsonObject {
                                        put("type", "text")
                                        put("text", JsonPrimitive(res.message))
                                    })
                                })
                                put("isError", JsonPrimitive(!res.ok))
                            })
                        })
                    }
                    runCatching { sock.send(reply.toString()) }
                        .onSuccess { AppLogger.d(TAG, "→ mcp tools/call $tool response sent (ok=${res.ok})") }
                        .onFailure { AppLogger.e(TAG, "发送 mcp tools/call 失败", it) }
                    _events.tryEmit(XiaozhiEvent.McpToolResult(res.message, res.ok))
                }
            }
            else -> {
                // 不认识的 method：按 JSON-RPC 2.0 回 method_not_found（-32601），避免静默
                AppLogger.w(TAG, "mcp 未知 method=$method，回 JSON-RPC error")
                sendMcpError(id, -32601, "Method not found: $method")
            }
        }
    }

    private fun sendMcpError(id: String?, code: Int, message: String) {
        val sock = ws ?: return
        val reply = buildJsonObject {
            put("type", "mcp")
            put("payload", buildJsonObject {
                put("jsonrpc", "2.0")
                if (id != null) put("id", JsonPrimitive(id))
                put("error", buildJsonObject {
                    put("code", JsonPrimitive(code))
                    put("message", JsonPrimitive(message))
                })
            })
        }
        runCatching { sock.send(reply.toString()) }
            .onFailure { AppLogger.e(TAG, "发送 mcp error 失败", it) }
    }

    private fun jsonElementToMap(element: JsonElement?): Map<String, Any?> {
        if (element !is JsonObject) return emptyMap()
        val map = mutableMapOf<String, Any?>()
        for ((k, v) in element) {
            map[k] = when (v) {
                is JsonPrimitive -> {
                    val c = v.content
                    c.toIntOrNull() ?: c.toBooleanStrictOrNull() ?: c
                }
                is JsonObject -> jsonElementToMap(v)
                else -> null
            }
        }
        return map
    }
}
