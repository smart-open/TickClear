package com.tickclear.app.domain.assistant

import android.content.Context
import com.tickclear.app.data.SecureStore
import com.tickclear.app.domain.log.AppLogger
import com.tickclear.app.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 小智 ESP32 设备模拟器（V2.8）。
 *
 * 模拟 ESP32 硬件设备的注册与激活流程，使 Android App 能以「虚拟设备」身份接入官方 xiaozhi.me：
 * 1. 生成/持久化设备标识（Device-Id = MAC 格式，Client-Id = UUID）
 * 2. 调用 OTA 接口完成设备注册，获取 6 位验证码
 * 3. 用户持验证码到 [xiaozhi.me](https://xiaozhi.me) 控制台 → 添加设备 → 绑定
 * 4. 绑定后 WebSocket 连接带上设备认证头即可正常通信
 *
 * ## 协议参考（对齐 py-xiaozhi 官方云已验证形态，2026-07-28 实证）
 * - OTA 端点：`POST https://api.tenclass.net/xiaozhi/ota/`
 * - OTA 请求头：`Device-Id` + `Client-Id` + `Activation-Version: 2` + `User-Agent` + `Accept-Language: zh-CN`
 *   （**绝不带** `Serial-Number` 头；OTA 体也不含 serial_number —— serial 只在 `/activate` 时提交）
 * - OTA 请求体：`{ application: {version, elf_sha256}, board: {type, name, ip, mac} }`
 * - `/activate` 体：`{ "Payload": { algorithm, serial_number, challenge, hmac } }`（外层 Payload 包裹）
 * - 响应（未绑定）：`{ activation: { code: "六位数字", challenge: "<uuid>" }, websocket: { url: "..." } }`
 * - WS 握手时同样需携带 `Device-Id` / `Client-Id` 头
 */
object XiaozhiDeviceSimulator {

    private const val TAG = "XzSim"

    private val json = Json { ignoreUnknownKeys = true }

    /** 官方 OTA 端点（xiaozhi.me）。自建服务器可修改。 */
    const val DEFAULT_OTA_URL = "https://api.tenclass.net/xiaozhi/ota/"

    /**
     * 模拟固件版本号（与 ESP32 固件保持一致的格式，如 "v2.4.0"）。
     *
     * ⚠️ 重要：官方 xiaozhi.me 服务端对固件版本有硬性门禁 **≥ v1.6.1**，
     * 低于此版本会在 WebSocket 握手（hello 之后）被服务端静默关闭（close_code=0, reason=''），
     * 表现为「连接中」几秒后断开、UI 收不到服务端 hello、调试日志里只有 onClosed。
     *
     * 旧值 "v1.0.0" 正是不通的根因——App 把自己伪装成一台 v1.0.0 老固件设备去注册/握手，
     * 被服务端版本门禁直接拒收。这里改为最新的稳定版 v2.4.0（2026-07-19 发布），
     * 既满足门禁，也贴近当前真实固件上报的版本。
     *
     * 注意：本常量只影响 OTA 注册体里的 `application.version`（固件版本标签），
     * 与 WebSocket 协议版本号无关——协议版本（`hello.version` / `Protocol-Version` 头）始终是 1，
     * 因为本 App 发送裸 Opus 二进制帧，对应官方 protocol v1。若日后官方服务端
     * 对 v2.x 固件强制要求二进制协议 v2/v3 封装，可把本值回退到 v1.9.4（v1 线最后一个稳定版）。
     *
     * V2.8X+：改为 internal 让 XiaozhiConnectionTester 等诊断探针能直接读到并打到日志。
     */
    internal const val SIMULATED_FW_VERSION = "v2.4.0"

    /**
     * OTA 注册结果。
     * @property code 6 位验证码（未绑定时非空，用户需去官网输入此码添加设备）
     * @property websocketUrl 服务端返回的 WebSocket 连接地址（可能负载均衡分配）
     * @property message 服务端附加消息（如前端跳转 URL）
     * @property error 错误信息（网络失败 / 解析失败时非空）
     */
    data class OtaResult(
        val code: String? = null,
        val websocketUrl: String? = null,
        val message: String? = null,
        val error: String? = null,
    ) {
        /** 是否成功获取到验证码（即设备尚未绑定，等待用户操作）。 */
        val needsBinding: Boolean get() = !code.isNullOrEmpty()
    }

    /**
     * 确保设备标识已生成并持久化。
     * 首次调用自动生成 MAC + UUID + 序列号 并写入 SecureStore/DataStore；
     * 后续调用直接返回已有值（幂等）。
     *
     * V2.8X++：deviceId 为空时**自动生成虚拟 MAC**（与 py-xiaozhi 同款 LAA 本地管理地址，
     * 02:xx:xx:xx:xx:xx，避开真实厂商 MAC 段），避免「打开设置页设备 ID 框空白
     * 用户不知道填什么 / 必须粘真机 MAC」的反人类体验。用户后续仍可在设置页
     * 覆盖为真实面包板 MAC；本方法始终幂等——已存值不重新生成。
     *
     * @return Triple(Device-Id, Client-Id, Serial-Number)
     */
    suspend fun ensureDeviceIdentity(
        context: Context,
        settings: SettingsRepository,
    ): Triple<String, String, String> = withContext(Dispatchers.IO) {
        var deviceId = SecureStore.getSecret(context, SettingsRepository.PREF_XZ_DEVICE_ID)
        var clientId = SecureStore.getSecret(context, SettingsRepository.PREF_XZ_CLIENT_ID)
        var serialNumber = SecureStore.getSecret(context, SettingsRepository.PREF_XZ_SERIAL_NUMBER)

        if (deviceId.isNullOrEmpty()) {
            // V2.8X++：deviceId 空时自动生成虚拟 MAC（SecureRandom + LAA 位），
            // 写入 SecureStore + DataStore，激活时再派生 serial。
            deviceId = generateMacAddress()
            SecureStore.putSecret(context, SettingsRepository.PREF_XZ_DEVICE_ID, deviceId)
            settings.setXzDeviceId(deviceId)
            AppLogger.d(TAG, "ensureDeviceIdentity：deviceId 空，已自动生成虚拟 MAC=$deviceId")
        }
        if (clientId.isNullOrEmpty()) {
            clientId = UUID.randomUUID().toString()
            SecureStore.putSecret(context, SettingsRepository.PREF_XZ_CLIENT_ID, clientId)
            settings.setXzClientId(clientId)
        }
        // serial_number 必须是 py-xiaozhi 同款 "SN-XXXXXXXX-<mac hex>" 格式
        //（官方云绑定校验依赖该格式）。仅当 deviceId 非空时才可派生 serial。
        if ((serialNumber.isNullOrEmpty() || !serialNumber.startsWith("SN-")) && !deviceId.isNullOrEmpty()) {
            serialNumber = generateSerialNumber(deviceId)
            SecureStore.putSecret(context, SettingsRepository.PREF_XZ_SERIAL_NUMBER, serialNumber)
            settings.setXzSerialNumber(serialNumber)
        }

        Triple(deviceId ?: "", clientId!!, serialNumber ?: "")
    }

    /**
     * 重置软件身份：同时重置 Device-Id（生成新虚拟 MAC）+ Client-Id + 派生新 serial。
     *
     * V2.8X++：与上版不同，**Device-Id 不再保留用户原值**，而是与 Client-Id 同步重置——
     * 用户在设置页点「重置软件身份」通常意味着"换一台软件身份重新激活"，
     * 此时保留旧 deviceId 反而导致 /activate 用新 Client-Id 旧 deviceId 提交，
     * 服务端 license 体系按 deviceId 校验会报 License not found。
     * 同步重置 deviceId+clientId+serial 后整组身份一致，重新激活即可。
     */
    suspend fun regenerateDeviceIdentity(
        context: Context,
        settings: SettingsRepository,
    ): Triple<String, String, String> = withContext(Dispatchers.IO) {
        val newDeviceId = generateMacAddress()
        val newClientId = UUID.randomUUID().toString()
        val newSerial = generateSerialNumber(newDeviceId)
        SecureStore.putSecret(context, SettingsRepository.PREF_XZ_DEVICE_ID, newDeviceId)
        SecureStore.putSecret(context, SettingsRepository.PREF_XZ_CLIENT_ID, newClientId)
        SecureStore.putSecret(context, SettingsRepository.PREF_XZ_SERIAL_NUMBER, newSerial)
        settings.setXzDeviceId(newDeviceId)
        settings.setXzClientId(newClientId)
        settings.setXzSerialNumber(newSerial)
        AppLogger.d(TAG, "regenerateDeviceIdentity：deviceId+clientId+serial 三件套全部重置 deviceId=$newDeviceId")
        Triple(newDeviceId, newClientId, newSerial)
    }

    /**
     * 生成虚拟设备 MAC（与 py-xiaozhi 同款 LAA——Locally Administered Address，
     * 第一字节最低位=0（单播）+ 最低次高位=1（本地管理），形如 02:xx:xx:xx:xx:xx）。
     * 用 SecureRandom 保证不与本机真实 NIC 撞车，激活时由服务端按 deviceId 做 license
     * 校验（虚拟 MAC 在 xiaozhi.me 官方云可能 license 校验失败，但用作软件身份时
     * 至少保证 6 字节格式合法、序列号派生稳定）。
     */
    private fun generateMacAddress(): String {
        val rnd = SecureRandom()
        val buf = ByteArray(6)
        rnd.nextBytes(buf)
        // 强制 LAA：清掉单播位（bit0）、置位本地管理位（bit1）→ 第一字节末两位 = 10。
        buf[0] = (buf[0].toInt() and 0xFC or 0x02).toByte()
        return buf.joinToString(":") { "%02x".format(it) }
    }

    /**
     * 执行 OTA 设备注册。
     *
     * 向官方（或自建）OTA 端点 POST 设备信息；服务端返回：
     * - 已绑定设备 → 含 firmware.websocket.url，无 activation
     * - 未绑定新设备 → 含 activation.code（6 位验证码），firmware.url = INVALID
     *
     * @param otaUrl OTA 端点，默认 [DEFAULT_OTA_URL]
     * @return [OtaResult] 含验证码或错误信息
     */
    suspend fun activateDevice(
        deviceId: String,
        clientId: String,
        serialNumber: String,
        otaUrl: String = DEFAULT_OTA_URL,
    ): OtaResult = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            // 重要纠正（2026-07-28 py-xiaozhi 源码实证）：官方云 OTA 注册阶段**绝不接收 serial_number**
            //（py-xiaozhi 的 OTA 体只有 application + board，既无顶层 serial_number 也无 Serial-Number 头）。
            // serial 只在 Step2 /activate 体里提交。若在 OTA 就带 serial，官方云会按"烧录序列号"做 license
            // 绑定/校验，自造 serial 无对应 license → 待激活记录创建异常 → /activate 直接 404"License not found"。
            // OTA 体对齐 py-xiaozhi（官方云已验证纯软件客户端）：仅 application + board，不带 serial/mac_address。
            val body = buildJsonObject {
                put("application", buildJsonObject {
                    put("version", JsonPrimitive(SIMULATED_FW_VERSION))
                    put("elf_sha256", JsonPrimitive(sha256Hex("$deviceId|$serialNumber")))
                })
                put("board", buildJsonObject {
                    put("type", JsonPrimitive("xiaozhi_v1"))
                    put("name", "xiaozhi-esp32")
                    put("ip", JsonPrimitive("127.0.0.1"))
                    put("mac", JsonPrimitive(normalizeMac(deviceId)))
                })
            }.toString()

            val request = Request.Builder()
                .url(otaUrl)
                .addHeader("Device-Id", normalizeMac(deviceId))
                .addHeader("Client-Id", clientId)
                .addHeader("Activation-Version", "2")
                .addHeader("User-Agent", "xiaozhi_v1/xiaozhi-esp32-$SIMULATED_FW_VERSION")
                .addHeader("Accept-Language", "zh-CN")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext OtaResult(error = "HTTP ${response.code}: $responseBody")
            }

            val root = json.parseToJsonElement(responseBody).jsonObject

            // 解析 activation 字段（未绑定设备才有）
            val activation = root["activation"]?.jsonObject
            val code = activation?.get("code")?.jsonPrimitive?.content
            val activationMsg = activation?.get("message")?.jsonPrimitive?.content

            // 解析 websocket 地址
            val wsObj = root["websocket"]?.jsonObject
            val wsUrl = wsObj?.get("url")?.jsonPrimitive?.content

            OtaResult(
                code = code,
                websocketUrl = wsUrl,
                message = activationMsg,
            )
        } catch (e: Exception) {
            OtaResult(error = e.message ?: "unknown error")
        }
    }

    /**
     * 两步激活结果（V2.8X++ 对齐 py-xiaozhi 已验证流程）。
     *
     * 走 py-xiaozhi / ESP32 固件同款 v2 激活流程：
     *   Step 1: POST /xiaozhi/ota/ with `Activation-Version: 2` header → 服务端返回 activation.code（6 位数字）
     *                                                                       + activation.challenge（UUID 字符串）
     *   Step 2: HMAC-SHA256(challenge, hmac_key) → **轮询** POST /xiaozhi/ota/activate
     *           payload: { "Payload": { algorithm, serial_number, challenge, hmac } }（外层 Payload 包裹，对齐 py-xiaozhi 官方云已验证客户端）
     *           服务端在用户尚未到官网输码前一直返回 202 → 每 5 秒重试；
     *           用户在官网输入 6 位码完成绑定后，下一次轮询返回 200 → 激活完成。
     *
     * ⚠️ 关键认知（2026-07-28 py-xiaozhi 源码实证）：
     * 官网「用验证码添加设备」校验的 serial_number **只来自 /activate 请求体里提交的 serial_number**（OTA 注册体里的
     * serial_number 字段不被官方云采信）。所以必须在用户输码期间保持 /activate 轮询在线，
     * 否则官网绑定时报 SERIAL_NUMBER_REQUIRED。
     *
     * @property code 6 位验证码（用户控制台「添加设备」输入用；若服务端未返回则 null）
     * @property challenge 服务端下发的 challenge（UUID 字符串）
     * @property step1Error Step 1 失败原因
     * @property step2Status "OK"（激活成功） / "TIMEOUT"（轮询用尽，用户未在时限内输码） / "SKIPPED"（Step1 失败未走）
     * @property step2Body 最后一次 Step 2 响应体原文（调试用）
     * @property step2Error Step 2 异常信息
     * @property websocketUrl OTA 下发的 WS 地址
     * @property websocketToken OTA 下发的 WS Bearer token（官方云一般为 "test-token"，自建服务端可能是真 token）
     */
    data class TwoStepResult(
        val code: String? = null,
        val challenge: String? = null,
        val step1Error: String? = null,
        val step2Status: String = "SKIPPED",
        val step2Body: String? = null,
        val step2Error: String? = null,
        val websocketUrl: String? = null,
        val websocketToken: String? = null,
    ) {
        /** 激活成功 = 服务端已把 serial_number + hmac 写入设备记录，官网绑定完成，可直接测试连接。 */
        val isActivated: Boolean get() = step2Status == "OK" && step1Error == null
    }

    /**
     * 两步激活：对齐 py-xiaozhi（已被大量用户验证可在 xiaozhi.me 官方云成功激活的纯软件模拟客户端）。
     *
     * 正确流程（py-xiaozhi src/activation/ 源码实证，2026-07-28）：
     * 1. Step1 POST /ota/（Activation-Version: 2）→ 拿 code + challenge，**立即通过 [onActivationCode] 回调
     *    把 6 位码展示给用户**（用户此时就该去官网输码）。
     * 2. Step2 **轮询** POST /ota/activate，body 为 `{"Payload": {algorithm, serial_number, challenge, hmac}}`（外层 Payload 包裹，对齐 py-xiaozhi 官方云已验证客户端）：
     *    - 202 = 用户还没输码 → 等 5s 重试（py-xiaozhi：最多 60 次 = 5 分钟）
     *    - 200 = 用户已输码、服务端完成绑定+激活 → 成功
     *    - 其它状态码/网络异常 → 同样等 5s 重试（py-xiaozhi 同款容错）
     * 3. 官网绑定页校验的 serial_number **正是本轮询请求体里提交的值**——
     *    历史 bug（已修复）：只发一次 /activate 且把 hmac_key 误填进 serial_number 字段、缺少轮询时序，
     *    服务端从未拿到合法 serial_number → 官网绑定报 SERIAL_NUMBER_REQUIRED。
     *    注：/activate 体采用**外层 Payload 包裹**——py-xiaozhi（官方云已验证的纯软件客户端）即如此，
     *    服务端取 data.get("Payload", data) 兼容平铺/包裹；采用 py-xiaozhi 的包裹形态以最大化官方云兼容性。
     *
     * hmac_key：py-xiaozhi 用主机指纹 sha256 自生成（服务端首次激活时只记录、不验签）。
     * 本 App 用 sha256("deviceId|serialNumber") 确定性派生 —— 与设备标识绑定、跨请求稳定、零额外存储。
     *
     * @param onActivationCode Step1 拿到 6 位码后立即回调（主/任意线程），UI 应即时展示引导用户去官网输码
     * @return [TwoStepResult] 详细分步状态，UI 据此给精准反馈
     */
    suspend fun activateDeviceTwoStep(
        deviceId: String,
        clientId: String,
        serialNumber: String,
        otaUrl: String = DEFAULT_OTA_URL,
        onActivationCode: ((code: String) -> Unit)? = null,
    ): TwoStepResult = withContext(Dispatchers.IO) {
        try {
            AppLogger.i(TAG, "两步激活开始：deviceId=$deviceId clientId=$clientId serialNumber=$serialNumber otaUrl=$otaUrl")
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            // ──────────────── Step 1: POST /xiaozhi/ota/ with Activation-Version: 2 ────────────────
            // OTA 注册体对齐 py-xiaozhi（官方云已验证纯软件客户端，main 2026-07-28）：
            // 仅 application + board，**绝不带 serial_number / mac_address 顶层字段，也不带 Serial-Number 头**。
            // serial 只在 Step2 /activate 体里提交。带 serial 会让官方云在 OTA 阶段按"烧录序列号"做 license
            // 绑定/校验 → 自造 serial 无 license → 待激活记录创建异常 → Step2 /activate 直接 404"License not found"。
            val body1 = buildJsonObject {
                put("application", buildJsonObject {
                    put("version", JsonPrimitive(SIMULATED_FW_VERSION))
                    put("elf_sha256", JsonPrimitive(sha256Hex("$deviceId|$serialNumber")))
                })
                put("board", buildJsonObject {
                    put("type", JsonPrimitive("xiaozhi_v1"))
                    put("name", "xiaozhi-esp32")
                    put("ip", JsonPrimitive("127.0.0.1"))
                    put("mac", JsonPrimitive(normalizeMac(deviceId)))
                })
            }.toString()

            val request1 = Request.Builder()
                .url(otaUrl)
                .addHeader("Device-Id", normalizeMac(deviceId))
                .addHeader("Client-Id", clientId)
                .addHeader("Activation-Version", "2")  // 声明支持 HMAC challenge-response 流程（官方云据此返回 activation.challenge）
                .addHeader("User-Agent", "xiaozhi_v1/xiaozhi-esp32-$SIMULATED_FW_VERSION")
                .addHeader("Accept-Language", "zh-CN")
                .addHeader("Content-Type", "application/json")
                .post(body1.toRequestBody("application/json".toMediaType()))
                .build()

            val response1 = client.newCall(request1).execute()
            val body1Text = response1.body?.string() ?: ""
            AppLogger.i(TAG, "Step1 响应 http=${response1.code} body.len=${body1Text.length}")

            if (!response1.isSuccessful) {
                AppLogger.w(TAG, "Step1 失败 http=${response1.code}: ${body1Text.take(300)}")
                return@withContext TwoStepResult(
                    step1Error = "HTTP ${response1.code}: $body1Text",
                )
            }

            val root1 = json.parseToJsonElement(body1Text).jsonObject
            val activation = root1["activation"]?.jsonObject
            val code = activation?.get("code")?.jsonPrimitive?.content
            val challenge = activation?.get("challenge")?.jsonPrimitive?.content
            val wsObj = root1["websocket"]?.jsonObject
            val wsUrl = wsObj?.get("url")?.jsonPrimitive?.content
            val wsToken = wsObj?.get("token")?.jsonPrimitive?.content

            // 已激活设备再次 OTA：无 activation 节点 → 直接成功（py-xiaozhi server_activated 同款判定）
            if (activation == null) {
                AppLogger.i(TAG, "Step1 无 activation 节点：设备已绑定/已激活，无需再走激活流程")
                return@withContext TwoStepResult(
                    websocketUrl = wsUrl,
                    websocketToken = wsToken,
                    step2Status = "OK",
                )
            }

            if (challenge.isNullOrBlank()) {
                return@withContext TwoStepResult(
                    code = code,
                    websocketUrl = wsUrl,
                    websocketToken = wsToken,
                    step1Error = "服务端未返回 activation.challenge 字段（可能服务端不支持 v2 激活，请改用「走真机 MAC 接入」方案）",
                )
            }

            // 立即把 6 位码交给 UI 展示 —— 用户要在 Step2 轮询期间去官网输码
            if (!code.isNullOrBlank()) {
                try { onActivationCode?.invoke(code) } catch (_: Exception) { /* UI 回调异常不阻塞激活 */ }
            }

            // ──────────────── Step 2: 轮询 POST /xiaozhi/ota/activate ────────────────
            // hmac_key：确定性派生（sha256("deviceId|serialNumber")）。服务端不验签、只在首次激活时记录。
            // /activate 请求体结构对齐 py-xiaozhi（官方云已验证的纯软件模拟客户端，main 2026-07-28 拉取）：
            // **外层 "Payload" 包裹**，字段 algorithm / serial_number / challenge / hmac 位于 Payload 内。
            // 说明：上一版误改为"平铺"以"对齐官方 C 固件"——但 C 固件是带真 license 的硬件，
            // 对软件模拟客户端无参考价值；py-xiaozhi 才是官方云上可通的软件形态，其用包裹层，故回归包裹层。
            val hmacKey = sha256Hex("$deviceId|$serialNumber")
            val appHmac = hmacSha256Hex(hmacKey.toByteArray(Charsets.UTF_8), challenge)

            val body2 = buildJsonObject {
                put("Payload", buildJsonObject {
                    put("algorithm", JsonPrimitive("hmac-sha256"))
                    put("serial_number", JsonPrimitive(serialNumber))
                    put("challenge", JsonPrimitive(challenge))
                    put("hmac", JsonPrimitive(appHmac))
                })
            }.toString()

            // 端点：base + "/activate"（78/xiaozhi-esp32 与 py-xiaozhi 同款路径规则）
            val activateUrl = if (otaUrl.endsWith("/")) "${otaUrl}activate" else "$otaUrl/activate"

            // py-xiaozhi 同款轮询：最多 60 次 × 5s = 5 分钟；202 = 等用户输码；网络异常/非 200 同样重试。
            var lastCode = -1
            var lastBody = ""
            var lastError: String? = null
            repeat(MAX_ACTIVATE_ATTEMPTS) { attempt ->
                val request2 = Request.Builder()
                    .url(activateUrl)
                    .addHeader("Activation-Version", "2")
                    .addHeader("Device-Id", deviceId)
                    .addHeader("Client-Id", clientId)
                    .addHeader("Content-Type", "application/json")
                    .post(body2.toRequestBody("application/json".toMediaType()))
                    .build()

                try {
                    val response2 = client.newCall(request2).execute()
                    lastCode = response2.code
                    lastBody = response2.body?.string() ?: ""
                    lastError = null
                    AppLogger.i(TAG, "Step2 轮询 ${attempt + 1}/$MAX_ACTIVATE_ATTEMPTS http=$lastCode body.len=${lastBody.length}")
                    if (lastCode == 200) {
                        AppLogger.i(TAG, "Step2 激活成功（用户已在官网完成输码绑定）")
                        return@withContext TwoStepResult(
                            code = code,
                            challenge = challenge,
                            websocketUrl = wsUrl,
                            websocketToken = wsToken,
                            step2Status = "OK",
                            step2Body = lastBody.take(500),
                        )
                    }
                    if (lastCode != 202) {
                        AppLogger.w(TAG, "Step2 非 200/202（http=$lastCode），5s 后重试 body=${lastBody.take(300)}")
                    }
                } catch (e: Exception) {
                    lastError = e.message ?: "unknown"
                    AppLogger.w(TAG, "Step2 轮询网络异常（${attempt + 1}/$MAX_ACTIVATE_ATTEMPTS）：$lastError")
                }
                delay(ACTIVATE_RETRY_INTERVAL_MS)
            }

            AppLogger.w(TAG, "Step2 轮询用尽（${MAX_ACTIVATE_ATTEMPTS} 次）：用户未在时限内完成官网输码。last http=$lastCode")
            TwoStepResult(
                code = code,
                challenge = challenge,
                websocketUrl = wsUrl,
                websocketToken = wsToken,
                step2Status = "TIMEOUT",
                step2Body = lastBody.take(500),
                step2Error = lastError ?: "last HTTP $lastCode",
            )
        } catch (e: Exception) {
            TwoStepResult(step1Error = e.message ?: "unknown error")
        }
    }

    /** Step2 轮询上限：60 次 × 5 秒 = 5 分钟（py-xiaozhi 同款），给用户足够时间到官网输码。 */
    private const val MAX_ACTIVATE_ATTEMPTS = 60

    /** Step2 轮询间隔（毫秒）。 */
    private const val ACTIVATE_RETRY_INTERVAL_MS = 5_000L

    /**
     * HMAC-SHA256 算 hex 字符串。零新依赖：用 JDK 自带 javax.crypto.Mac。
     */
    private fun hmacSha256Hex(key: ByteArray, message: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        val result = mac.doFinal(message.toByteArray(Charsets.UTF_8))
        return result.joinToString("") { "%02x".format(it) }
    }

    /** SHA-256 hex（64 字符）。零新依赖：JDK MessageDigest。 */
    private fun sha256Hex(message: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(message.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /** MD5 hex（32 字符）。仅用于 serial_number 短哈希，非安全用途。 */
    private fun md5Hex(message: String): String =
        MessageDigest.getInstance("MD5")
            .digest(message.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /**
     * MAC 地址格式校验（如 "aa:bb:cc:dd:ee:ff"，大小写均可）。
     * @return true 表示格式合法。仅校验格式，不判定是否为真实硬件 MAC——
     * 真实性由 xiaozhi.me 服务端在绑定/握手阶段判定（虚拟/未授权 MAC 会被 license 体系拒绝）。
     */
    fun isValidMacAddress(mac: String): Boolean {
        val normalized = normalizeMac(mac)
        return MAC_REGEX.matches(normalized)
    }

    /**
     * 归一化用户输入的 MAC：去空白、转【小写】、连字符/点/空格统一为冒号；
     * 12 位连续十六进制自动补冒号（如 A1B2C3D4E5F6 → a1:b2:c3:d4:e5:f6）。
     *
     * ⚠️ 关键（2026-07-29 协议实证）：xiaozhi.me 服务端对 Device-Id(MAC) **大小写敏感**，
     * 必须以控制台绑定的原样小写 MAC（如 e8:06:90:98:6c:d4）发送，发大写会被静默拒→1005。
     * 故此处强制小写而非大写。
     */
    fun normalizeMac(mac: String): String {
        var s = mac.trim().lowercase().replace("-", ":").replace(".", ":").replace(" ", "")
        if (s.length == 12 && s.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
            s = s.chunked(2).joinToString(":")
        }
        return s
    }

    /** MAC 格式正则：6 组两位十六进制，冒号分隔（大小写均可）。 */
    private val MAC_REGEX = Regex("^([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}$")

    /**
     * 生成设备序列号（serial_number）——py-xiaozhi 同款格式（官方云已验证）：
     * `SN-{MD5(mac去冒号小写)[:8]大写}-{mac去冒号小写}`，如 `SN-1A2B3C4D-6c59eb2a66ec`。
     * 与 deviceId 绑定：同一 MAC 永远得到同一序列号（确定性，无需担心重复生成不一致）。
     */
    internal fun generateSerialNumber(deviceId: String): String {
        val macClean = deviceId.lowercase().replace(":", "")
        val shortHash = md5Hex(macClean).take(8).uppercase()
        return "SN-$shortHash-$macClean"
    }
}
