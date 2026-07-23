package com.tickclear.app.data.repositories

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tickclear.app.data.SecureStore
import com.tickclear.app.ui.theme.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("tickclear_settings")

/**
 * 全局偏好设置（DataStore）。
 * 敏感值（ASR/LLM 密钥、SQLCipher 口令）不存此处，改用 EncryptedSharedPreferences（见 data/SecureStore）。
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore = context.dataStore

    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        runCatching { ThemeMode.valueOf(prefs[KEY_THEME] ?: ThemeMode.LIGHT.name) }
            .getOrDefault(ThemeMode.LIGHT)
    }

    val animationEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_ANIMATION] ?: true
    }

    // ── 静音时段（低优先级通知自动静默；默认关闭，默认 22:00–7:00）──
    val quietHoursEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_QUIET_ENABLED] ?: false }
    val quietStartMin: Flow<Int> = dataStore.data.map { it[KEY_QUIET_START] ?: DEFAULT_QUIET_START }
    val quietEndMin: Flow<Int> = dataStore.data.map { it[KEY_QUIET_END] ?: DEFAULT_QUIET_END }

    /** 首次启动种子标记：false=尚未注入示例数据。 */
    val firstRunDone: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_FIRST_RUN] ?: false
    }

    // ── AI 助手选型（Phase 5 填充；此处先定义契约）──
    val aiMode: Flow<String> = dataStore.data.map { it[KEY_AI_MODE] ?: "LOCAL_NLU" }
    val asrType: Flow<String> = dataStore.data.map { it[KEY_ASR] ?: "NONE" }
    val llmType: Flow<String> = dataStore.data.map { it[KEY_LLM] ?: "NONE" }

    // ── 小智助手配置（Phase 6；token 等敏感值走 SecureStore）──
    val assistantMode: Flow<String> = dataStore.data.map { it[KEY_ASSISTANT_MODE] ?: "MOCK" }
    val assistantEndpoint: Flow<String> = dataStore.data.map { it[KEY_ASSISTANT_ENDPOINT] ?: "wss://api.xiaozhi.me/ws" }
    val assistantPrompt: Flow<String> = dataStore.data.map {
        it[KEY_ASSISTANT_PROMPT]
            ?: "你是用户的专属私人AI助手，说话简短温柔、口语化，回答不超过两句话，用户唤醒后主动友好回应，贴合日常对话场景。"
    }

    // ── 多服务商 LLM（P5.4/P5.5；默认小智，可选 OpenAI 兼容；API Key 走 SecureStore）──
    val llmProvider: Flow<String> = dataStore.data.map { it[KEY_LLM_PROVIDER] ?: DEFAULT_LLM_PROVIDER }
    val llmBaseUrl: Flow<String> = dataStore.data.map { it[KEY_LLM_BASE_URL] ?: DEFAULT_LLM_BASE_URL }
    val llmModel: Flow<String> = dataStore.data.map { it[KEY_LLM_MODEL] ?: DEFAULT_LLM_MODEL }

    // ── 多服务商 ASR（P5.5；默认小智，可选 OpenAI 兼容 /audio/transcriptions；API Key 走 SecureStore）──
    val asrProvider: Flow<String> = dataStore.data.map { it[KEY_ASR_PROVIDER] ?: DEFAULT_ASR_PROVIDER }
    val asrBaseUrl: Flow<String> = dataStore.data.map { it[KEY_ASR_BASE_URL] ?: DEFAULT_ASR_BASE_URL }
    val asrModel: Flow<String> = dataStore.data.map { it[KEY_ASR_MODEL] ?: DEFAULT_ASR_MODEL }

    // ── 语音唤醒词（离线 best-effort，系统识别服务兜底；默认关闭）──
    val wakeWordEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_WAKE_WORD_ENABLED] ?: false }
    val wakeWord: Flow<String> = dataStore.data.map { it[KEY_WAKE_WORD] ?: DEFAULT_WAKE_WORD }

    suspend fun setThemeMode(mode: ThemeMode) = dataStore.edit { it[KEY_THEME] = mode.name }
    suspend fun setAnimationEnabled(enabled: Boolean) = dataStore.edit { it[KEY_ANIMATION] = enabled }
    suspend fun setQuietHoursEnabled(enabled: Boolean) = dataStore.edit { it[KEY_QUIET_ENABLED] = enabled }
    suspend fun setQuietStartMin(min: Int) = dataStore.edit { it[KEY_QUIET_START] = min.coerceIn(0, 1439) }
    suspend fun setQuietEndMin(min: Int) = dataStore.edit { it[KEY_QUIET_END] = min.coerceIn(0, 1439) }
    suspend fun setFirstRunDone(done: Boolean) = dataStore.edit { it[KEY_FIRST_RUN] = done }
    suspend fun setAiMode(mode: String) = dataStore.edit { it[KEY_AI_MODE] = mode }
    suspend fun setAsrType(type: String) = dataStore.edit { it[KEY_ASR] = type }
    suspend fun setLlmType(type: String) = dataStore.edit { it[KEY_LLM] = type }
    suspend fun setAssistantMode(mode: String) = dataStore.edit { it[KEY_ASSISTANT_MODE] = mode }
    suspend fun setAssistantEndpoint(endpoint: String) = dataStore.edit { it[KEY_ASSISTANT_ENDPOINT] = endpoint }
    suspend fun setAssistantPrompt(prompt: String) = dataStore.edit { it[KEY_ASSISTANT_PROMPT] = prompt }
    suspend fun setLlmProvider(provider: String) = dataStore.edit { it[KEY_LLM_PROVIDER] = provider }
    suspend fun setLlmBaseUrl(url: String) = dataStore.edit { it[KEY_LLM_BASE_URL] = url }
    suspend fun setLlmModel(model: String) = dataStore.edit { it[KEY_LLM_MODEL] = model }
    suspend fun setAsrProvider(provider: String) = dataStore.edit { it[KEY_ASR_PROVIDER] = provider }
    suspend fun setAsrBaseUrl(url: String) = dataStore.edit { it[KEY_ASR_BASE_URL] = url }
    suspend fun setAsrModel(model: String) = dataStore.edit { it[KEY_ASR_MODEL] = model }
    suspend fun setWakeWordEnabled(enabled: Boolean) = dataStore.edit { it[KEY_WAKE_WORD_ENABLED] = enabled }
    suspend fun setWakeWord(word: String) = dataStore.edit { it[KEY_WAKE_WORD] = word.trim().ifEmpty { DEFAULT_WAKE_WORD } }

    /** 真实小智模式的网关令牌（存于加密存储，非 DataStore）。 */
    suspend fun getAssistantToken(): String? = withContext(Dispatchers.IO) {
        SecureStore.getSecret(context, PREF_XZ_TOKEN)
    }

    /** OpenAI 兼容服务商的 API Key（存于加密存储，按服务商区分）。 */
    suspend fun getLlmApiKey(providerId: String = DEFAULT_LLM_PROVIDER): String? = withContext(Dispatchers.IO) {
        SecureStore.getSecret(context, llmKeyFor(providerId))
    }

    /** 写入指定服务商的 LLM API Key（存于加密存储，按服务商区分）。 */
    suspend fun setLlmApiKey(providerId: String = DEFAULT_LLM_PROVIDER, key: String) = withContext(Dispatchers.IO) {
        SecureStore.putSecret(context, llmKeyFor(providerId), key)
    }

    /** OpenAI 兼容 ASR 服务商的 API Key（存于加密存储）。 */
    suspend fun getAsrApiKey(): String? = withContext(Dispatchers.IO) {
        SecureStore.getSecret(context, PREF_ASR_API_KEY)
    }

    // ── 腾讯云 ASR 凭据（SecretId / SecretKey，TC3-HMAC-SHA256 签名用）──
    suspend fun getTencentSecretId(): String? = withContext(Dispatchers.IO) {
        SecureStore.getSecret(context, PREF_TENCENT_SECRET_ID)
    }
    suspend fun setTencentSecretId(v: String) = withContext(Dispatchers.IO) {
        SecureStore.putSecret(context, PREF_TENCENT_SECRET_ID, v)
    }
    suspend fun getTencentSecretKey(): String? = withContext(Dispatchers.IO) {
        SecureStore.getSecret(context, PREF_TENCENT_SECRET_KEY)
    }
    suspend fun setTencentSecretKey(v: String) = withContext(Dispatchers.IO) {
        SecureStore.putSecret(context, PREF_TENCENT_SECRET_KEY, v)
    }

    // ── 阿里云 ASR 凭据（AccessKeyId / AccessKeySecret / AppKey，RPC 签名用）──
    suspend fun getAliyunAccessKeyId(): String? = withContext(Dispatchers.IO) {
        SecureStore.getSecret(context, PREF_ALIYUN_ACCESS_KEY)
    }
    suspend fun setAliyunAccessKeyId(v: String) = withContext(Dispatchers.IO) {
        SecureStore.putSecret(context, PREF_ALIYUN_ACCESS_KEY, v)
    }
    suspend fun getAliyunAccessKeySecret(): String? = withContext(Dispatchers.IO) {
        SecureStore.getSecret(context, PREF_ALIYUN_ACCESS_SECRET)
    }
    suspend fun setAliyunAccessKeySecret(v: String) = withContext(Dispatchers.IO) {
        SecureStore.putSecret(context, PREF_ALIYUN_ACCESS_SECRET, v)
    }
    suspend fun getAliyunAppKey(): String? = withContext(Dispatchers.IO) {
        SecureStore.getSecret(context, PREF_ALIYUN_APP_KEY)
    }
    suspend fun setAliyunAppKey(v: String) = withContext(Dispatchers.IO) {
        SecureStore.putSecret(context, PREF_ALIYUN_APP_KEY, v)
    }

    private fun llmKeyFor(providerId: String): String = when (providerId) {
        "doubao" -> PREF_LLM_API_KEY_DOUBAO
        "qianwen" -> PREF_LLM_API_KEY_QIANWEN
        else -> PREF_LLM_API_KEY_OPENAI
    }

    companion object {
        const val PREF_XZ_TOKEN = "xiaozhi_token"
        const val PREF_LLM_API_KEY_OPENAI = "llm_api_key_openai"
        const val PREF_LLM_API_KEY_DOUBAO = "llm_api_key_doubao"
        const val PREF_LLM_API_KEY_QIANWEN = "llm_api_key_qianwen"
        const val PREF_ASR_API_KEY = "asr_api_key"
        const val PREF_TENCENT_SECRET_ID = "tencent_asr_secret_id"
        const val PREF_TENCENT_SECRET_KEY = "tencent_asr_secret_key"
        const val PREF_ALIYUN_ACCESS_KEY = "aliyun_asr_access_key"
        const val PREF_ALIYUN_ACCESS_SECRET = "aliyun_asr_access_secret"
        const val PREF_ALIYUN_APP_KEY = "aliyun_asr_app_key"
        const val DEFAULT_LLM_PROVIDER = "xiaozhi"
        const val DEFAULT_LLM_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_LLM_MODEL = "gpt-4o-mini"
        const val DEFAULT_ASR_PROVIDER = "xiaozhi"
        const val DEFAULT_ASR_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_ASR_MODEL = "whisper-1"
        const val DEFAULT_WAKE_WORD = "小清"
        const val DEFAULT_QUIET_START = 22 * 60 // 22:00
        const val DEFAULT_QUIET_END = 7 * 60    // 07:00

        /** 判断某分钟数(0-1439)是否落在静音时段 [start,end)，支持跨午夜。 */
        fun isInQuietWindow(nowMin: Int, startMin: Int, endMin: Int): Boolean {
            if (startMin == endMin) return false
            return if (startMin < endMin) {
                nowMin in startMin until endMin
            } else {
                // 跨午夜：如 22:00–07:00
                nowMin >= startMin || nowMin < endMin
            }
        }

        private val KEY_THEME = stringPreferencesKey("theme_mode")
        private val KEY_ANIMATION = booleanPreferencesKey("animation_enabled")
        private val KEY_QUIET_ENABLED = booleanPreferencesKey("quiet_enabled")
        private val KEY_QUIET_START = intPreferencesKey("quiet_start_min")
        private val KEY_QUIET_END = intPreferencesKey("quiet_end_min")
        private val KEY_AI_MODE = stringPreferencesKey("ai_mode")
        private val KEY_ASR = stringPreferencesKey("asr_type")
        private val KEY_LLM = stringPreferencesKey("llm_type")
        private val KEY_FIRST_RUN = booleanPreferencesKey("first_run_done")
        private val KEY_ASSISTANT_MODE = stringPreferencesKey("assistant_mode")
        private val KEY_ASSISTANT_ENDPOINT = stringPreferencesKey("assistant_endpoint")
        private val KEY_ASSISTANT_PROMPT = stringPreferencesKey("assistant_prompt")
        private val KEY_LLM_PROVIDER = stringPreferencesKey("llm_provider")
        private val KEY_LLM_BASE_URL = stringPreferencesKey("llm_base_url")
        private val KEY_LLM_MODEL = stringPreferencesKey("llm_model")
        private val KEY_ASR_PROVIDER = stringPreferencesKey("asr_provider")
        private val KEY_ASR_BASE_URL = stringPreferencesKey("asr_base_url")
        private val KEY_ASR_MODEL = stringPreferencesKey("asr_model")
        private val KEY_WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
        private val KEY_WAKE_WORD = stringPreferencesKey("wake_word")
    }
}
