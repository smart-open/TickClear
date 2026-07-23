package com.tickclear.app.data.repositories

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tickclear.app.data.SecureStore
import com.tickclear.app.domain.repository.SettingsRepository
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
 * 全局偏好设置实现（DataStore + SecureStore）。契约见 [SettingsRepository]。
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {
    private val dataStore = context.dataStore

    override val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        runCatching { ThemeMode.valueOf(prefs[KEY_THEME] ?: ThemeMode.LIGHT.name) }
            .getOrDefault(ThemeMode.LIGHT)
    }

    override val animationEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_ANIMATION] ?: true
    }

    // ── 静音时段（低优先级通知自动静默；PRD 默认开启，默认 22:00–7:00）──
    override val quietHoursEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_QUIET_ENABLED] ?: true }
    override val quietStartMin: Flow<Int> = dataStore.data.map { it[KEY_QUIET_START] ?: SettingsRepository.DEFAULT_QUIET_START }
    override val quietEndMin: Flow<Int> = dataStore.data.map { it[KEY_QUIET_END] ?: SettingsRepository.DEFAULT_QUIET_END }

    /** 首次启动种子标记：false=尚未注入示例数据。 */
    override val firstRunDone: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_FIRST_RUN] ?: false
    }

    // ── AI 助手选型（Phase 5 填充；此处先定义契约）──
    override val aiMode: Flow<String> = dataStore.data.map { it[KEY_AI_MODE] ?: "LOCAL_NLU" }
    override val asrType: Flow<String> = dataStore.data.map { it[KEY_ASR] ?: "NONE" }
    override val llmType: Flow<String> = dataStore.data.map { it[KEY_LLM] ?: "NONE" }

    // ── 小智助手配置（Phase 6；token 等敏感值走 SecureStore）──
    override val assistantMode: Flow<String> = dataStore.data.map { it[KEY_ASSISTANT_MODE] ?: "MOCK" }
    override val assistantEndpoint: Flow<String> = dataStore.data.map { it[KEY_ASSISTANT_ENDPOINT] ?: "wss://api.xiaozhi.me/ws" }
    override val assistantPrompt: Flow<String> = dataStore.data.map {
        it[KEY_ASSISTANT_PROMPT]
            ?: "你是用户的专属私人AI助手，说话简短温柔、口语化，回答不超过两句话，用户唤醒后主动友好回应，贴合日常对话场景。"
    }

    // ── 多服务商 LLM（P5.4/P5.5；默认小智，可选 OpenAI 兼容；API Key 走 SecureStore）──
    override val llmProvider: Flow<String> = dataStore.data.map { it[KEY_LLM_PROVIDER] ?: SettingsRepository.DEFAULT_LLM_PROVIDER }
    override val llmBaseUrl: Flow<String> = dataStore.data.map { it[KEY_LLM_BASE_URL] ?: SettingsRepository.DEFAULT_LLM_BASE_URL }
    override val llmModel: Flow<String> = dataStore.data.map { it[KEY_LLM_MODEL] ?: SettingsRepository.DEFAULT_LLM_MODEL }

    // ── 多服务商 ASR（P5.5；默认小智，可选 OpenAI 兼容 /audio/transcriptions；API Key 走 SecureStore）──
    override val asrProvider: Flow<String> = dataStore.data.map { it[KEY_ASR_PROVIDER] ?: SettingsRepository.DEFAULT_ASR_PROVIDER }
    override val asrBaseUrl: Flow<String> = dataStore.data.map { it[KEY_ASR_BASE_URL] ?: SettingsRepository.DEFAULT_ASR_BASE_URL }
    override val asrModel: Flow<String> = dataStore.data.map { it[KEY_ASR_MODEL] ?: SettingsRepository.DEFAULT_ASR_MODEL }

    // ── 语音唤醒词（离线 best-effort，系统识别服务兜底；默认关闭）──
    override val wakeWordEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_WAKE_WORD_ENABLED] ?: false }
    override val wakeWord: Flow<String> = dataStore.data.map { it[KEY_WAKE_WORD] ?: SettingsRepository.DEFAULT_WAKE_WORD }

    // ── 信任模式（PRD D20：开启后语音建任务免确认；危险操作仍强制确认；默认关闭）──
    override val trustMode: Flow<Boolean> = dataStore.data.map { it[KEY_TRUST_MODE] ?: false }

    // ── 自动备份（V2.5）：默认关闭，需用户主动开启；记录最近执行时间用于回显。──
    override val autoBackupEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_AUTO_BACKUP] ?: false }
    override val lastAutoBackupAt: Flow<Long> = dataStore.data.map { it[KEY_LAST_BACKUP_AT] ?: 0L }

    override suspend fun setThemeMode(mode: ThemeMode) { dataStore.edit { it[KEY_THEME] = mode.name } }
    override suspend fun setAnimationEnabled(enabled: Boolean) { dataStore.edit { it[KEY_ANIMATION] = enabled } }
    override suspend fun setQuietHoursEnabled(enabled: Boolean) { dataStore.edit { it[KEY_QUIET_ENABLED] = enabled } }
    override suspend fun setQuietStartMin(min: Int) { dataStore.edit { it[KEY_QUIET_START] = min.coerceIn(0, 1439) } }
    override suspend fun setQuietEndMin(min: Int) { dataStore.edit { it[KEY_QUIET_END] = min.coerceIn(0, 1439) } }
    override suspend fun setFirstRunDone(done: Boolean) { dataStore.edit { it[KEY_FIRST_RUN] = done } }
    override suspend fun setAiMode(mode: String) { dataStore.edit { it[KEY_AI_MODE] = mode } }
    override suspend fun setAsrType(type: String) { dataStore.edit { it[KEY_ASR] = type } }
    override suspend fun setLlmType(type: String) { dataStore.edit { it[KEY_LLM] = type } }
    override suspend fun setAssistantMode(mode: String) { dataStore.edit { it[KEY_ASSISTANT_MODE] = mode } }
    override suspend fun setAssistantEndpoint(endpoint: String) { dataStore.edit { it[KEY_ASSISTANT_ENDPOINT] = endpoint } }
    override suspend fun setAssistantPrompt(prompt: String) { dataStore.edit { it[KEY_ASSISTANT_PROMPT] = prompt } }
    override suspend fun setLlmProvider(provider: String) { dataStore.edit { it[KEY_LLM_PROVIDER] = provider } }
    override suspend fun setLlmBaseUrl(url: String) { dataStore.edit { it[KEY_LLM_BASE_URL] = url } }
    override suspend fun setLlmModel(model: String) { dataStore.edit { it[KEY_LLM_MODEL] = model } }
    override suspend fun setAsrProvider(provider: String) { dataStore.edit { it[KEY_ASR_PROVIDER] = provider } }
    override suspend fun setAsrBaseUrl(url: String) { dataStore.edit { it[KEY_ASR_BASE_URL] = url } }
    override suspend fun setAsrModel(model: String) { dataStore.edit { it[KEY_ASR_MODEL] = model } }
    override suspend fun setWakeWordEnabled(enabled: Boolean) { dataStore.edit { it[KEY_WAKE_WORD_ENABLED] = enabled } }
    override suspend fun setWakeWord(word: String) { dataStore.edit { it[KEY_WAKE_WORD] = word.trim().ifEmpty { SettingsRepository.DEFAULT_WAKE_WORD } } }
    override suspend fun setTrustMode(enabled: Boolean) { dataStore.edit { it[KEY_TRUST_MODE] = enabled } }
    override suspend fun setAutoBackupEnabled(enabled: Boolean) { dataStore.edit { it[KEY_AUTO_BACKUP] = enabled } }
    override suspend fun setLastAutoBackupAt(at: Long) { dataStore.edit { it[KEY_LAST_BACKUP_AT] = at } }

    override suspend fun getAssistantToken(): String? = withContext(Dispatchers.IO) {
        SecureStore.getSecret(context, SettingsRepository.PREF_XZ_TOKEN)
    }

    override suspend fun getLlmApiKey(providerId: String): String? = withContext(Dispatchers.IO) {
        SecureStore.getSecret(context, llmKeyFor(providerId))
    }

    override suspend fun setLlmApiKey(providerId: String, key: String) = withContext(Dispatchers.IO) {
        SecureStore.putSecret(context, llmKeyFor(providerId), key)
    }

    override suspend fun getAsrApiKey(): String? = withContext(Dispatchers.IO) {
        SecureStore.getSecret(context, SettingsRepository.PREF_ASR_API_KEY)
    }

    override suspend fun getTencentSecretId(): String? = withContext(Dispatchers.IO) {
        SecureStore.getSecret(context, SettingsRepository.PREF_TENCENT_SECRET_ID)
    }
    override suspend fun setTencentSecretId(v: String) = withContext(Dispatchers.IO) {
        SecureStore.putSecret(context, SettingsRepository.PREF_TENCENT_SECRET_ID, v)
    }
    override suspend fun getTencentSecretKey(): String? = withContext(Dispatchers.IO) {
        SecureStore.getSecret(context, SettingsRepository.PREF_TENCENT_SECRET_KEY)
    }
    override suspend fun setTencentSecretKey(v: String) = withContext(Dispatchers.IO) {
        SecureStore.putSecret(context, SettingsRepository.PREF_TENCENT_SECRET_KEY, v)
    }

    override suspend fun getAliyunAccessKeyId(): String? = withContext(Dispatchers.IO) {
        SecureStore.getSecret(context, SettingsRepository.PREF_ALIYUN_ACCESS_KEY)
    }
    override suspend fun setAliyunAccessKeyId(v: String) = withContext(Dispatchers.IO) {
        SecureStore.putSecret(context, SettingsRepository.PREF_ALIYUN_ACCESS_KEY, v)
    }
    override suspend fun getAliyunAccessKeySecret(): String? = withContext(Dispatchers.IO) {
        SecureStore.getSecret(context, SettingsRepository.PREF_ALIYUN_ACCESS_SECRET)
    }
    override suspend fun setAliyunAccessKeySecret(v: String) = withContext(Dispatchers.IO) {
        SecureStore.putSecret(context, SettingsRepository.PREF_ALIYUN_ACCESS_SECRET, v)
    }
    override suspend fun getAliyunAppKey(): String? = withContext(Dispatchers.IO) {
        SecureStore.getSecret(context, SettingsRepository.PREF_ALIYUN_APP_KEY)
    }
    override suspend fun setAliyunAppKey(v: String) = withContext(Dispatchers.IO) {
        SecureStore.putSecret(context, SettingsRepository.PREF_ALIYUN_APP_KEY, v)
    }

    private fun llmKeyFor(providerId: String): String = when (providerId) {
        "doubao" -> SettingsRepository.PREF_LLM_API_KEY_DOUBAO
        "qianwen" -> SettingsRepository.PREF_LLM_API_KEY_QIANWEN
        else -> SettingsRepository.PREF_LLM_API_KEY_OPENAI
    }

    private companion object {
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
        private val KEY_TRUST_MODE = booleanPreferencesKey("trust_mode")
        private val KEY_AUTO_BACKUP = booleanPreferencesKey("auto_backup_enabled")
        private val KEY_LAST_BACKUP_AT = longPreferencesKey("last_auto_backup_at")
    }
}
