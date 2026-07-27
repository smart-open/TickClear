package com.tickclear.app.domain.assistant

import android.content.Context
import com.tickclear.app.data.SecureStore
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 小智 ESP32 设备模拟器（V2.8）。
 *
 * 模拟 ESP32 硬件设备的注册与激活流程，使 Android App 能以「虚拟设备」身份接入官方 xiaozhi.me：
 * 1. 生成/持久化设备标识（Device-Id = MAC 格式，Client-Id = UUID）
 * 2. 调用 OTA 接口完成设备注册，获取 6 位验证码
 * 3. 用户持验证码到 [xiaozhi.me](https://xiaozhi.me) 控制台 → 添加设备 → 绑定
 * 4. 绑定后 WebSocket 连接带上设备认证头即可正常通信
 *
 * ## 协议参考
 * - OTA 端点：`POST https://api.tenclass.net/xiaozhi/ota/`
 * - 请求头：`Device-Id: <MAC>` + `Client-Id: <UUID>`
 * - 请求体：`{ version, mac_address, application: {version, name}, board: {type} }`
 * - 响应（未绑定）：`{ activation: { code: "六位数字" }, websocket: { url: "..." } }`
 * - WS 握手时同样需携带 `Device-Id` / `Client-Id` 头
 */
object XiaozhiDeviceSimulator {

    private val json = Json { ignoreUnknownKeys = true }

    /** 官方 OTA 端点（xiaozhi.me）。自建服务器可修改。 */
    const val DEFAULT_OTA_URL = "https://api.tenclass.net/xiaozhi/ota/"

    /** 模拟固件版本号（与 ESP32 固件保持一致的格式）。 */
    private const val SIMULATED_FW_VERSION = "v1.0.0"

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
     * 首次调用自动生成 MAC + UUID 并写入 SecureStore/DataStore；
     * 后续调用直接返回已有值（幂等）。
     *
     * @return Pair(Device-Id, Client-Id)
     */
    suspend fun ensureDeviceIdentity(
        context: Context,
        settings: SettingsRepository,
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        var deviceId = SecureStore.getSecret(context, SettingsRepository.PREF_XZ_DEVICE_ID)
        var clientId = SecureStore.getSecret(context, SettingsRepository.PREF_XZ_CLIENT_ID)

        if (deviceId.isNullOrEmpty()) {
            deviceId = generateMacAddress()
            SecureStore.putSecret(context, SettingsRepository.PREF_XZ_DEVICE_ID, deviceId)
            settings.setXzDeviceId(deviceId)
        }
        if (clientId.isNullOrEmpty()) {
            clientId = UUID.randomUUID().toString()
            SecureStore.putSecret(context, SettingsRepository.PREF_XZ_CLIENT_ID, clientId)
            settings.setXzClientId(clientId)
        }

        Pair(deviceId, clientId)
    }

    /**
     * 重新生成设备标识（换一台「虚拟设备」）。
     * 原标识作废，需重新走 OTA 激活流程。
     */
    suspend fun regenerateDeviceIdentity(
        context: Context,
        settings: SettingsRepository,
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        val newDeviceId = generateMacAddress()
        val newClientId = UUID.randomUUID().toString()
        SecureStore.putSecret(context, SettingsRepository.PREF_XZ_DEVICE_ID, newDeviceId)
        SecureStore.putSecret(context, SettingsRepository.PREF_XZ_CLIENT_ID, newClientId)
        settings.setXzDeviceId(newDeviceId)
        settings.setXzClientId(newClientId)
        Pair(newDeviceId, newClientId)
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
        otaUrl: String = DEFAULT_OTA_URL,
    ): OtaResult = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            val body = buildJsonObject {
                put("version", JsonPrimitive(1))
                put("mac_address", JsonPrimitive(deviceId))
                put("application", buildJsonObject {
                    put("version", JsonPrimitive(SIMULATED_FW_VERSION))
                    put("name", JsonPrimitive("xiaozhi-esp32"))
                })
                put("board", buildJsonObject {
                    put("type", JsonPrimitive("xiaozhi_v1"))
                })
            }.toString()

            val request = Request.Builder()
                .url(otaUrl)
                .addHeader("Device-Id", deviceId)
                .addHeader("Client-Id", clientId)
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

    /** 生成随机 MAC 地址（如 "AA:BB:CC:DD:EE:FF"）。 */
    private fun generateMacAddress(): String {
        val bytes = ByteArray(6).apply { java.security.SecureRandom().nextBytes(this) }
        return bytes.joinToString(":") { "%02X".format(it) }
    }
}
