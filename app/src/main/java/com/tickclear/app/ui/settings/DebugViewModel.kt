package com.tickclear.app.ui.settings

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tickclear.app.BuildConfig
import com.tickclear.app.data.repositories.CheckInRepository
import com.tickclear.app.data.repositories.CompletionRepository
import com.tickclear.app.data.repositories.GroupRepository
import com.tickclear.app.data.repositories.MedalRepository
import com.tickclear.app.data.repositories.SettingsRepository
import com.tickclear.app.data.repositories.TaskRepository
import com.tickclear.app.domain.assistant.OpusCodec
import com.tickclear.app.domain.scheduler.ReminderReceiver
import com.tickclear.app.domain.scheduler.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 各维度诊断信息（供 DebugScreen 展示）。 */
data class DebugInfo(
    val appVersion: String = "",
    val assistantMode: String = "",
    val assistantEndpoint: String = "",
    val aiMode: String = "",
    val voiceSupported: Boolean = false,
    val canScheduleExact: Boolean = true,
    val quietHoursEnabled: Boolean = false,
    val notificationsEnabled: Boolean = false,
    val channelCount: Int = 0,
    val taskCount: Int = 0,
    val groupCount: Int = 0,
    val completionCount: Int = 0,
    val checkInCount: Int = 0,
    val medalCount: Int = 0,
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

    init { load() }

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
            quietHoursEnabled = settingsRepository.quietHoursEnabled.first(),
            notificationsEnabled = nm.areNotificationsEnabled(),
            channelCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.notificationChannels.size
            } else {
                -1
            },
            taskCount = taskRepository.observeAll().first().size,
            groupCount = groupRepository.observeActive().first().size,
            completionCount = completionRepository.observeAll().first().size,
            checkInCount = checkInRepository.getAll().size,
            medalCount = medalRepository.all().size,
        )
    }

    /** 发送测试通知（核对渠道/优先级）。 */
    fun testNotification() {
        ReminderReceiver().fireTestNotification(appContext)
    }

    /** 重新排程今日所有提醒。 */
    fun reschedule() = viewModelScope.launch {
        ReminderScheduler.rescheduleAll(appContext)
    }
}
