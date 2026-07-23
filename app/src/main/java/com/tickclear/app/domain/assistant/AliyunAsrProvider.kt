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
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.TreeMap
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 阿里云语音识别（「一句话识别」短句 REST 接口，同步）。
 *
 * - 鉴权使用阿里云 RPC 签名（HMAC-SHA1 + RFC3986 百分比编码，纯 JDK 实现，[Signing]，不引入新依赖）；
 * - 凭据 AccessKeyId / AccessKeySecret / AppKey 存于加密存储；
 * - 音频以 WAV 整文件 base64 作为 `voice` 参数上传。
 */
@Singleton
class AliyunAsrProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) : AsrProvider {

    override val id: String = AsrProviderCatalog.ALIYUN

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun transcribe(audio: File): String = withContext(Dispatchers.IO) {
        val accessKeyId = SecureStore.getSecret(context, SettingsRepository.PREF_ALIYUN_ACCESS_KEY)
        val accessKeySecret = SecureStore.getSecret(context, SettingsRepository.PREF_ALIYUN_ACCESS_SECRET)
        val appKey = SecureStore.getSecret(context, SettingsRepository.PREF_ALIYUN_APP_KEY)
        if (accessKeyId.isNullOrBlank() || accessKeySecret.isNullOrBlank() || appKey.isNullOrBlank()) {
            throw AppException(ErrorCode.ASSISTANT_NOT_CONFIGURED, detail = "阿里云 AccessKey/AppKey 未配置")
        }
        if (!audio.exists() || audio.length() == 0L) {
            throw AppException(ErrorCode.ASSISTANT_CONNECT_FAILED, detail = "音频文件为空")
        }

        val voice = Base64.encodeToString(audio.readBytes(), Base64.NO_WRAP)
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date())

        // 公共参数 + 业务参数
        val params = TreeMap<String, String>(compareBy { it })
        params["appkey"] = appKey
        params["format"] = "wav"
        params["sample_rate"] = "16000"
        params["voice"] = voice
        params["version"] = "v1.0"
        params["enable_punctuation"] = "true"
        params["AccessKeyId"] = accessKeyId
        params["SignatureMethod"] = "HMAC-SHA1"
        params["SignatureNonce"] = UUID.randomUUID().toString()
        params["SignatureVersion"] = "1.0"
        params["Timestamp"] = timestamp
        params["Format"] = "JSON"

        // 规范化的查询串（key=value 按编码后 key 字典序）
        val canonicalized = params.entries.joinToString("&") { (k, v) ->
            "${Signing.percentEncode(k)}=${Signing.percentEncode(v)}"
        }
        val stringToSign = "GET&${Signing.percentEncode("/")}&${Signing.percentEncode(canonicalized)}"
        val keyBytes = "$accessKeySecret&".toByteArray(StandardCharsets.UTF_8)
        val signature = Base64.encodeToString(
            Signing.hmacSha1(keyBytes, stringToSign.toByteArray(StandardCharsets.UTF_8)),
            Base64.NO_WRAP,
        )

        val url = "$ENDPOINT?$canonicalized&Signature=${Signing.percentEncode(signature)}"
        val req = Request.Builder().url(url).get().build()

        runCatching {
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw AppException(ErrorCode.ASSISTANT_CONNECT_FAILED, detail = "HTTP ${resp.code}: ${text.take(200)}")
                }
                val json = JSONObject(text)
                val status = json.optInt("status", -1)
                if (status != 200) {
                    throw AppException(ErrorCode.ASSISTANT_CONNECT_FAILED, detail = "status=$status: ${json.optString("message")}")
                }
                json.optString("result", "").trim()
            }
        }.getOrElse { e ->
            throw if (e is AppException) e else AppException(ErrorCode.ASSISTANT_CONNECT_FAILED, cause = e, detail = e.message)
        }.also { if (it.isEmpty()) throw AppException(ErrorCode.ASSISTANT_CONNECT_FAILED, detail = "识别结果为空") }
    }

    override suspend fun test(): Boolean = withContext(Dispatchers.IO) {
        val accessKeyId = SecureStore.getSecret(context, SettingsRepository.PREF_ALIYUN_ACCESS_KEY)
        val accessKeySecret = SecureStore.getSecret(context, SettingsRepository.PREF_ALIYUN_ACCESS_SECRET)
        val appKey = SecureStore.getSecret(context, SettingsRepository.PREF_ALIYUN_APP_KEY)
        if (accessKeyId.isNullOrBlank() || accessKeySecret.isNullOrBlank() || appKey.isNullOrBlank()) return@withContext false
        // 端点连通性探测（阿里云 ASR 无免鉴权轻量 ping，仅验证网络可达）。
        runCatching {
            val req = Request.Builder().url("https://nls-gateway.cn-shanghai.aliyuncs.com/").get().build()
            client.newCall(req).execute().use { true }
        }.getOrDefault(false)
    }

    companion object {
        private const val ENDPOINT = "https://nls-gateway.cn-shanghai.aliyuncs.com/stream/v1/asr"
    }
}
