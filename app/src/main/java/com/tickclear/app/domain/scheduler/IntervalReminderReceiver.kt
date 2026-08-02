package com.tickclear.app.domain.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tickclear.app.domain.repository.SettingsRepository

/**
 * 工具箱间隔提醒接收器（V2.9）：由 [IntervalReminderScheduler] 的 AlarmManager 闹钟触发，
 * 弹出通知并自我续排下一次。
 */
class IntervalReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val type = intent?.getStringExtra(IntervalReminderScheduler.EXTRA_TYPE)
            ?.let { runCatching { IntervalType.valueOf(it) }.getOrNull() } ?: return
        val intervalMin = intent.getIntExtra(
            IntervalReminderScheduler.EXTRA_INTERVAL_MIN,
            defaultFor(type),
        )
        runCatching { NotificationHelper.showIntervalReminder(context, type) }
            .onFailure { com.tickclear.app.domain.log.AppLogger.e("IntervalReminderReceiver", "发通知失败：${it.message}") }
        // 续排下一次（即便本次通知因权限被系统吞掉，闹钟链路仍保持）。
        runCatching { IntervalReminderScheduler.rearm(context, type, intervalMin) }
            .onFailure { com.tickclear.app.domain.log.AppLogger.e("IntervalReminderReceiver", "续排失败：${it.message}") }
    }

    private fun defaultFor(type: IntervalType): Int =
        if (type == IntervalType.WATER) {
            SettingsRepository.DEFAULT_WATER_INTERVAL_MIN
        } else {
            SettingsRepository.DEFAULT_REST_INTERVAL_MIN
        }
}
