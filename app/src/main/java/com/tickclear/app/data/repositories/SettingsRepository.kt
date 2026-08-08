package com.tickclear.app.data.repositories

import android.content.Context
import com.tickclear.app.R
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tickclear.app.data.SecureStore
import com.tickclear.app.domain.backup.BackupHealth
import com.tickclear.app.domain.log.AppLogger
import com.tickclear.app.domain.repository.SettingsRepository
import com.tickclear.app.ui.theme.ThemeMode
import com.tickclear.app.ui.theme.ThemeSkin
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
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

    override val themeSkin: Flow<ThemeSkin> = dataStore.data.map { prefs ->
        runCatching { ThemeSkin.valueOf(prefs[KEY_THEME_SKIN] ?: ThemeSkin.BLUE.name) }
            .getOrDefault(ThemeSkin.BLUE)
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

    // ── 智能助手配置（Phase 6；token 等敏感值走 SecureStore）──
    override val assistantMode: Flow<String> = dataStore.data.map { it[KEY_ASSISTANT_MODE] ?: "MOCK" }
    override val assistantEndpoint: Flow<String> = dataStore.data.map { it[KEY_ASSISTANT_ENDPOINT] ?: "wss://api.tenclass.net/xiaozhi/v1/" }
    override val assistantPrompt: Flow<String> = dataStore.data.map {
        it[KEY_ASSISTANT_PROMPT] ?: context.getString(R.string.assistant_prompt_default)
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
    override val wakeWord: Flow<String> = dataStore.data.map { it[KEY_WAKE_WORD] ?: context.getString(R.string.wake_word_default) }

    // ── 信任模式（PRD D20：开启后语音建任务免确认；危险操作仍强制确认；默认关闭）──
    override val trustMode: Flow<Boolean> = dataStore.data.map { it[KEY_TRUST_MODE] ?: false }

    // ── 自动备份（V2.5）：默认关闭，需用户主动开启；记录最近执行时间用于回显。──
    override val autoBackupEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_AUTO_BACKUP] ?: false }
    override val lastAutoBackupAt: Flow<Long> = dataStore.data.map { it[KEY_LAST_BACKUP_AT] ?: 0L }

    override val lastBackupHealth: Flow<BackupHealth> = dataStore.data.map { prefs ->
        runCatching { BackupHealth.valueOf(prefs[KEY_LAST_BACKUP_HEALTH] ?: BackupHealth.NONE.name) }
            .getOrDefault(BackupHealth.NONE)
    }

    // ── 稍后提醒默认时长（V2.30）：默认 15 分钟，受 SNOOZE_OPTIONS 约束。──
    override val snoozeDefaultMin: Flow<Int> = dataStore.data.map { prefs ->
        com.tickclear.app.domain.scheduler.ReminderPrefs.normalizeSnoozeMin(
            prefs[KEY_SNOOZE_MIN] ?: SettingsRepository.DEFAULT_SNOOZE_MIN,
        )
    }

    // ── 提醒音效开关（V2.31）：默认开启。──
    override val soundEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_SOUND_ENABLED] ?: true }

    // ── 清空前确认（V2.40）：默认开启。──
    override val clearConfirmEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_CLEAR_CONFIRM] ?: true }

    // ── 离线语音指令（V2.42）：默认开启。──
    override val offlineCommandEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_OFFLINE_CMD] ?: true }

    // ── 系统 ASR 语言（方言）（V2.43）：默认普通话 zh-CN。──
    override val asrLanguage: Flow<String> = dataStore.data.map { it[KEY_ASR_LANGUAGE] ?: SettingsRepository.DEFAULT_ASR_LANGUAGE }

    // ── 语音历史保存（V2.65）：默认关闭。──
    override val voiceHistoryEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_VOICE_HISTORY] ?: false }

    // ── 调试日志开关（V2.8X）：默认关闭，仅保留 WARN/ERROR。──
    override val debugLogEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_DEBUG_LOG] ?: false }

    // ── 工具箱：间隔提醒（V2.9）：默认关闭，需用户在工具页主动开启。──
    override val waterEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_WATER_ENABLED] ?: false }
    override val waterIntervalMin: Flow<Int> = dataStore.data.map { it[KEY_WATER_INTERVAL] ?: SettingsRepository.DEFAULT_WATER_INTERVAL_MIN }
    override val waterIntakeMl: Flow<Int> = dataStore.data.map { it[KEY_WATER_INTAKE_ML] ?: 0 }
    override val waterIntakeDate: Flow<String> = dataStore.data.map { it[KEY_WATER_INTAKE_DATE] ?: "" }
    override val waterGoalMl: Flow<Int> = dataStore.data.map { it[KEY_WATER_GOAL_ML] ?: SettingsRepository.DEFAULT_WATER_GOAL_ML }
    override val restEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_REST_ENABLED] ?: false }
    override val restIntervalMin: Flow<Int> = dataStore.data.map { it[KEY_REST_INTERVAL] ?: SettingsRepository.DEFAULT_REST_INTERVAL_MIN }
    override val eyecareEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_EYECARE_ENABLED] ?: false }
    override val eyecareIntervalMin: Flow<Int> = dataStore.data.map { it[KEY_EYECARE_INTERVAL] ?: SettingsRepository.DEFAULT_EYECARE_INTERVAL_MIN }
    override val napLastDurationMin: Flow<Int> = dataStore.data.map { it[KEY_NAP_DURATION] ?: SettingsRepository.DEFAULT_NAP_DURATION_MIN }
    override val napNoiseEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_NAP_NOISE_ENABLED] ?: SettingsRepository.DEFAULT_NAP_NOISE_ENABLED }
    override val napNoiseScene: Flow<String> = dataStore.data.map { it[KEY_NAP_NOISE_SCENE] ?: SettingsRepository.DEFAULT_NAP_NOISE_SCENE }
    override val napFadeMin: Flow<Int> = dataStore.data.map { it[KEY_NAP_FADE_MIN] ?: SettingsRepository.DEFAULT_NAP_FADE_MIN }

    // ── 工具箱：听力保护（V2.9++）──
    override val hearingEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_HEARING_ENABLED] ?: false }
    override val hearingVolumeThreshold: Flow<Int> = dataStore.data.map { it[KEY_HEARING_VOLUME] ?: SettingsRepository.DEFAULT_HEARING_VOLUME_THRESHOLD }
    override val hearingMaxWearMin: Flow<Int> = dataStore.data.map { it[KEY_HEARING_WEAR] ?: SettingsRepository.DEFAULT_HEARING_WEAR_MIN }

    // ── 工具箱：抽签器（V2.9++）──
    override val lotteryOptions: Flow<String> = dataStore.data.map { it[KEY_LOTTERY_OPTIONS] ?: "" }

    // ── 工具箱：情绪打卡（V2.9++）──
    override val moodLog: Flow<String> = dataStore.data.map { it[KEY_MOOD_LOG] ?: "" }

    // ── 工具箱：重要日子倒计时（V2.9++）──
    override val countdownEvents: Flow<String> = dataStore.data.map { it[KEY_COUNTDOWN_EVENTS] ?: "" }

    // ── 工具箱：语音备忘降噪（V2.9++）──
    override val voiceNoiseReduction: Flow<Boolean> = dataStore.data.map { it[KEY_VOICE_NOISE] ?: false }

    // ── 工具箱：剪贴板防窃取（V2.9++）──
    override val clipboardAutoClear: Flow<Boolean> = dataStore.data.map { it[KEY_CLIP_AUTO_CLEAR] ?: false }
    override val clipboardClearDelaySec: Flow<Int> = dataStore.data.map { prefs ->
        (prefs[KEY_CLIP_CLEAR_DELAY] ?: SettingsRepository.DEFAULT_CLIPBOARD_CLEAR_DELAY_SEC).coerceIn(5, 120)
    }

    // ── 工具箱：到站提醒（V2.9++）──
    override val arrivalStations: Flow<String> = dataStore.data.map { it[KEY_ARRIVAL_STATIONS] ?: "" }
    override val arrivalEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_ARRIVAL_ENABLED] ?: false }

    // ── 工具箱：常用工具置顶（V2.9++）──
    // 用 Set 存储去重，但 Flow 在 map 内转 List 时按插入顺序（DataStore 保证 Set 有序）。
    override val favoriteToolRoutes: Flow<List<String>> = dataStore.data.map { prefs ->
        prefs[KEY_FAVORITE_TOOLS]?.toList().orEmpty()
    }
    override suspend fun setFavoriteToolRoutes(routes: List<String>) {
        // 同一路由重复传会被 LinkedHashSet 自动去重，保序
        dataStore.edit { it[KEY_FAVORITE_TOOLS] = LinkedHashSet(routes) }
    }

    override suspend fun setThemeMode(mode: ThemeMode) { dataStore.edit { it[KEY_THEME] = mode.name } }
    override suspend fun setThemeSkin(skin: ThemeSkin) { dataStore.edit { it[KEY_THEME_SKIN] = skin.name } }
    override suspend fun setAnimationEnabled(enabled: Boolean) { dataStore.edit { it[KEY_ANIMATION] = enabled } }
    override suspend fun setQuietHoursEnabled(enabled: Boolean) { dataStore.edit { it[KEY_QUIET_ENABLED] = enabled } }
    override suspend fun setQuietStartMin(min: Int) { dataStore.edit { it[KEY_QUIET_START] = min.coerceIn(0, 1439) } }
    override suspend fun setQuietEndMin(min: Int) { dataStore.edit { it[KEY_QUIET_END] = min.coerceIn(0, 1439) } }
    override suspend fun setFirstRunDone(done: Boolean) { dataStore.edit { it[KEY_FIRST_RUN] = done } }
    override suspend fun setAiMode(mode: String) { dataStore.edit { it[KEY_AI_MODE] = mode } }
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
    override suspend fun setWakeWord(word: String) { dataStore.edit { it[KEY_WAKE_WORD] = word.trim().ifEmpty { context.getString(R.string.wake_word_default) } } }
    override suspend fun setTrustMode(enabled: Boolean) { dataStore.edit { it[KEY_TRUST_MODE] = enabled } }
    override suspend fun setAutoBackupEnabled(enabled: Boolean) { dataStore.edit { it[KEY_AUTO_BACKUP] = enabled } }
    override suspend fun setLastAutoBackupAt(at: Long) { dataStore.edit { it[KEY_LAST_BACKUP_AT] = at } }
    override suspend fun setLastBackupHealth(health: BackupHealth) { dataStore.edit { it[KEY_LAST_BACKUP_HEALTH] = health.name } }
    override suspend fun setSnoozeDefaultMin(min: Int) {
        dataStore.edit { it[KEY_SNOOZE_MIN] = com.tickclear.app.domain.scheduler.ReminderPrefs.normalizeSnoozeMin(min) }
    }
    override suspend fun setSoundEnabled(enabled: Boolean) { dataStore.edit { it[KEY_SOUND_ENABLED] = enabled } }
    override suspend fun setClearConfirmEnabled(enabled: Boolean) { dataStore.edit { it[KEY_CLEAR_CONFIRM] = enabled } }
    override suspend fun setOfflineCommandEnabled(enabled: Boolean) { dataStore.edit { it[KEY_OFFLINE_CMD] = enabled } }
    override suspend fun setAsrLanguage(language: String) { dataStore.edit { it[KEY_ASR_LANGUAGE] = language } }
    override suspend fun setVoiceHistoryEnabled(enabled: Boolean) { dataStore.edit { it[KEY_VOICE_HISTORY] = enabled } }

    // 写 DataStore 的同时立刻同步内存开关，保证「配置即生效」（不必等 Flow 回灌）。
    override suspend fun setDebugLogEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_DEBUG_LOG] = enabled }
        com.tickclear.app.domain.log.AppLogger.setDebugEnabled(enabled)
    }

    // ── 工具箱：间隔提醒（V2.9）──
    override suspend fun setWaterEnabled(enabled: Boolean) { dataStore.edit { it[KEY_WATER_ENABLED] = enabled } }
    override suspend fun setWaterIntervalMin(min: Int) { dataStore.edit { it[KEY_WATER_INTERVAL] = min.coerceAtLeast(5) } }
    override suspend fun setWaterIntakeMl(ml: Int) { dataStore.edit { it[KEY_WATER_INTAKE_ML] = ml.coerceAtLeast(0) } }
    override suspend fun setWaterIntakeDate(date: String) { dataStore.edit { it[KEY_WATER_INTAKE_DATE] = date } }
    override suspend fun setWaterGoalMl(ml: Int) { dataStore.edit { it[KEY_WATER_GOAL_ML] = ml.coerceAtLeast(200) } }
    override suspend fun setRestEnabled(enabled: Boolean) { dataStore.edit { it[KEY_REST_ENABLED] = enabled } }
    override suspend fun setRestIntervalMin(min: Int) { dataStore.edit { it[KEY_REST_INTERVAL] = min.coerceAtLeast(5) } }
    override suspend fun setEyecareEnabled(enabled: Boolean) { dataStore.edit { it[KEY_EYECARE_ENABLED] = enabled } }
    override suspend fun setEyecareIntervalMin(min: Int) { dataStore.edit { it[KEY_EYECARE_INTERVAL] = min.coerceAtLeast(5) } }
    override suspend fun setNapLastDurationMin(min: Int) { dataStore.edit { it[KEY_NAP_DURATION] = min.coerceAtLeast(5) } }
    override suspend fun setNapNoiseEnabled(enabled: Boolean) { dataStore.edit { it[KEY_NAP_NOISE_ENABLED] = enabled } }
    override suspend fun setNapNoiseScene(scene: String) { dataStore.edit { it[KEY_NAP_NOISE_SCENE] = scene } }
    override suspend fun setNapFadeMin(min: Int) { dataStore.edit { it[KEY_NAP_FADE_MIN] = min.coerceAtLeast(0) } }

    // ── 工具箱：听力保护（V2.9++）──
    override suspend fun setHearingEnabled(enabled: Boolean) { dataStore.edit { it[KEY_HEARING_ENABLED] = enabled } }
    override suspend fun setHearingVolumeThreshold(threshold: Int) { dataStore.edit { it[KEY_HEARING_VOLUME] = threshold.coerceIn(0, 100) } }
    override suspend fun setHearingMaxWearMin(min: Int) { dataStore.edit { it[KEY_HEARING_WEAR] = min.coerceAtLeast(5) } }

    // ── 工具箱：抽签器（V2.9++）──
    override suspend fun setLotteryOptions(options: String) { dataStore.edit { it[KEY_LOTTERY_OPTIONS] = options } }

    // ── 工具箱：情绪打卡（V2.9++）──
    override suspend fun setMoodLog(log: String) { dataStore.edit { it[KEY_MOOD_LOG] = log } }

    // ── 工具箱：重要日子倒计时（V2.9++）──
    override suspend fun setCountdownEvents(events: String) { dataStore.edit { it[KEY_COUNTDOWN_EVENTS] = events } }

    // ── 工具箱：语音备忘降噪（V2.9++）──
    override suspend fun setVoiceNoiseReduction(enabled: Boolean) { dataStore.edit { it[KEY_VOICE_NOISE] = enabled } }

    // ── 工具箱：剪贴板防窃取（V2.9++）──
    override suspend fun setClipboardAutoClear(enabled: Boolean) { dataStore.edit { it[KEY_CLIP_AUTO_CLEAR] = enabled } }
    override suspend fun setClipboardClearDelaySec(sec: Int) { dataStore.edit { it[KEY_CLIP_CLEAR_DELAY] = sec.coerceIn(5, 120) } }

    // ── 工具箱：到站提醒（V2.9++）──
    override suspend fun setArrivalStations(text: String) { dataStore.edit { it[KEY_ARRIVAL_STATIONS] = text } }
    override suspend fun setArrivalEnabled(on: Boolean) { dataStore.edit { it[KEY_ARRIVAL_ENABLED] = on } }

    /** 导出全部偏好为 JSON（零依赖，org.json）。覆盖布尔/整型/长整型/浮点/双精度/字符串/字符串集合。 */
    override suspend fun exportSettingsJson(): String {
        val prefs = dataStore.data.first()
        val obj = JSONObject()
        for ((key, value) in prefs.asMap().entries) {
            when (value) {
                is Boolean -> obj.put(key.name, value)
                is Int -> obj.put(key.name, value)
                is Long -> obj.put(key.name, value)
                is Float -> obj.put(key.name, value)
                is Double -> obj.put(key.name, value)
                is String -> obj.put(key.name, value)
                is Set<*> -> obj.put(key.name, JSONArray(value.map { it.toString() }))
                else -> obj.put(key.name, value.toString())
            }
        }
        return obj.toString()
    }

    /**
     * 从 JSON 恢复全部偏好（覆盖式写入已知键，未知键跳过）。
     * DataStore 的 Preferences.Key 是类型绑定的，JSON 丢失了原始类型，故按 key 名路由到正确的类型写入。
     */
    override suspend fun importSettingsJson(json: String) {
        val obj = JSONObject(json)
        val it = obj.keys()
        dataStore.edit { prefs ->
            while (it.hasNext()) {
                val key = it.next()
                when (key) {
                    "theme_mode" -> prefs[KEY_THEME] = obj.optString(key, ThemeMode.LIGHT.name)
                    "theme_skin" -> prefs[KEY_THEME_SKIN] = obj.optString(key, ThemeSkin.BLUE.name)
                    "animation_enabled" -> prefs[KEY_ANIMATION] = obj.optBoolean(key, true)
                    "quiet_enabled" -> prefs[KEY_QUIET_ENABLED] = obj.optBoolean(key, true)
                    "quiet_start_min" -> prefs[KEY_QUIET_START] = numToInt(obj, key, SettingsRepository.DEFAULT_QUIET_START)
                    "quiet_end_min" -> prefs[KEY_QUIET_END] = numToInt(obj, key, SettingsRepository.DEFAULT_QUIET_END)
                    "first_run_done" -> prefs[KEY_FIRST_RUN] = obj.optBoolean(key, false)
                    "ai_mode" -> prefs[KEY_AI_MODE] = obj.optString(key, "LOCAL_NLU")
                    "assistant_mode" -> prefs[KEY_ASSISTANT_MODE] = obj.optString(key, "MOCK")
                    "assistant_endpoint" -> prefs[KEY_ASSISTANT_ENDPOINT] = obj.optString(key, "wss://api.tenclass.net/xiaozhi/v1/")
                    "assistant_prompt" -> prefs[KEY_ASSISTANT_PROMPT] = obj.optString(key, context.getString(R.string.assistant_prompt_default))
                    "llm_provider" -> prefs[KEY_LLM_PROVIDER] = obj.optString(key, SettingsRepository.DEFAULT_LLM_PROVIDER)
                    "llm_base_url" -> prefs[KEY_LLM_BASE_URL] = obj.optString(key, SettingsRepository.DEFAULT_LLM_BASE_URL)
                    "llm_model" -> prefs[KEY_LLM_MODEL] = obj.optString(key, SettingsRepository.DEFAULT_LLM_MODEL)
                    "asr_provider" -> prefs[KEY_ASR_PROVIDER] = obj.optString(key, SettingsRepository.DEFAULT_ASR_PROVIDER)
                    "asr_base_url" -> prefs[KEY_ASR_BASE_URL] = obj.optString(key, SettingsRepository.DEFAULT_ASR_BASE_URL)
                    "asr_model" -> prefs[KEY_ASR_MODEL] = obj.optString(key, SettingsRepository.DEFAULT_ASR_MODEL)
                    "wake_word_enabled" -> prefs[KEY_WAKE_WORD_ENABLED] = obj.optBoolean(key, false)
                    "wake_word" -> prefs[KEY_WAKE_WORD] = obj.optString(key, context.getString(R.string.wake_word_default))
                    "trust_mode" -> prefs[KEY_TRUST_MODE] = obj.optBoolean(key, false)
                    "auto_backup_enabled" -> prefs[KEY_AUTO_BACKUP] = obj.optBoolean(key, false)
                    "last_auto_backup_at" -> prefs[KEY_LAST_BACKUP_AT] = numToLong(obj, key, 0L)
                    "last_backup_health" -> prefs[KEY_LAST_BACKUP_HEALTH] = obj.optString(key, BackupHealth.NONE.name)
                    "snooze_default_min" -> prefs[KEY_SNOOZE_MIN] = numToInt(obj, key, SettingsRepository.DEFAULT_SNOOZE_MIN)
                    "sound_enabled" -> prefs[KEY_SOUND_ENABLED] = obj.optBoolean(key, true)
                    "clear_confirm_enabled" -> prefs[KEY_CLEAR_CONFIRM] = obj.optBoolean(key, true)
                    "offline_command_enabled" -> prefs[KEY_OFFLINE_CMD] = obj.optBoolean(key, true)
                    "asr_language" -> prefs[KEY_ASR_LANGUAGE] = obj.optString(key, SettingsRepository.DEFAULT_ASR_LANGUAGE)
                    "voice_history_enabled" -> prefs[KEY_VOICE_HISTORY] = obj.optBoolean(key, false)
                    "debug_log_enabled" -> prefs[KEY_DEBUG_LOG] = obj.optBoolean(key, false)
                    "water_reminder_enabled" -> prefs[KEY_WATER_ENABLED] = obj.optBoolean(key, false)
                    "water_reminder_interval_min" -> prefs[KEY_WATER_INTERVAL] = numToInt(obj, key, SettingsRepository.DEFAULT_WATER_INTERVAL_MIN).coerceAtLeast(5)
                    "water_intake_ml" -> prefs[KEY_WATER_INTAKE_ML] = numToInt(obj, key, 0).coerceAtLeast(0)
                    "water_intake_date" -> prefs[KEY_WATER_INTAKE_DATE] = obj.optString(key, "")
                    "water_goal_ml" -> prefs[KEY_WATER_GOAL_ML] = numToInt(obj, key, SettingsRepository.DEFAULT_WATER_GOAL_ML).coerceAtLeast(200)
                    "rest_reminder_enabled" -> prefs[KEY_REST_ENABLED] = obj.optBoolean(key, false)
                    "rest_reminder_interval_min" -> prefs[KEY_REST_INTERVAL] = numToInt(obj, key, SettingsRepository.DEFAULT_REST_INTERVAL_MIN).coerceAtLeast(5)
                    "eyecare_reminder_enabled" -> prefs[KEY_EYECARE_ENABLED] = obj.optBoolean(key, false)
                    "eyecare_reminder_interval_min" -> prefs[KEY_EYECARE_INTERVAL] = numToInt(obj, key, SettingsRepository.DEFAULT_EYECARE_INTERVAL_MIN).coerceAtLeast(5)
                    "nap_last_duration_min" -> prefs[KEY_NAP_DURATION] = numToInt(obj, key, SettingsRepository.DEFAULT_NAP_DURATION_MIN).coerceAtLeast(5)
                    "nap_noise_enabled" -> prefs[KEY_NAP_NOISE_ENABLED] = obj.optBoolean(key, SettingsRepository.DEFAULT_NAP_NOISE_ENABLED)
                    "nap_noise_scene" -> prefs[KEY_NAP_NOISE_SCENE] = obj.optString(key, SettingsRepository.DEFAULT_NAP_NOISE_SCENE)
                    "nap_fade_min" -> prefs[KEY_NAP_FADE_MIN] = numToInt(obj, key, SettingsRepository.DEFAULT_NAP_FADE_MIN).coerceAtLeast(0)
                    "hearing_protection_enabled" -> prefs[KEY_HEARING_ENABLED] = obj.optBoolean(key, false)
                    "hearing_volume_threshold" -> prefs[KEY_HEARING_VOLUME] = numToInt(obj, key, SettingsRepository.DEFAULT_HEARING_VOLUME_THRESHOLD).coerceIn(0, 100)
                    "hearing_max_wear_min" -> prefs[KEY_HEARING_WEAR] = numToInt(obj, key, SettingsRepository.DEFAULT_HEARING_WEAR_MIN).coerceAtLeast(5)
                    "lottery_options" -> prefs[KEY_LOTTERY_OPTIONS] = obj.optString(key, "")
                    "mood_log" -> prefs[KEY_MOOD_LOG] = obj.optString(key, "")
                    "countdown_events" -> prefs[KEY_COUNTDOWN_EVENTS] = obj.optString(key, "")
                    "voice_noise_reduction" -> prefs[KEY_VOICE_NOISE] = obj.optBoolean(key, false)
                    "clipboard_auto_clear" -> prefs[KEY_CLIP_AUTO_CLEAR] = obj.optBoolean(key, false)
                    "clipboard_clear_delay_sec" -> prefs[KEY_CLIP_CLEAR_DELAY] = numToInt(obj, key, SettingsRepository.DEFAULT_CLIPBOARD_CLEAR_DELAY_SEC).coerceIn(5, 120)
                    "arrival_stations" -> prefs[KEY_ARRIVAL_STATIONS] = obj.optString(key, "")
                    "arrival_enabled" -> prefs[KEY_ARRIVAL_ENABLED] = obj.optBoolean(key, false)
                    "favorite_tool_routes" -> {
                        val arr = obj.optJSONArray(key)
                        if (arr != null) {
                            val set = LinkedHashSet<String>()
                            for (i in 0 until arr.length()) arr.optString(i, null)?.takeIf { it.isNotBlank() }?.let { set.add(it) }
                            prefs[KEY_FAVORITE_TOOLS] = set
                        }
                    }
                    "xz_device_id" -> prefs[KEY_XZ_DEVICE_ID] = obj.optString(key, "")
                    "xz_client_id" -> prefs[KEY_XZ_CLIENT_ID] = obj.optString(key, "")
                    "xz_serial_number" -> prefs[KEY_XZ_SERIAL_NUMBER] = obj.optString(key, "")
                    else -> { /* 未知键：跳过，避免写入无法识别的偏好 */ }
                }
            }
        }
    }

    /** JSON 数字统一按 [Number] 取 int（容错 Integer/Long/Double）。 */
    private fun numToInt(obj: JSONObject, key: String, fallback: Int): Int =
        (obj.opt(key) as? Number)?.toInt() ?: fallback

    /** JSON 数字统一按 [Number] 取 long。 */
    private fun numToLong(obj: JSONObject, key: String, fallback: Long): Long =
        (obj.opt(key) as? Number)?.toLong() ?: fallback

    // ── 小智设备模拟（V2.8X++）：Device-Id 必须由用户在设置页显式输入真实设备 MAC，
    // 不再自动生成虚拟 MAC（虚拟 MAC 在 xiaozhi.me 官方云无法完成绑定/握手）。
    // 未设置时返回空字符串，由 UI 强制用户先输入真实 MAC。
    override val xzDeviceId: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_XZ_DEVICE_ID] ?: ""
    }
    // clientId / serialNumber 首次缺失时惰性生成并持久化。
    // 必须串行 + 二次检查：SettingsViewModel 与 WebSocketXiaozhiTransport 都经 .first() 消费，
    // 并发触发时若各自生成随机 UUID 会互相覆盖，导致握手用的设备身份在同一次启动内漂移。
    // 写失败亦不可静默——一旦吞掉，下次冷启动会再生成新 ID，服务端设备绑定与会话连续性直接失效。
    private val identityMutex = Mutex()

    private suspend fun ensureIdentity(key: Preferences.Key<String>): String = identityMutex.withLock {
        dataStore.data.first()[key]?.takeIf { it.isNotEmpty() }?.let { return@withLock it }
        val generated = java.util.UUID.randomUUID().toString()
        runCatching { dataStore.edit { it[key] = generated } }
            .onFailure { AppLogger.e(TAG_SETTINGS, "持久化小智设备身份失败：${key.name}", it) }
        generated
    }

    override val xzClientId: Flow<String> = dataStore.data
        .map { it[KEY_XZ_CLIENT_ID].orEmpty() }
        .map { it.ifEmpty { ensureIdentity(KEY_XZ_CLIENT_ID) } }

    override val xzSerialNumber: Flow<String> = dataStore.data
        .map { it[KEY_XZ_SERIAL_NUMBER].orEmpty() }
        .map { it.ifEmpty { ensureIdentity(KEY_XZ_SERIAL_NUMBER) } }

    // 注意：dataStore.edit 返回 Preferences，接口声明返回 Unit → 必须用块体而非表达式体。
    override suspend fun setXzDeviceId(deviceId: String) { dataStore.edit { it[KEY_XZ_DEVICE_ID] = deviceId } }
    override suspend fun setXzClientId(clientId: String) { dataStore.edit { it[KEY_XZ_CLIENT_ID] = clientId } }
    override suspend fun setXzSerialNumber(sn: String) { dataStore.edit { it[KEY_XZ_SERIAL_NUMBER] = sn } }

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
        private const val TAG_SETTINGS = "SettingsRepo"
        private val KEY_THEME = stringPreferencesKey("theme_mode")
        private val KEY_THEME_SKIN = stringPreferencesKey("theme_skin")
        private val KEY_ANIMATION = booleanPreferencesKey("animation_enabled")
        private val KEY_QUIET_ENABLED = booleanPreferencesKey("quiet_enabled")
        private val KEY_QUIET_START = intPreferencesKey("quiet_start_min")
        private val KEY_QUIET_END = intPreferencesKey("quiet_end_min")
        private val KEY_AI_MODE = stringPreferencesKey("ai_mode")
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
        private val KEY_LAST_BACKUP_HEALTH = stringPreferencesKey("last_backup_health")
        private val KEY_SNOOZE_MIN = intPreferencesKey("snooze_default_min")
        private val KEY_SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        private val KEY_CLEAR_CONFIRM = booleanPreferencesKey("clear_confirm_enabled")
        private val KEY_OFFLINE_CMD = booleanPreferencesKey("offline_command_enabled")
        private val KEY_ASR_LANGUAGE = stringPreferencesKey("asr_language")
        private val KEY_VOICE_HISTORY = booleanPreferencesKey("voice_history_enabled")
        private val KEY_DEBUG_LOG = booleanPreferencesKey("debug_log_enabled")
        private val KEY_WATER_ENABLED = booleanPreferencesKey("water_reminder_enabled")
        private val KEY_WATER_INTERVAL = intPreferencesKey("water_reminder_interval_min")
        private val KEY_WATER_INTAKE_ML = intPreferencesKey("water_intake_ml")
        private val KEY_WATER_INTAKE_DATE = stringPreferencesKey("water_intake_date")
        private val KEY_WATER_GOAL_ML = intPreferencesKey("water_goal_ml")
        private val KEY_REST_ENABLED = booleanPreferencesKey("rest_reminder_enabled")
        private val KEY_REST_INTERVAL = intPreferencesKey("rest_reminder_interval_min")
        private val KEY_EYECARE_ENABLED = booleanPreferencesKey("eyecare_reminder_enabled")
        private val KEY_EYECARE_INTERVAL = intPreferencesKey("eyecare_reminder_interval_min")
        private val KEY_NAP_DURATION = intPreferencesKey("nap_last_duration_min")
        private val KEY_NAP_NOISE_ENABLED = booleanPreferencesKey("nap_noise_enabled")
        private val KEY_NAP_NOISE_SCENE = stringPreferencesKey("nap_noise_scene")
        private val KEY_NAP_FADE_MIN = intPreferencesKey("nap_fade_min")
        private val KEY_HEARING_ENABLED = booleanPreferencesKey("hearing_protection_enabled")
        private val KEY_HEARING_VOLUME = intPreferencesKey("hearing_volume_threshold")
        private val KEY_HEARING_WEAR = intPreferencesKey("hearing_max_wear_min")
        private val KEY_LOTTERY_OPTIONS = stringPreferencesKey("lottery_options")
        private val KEY_MOOD_LOG = stringPreferencesKey("mood_log")
        private val KEY_COUNTDOWN_EVENTS = stringPreferencesKey("countdown_events")
        private val KEY_VOICE_NOISE = booleanPreferencesKey("voice_noise_reduction")
        private val KEY_CLIP_AUTO_CLEAR = booleanPreferencesKey("clipboard_auto_clear")
        private val KEY_CLIP_CLEAR_DELAY = intPreferencesKey("clipboard_clear_delay_sec")
        private val KEY_ARRIVAL_STATIONS = stringPreferencesKey("arrival_stations")
        private val KEY_ARRIVAL_ENABLED = booleanPreferencesKey("arrival_enabled")
        private val KEY_FAVORITE_TOOLS = stringSetPreferencesKey("favorite_tool_routes")
        private val KEY_XZ_DEVICE_ID = stringPreferencesKey("xz_device_id")
        private val KEY_XZ_CLIENT_ID = stringPreferencesKey("xz_client_id")
        private val KEY_XZ_SERIAL_NUMBER = stringPreferencesKey("xz_serial_number")
    }
}
