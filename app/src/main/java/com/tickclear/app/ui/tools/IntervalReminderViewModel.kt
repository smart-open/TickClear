package com.tickclear.app.ui.tools

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tickclear.app.domain.repository.SettingsRepository
import com.tickclear.app.domain.scheduler.IntervalReminderScheduler
import com.tickclear.app.domain.scheduler.IntervalType
import com.tickclear.app.domain.scheduler.NotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 工具箱间隔提醒共享 ViewModel（V2.9）。
 * 基类持有 [type] 与设置读写 + 调度；喝水 / 休息两个子类各固化一种类型，供 Hilt 注入。
 */
open class IntervalReminderViewModel(
    @ApplicationContext protected val appContext: Context,
    protected val settings: SettingsRepository,
    val type: IntervalType,
) : ViewModel() {

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _intervalMin = MutableStateFlow(60)
    val intervalMin: StateFlow<Int> = _intervalMin.asStateFlow()

    init {
        viewModelScope.launch {
            val (e, i) = readSettings()
            _enabled.value = e
            _intervalMin.value = i
        }
    }

    private suspend fun readSettings(): Pair<Boolean, Int> = when (type) {
        IntervalType.WATER -> settings.waterEnabled.first() to settings.waterIntervalMin.first()
        IntervalType.REST -> settings.restEnabled.first() to settings.restIntervalMin.first()
        IntervalType.EYECARE -> settings.eyecareEnabled.first() to settings.eyecareIntervalMin.first()
    }

    fun setEnabled(value: Boolean) {
        viewModelScope.launch {
            when (type) {
                IntervalType.WATER -> settings.setWaterEnabled(value)
                IntervalType.REST -> settings.setRestEnabled(value)
                IntervalType.EYECARE -> settings.setEyecareEnabled(value)
            }
            _enabled.value = value
            IntervalReminderScheduler.schedule(appContext, type)
        }
    }

    fun setIntervalMin(min: Int) {
        viewModelScope.launch {
            when (type) {
                IntervalType.WATER -> settings.setWaterIntervalMin(min)
                IntervalType.REST -> settings.setRestIntervalMin(min)
                IntervalType.EYECARE -> settings.setEyecareIntervalMin(min)
            }
            _intervalMin.value = min
            IntervalReminderScheduler.schedule(appContext, type)
        }
    }

    /** 立即发一条测试通知（不续排闹钟）。 */
    fun testNotify() {
        NotificationHelper.showIntervalReminder(appContext, type)
    }
}

@HiltViewModel
class WaterReminderViewModel @Inject constructor(
    @ApplicationContext appContext: Context,
    settings: SettingsRepository,
) : IntervalReminderViewModel(appContext, settings, IntervalType.WATER)

@HiltViewModel
class RestReminderViewModel @Inject constructor(
    @ApplicationContext appContext: Context,
    settings: SettingsRepository,
) : IntervalReminderViewModel(appContext, settings, IntervalType.REST)

@HiltViewModel
class EyeCareReminderViewModel @Inject constructor(
    @ApplicationContext appContext: Context,
    settings: SettingsRepository,
) : IntervalReminderViewModel(appContext, settings, IntervalType.EYECARE)
