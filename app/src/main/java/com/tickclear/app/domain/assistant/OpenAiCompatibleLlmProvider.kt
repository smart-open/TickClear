package com.tickclear.app.domain.assistant

import android.content.Context
import com.tickclear.app.data.SecureStore
import com.tickclear.app.domain.repository.SettingsRepository
import com.tickclear.app.domain.model.AppException
import com.tickclear.app.domain.model.ErrorCode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * OpenAI 兼容的 LLM 服务商实现基类（Chat Completions，文本对话）。
 *
 * - 复用项目既有 OkHttp（与 XiaozhiTransport 同一客户端栈），不引入新依赖；
 * - 请求体用 org.json 手工构造（与 BackupManager 一致），避免新增序列化库；
 * - API Key 存于加密存储（由子类指定具体的 [apiKeyPrefKey]），不落 DataStore；
 * - 失败抛 [AppException]（E3001 未配置 / E3002 连接失败），由 UI 映射为提示。
 *
 * 子类（OpenAI / 豆包 Doubao / 通义千问 Qianwen）均为 OpenAI 兼容端点 + Bearer 鉴权，
 * 仅默认 baseUrl / 默认模型 / 密钥存储键不同，体现「同接口可扩展」。
 */
open class OpenAiCompatibleLlmProvider(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val providerId: String,
    private val defaultBaseUrl: String,
    private val defaultModel: String,
    private val apiKeyPrefKey: String,
) : LlmProvider {

    override val id: String = providerId
    override val label: String = providerId

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun chat(systemPrompt: String, userText: String): String =
        withContext(Dispatchers.IO) {
            val baseUrl = settingsRepository.llmBaseUrl.first().trimEnd('/')
                .ifBlank { defaultBaseUrl }
            val model = settingsRepository.llmModel.first().ifBlank { defaultModel }
            val apiKey = SecureStore.getSecret(context, apiKeyPrefKey)
                ?: throw AppException(ErrorCode.ASSISTANT_NOT_CONFIGURED, detail = "missing API key for $providerId")

            val body = JSONObject().apply {
                put("model", model)
                put(
                    "messages",
                    JSONArray().apply {
                        put(JSONObject().put("role", "system").put("content", systemPrompt))
                        put(JSONObject().put("role", "user").put("content", userText))
                    },
                )
                put("temperature", 0.7)
            }

            val request = Request.Builder()
                .url("$baseUrl/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(MEDIA_TYPE_JSON))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw AppException(
                    ErrorCode.ASSISTANT_CONNECT_FAILED,
                    detail = "HTTP ${response.code}",
                )
            }
            val raw = response.body?.string()
                ?: throw AppException(ErrorCode.ASSISTANT_CONNECT_FAILED, detail = "empty body")
            val content = JSONObject(raw)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
            content
        }

    companion object {
        private val MEDIA_TYPE_JSON = "application/json".toMediaType()
    }
}
