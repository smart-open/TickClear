package com.tickclear.app.domain.assistant

import android.content.Context
import com.tickclear.app.data.SecureStore
import com.tickclear.app.data.repositories.SettingsRepository
import com.tickclear.app.domain.model.AppException
import com.tickclear.app.domain.model.ErrorCode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OpenAI 兼容的语音识别后端（如 OpenAI Whisper、本地 Whisper 服务、兼容网关）。
 * 复用项目既有的 OkHttp 4.12 与 org.json，**不引入任何新依赖**。
 * 音频经多部件表单上传至 `${baseUrl}/audio/transcriptions`，密钥取自加密存储。
 */
@Singleton
class WhisperCompatibleAsrProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) : AsrProvider {

    override val id: String = AsrProvider.OPENAI

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun transcribe(audio: File): String = withContext(Dispatchers.IO) {
        val baseUrl = settingsRepository.asrBaseUrl.first().trim().removeSuffix("/")
        val model = settingsRepository.asrModel.first().trim().ifEmpty { DEFAULT_MODEL }
        val apiKey = SecureStore.getSecret(context, SettingsRepository.PREF_ASR_API_KEY)

        if (apiKey.isNullOrBlank()) {
            throw AppException(ErrorCode.ASSISTANT_NOT_CONFIGURED, detail = "ASR API Key 未配置")
        }
        if (!audio.exists() || audio.length() == 0L) {
            throw AppException(ErrorCode.ASSISTANT_CONNECT_FAILED, detail = "音频文件为空")
        }

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", model)
            .addFormDataPart(
                "file",
                audio.name,
                audio.asRequestBody("audio/wav".toMediaType()),
            )
            .build()

        val req = Request.Builder()
            .url("$baseUrl/audio/transcriptions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        runCatching {
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw AppException(
                        ErrorCode.ASSISTANT_CONNECT_FAILED,
                        detail = "HTTP ${resp.code}: ${text.take(200)}",
                    )
                }
                JSONObject(text).optString("text", "").trim()
            }
        }.getOrElse { e ->
            throw if (e is AppException) e else AppException(
                ErrorCode.ASSISTANT_CONNECT_FAILED,
                cause = e,
                detail = e.message,
            )
        }.also { if (it.isEmpty()) throw AppException(ErrorCode.ASSISTANT_CONNECT_FAILED, detail = "识别结果为空") }
    }

    override suspend fun test(): Boolean = withContext(Dispatchers.IO) {
        val baseUrl = settingsRepository.asrBaseUrl.first().trim().removeSuffix("/")
        val apiKey = SecureStore.getSecret(context, SettingsRepository.PREF_ASR_API_KEY)
        if (apiKey.isNullOrBlank() || baseUrl.isBlank()) return@withContext false
        // 轻量鉴权探测：OpenAI 兼容的 /models 端点，2xx 或 401/403 均说明端点与密钥格式有效。
        runCatching {
            val req = Request.Builder().url("$baseUrl/models")
                .addHeader("Authorization", "Bearer $apiKey").build()
            client.newCall(req).execute().use { resp -> resp.isSuccessful || resp.code == 401 || resp.code == 403 }
        }.getOrDefault(false)
    }

companion object {
        const val DEFAULT_MODEL = "whisper-1"
    }
}
