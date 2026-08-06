package com.tickclear.app.ui.settings

import android.app.AlarmManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tickclear.app.BuildConfig
import com.tickclear.app.domain.log.AppLogger
import com.tickclear.app.domain.log.LogEntry
import com.tickclear.app.domain.repository.CheckInRepository
import com.tickclear.app.domain.repository.CompletionRepository
import com.tickclear.app.domain.repository.GroupRepository
import com.tickclear.app.domain.repository.MedalRepository
import com.tickclear.app.domain.repository.SettingsRepository
import com.tickclear.app.domain.repository.TaskRepository
import com.tickclear.app.domain.assistant.OpusCodec
import com.tickclear.app.domain.scheduler.ReminderReceiver
import com.tickclear.app.domain.scheduler.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import javax.inject.Inject

/** 各维度诊断信息（供 DebugScreen 展示）。 */
data class DebugInfo(
    val appVersion: String = "",
    val assistantMode: String = "",
    val assistantEndpoint: String = "",
    val aiMode: String = "",
    val voiceSupported: Boolean = false,
    val canScheduleExact: Boolean = true,
    val batteryOptimized: Boolean = false,
    val quietHoursEnabled: Boolean = false,
    val notificationsEnabled: Boolean = false,
    val channelCount: Int = 0,
    val taskCount: Int = 0,
    val groupCount: Int = 0,
    val completionCount: Int = 0,
    val checkInCount: Int = 0,
    val medalCount: Int = 0,
    val dndAccessGranted: Boolean = false,
)

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val groupRepository: GroupRepository,
    private val completionRepository: CompletionRepository,
    private val checkInRepository: CheckInRepository,
    private val medalRepository: MedalRepository,
    private val settingsRepository: SettingsRepository,
    private val opusCodec: OpusCodec,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _info = MutableStateFlow(DebugInfo())
    val info: StateFlow<DebugInfo> = _info.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), DebugInfo(),
    )

    /** 运行日志（v2.0 / V2.2）：订阅 AppLogger 内存环形缓冲，供在屏查看与导出。 */
    val logs: StateFlow<List<LogEntry>> = AppLogger.entries

    init {
        AppLogger.i("AppLogger", "调试日志已就绪（v2.0 V2.2）")
        load()
    }

    /** 刷新全部诊断数据。 */
    fun load() = viewModelScope.launch {
        val nm = NotificationManagerCompat.from(appContext)
        val am = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.canScheduleExactAlarms()
        } else {
            true
        }
        _info.value = DebugInfo(
            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            assistantMode = settingsRepository.assistantMode.first(),
            assistantEndpoint = settingsRepository.assistantEndpoint.first(),
            aiMode = settingsRepository.aiMode.first(),
            voiceSupported = opusCodec.isEncoderAvailable(),
            canScheduleExact = canExact,
            batteryOptimized = run {
                val pm = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
                pm.isIgnoringBatteryOptimizations(appContext.packageName).not()
            },
            quietHoursEnabled = settingsRepository.quietHoursEnabled.first(),
            notificationsEnabled = nm.areNotificationsEnabled(),
            channelCount = nm.notificationChannels.size,
            taskCount = taskRepository.observeAll().first().size,
            groupCount = groupRepository.observeActive().first().size,
            completionCount = completionRepository.observeAll().first().size,
            checkInCount = checkInRepository.getAll().size,
            medalCount = medalRepository.all().size,
            dndAccessGranted = (appContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager)
                .isNotificationPolicyAccessGranted,
        )
    }

    /** 发送测试通知（核对渠道/优先级）。 */
    fun testNotification() {
        ReminderReceiver().fireTestNotification(appContext)
    }

    /**
     * 重新排程今日所有提醒；返回实际排程的闹钟数，供 UI 反馈。
     * 排程可能因系统权限（精确闹钟）被拒而抛异常——调试页操作绝不能把 App 打崩，失败按 0 上报。
     */
    fun reschedule() = viewModelScope.launch {
        _rescheduleResult.value = runCatching { ReminderScheduler.rescheduleAll(appContext) }
            .getOrElse {
                AppLogger.e("DebugViewModel", "手动重排失败：${it.message}")
                0
            }
    }

    private val _rescheduleResult = MutableStateFlow<Int?>(null)
    /** 最近一次重排排程的闹钟数（null 表示尚未操作）。UI 消费后应调用 [clearRescheduleResult] 复位。 */
    val rescheduleResult: StateFlow<Int?> = _rescheduleResult

    /** 复位重排结果，允许重复点击再次触发反馈。 */
    fun clearRescheduleResult() { _rescheduleResult.value = null }

    /** 导出运行日志为纯文本文件（SAF 落盘，零新依赖）。 */
    fun exportLogs(uri: Uri) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            runCatching {
                appContext.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(AppLogger.formatPlain().toByteArray(StandardCharsets.UTF_8))
                }
            }
        }
    }

    /** 清空内存日志缓冲。 */
    fun clearLogs() = AppLogger.clear()
}
