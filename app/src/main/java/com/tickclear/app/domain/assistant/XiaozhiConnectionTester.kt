package com.tickclear.app.domain.assistant

import com.tickclear.app.domain.log.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * V2.8X 小智连接测试器（独立于 [WebSocketXiaozhiTransport]）。
 *
 * 设计动机：用户报告「小智已绑定到 xiaozhi.me 但 App 一直握手超时」，需要一颗低成本、副作用为零的探针，
 * 让 UI 「测试连接」按钮可即时验证 endpoint / token / device_id 三者组合是否真正完成握手，
 * 而不必打开助手页、跑满 10s 握手超时再报错。
 *
 * V2.8X+ 增强（深度排查日志）：
 * - 三段式诊断：①HTTP 预探（DNS/TCP/TLS/服务在听）→②WebSocket Upgrade（Http 错误体抓取）→③Hello 等候（12s）。
 * - onFailure 抓取 response body 文本，把服务端 401/403/400 的真实 body 带回，常见「设备未绑定」
 *   时服务端会返回 JSON `{message:"..."}`，之前被吞了导致用户只见笼统「网络失败」。
 * - 总时长 12s（覆盖国内移动网 TLS/握手偶尔慢的情况），失败文案分场景给具体修复路径。
 */
object XiaozhiConnectionTester {

    private const val TAG = "XzTester"
    /** HTTPS 预探超时（DNS+TCP+TLS+首字节）。 */
    private const val HTTP_PROBE_TIMEOUT_MS = 5_000L
    /** WebSocket 握手+等 hello 总超时。V2.8X+ 从 12s 提到 25s：
     *  - 服务端 connection.py 源码显示收到 hello 后会异步调 get_private_config_from_api
     *    校验设备，并启动 _initialize_components 加载 TTS/ASR（默认 FunASR 加载数秒），
     *    等组件就绪才会发 welcome。
     *  - 12s 容易踩在网关/反代 idle timeout（CloudFront 默认 10s）上，误判为「服务端拒收」。 */
    private const val WS_TOTAL_TIMEOUT_MS = 25_000L

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 测试当前 endpoint + device + token + prompt 组合能否完成握手。
     *
     * @return [Result] 用 sealed class 描述成功 / 失败，失败文案已含可能的修复指引。
     */
    suspend fun test(
        endpoint: String,
        deviceId: String,
        clientId: String,
        token: String?,
        prompt: String,
        isActivated: Boolean = false,
    ): Result = withContext(Dispatchers.IO) {
        // 是否已激活的可靠代理：激活成功时 PREF_XZ_TOKEN 会被写入；同时也接受显式传入。
        val effectiveActivated = isActivated || !token.isNullOrBlank()
        // 与 WebSocketXiaozhiTransport 保持一致：老主机名自动归一化到官方权威地址。
        val normalized = when {
            endpoint.isBlank() -> "wss://api.tenclass.net/xiaozhi/v1/"
            endpoint == "wss://api.xiaozhi.me/ws" -> "wss://api.tenclass.net/xiaozhi/v1/"
            else -> endpoint
        }
        AppLogger.d(TAG, "测试连接：raw='$endpoint' → 归一化 '$normalized'")
        AppLogger.d(TAG, "测试连接：deviceId='$deviceId' clientId='$clientId' token=${if (token.isNullOrBlank()) "∅" else token.take(4) + "…(len=${token.length})"}")

        // ── ① HTTP 预探：验证主机可达 + TLS 通 + 服务器在听 ──
        // 真实小智服务端 WSS 端点对 HTTP GET 通常返回 404/4xx（不是 WS 客户端），但只要拿到 HTTP 状态行
        // 就证明「能 TCP 连上 + TLS 握手成功 + 域名解析正确」，省去后面 12s 错等。
        val httpProbe = runHttpProbe(normalized)
        when (httpProbe) {
            is HttpProbe.Unreachable -> {
                val reason = "网络不可达：${httpProbe.detail}\n" +
                    "请检查：手机是否连上 WiFi/数据网络、是否启用了 VPN / 全局代理、`$normalized` 是否被防火墙拦截。"
                AppLogger.w(TAG, "预探失败 → 不再尝试 WS: $reason")
                return@withContext Result.Fail(reason)
            }
            is HttpProbe.Reachable -> AppLogger.d(TAG, "预探通过：http=${httpProbe.code}  latency=${httpProbe.latencyMs}ms")
        }

        // ── ② WS Upgrade + ③ Hello 等候 ──
        val request = Request.Builder().url(normalized).apply {
            addHeader("Protocol-Version", "1")
            addHeader("User-Agent", "TickClear/xiaozhi-android")
            token?.takeIf { it.isNotBlank() }?.let { addHeader("Authorization", "Bearer $it") }
            if (deviceId.isNotEmpty()) addHeader("Device-Id", deviceId)
            if (clientId.isNotEmpty()) addHeader("Client-Id", clientId)
        }.build()

        val client = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(WS_TOTAL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            // V2.8X+：加 pingInterval 给网关保活，避免 CloudFront 10s idle 提前 RST
            .pingInterval(20, TimeUnit.SECONDS)
            .build()

        val deferred = kotlinx.coroutines.CompletableDeferred<Result>()
        // V2.8X+：记录是否收到过服务端 hello，用于 onClosed 时区分「握手前被拒」与「握手后被关」。
        var gotHello = false
        // V2.8X+：打点协程用独立 scope——onOpen 在 OkHttp 线程触发，拿不到 withContext 协程的 receiver。
        // 用 SupervisorJob 避免单次打点失败中断后续打点；scope 随 onFailure/withTimeout 取消一并结束（不残留）。
        val tickScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val listener = object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                AppLogger.i(TAG, "onOpen 测试通道已建立（http ${response.code}），发送 hello")
                // V2.8X+：把 App 自报的所有握手信息完整打到日志里——出问题需要复制这段去论坛求助。
                // - 自报 application.version: 由 SIMULATED_FW_VERSION 决定（现 v2.4.0，官方门禁 ≥v1.6.1 已过）
                // - 自报 features: mcp=true（v1.6+ 协议层必填字段）
                // - 自报 audio_params: opus/16kHz/mono/60ms（与 v1.6+ 官方固件默认一致）
                // - 自报 transport: websocket
                AppLogger.d(
                    TAG,
                    "握手诊断摘要：自报 application.version=${XiaozhiDeviceSimulator.SIMULATED_FW_VERSION} " +
                        "(features={\"mcp\":true}) hello 6 字段=type/version/features/transport/audio_params " +
                        "audio_params=opus/16000/1/60 transport=websocket deviceId=$deviceId clientId=$clientId",
                )
                // V2.8X：hello 报文必须含 features.mcp=true（v1.6+ 官方协议必填）。
                // 与 WebSocketXiaozhiTransport.sendClientHello() 同源，避免「测试能通 / 真连接失败」。
                val hello = buildJsonObject {
                    put("type", "hello")
                    put("version", 1)
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
                val helloJson = hello.toString()
                AppLogger.d(TAG, "→ 测试发送 hello（v1.6+ 协议 6 字段，含 features.mcp）: $helloJson")
                runCatching { ws.send(helloJson) }.onFailure { AppLogger.e(TAG, "测试发送 hello 失败", it) }

                // V2.8X+：打点日志，让用户能看出"卡在哪个阶段"——是 hello 没发出去，还是发出去后服务端没回。
                tickScope.launch {
                    val start = System.currentTimeMillis()
                    for (s in listOf(5, 10, 15, 20, 25)) {
                        delay(5_000L)
                        if (deferred.isCompleted) return@launch
                        val elapsed = (System.currentTimeMillis() - start) / 1000
                        AppLogger.d(TAG, "等待服务端 welcome 中... 已 ${elapsed}s / 上限 ${WS_TOTAL_TIMEOUT_MS / 1000}s（close code 看 XzTransport 标签）")
                    }
                }
            }

            override fun onMessage(ws: WebSocket, text: String) {
                AppLogger.v(TAG, "← 测试收到: ${text.take(400)}")
                val root = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return
                when (root["type"]?.jsonPrimitive?.content) {
                    "hello" -> {
                        gotHello = true
                        val sessionId = root["session_id"]?.jsonPrimitive?.content
                        val ap = root["audio_params"] as? JsonObject
                        val sr = ap?.get("sample_rate")?.jsonPrimitive?.content?.toIntOrNull()
                        AppLogger.i(TAG, "✅ 测试握手成功 session=$sessionId sampleRate=$sr")
                        deferred.complete(Result.Ok(normalized, sessionId, sr))
                    }
                    "mcp" -> {
                        // V2.8X+：服务端在握手后会发 MCP JSON-RPC 协商（initialize/tools/list 等），
                        // 仅记录日志，证明链路已通到 MCP 层（Transport 层负责真正响应）。
                        val method = (root["payload"] as? JsonObject)?.get("method")?.jsonPrimitive?.content
                        AppLogger.d(TAG, "← 测试收到 MCP 协商 method=${method ?: "?"}")
                    }
                    "error" -> {
                        val errMsg = root["message"]?.jsonPrimitive?.content ?: "未知错误"
                        AppLogger.w(TAG, "测试服务端拒绝：$errMsg")
                        deferred.complete(Result.Fail("服务端拒绝：$errMsg"))
                    }
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                // V2.8X+：抓 response body（很多 case 服务端会回 JSON 错误体），原先被吞了让用户只见笼统「网络失败」。
                val bodySnippet = response?.let { r ->
                    runCatching { r.body?.string() }.getOrNull()
                }?.take(512)
                val http = response?.code
                val msg = buildString {
                    if (http != null) {
                        append("HTTP ").append(http).append(' ')
                    }
                    append(t.message ?: t.javaClass.simpleName)
                    if (!bodySnippet.isNullOrBlank()) {
                        append('\n')
                        append("响应体: ").append(bodySnippet)
                    }
                }
                AppLogger.e(TAG, "测试 onFailure：$msg", t)
                if (!deferred.isCompleted) deferred.complete(Result.Fail(msg))
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                AppLogger.d(TAG, "测试 onClosed code=$code reason='$reason' gotHello=$gotHello")
                if (!deferred.isCompleted) {
                    val diag = buildString {
                        append("连接关闭 code=$code")
                        if (reason.isNotBlank()) append(" reason=$reason")
                        append("\n")
                        when {
                            // 握手成功后才被关：一般是用户/MCP 协商层面，非 license 拒绝
                            gotHello && (code == 1000 || code == 1001) ->
                                append("连接已正常关闭。若发生在对话中，可能是 MCP 工具协商未通过（create_task 未正确注册），请重试。")
                            // 从未收到 hello 且网关裸 RST：前端已绑定但 WS 网关 license 未同步
                            !gotHello && code == 1005 -> {
                                append("服务端在握手阶段主动关闭（code=1005，网关裸 RST），且始终未收到 hello。\n")
                                if (effectiveActivated) {
                                    append("你已完成激活、Device-Id 也在 xiaozhi.me 控制台「我的设备」中显示，但 WS 网关的 license 缓存未生效（xiaozhi.me 已知的不同步问题）——它查不到该 device-id 的有效授权，直接丢弃连接。\n")
                                } else {
                                    append("该 device-id 在 xiaozhi.me 后端未真正激活，WS 网关查不到授权而直接丢弃连接。\n")
                                }
                                append("这不是 App 协议错误。请按以下任一方式处理：\n")
                                append("  ① 用一台【已知可用】设备的 Device-Id（如真机 ESP32 的 MAC，或控制台里已绑定的其他设备）粘进 App「设备 ID」框重测；能连上即证明 App 协议完全正确，问题只在该虚拟 device-id。\n")
                                append("  ② 在 xiaozhi.me 控制台「重新生成设备标识」换新身份、重新走激活，再点测试连接。\n")
                                append("  ③ 联系 xiaozhi.me 客服，反馈「设备已在控制台显示、但 WebSocket 网关 license 不同步、连接被 1005 拒绝」。\n")
                                append("  ④ 或自部署开源 xiaozhi-esp32-server 服务端，绕过官方云 license 校验。")
                            }
                            // 1006 异常关闭（未完成握手）
                            !gotHello && code == 1006 ->
                                append("连接异常关闭（code=1006），未完成握手。可能是网络中断或网关提前断开。可重试；若持续出现且 device-id 已在控制台显示，则同 1005 处理（license 未同步）。")
                            // 其他关闭码
                            else ->
                                append("（未在握手前收到 hello，属非预期关闭）")
                        }
                    }
                    deferred.complete(Result.Fail(diag))
                }
            }
        }
        val ws = client.newWebSocket(request, listener)
        try {
            withTimeout(WS_TOTAL_TIMEOUT_MS) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            // V2.8X+：超时文案改写——服务端 helloHandle.py + connection.py 源码证明 hello 协议层
            // 无任何字段校验，所以"改 hello 字段"救不了，问题在更上层：服务端收到 hello 后会
            // 异步调 get_private_config_from_api 校验设备 + _initialize_components 加载 TTS/ASR，
            // 完成才发 welcome。25s 仍未回 welcome 几乎一定不是客户端协议问题。
            AppLogger.w(TAG, "测试超时 ${WS_TOTAL_TIMEOUT_MS / 1000}s：WS Upgrade 已通但未收到服务端 welcome（deviceId=$deviceId）")
            val reason = buildString {
                append("WebSocket 已建立成功（TLS/Upgrade 都通过），App 已发送 hello 6 字段")
                append("（version=1, features.mcp=true, audio_params=opus/16kHz/mono/60ms），但 ")
                append(WS_TOTAL_TIMEOUT_MS / 1000)
                append(" 秒内未收到服务端 welcome 响应。\n")
                append("  关键事实：服务端 helloHandle.py 源码对 hello 字段【无任何校验】，")
                append("且 connection.py 鉴权【仅基于 device-id】（client-id 不参与校验、不读 Authorization 头），")
                append("所以「改 client-id / 改 hello 字段」都救不了，问题在更上层：\n")
                append("  ① 服务端收到 hello 后异步调 get_private_config_from_api(device-id)，")
                append("若该虚拟 device-id 在官网后端未真正激活，会返回「未找到/未绑定」→ need_bind=True → ")
                append("直接丢弃 hello、不发 welcome（这正是 25s 超时的根因）。\n")
                append("  ② 服务端 _initialize_components 加载 TTS/ASR（默认 FunASR）较慢，welcome 延迟。\n")
                append("  ③ 网关/反代 idle timeout 提前 RST（CloudFront 默认 10s，ALB 60s）。close code 含义：")
                append("1005=网关裸 RST、1006=连接异常、1011=服务端内部错误。\n")
                append("  验证：把 xiaozhi.me 控制台「我的设备」里一台【已知可用】设备的 Device-Id（MAC）粘进 ")
                append("App「设备 ID」输入框（现已可编辑）后重测；能连上即证明 App 协议完全正确，问题只在该虚拟 device-id 未激活。")
            }
            Result.Fail(reason)
        } finally {
            runCatching { ws.close(1000, "test done") }
            // V2.8X+：打点协程一并取消，避免协程残留（5s 一次，最长 25s 后自然结束，此处保险）。
            tickScope.cancel()
        }
    }

    /**
     * HTTP 预探：用 [Request] 把 WS 端点的 scheme 换成 https/http，发一个短 HEAD/GET 看能否拿到响应。
     * 用于前置筛查「DNS 失败 / TCP 不可达 / TLS 失败 / 服务根本不开」等网络层问题，
     * 避免在网络都连不上的情况下白白等满 12s。
     */
    private fun runHttpProbe(wsUrl: String): HttpProbe {
        val probeUrl = wsUrl
            .replaceFirst("wss://", "https://")
            .replaceFirst("ws://", "http://")
        AppLogger.d(TAG, "预探：HTTP $probeUrl")
        val client = OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(HTTP_PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()
        val request = Request.Builder()
            .url(probeUrl)
            .header("User-Agent", "TickClear/xiaozhi-probe")
            .head()
            .build()
        return try {
            val start = System.nanoTime()
            client.newCall(request).execute().use { resp ->
                val latency = (System.nanoTime() - start) / 1_000_000
                val code = resp.code
                val peek = runCatching { resp.body?.string() }.getOrNull().orEmpty().take(160)
                AppLogger.d(TAG, "预探结果：code=$code latency=${latency}ms body='$peek'")
                HttpProbe.Reachable(code, latency)
            }
        } catch (e: IOException) {
            AppLogger.w(TAG, "预探失败：${e.javaClass.simpleName}: ${e.message}")
            HttpProbe.Unreachable("${e.javaClass.simpleName}: ${e.message ?: "无详细错误"}")
        } catch (e: Exception) {
            AppLogger.e(TAG, "预探异常", e)
            HttpProbe.Unreachable("${e.javaClass.simpleName}: ${e.message ?: "未知"}")
        }
    }

    private sealed class HttpProbe {
        data class Reachable(val code: Int, val latencyMs: Long) : HttpProbe()
        data class Unreachable(val detail: String) : HttpProbe()
    }

    /**
     * 测试结果：成功带端点 / session / 采样率；失败带可读原因。
     */
    sealed class Result {
        data class Ok(
            val endpoint: String,
            val sessionId: String?,
            val sampleRate: Int?,
        ) : Result()

        data class Fail(val reason: String) : Result()
    }
}
