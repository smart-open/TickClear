package com.tickclear.app.domain.assistant

import android.content.Context
import android.util.Base64
import com.tickclear.app.data.SecureStore
import com.tickclear.app.data.repositories.SettingsRepository
import com.tickclear.app.domain.model.AppException
import com.tickclear.app.domain.model.ErrorCode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 腾讯云语音识别（「一句话识别」SentenceRecognition，同步）。
 *
 * - 鉴权使用腾讯云 TC3-HMAC-SHA256 签名（纯 JDK 实现，[Signing]，不引入新依赖）；
 * - 凭据 SecretId / SecretKey 存于加密存储；
 * - 音频以 WAV 整文件 base64 上传（[AsrProvider.transcribe] 入参即 WAV）。
 */
@Singleton
class TencentAsrProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) : AsrProvider {

    override val id: String = AsrProviderCatalog.TENCENT

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun transcribe(audio: File): String = withContext(Dispatchers.IO) {
        val secretId = SecureStore.getSecret(context, SettingsRepository.PREF_TENCENT_SECRET_ID)
        val secretKey = SecureStore.getSecret(context, SettingsRepository.PREF_TENCENT_SECRET_KEY)
        if (secretId.isNullOrBlank() || secretKey.isNullOrBlank()) {
            throw AppException(ErrorCode.ASSISTANT_NOT_CONFIGURED, detail = "腾讯云 SecretId/SecretKey 未配置")
        }
        if (!audio.exists() || audio.length() == 0L) {
            throw AppException(ErrorCode.ASSISTANT_CONNECT_FAILED, detail = "音频文件为空")
        }

        val audioBytes = audio.readBytes()
        val dataB64 = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
        val body = JSONObject().apply {
            put("ProjectId", 0)
            put("SubServiceType", 2)
            put("SourceType", 1)
            put("VoiceFormat", "wav")
            put("UsrAudioKey", UUID.randomUUID().toString())
            put("Data", dataB64)
            put("DataLen", audioBytes.size)
        }.toString()

        val host = "asr.tencentcloudapi.com"
        val service = "asr"
        val region = "ap-beijing"
        val action = "SentenceRecognition"
        val version = "2019-06-14"
        val timestamp = System.currentTimeMillis() / 1000
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(timestamp * 1000))

        val hashedPayload = Signing.sha256Hex(body.toByteArray(StandardCharsets.UTF_8))
        val canonicalHeaders =
            "content-type:application/json; charset=utf-8\nhost:$host\nx-tc-action:${action.lowercase(Locale.US)}\n"
        val signedHeaders = "content-type;host;x-tc-action"
        val canonicalRequest = "POST\n/\n\n$canonicalHeaders\n$signedHeaders\n$hashedPayload"
        val credentialScope = "$date/$service/tc3_request"
        val stringToSign = "TC3-HMAC-SHA256\n$timestamp\n$credentialScope\n" +
            Signing.sha256Hex(canonicalRequest.toByteArray(StandardCharsets.UTF_8))

        val secretDate = Signing.hmacSha256("TC3$secretKey".toByteArray(StandardCharsets.UTF_8), date.toByteArray(StandardCharsets.UTF_8))
        val secretService = Signing.hmacSha256(secretDate, service.toByteArray(StandardCharsets.UTF_8))
        val secretSigning = Signing.hmacSha256(secretService, "tc3_request".toByteArray(StandardCharsets.UTF_8))
        val signature = Signing.hex(
            Signing.hmacSha256(secretSigning, stringToSign.toByteArray(StandardCharsets.UTF_8)),
        )
        val authorization =
            "TC3-HMAC-SHA256 Credential=$secretId/$credentialScope, SignedHeaders=$signedHeaders, Signature=$signature"

        val req = Request.Builder()
            .url("https://$host")
            .addHeader("Content-Type", "application/json; charset=utf-8")
            .addHeader("Host", host)
            .addHeader("X-TC-Action", action)
            .addHeader("X-TC-Version", version)
            .addHeader("X-TC-Timestamp", timestamp.toString())
            .addHeader("X-TC-Region", region)
            .addHeader("Authorization", authorization)
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        runCatching {
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw AppException(ErrorCode.ASSISTANT_CONNECT_FAILED, detail = "HTTP ${resp.code}: ${text.take(200)}")
                }
                val json = JSONObject(text)
                val response = json.optJSONObject("Response")
                    ?: throw AppException(ErrorCode.ASSISTANT_CONNECT_FAILED, detail = "无 Response 字段")
                val err = response.optJSONObject("Error")
                if (err != null) {
                    throw AppException(
                        ErrorCode.ASSISTANT_CONNECT_FAILED,
                        detail = "${err.optString("Code")}: ${err.optString("Message")}",
                    )
                }
                response.optString("Result", "").trim()
            }
        }.getOrElse { e ->
            throw if (e is AppException) e else AppException(ErrorCode.ASSISTANT_CONNECT_FAILED, cause = e, detail = e.message)
        }.also { if (it.isEmpty()) throw AppException(ErrorCode.ASSISTANT_CONNECT_FAILED, detail = "识别结果为空") }
    }
}
