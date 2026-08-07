package com.tickclear.app.domain.repository

import com.tickclear.app.domain.backup.BackupHealth
import com.tickclear.app.ui.theme.ThemeMode
import com.tickclear.app.ui.theme.ThemeSkin
import kotlinx.coroutines.flow.Flow

/**
 * 全局偏好设置契约（domain 层）。
 * 敏感值（ASR/LLM 密钥、SQLCipher 口令）不存 DataStore，改用 EncryptedSharedPreferences（见 data/SecureStore）。
 * 公开常量（PREF_ 前缀 / DEFAULT_ 前缀）与 [isInQuietWindow] 置于 companion，供全局静态引用。
 */
interface SettingsRepository {
    val themeMode: Flow<ThemeMode>
    /** 主题皮肤（预设配色，V2.68）：独立于明/暗/动态模式。 */
    val themeSkin: Flow<ThemeSkin>
    val animationEnabled: Flow<Boolean>
    val quietHoursEnabled: Flow<Boolean>
    val quietStartMin: Flow<Int>
    val quietEndMin: Flow<Int>
    val firstRunDone: Flow<Boolean>
    val aiMode: Flow<String>
    val assistantMode: Flow<String>
    val assistantEndpoint: Flow<String>
    val assistantPrompt: Flow<String>
    val llmProvider: Flow<String>
    val llmBaseUrl: Flow<String>
    val llmModel: Flow<String>
    val asrProvider: Flow<String>
    val asrBaseUrl: Flow<String>
    val asrModel: Flow<String>
    val wakeWordEnabled: Flow<Boolean>
    val wakeWord: Flow<String>
    val trustMode: Flow<Boolean>

    /** 自动备份开关（V2.5）：开启后每日经 AlarmManager 周期导出加密 JSON。 */
    val autoBackupEnabled: Flow<Boolean>
    /** 最近一次自动备份时间（epoch millis，0=从未），用于设置页回显。 */
    val lastAutoBackupAt: Flow<Long>

    /** 最近一次自动备份的健康状态（V2.23 自愈校验），用于设置页回显。 */
    val lastBackupHealth: Flow<BackupHealth>

    /** 「稍后提醒」默认时长（分钟），受 [SNOOZE_OPTIONS] 约束（V2.30）。 */
    val snoozeDefaultMin: Flow<Int>

    /** 提醒音效开关：关闭后高优先级提醒不发声/不震动（V2.31）。 */
    val soundEnabled: Flow<Boolean>

    /** 清空前确认开关：关闭后「一键清空」直接执行，不再弹确认框（V2.40）。 */
    val clearConfirmEnabled: Flow<Boolean>

    /** 离线语音指令开关：开启后可用「暂停/启用/删除 + 任务名」热词直接操作（V2.42）。 */
    val offlineCommandEnabled: Flow<Boolean>

    /** 系统 ASR 语言（方言）代码，如 zh-CN / yue-Hant / zh-TW / en-US（V2.43）。 */
    val asrLanguage: Flow<String>

    /** 语音历史保存开关（V2.65）：默认关闭，开启后记录与助手的语音对话。 */
    val voiceHistoryEnabled: Flow<Boolean>

    /**
     * 调试日志开关（V2.8X）：默认关闭。
     * 关闭时 [com.tickclear.app.domain.log.AppLogger] 丢弃 V/D/I 三级（WARN/ERROR 始终保留），
     * 调试页「运行日志」只展示错误线索；开启后记录全量日志便于排障。
     */
    val debugLogEnabled: Flow<Boolean>

    // ── 工具箱：间隔提醒（V2.9）──
    /** 喝水提醒开关。 */
    val waterEnabled: Flow<Boolean>

    /** 喝水提醒间隔（分钟），默认 60。 */
    val waterIntervalMin: Flow<Int>

    /** 久坐 / 眨眼休息提醒开关。 */
    val restEnabled: Flow<Boolean>

    /** 休息提醒间隔（分钟），默认 45。 */
    val restIntervalMin: Flow<Int>

    /** 眼保健提醒开关（V2.9+，由「久坐/眨眼」拆分独立）。 */
    val eyecareEnabled: Flow<Boolean>

    /** 眼保健提醒间隔（分钟），默认 45。 */
    val eyecareIntervalMin: Flow<Int>

    /** 午休小憩上次选择的时长（分钟，V2.9++），用于记忆偏好。 */
    val napLastDurationMin: Flow<Int>

    /** 午休小憩：白噪音助眠开关（默认关）。 */
    val napNoiseEnabled: Flow<Boolean>

    /** 午休小憩：白噪音场景（rain/cafe/stream，默认 rain）。 */
    val napNoiseScene: Flow<String>

    /** 午休小憩：白噪音渐隐时长（分钟，0=不渐隐，默认 0）。 */
    val napFadeMin: Flow<Int>

    // ── 工具箱：听力保护（V2.9++）──
    /** 听力保护总开关（监测耳机音量/佩戴时长）。 */
    val hearingEnabled: Flow<Boolean>

    /** 音量安全阈值（0-100，媒体音量占比超此值提醒）。 */
    val hearingVolumeThreshold: Flow<Int>

    /** 建议最大连续佩戴分钟数，超过则提醒休息。 */
    val hearingMaxWearMin: Flow<Int>

    // ── 工具箱：抽签器（V2.9++）──
    /** 抽签器自定义选项名单（每行一个，以换行符连接；空字符串=未设置）。 */
    val lotteryOptions: Flow<String>

    // ── 工具箱：情绪打卡（V2.9++）──
    /** 情绪打卡记录（每行 "epochDay|心情码|备注"，备注可含 '|' 用 limit=3 解析；空字符串=无记录）。 */
    val moodLog: Flow<String>

    // ── 工具箱：重要日子倒计时（V2.9++）──
    /** 倒计时事件列表（每行 "名称|目标epochMillis"，以换行连接；空字符串=无事件）。 */
    val countdownEvents: Flow<String>

    // ── 工具箱：语音备忘降噪（V2.9++）──
    /** 录音降噪开关：开启后语音备忘使用 VOICE_RECOGNITION 音源（平台级降噪/回声消除）。 */
    val voiceNoiseReduction: Flow<Boolean>

    // ── 工具箱：到站提醒（V2.9++）──
    /** 到站提醒站点列表（每行 "id|name|lat|lng|radius"，换行连接；空字符串=无站点）。 */
    val arrivalStations: Flow<String>

    /** 到站提醒监测开关（开启后启动前台定位轮询服务，靠近站点震动）。 */
    val arrivalEnabled: Flow<Boolean>

    /** 导出全部偏好设置（DataStore）为 JSON 字符串，供「备份导出」工具一键导出。 */
    suspend fun exportSettingsJson(): String

    // ── 工具箱：剪贴板防窃取（V2.9++）──
    /** 剪贴板自动清除开关：开启后复制内容延迟 N 秒后自动清空，规避后台读取。 */
    val clipboardAutoClear: Flow<Boolean>

    /** 剪贴板自动清除延迟（秒）。 */
    val clipboardClearDelaySec: Flow<Int>

    // ── 小智设备模拟（V2.8）：模拟 ESP32 设备接入官方 xiaozhi.me ──
    /** 模拟设备 ID（MAC 地址格式，如 "AA:BB:CC:DD:EE:FF"），用于 OTA 注册与 WS 认证头。 */
    val xzDeviceId: Flow<String>
    /** 模拟客户端 ID（UUID 格式），用于 WS 认证头。 */
    val xzClientId: Flow<String>
    /** 模拟设备序列号（serial_number）。ESP32 固件烧录在 NVS，官方服务端 OTA 注册与「用验证码添加设备」强制要求非空（缺则报"serial_number 是必须的"）。 */
    val xzSerialNumber: Flow<String>

    suspend fun setXzDeviceId(deviceId: String)
    suspend fun setXzClientId(clientId: String)
    suspend fun setXzSerialNumber(sn: String)

    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setThemeSkin(skin: ThemeSkin)
    suspend fun setAnimationEnabled(enabled: Boolean)
    suspend fun setQuietHoursEnabled(enabled: Boolean)
    suspend fun setQuietStartMin(min: Int)
    suspend fun setQuietEndMin(min: Int)
    suspend fun setFirstRunDone(done: Boolean)
    suspend fun setAiMode(mode: String)
    suspend fun setAssistantMode(mode: String)
    suspend fun setAssistantEndpoint(endpoint: String)
    suspend fun setAssistantPrompt(prompt: String)
    suspend fun setLlmProvider(provider: String)
    suspend fun setLlmBaseUrl(url: String)
    suspend fun setLlmModel(model: String)
    suspend fun setAsrProvider(provider: String)
    suspend fun setAsrBaseUrl(url: String)
    suspend fun setAsrModel(model: String)
    suspend fun setWakeWordEnabled(enabled: Boolean)
    suspend fun setWakeWord(word: String)
    suspend fun setTrustMode(enabled: Boolean)
    suspend fun setAutoBackupEnabled(enabled: Boolean)
    suspend fun setLastAutoBackupAt(at: Long)
    suspend fun setLastBackupHealth(health: BackupHealth)
    suspend fun setSnoozeDefaultMin(min: Int)
    suspend fun setSoundEnabled(enabled: Boolean)

    /** 清空前确认开关（V2.40）。 */
    suspend fun setClearConfirmEnabled(enabled: Boolean)

    /** 离线语音指令开关（V2.42）。 */
    suspend fun setOfflineCommandEnabled(enabled: Boolean)

    /** 设置系统 ASR 语言（方言）代码（V2.43）。 */
    suspend fun setAsrLanguage(language: String)

    /** 语音历史保存开关（V2.65）。 */
    suspend fun setVoiceHistoryEnabled(enabled: Boolean)

    /** 调试日志开关（V2.8X）。写入后须同步 [com.tickclear.app.domain.log.AppLogger.setDebugEnabled]。 */
    suspend fun setDebugLogEnabled(enabled: Boolean)

    // ── 工具箱：间隔提醒（V2.9）──
    suspend fun setWaterEnabled(enabled: Boolean)
    suspend fun setWaterIntervalMin(min: Int)
    suspend fun setRestEnabled(enabled: Boolean)
    suspend fun setRestIntervalMin(min: Int)
    suspend fun setEyecareEnabled(enabled: Boolean)
    suspend fun setEyecareIntervalMin(min: Int)

    /** 午休小憩：保存上次选择的时长（分钟，V2.9++）。 */
    suspend fun setNapLastDurationMin(min: Int)

    /** 午休小憩：保存白噪音助眠开关。 */
    suspend fun setNapNoiseEnabled(enabled: Boolean)

    /** 午休小憩：保存白噪音场景。 */
    suspend fun setNapNoiseScene(scene: String)

    /** 午休小憩：保存白噪音渐隐时长（分钟）。 */
    suspend fun setNapFadeMin(min: Int)

    // ── 工具箱：听力保护（V2.9++）──
    suspend fun setHearingEnabled(enabled: Boolean)
    suspend fun setHearingVolumeThreshold(threshold: Int)
    suspend fun setHearingMaxWearMin(min: Int)

    /** 保存抽签器选项名单（换行连接）。 */
    suspend fun setLotteryOptions(options: String)

    /** 保存情绪打卡记录（换行连接，覆盖式写入）。 */
    suspend fun setMoodLog(log: String)

    /** 保存倒计时事件列表（换行连接，覆盖式写入）。 */
    suspend fun setCountdownEvents(events: String)

    // ── 工具箱：语音备忘降噪（V2.9++）──
    /** 设置录音降噪开关。 */
    suspend fun setVoiceNoiseReduction(enabled: Boolean)

    // ── 工具箱：剪贴板防窃取（V2.9++）──
    /** 设置剪贴板自动清除开关。 */
    suspend fun setClipboardAutoClear(enabled: Boolean)

    /** 设置剪贴板自动清除延迟（秒，5-120）。 */
    suspend fun setClipboardClearDelaySec(sec: Int)

    // ── 工具箱：到站提醒（V2.9++）──
    /** 保存到站提醒站点列表（换行连接编码串）。 */
    suspend fun setArrivalStations(text: String)

    /** 设置到站提醒监测开关。 */
    suspend fun setArrivalEnabled(on: Boolean)

    // ── 工具箱：常用工具置顶（V2.9++）──
    /** 置顶工具路由列表（按用户点击顺序保存，列表前半段先展示）。 */
    val favoriteToolRoutes: Flow<List<String>>

    /** 覆盖置顶列表（路由按显示顺序传）。 */
    suspend fun setFavoriteToolRoutes(routes: List<String>)

    /** 真实小智模式的网关令牌（存于加密存储，非 DataStore）。 */
    suspend fun getAssistantToken(): String?

    /** OpenAI 兼容服务商的 API Key（存于加密存储，按服务商区分）。 */
    suspend fun getLlmApiKey(providerId: String = DEFAULT_LLM_PROVIDER): String?

    /** 写入指定服务商的 LLM API Key（存于加密存储，按服务商区分）。 */
    suspend fun setLlmApiKey(providerId: String = DEFAULT_LLM_PROVIDER, key: String)

    /** OpenAI 兼容 ASR 服务商的 API Key（存于加密存储）。 */
    suspend fun getAsrApiKey(): String?

    // ── 腾讯云 ASR 凭据 ──
    suspend fun getTencentSecretId(): String?
    suspend fun setTencentSecretId(v: String)
    suspend fun getTencentSecretKey(): String?
    suspend fun setTencentSecretKey(v: String)

    // ── 阿里云 ASR 凭据 ──
    suspend fun getAliyunAccessKeyId(): String?
    suspend fun setAliyunAccessKeyId(v: String)
    suspend fun getAliyunAccessKeySecret(): String?
    suspend fun setAliyunAccessKeySecret(v: String)
    suspend fun getAliyunAppKey(): String?
    suspend fun setAliyunAppKey(v: String)

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
        const val PREF_XZ_DEVICE_ID = "xz_device_id"
        const val PREF_XZ_CLIENT_ID = "xz_client_id"
        const val PREF_XZ_SERIAL_NUMBER = "xz_serial_number"
        const val DEFAULT_LLM_PROVIDER = "xiaozhi"
        const val DEFAULT_LLM_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_LLM_MODEL = "gpt-4o-mini"
        const val DEFAULT_ASR_PROVIDER = "xiaozhi"
        const val DEFAULT_ASR_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_ASR_MODEL = "whisper-1"
        // 唤醒词默认短语抽离到 strings.xml（wake_word_default），避免源码硬编码中文。
        const val DEFAULT_QUIET_START = 22 * 60 // 22:00
        const val DEFAULT_QUIET_END = 7 * 60    // 07:00

        /** 「稍后提醒」默认时长（分钟），须与 [com.tickclear.app.domain.scheduler.ReminderPrefs.SNOOZE_OPTIONS] 一致。 */
        const val DEFAULT_SNOOZE_MIN = com.tickclear.app.domain.scheduler.ReminderPrefs.DEFAULT_SNOOZE_MIN

        /** 「清空前确认」默认开启（V2.40）。 */
        const val DEFAULT_CLEAR_CONFIRM_ENABLED = true

        /** 离线语音指令默认开启（V2.42）。 */
        const val DEFAULT_OFFLINE_COMMAND_ENABLED = true

        /** 系统 ASR 默认语言：普通话（V2.43）。 */
        const val DEFAULT_ASR_LANGUAGE = "zh-CN"

        /** 喝水提醒默认间隔（分钟，V2.9）。 */
        const val DEFAULT_WATER_INTERVAL_MIN = 60

    /** 休息提醒默认间隔（分钟，V2.9）。 */
    const val DEFAULT_REST_INTERVAL_MIN = 45

    /** 眼保健提醒默认间隔（分钟，V2.9+）。 */
    const val DEFAULT_EYECARE_INTERVAL_MIN = 45

    /** 午休小憩默认时长（分钟，V2.9++）：30 分钟落在浅睡窗口，醒后不易昏沉。 */
    const val DEFAULT_NAP_DURATION_MIN = 30
    const val DEFAULT_NAP_NOISE_ENABLED = false
    const val DEFAULT_NAP_NOISE_SCENE = "rain"
    const val DEFAULT_NAP_FADE_MIN = 0

    /** 听力保护默认音量阈值（媒体音量占比 %）。 */
    const val DEFAULT_HEARING_VOLUME_THRESHOLD = 80

    /** 听力保护默认最大连续佩戴分钟数。 */
    const val DEFAULT_HEARING_WEAR_MIN = 60

    /** 剪贴板自动清除默认延迟（秒）。 */
    const val DEFAULT_CLIPBOARD_CLEAR_DELAY_SEC = 30

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
    }
}
