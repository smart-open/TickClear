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
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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

    /** 下一次提醒的触发时刻（epoch millis）。仅在 enabled 时有效，供 UI 倒计时环使用。 */
    private val _nextTriggerMs = MutableStateFlow(0L)
    val nextTriggerMs: StateFlow<Long> = _nextTriggerMs.asStateFlow()

    // ── 喝水记录（仅 WATER 类型使用）──
    private val _waterMl = MutableStateFlow(0)
    val waterMl: StateFlow<Int> = _waterMl.asStateFlow()
    private val _waterGoalMl = MutableStateFlow(2000)
    val waterGoalMl: StateFlow<Int> = _waterGoalMl.asStateFlow()

    private fun todayStr(): String =
        LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

    init {
        viewModelScope.launch {
            val (e, i) = readSettings()
            _enabled.value = e
            _intervalMin.value = i
            if (e) _nextTriggerMs.value = System.currentTimeMillis() + i * 60_000L
            if (type == IntervalType.WATER) {
                val today = todayStr()
                if (settings.waterIntakeDate.first() == today) {
                    _waterMl.value = settings.waterIntakeMl.first()
                } else {
                    // 跨天：归零旧记录并写入今日日期
                    settings.setWaterIntakeDate(today)
                    settings.setWaterIntakeMl(0)
                    _waterMl.value = 0
                }
                _waterGoalMl.value = settings.waterGoalMl.first()
            }
        }
    }

    /** 页面打开时锚定倒计时起点为「现在 + 间隔」，使环从满周期开始平滑递减。 */
    fun refreshCountdown() {
        if (_enabled.value) {
            _nextTriggerMs.value = System.currentTimeMillis() + _intervalMin.value * 60_000L
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
            _nextTriggerMs.value = if (value) {
                System.currentTimeMillis() + _intervalMin.value * 60_000L
            } else {
                0L
            }
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
            if (_enabled.value) {
                _nextTriggerMs.value = System.currentTimeMillis() + min * 60_000L
            }
            IntervalReminderScheduler.schedule(appContext, type)
        }
    }

    /** 立即发一条测试通知（不续排闹钟）。 */
    fun testNotify() {
        NotificationHelper.showIntervalReminder(appContext, type)
    }

    /** 记录一次饮水（ml）；跨天自动先归零再累加。仅 WATER 类型有效。 */
    fun addWater(ml: Int) {
        if (type != IntervalType.WATER) return
        viewModelScope.launch {
            val today = todayStr()
            if (settings.waterIntakeDate.first() != today) {
                settings.setWaterIntakeDate(today)
                settings.setWaterIntakeMl(0)
                _waterMl.value = 0
            }
            val next = (_waterMl.value + ml).coerceAtLeast(0)
            _waterMl.value = next
            settings.setWaterIntakeMl(next)
        }
    }

    /** 设定每日饮水目标（毫升）。仅 WATER 类型有效。 */
    fun setWaterGoal(ml: Int) {
        if (type != IntervalType.WATER) return
        viewModelScope.launch {
            _waterGoalMl.value = ml
            settings.setWaterGoalMl(ml)
        }
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
