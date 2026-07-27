package com.tickclear.app.domain.assistant

import com.tickclear.app.data.SecureStore
import com.tickclear.app.domain.repository.SettingsRepository
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
 * - 语音模式经二进制帧收发 Opus：上行麦克风编码帧，下行服务端 TTS 解码后 AudioTrack 播放
 *   （设备无 Opus 编码器时由 UI 降级为文字，见 OpusCodec / P6.3）。
 */
class WebSocketXiaozhiTransport(
    private val settings: SettingsRepository,
    private val mcpTools: XiaozhiMcpTools,
    private val codec: OpusCodec,
) : XiaozhiTransport {

    private val _events = MutableSharedFlow<XiaozhiEvent>(extraBufferCapacity = 64)
    override val events: Flow<XiaozhiEvent> = _events.asSharedFlow()

    private val json = Json { ignoreUnknownKeys = true }

    // 服务端 TTS 下行：解码 Opus → PCM → AudioTrack 播放（best-effort，失败静默丢弃）。
    private val player = AudioPlayer()

    // 心跳保活（V2.17）：WebSocket 协议层 ping/pong，20s 间隔；pong 超时即触发 onFailure → 退避重连，
    // 兼作 NAT/代理空闲链路保活与死链检测，无需应用层自定义心跳报文。
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private companion object {
        /** 意外断线最大自动重连次数（1s/2s/4s/8s/16s 指数退避）。 */
        const val MAX_RECONNECT = 5
    }

    @Volatile private var ws: WebSocket? = null
    @Volatile private var sessionId: String? = null
    @Volatile private var connected = false

    /** 设备标识缓存（握手头 + hello.device_id 共用），openSocket 时刷新。 */
    @Volatile private var deviceIdCache: String? = null
    @Volatile private var clientIdCache: String? = null

    /** V2.8X：hello 握手是否完成（用于超时守卫，避免设备未绑定时无限「连接中」）。 */
    @Volatile private var handshakeDone = false

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
        // 若重连进行中，先取消旧作用域，避免旧延迟重连与新连接竞建重复 WebSocket（P2）。
        scope?.cancel()
        scope = null
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        openSocket()
        // V2.8X：握手超时守卫（10s）。小智官方服务要求设备先在 xiaozhi.me 控制台绑定，
        // 未绑定/绑定失效时服务端不会响应 hello，连接会无限「连接中」且毫无报错。
        // 超时给出明确错误，便于用户排查（而非无日志无报错）。
        scope?.launch {
            delay(10_000)
            if (!handshakeDone && !userDisconnect && ws != null) {
                _events.tryEmit(
                    XiaozhiEvent.Error(
                        "握手超时：服务端未响应。请确认该虚拟设备已在 xiaozhi.me 控制台绑定" +
                            "（设置→助手→小智→激活设备 获取 6 位验证码并添加），或检查网络。",
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
            // 读取设备标识（模拟 ESP32），用于官方服务端设备认证
            val deviceId = settings.xzDeviceId.first().takeIf { it.isNotEmpty() }
            val clientId = settings.xzClientId.first().takeIf { it.isNotEmpty() }
            deviceIdCache = deviceId
            clientIdCache = clientId
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
            _events.tryEmit(XiaozhiEvent.Error(e.message ?: "unknown"))
            return
        }
        try {
            ws = client.newWebSocket(request, listener)
            connected = true
        } catch (e: Exception) {
            connected = false
            ws = null
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
        val sock = ws ?: return
        // 文本输入：先发 listen 开始，再发结束并携带识别文本，模拟 ASR 结果。
        sock.send(
            buildJsonObject {
                put("type", "listen")
                put("mode", "manual")
                put("state", "start")
                sessionId?.let { put("session_id", it) }
            }.toString(),
        )
        sock.send(
            buildJsonObject {
                put("type", "listen")
                put("mode", "manual")
                put("state", "stop")
                put("text", text)
                sessionId?.let { put("session_id", it) }
            }.toString(),
        )
    }

    override suspend fun sendListenStart() {
        val sock = ws ?: return
        sock.send(
            buildJsonObject {
                put("type", "listen")
                put("mode", "realtime")
                put("state", "start")
                sessionId?.let { put("session_id", it) }
            }.toString(),
        )
    }

    override suspend fun sendListenStop() {
        val sock = ws ?: return
        sock.send(
            buildJsonObject {
                put("type", "listen")
                put("mode", "realtime")
                put("state", "stop")
                sessionId?.let { put("session_id", it) }
            }.toString(),
        )
    }

    /** 客户端 hello 握手：在收到服务端 hello 后回送，携带 prompt/token 完成设备鉴权。 */
    private fun sendClientHello() {
        val sock = ws ?: return
        val hello = buildJsonObject {
            put("type", "hello")
            put("version", 1)
            put("transport", "websocket")
            // 设备标识（与握手头一致），部分网关以 hello.device_id / client_id 作为会话归属。
            deviceIdCache?.let { put("device_id", it) }
            clientIdCache?.let { put("client_id", it) }
            put(
                "audio_params",
                buildJsonObject {
                    put("format", "opus")
                    put("sample_rate", 16000)
                    put("channels", 1)
                    put("frame_duration", 60)
                },
            )
            put("prompt", pendingPrompt)
            pendingToken?.takeIf { it.isNotBlank() }?.let { put("token", it) }
        }
        runCatching { sock.send(hello.toString()) }
    }

    override fun sendAudio(bytes: ByteArray) {
        // WebSocket 二进制帧承载 Opus 音频；OkHttp send 线程安全，可在采集线程直接调用。
        val sock = ws ?: return
        runCatching { sock.send(ByteString.of(*bytes)) }
    }

    override suspend fun disconnect() {
        userDisconnect = true
        // 无论如何都释放资源：旧实现在「重连中」(connected=false) 时早退，
        // 导致 AudioTrack / Opus MediaCodec 未释放、声音残留，且作用域/WS 滞留（P2）。
        connected = false
        handshakeDone = false
        runCatching { player.release() }
        // 会话结束释放 Opus MediaCodec，避免编解码器终生死占（下次 encode/decode 惰性重建）。
        runCatching { codec.release() }
        scope?.cancel()
        scope = null
        runCatching { ws?.close(1000, "bye") }
        ws = null
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            // 官方协议（xiaozhi.me / xiaozhi.dev / ESP32 固件三方一致）：连接建立后【客户端先发 hello】，
            // 服务端随后回 hello 完成握手。此前实现错误地等待服务端先发 hello，导致握手永不完成、
            // 一直「未连接」且无任何报错。修正：onOpen 立即发送客户端 hello。
            sendClientHello()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            runCatching { handleServerMessage(text) }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            // 断开过程中（connected=false）可能仍有排队的帧到达，跳过解码避免触碰已释放的 codec/player（L2）。
            if (!connected) return
            // 服务端 TTS 二进制 Opus 帧：按服务端声明的采样率解码后播放；任一环节失败则静默丢弃。
            runCatching {
                val pcm = codec.decodeFrame(bytes.toByteArray()) ?: return@runCatching
                if (player.init(currentSampleRate)) player.play(pcm)
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            // 网络异常/服务端崩溃等意外失败：走退避重连（V2.17）。
            onUnexpectedDrop()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            // 服务端优雅关闭视为会话结束，不自动重连（与 goodbye 语义一致）。
            connected = false
            ws = null
            _events.tryEmit(XiaozhiEvent.Disconnected)
        }
    }

    private fun handleServerMessage(text: String) {
        val root = (json.parseToJsonElement(text) as? JsonObject) ?: return
        when (val type = root["type"]?.jsonPrimitive?.content) {
            "hello" -> {
                handshakeDone = true
                sessionId = root["session_id"]?.jsonPrimitive?.content
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
                val welcome = root["message"]?.jsonPrimitive?.content
                if (!welcome.isNullOrBlank()) _events.tryEmit(XiaozhiEvent.LlmText(welcome))
            }
            "welcome" -> {
                // 按落地文档 4.1，拦截官方默认欢迎话术；人设已在 hello.prompt 中配置。
            }
            "stt" -> {
                val t = root["text"]?.jsonPrimitive?.content
                if (!t.isNullOrBlank()) _events.tryEmit(XiaozhiEvent.SttText(t))
            }
            "llm" -> {
                val t = root["text"]?.jsonPrimitive?.content
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
                        }
                        runCatching { player.init(currentSampleRate) }
                    }
                    "sentence_start" -> {
                        // 仅以 sentence_start 的文本作为可见回复，避免 sentence_end 重复追加。
                        if (!text.isNullOrBlank()) _events.tryEmit(XiaozhiEvent.LlmText(text))
                    }
                    "stop" -> {
                        runCatching { player.release() }
                    }
                    else -> Unit // sentence_end 等无需处理
                }
            }
            "mcp" -> {
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
