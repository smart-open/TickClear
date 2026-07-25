package com.tickclear.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tickclear.app.R
import com.tickclear.app.data.SecureStore
import com.tickclear.app.domain.repository.SettingsRepository
import com.tickclear.app.domain.assistant.AsrProviderCatalog
import com.tickclear.app.domain.assistant.AsrProviderResolver
import com.tickclear.app.domain.backup.AutoBackupRunner
import com.tickclear.app.domain.backup.AutoBackupScheduler
import com.tickclear.app.domain.backup.BackupHealth
import com.tickclear.app.domain.backup.BackupManager
import com.tickclear.app.domain.ics.IcsManager
import com.tickclear.app.domain.model.AppException
import com.tickclear.app.domain.model.ErrorCode
import com.tickclear.app.domain.repository.TaskRepository
import com.tickclear.app.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SettingsEvent {
    data object NavigateToRecycleBin : SettingsEvent()
    data object NavigateToAbout : SettingsEvent()
}

/** 备份/恢复操作的一次性提示（成功/失败均带用户可读文案）。 */
data class BackupToast(val message: String)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val backupManager: BackupManager,
    private val taskRepository: TaskRepository,
    private val asrResolver: AsrProviderResolver,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    val events = MutableSharedFlow<SettingsEvent>(extraBufferCapacity = 1)
    val backupToasts = MutableSharedFlow<BackupToast>(extraBufferCapacity = 1)

    /** 导出备份到用户选择的 uri（SAF）。 */
    fun exportTo(uri: Uri) = viewModelScope.launch {
        try {
            val json = backupManager.exportToJson()
            appContext.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
            } ?: throw AppException(ErrorCode.EXPORT_WRITE_FAILED)
            backupToasts.tryEmit(BackupToast(appContext.getString(R.string.backup_export_ok)))
        } catch (e: Exception) {
            backupToasts.tryEmit(BackupToast(AppException.from(e, ErrorCode.EXPORT_WRITE_FAILED).userMessage(appContext)))
        }
    }

    /** 从用户选择的 uri（SAF）导入备份。 */
    fun importFrom(uri: Uri) = viewModelScope.launch {
        try {
            val json = appContext.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            } ?: throw AppException(ErrorCode.IMPORT_READ_FAILED)
            val r = backupManager.importFromJson(json)
            backupToasts.tryEmit(
                BackupToast(appContext.getString(R.string.backup_import_ok, r.tasks, r.groups)),
            )
        } catch (e: Exception) {
            backupToasts.tryEmit(BackupToast(AppException.from(e, ErrorCode.IMPORT_PARSE_FAILED).userMessage(appContext)))
        }
    }

    /** 导出为 ICS 日历文件（V2.7）。 */
    fun exportIcsTo(uri: Uri) = viewModelScope.launch {
        try {
            val tasks = taskRepository.observeAll().first()
            val ics = IcsManager.exportTasksToIcs(tasks)
            appContext.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(ics.toByteArray(Charsets.UTF_8))
            } ?: throw AppException(ErrorCode.EXPORT_WRITE_FAILED)
            backupToasts.tryEmit(BackupToast(appContext.getString(R.string.ics_export_ok, tasks.size)))
        } catch (e: Exception) {
            backupToasts.tryEmit(BackupToast(AppException.from(e, ErrorCode.EXPORT_WRITE_FAILED).userMessage(appContext)))
        }
    }

    /** 从 ICS 日历文件导入（V2.7，按主键合并）。 */
    fun importIcsFrom(uri: Uri) = viewModelScope.launch {
        try {
            val ics = appContext.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            } ?: throw AppException(ErrorCode.IMPORT_READ_FAILED)
            val tasks = IcsManager.parseIcsToTasks(ics)
            if (tasks.isEmpty()) throw AppException(ErrorCode.IMPORT_EMPTY)
            tasks.forEach { taskRepository.upsert(it) }
            backupToasts.tryEmit(BackupToast(appContext.getString(R.string.ics_import_ok, tasks.size)))
        } catch (e: Exception) {
            backupToasts.tryEmit(BackupToast(AppException.from(e, ErrorCode.IMPORT_PARSE_FAILED).userMessage(appContext)))
        }
    }

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.LIGHT,
    )
    val animationEnabled: StateFlow<Boolean> = settingsRepository.animationEnabled.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), true,
    )
    val quietHoursEnabled: StateFlow<Boolean> = settingsRepository.quietHoursEnabled.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false,
    )
    val aiMode: StateFlow<String> = settingsRepository.aiMode.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), "LOCAL_NLU",
    )
    val assistantMode: StateFlow<String> = settingsRepository.assistantMode.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), "MOCK",
    )
    val assistantEndpoint: StateFlow<String> = settingsRepository.assistantEndpoint.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), "wss://api.xiaozhi.me/ws",
    )
    val assistantPrompt: StateFlow<String> = settingsRepository.assistantPrompt.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), "",
    )
    val llmProvider: StateFlow<String> = settingsRepository.llmProvider.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), "xiaozhi",
    )
    val llmBaseUrl: StateFlow<String> = settingsRepository.llmBaseUrl.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), "https://api.openai.com/v1",
    )
    val llmModel: StateFlow<String> = settingsRepository.llmModel.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), "gpt-4o-mini",
    )
    val asrProvider: StateFlow<String> = settingsRepository.asrProvider.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), "xiaozhi",
    )
    val asrBaseUrl: StateFlow<String> = settingsRepository.asrBaseUrl.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), "https://api.openai.com/v1",
    )
    val asrModel: StateFlow<String> = settingsRepository.asrModel.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), "whisper-1",
    )
    val wakeWordEnabled: StateFlow<Boolean> = settingsRepository.wakeWordEnabled.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false,
    )
    val wakeWord: StateFlow<String> = settingsRepository.wakeWord.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), "小清",
    )
    val trustMode: StateFlow<Boolean> = settingsRepository.trustMode.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false,
    )

    // ── 自动备份（V2.5/V2.6）──
    val autoBackupEnabled: StateFlow<Boolean> = settingsRepository.autoBackupEnabled.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false,
    )
    val lastAutoBackupAt: StateFlow<Long> = settingsRepository.lastAutoBackupAt.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), 0L,
    )
    /** 最近一次自动备份的健康状态（V2.23 自愈校验回显）。 */
    val lastBackupHealth: StateFlow<BackupHealth> = settingsRepository.lastBackupHealth.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), BackupHealth.NONE,
    )

    // ── 稍后提醒默认时长（V2.30）──
    val snoozeDefaultMin: StateFlow<Int> = settingsRepository.snoozeDefaultMin.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), com.tickclear.app.domain.scheduler.ReminderPrefs.DEFAULT_SNOOZE_MIN,
    )

    // ── 提醒音效开关（V2.31）──
    val soundEnabled: StateFlow<Boolean> = settingsRepository.soundEnabled.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), true,
    )

    // ── 清空前确认（V2.40）──
    val clearConfirmEnabled: StateFlow<Boolean> = settingsRepository.clearConfirmEnabled.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), true,
    )

    // ── 离线语音指令（V2.42）──
    val offlineCommandEnabled: StateFlow<Boolean> = settingsRepository.offlineCommandEnabled.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), true,
    )

    // ── 系统 ASR 语言（方言）（V2.43）──
    val asrLanguage: StateFlow<String> = settingsRepository.asrLanguage.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsRepository.DEFAULT_ASR_LANGUAGE,
    )

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    fun setAnimationEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setAnimationEnabled(enabled) }
    fun setQuietHoursEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setQuietHoursEnabled(enabled) }
    fun setAiMode(mode: String) = viewModelScope.launch { settingsRepository.setAiMode(mode) }
    fun setAssistantMode(mode: String) = viewModelScope.launch { settingsRepository.setAssistantMode(mode) }
    fun setAssistantEndpoint(endpoint: String) = viewModelScope.launch { settingsRepository.setAssistantEndpoint(endpoint) }
    fun setAssistantPrompt(prompt: String) = viewModelScope.launch { settingsRepository.setAssistantPrompt(prompt) }
    fun setLlmProvider(provider: String) = viewModelScope.launch { settingsRepository.setLlmProvider(provider) }
    fun setLlmBaseUrl(url: String) = viewModelScope.launch { settingsRepository.setLlmBaseUrl(url) }
    fun setLlmModel(model: String) = viewModelScope.launch { settingsRepository.setLlmModel(model) }
    fun setAsrProvider(provider: String) = viewModelScope.launch { settingsRepository.setAsrProvider(provider) }
    fun setAsrBaseUrl(url: String) = viewModelScope.launch { settingsRepository.setAsrBaseUrl(url) }
    fun setAsrModel(model: String) = viewModelScope.launch { settingsRepository.setAsrModel(model) }
    suspend fun getLlmApiKey(providerId: String = "openai"): String? = settingsRepository.getLlmApiKey(providerId)
    fun setLlmApiKey(providerId: String, key: String) = viewModelScope.launch {
        settingsRepository.setLlmApiKey(providerId, key)
    }
    suspend fun getAsrApiKey(): String? = settingsRepository.getAsrApiKey()
    fun setAsrApiKey(key: String) = viewModelScope.launch { SecureStore.putSecret(appContext, SettingsRepository.PREF_ASR_API_KEY, key) }

    // ── 腾讯云 ASR 凭据 ──
    suspend fun getTencentSecretId(): String? = settingsRepository.getTencentSecretId()
    fun setTencentSecretId(v: String) = viewModelScope.launch { settingsRepository.setTencentSecretId(v) }
    suspend fun getTencentSecretKey(): String? = settingsRepository.getTencentSecretKey()
    fun setTencentSecretKey(v: String) = viewModelScope.launch { settingsRepository.setTencentSecretKey(v) }

    // ── 阿里云 ASR 凭据 ──
    suspend fun getAliyunAccessKeyId(): String? = settingsRepository.getAliyunAccessKeyId()
    fun setAliyunAccessKeyId(v: String) = viewModelScope.launch { settingsRepository.setAliyunAccessKeyId(v) }
    suspend fun getAliyunAccessKeySecret(): String? = settingsRepository.getAliyunAccessKeySecret()
    fun setAliyunAccessKeySecret(v: String) = viewModelScope.launch { settingsRepository.setAliyunAccessKeySecret(v) }
    suspend fun getAliyunAppKey(): String? = settingsRepository.getAliyunAppKey()
    fun setAliyunAppKey(v: String) = viewModelScope.launch { settingsRepository.setAliyunAppKey(v) }

    // ── 唤醒词 ──
    fun setWakeWordEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setWakeWordEnabled(enabled) }
    fun setWakeWord(word: String) = viewModelScope.launch { settingsRepository.setWakeWord(word) }
    fun setTrustMode(enabled: Boolean) = viewModelScope.launch { settingsRepository.setTrustMode(enabled) }

    // ── 自动备份 ──
    fun setAutoBackupEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setAutoBackupEnabled(enabled)
        AutoBackupScheduler.sync(appContext)
    }

    // ── 稍后提醒默认时长（V2.30）──
    fun setSnoozeDefaultMin(min: Int) = viewModelScope.launch {
        settingsRepository.setSnoozeDefaultMin(
            com.tickclear.app.domain.scheduler.ReminderPrefs.normalizeSnoozeMin(min),
        )
    }

    // ── 提醒音效开关（V2.31）──
    fun setSoundEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setSoundEnabled(enabled) }

    // ── 清空前确认（V2.40）──
    fun setClearConfirmEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setClearConfirmEnabled(enabled) }

    // ── 离线语音指令（V2.42）──
    fun setOfflineCommandEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setOfflineCommandEnabled(enabled) }

    // ── 系统 ASR 语言（方言）（V2.43）──
    fun setAsrLanguage(language: String) = viewModelScope.launch { settingsRepository.setAsrLanguage(language) }

    /** 「立即备份」：手动触发一次加密自动备份（写入私有目录，保留最近 N 份）。 */
    fun runAutoBackupNow() = viewModelScope.launch {
        try {
            AutoBackupRunner.run(
                baseDir = appContext.filesDir,
                backupManager = backupManager,
                settingsRepository = settingsRepository,
            )
            backupToasts.tryEmit(BackupToast(appContext.getString(R.string.backup_auto_now_ok)))
        } catch (e: Exception) {
            backupToasts.tryEmit(
                BackupToast(AppException.from(e, ErrorCode.EXPORT_WRITE_FAILED).userMessage(appContext)),
            )
        }
    }

    /**
     * 测试连接前先把当前选择的 ASR 服务商与对应凭据落库（仅写相关字段，避免覆盖其它服务商凭据），
     * 使 [testCurrentAsr] 能基于最新配置解析到正确的 Provider。
     */
    suspend fun persistAsrForTest(
        provider: String,
        baseUrl: String,
        model: String,
        apiKey: String,
        tencentSecretId: String,
        tencentSecretKey: String,
        aliyunAccessKeyId: String,
        aliyunAccessKeySecret: String,
        aliyunAppKey: String,
    ) {
        settingsRepository.setAsrProvider(provider)
        when (provider) {
            AsrProviderCatalog.OPENAI -> {
                settingsRepository.setAsrBaseUrl(baseUrl)
                settingsRepository.setAsrModel(model)
                SecureStore.putSecret(appContext, SettingsRepository.PREF_ASR_API_KEY, apiKey)
            }
            AsrProviderCatalog.TENCENT -> {
                SecureStore.putSecret(appContext, SettingsRepository.PREF_TENCENT_SECRET_ID, tencentSecretId)
                SecureStore.putSecret(appContext, SettingsRepository.PREF_TENCENT_SECRET_KEY, tencentSecretKey)
            }
            AsrProviderCatalog.ALIYUN -> {
                SecureStore.putSecret(appContext, SettingsRepository.PREF_ALIYUN_ACCESS_KEY, aliyunAccessKeyId)
                SecureStore.putSecret(appContext, SettingsRepository.PREF_ALIYUN_ACCESS_SECRET, aliyunAccessKeySecret)
                SecureStore.putSecret(appContext, SettingsRepository.PREF_ALIYUN_APP_KEY, aliyunAppKey)
            }
        }
    }

    /** 基于已保存的 ASR 配置解析 Provider 并执行连通性自检。 */
    suspend fun testCurrentAsr(): Boolean =
        runCatching { asrResolver.resolve()?.test() ?: false }.getOrDefault(false)

    fun navigateToRecycleBin() = events.tryEmit(SettingsEvent.NavigateToRecycleBin)
    fun navigateToAbout() = events.tryEmit(SettingsEvent.NavigateToAbout)
}
