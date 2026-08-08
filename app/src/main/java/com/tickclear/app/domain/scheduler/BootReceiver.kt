package com.tickclear.app.domain.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tickclear.app.domain.backup.AutoBackupScheduler
import com.tickclear.app.domain.log.AppLogger
import com.tickclear.app.domain.scheduler.HabitReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 系统级重排入口。
 *
 * V2.8X 修复：此前只监听 [Intent.ACTION_BOOT_COMPLETED]，导致三类常见场景闹钟静默丢失且永不自愈：
 * - **应用升级覆盖安装**（MY_PACKAGE_REPLACED）：系统会清空该应用全部 AlarmManager 闹钟；
 * - **时区变更**（TIMEZONE_CHANGED）：跨时区出行后触发时刻整体偏移；
 * - **手动改系统时间**（TIME_SET）：RTC 闹钟基准漂移。
 * 三者都必须重新全量排程，否则用户要等到下次冷启动 App 才补得回来。
 *
 * 同时监听 [Intent.ACTION_LOCKED_BOOT_COMPLETED]（直接启动模式）纯属兜底：DB 走加密存储、
 * 用户解锁前不可读，此时只重建通知渠道，真正的重排仍等 BOOT_COMPLETED。
 */
class BootReceiver : BroadcastReceiver() {
    companion object {
        private val RESCHEDULE_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
        )
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action in RESCHEDULE_ACTIONS) {
            context?.let { ctx ->
                // 重启/升级/改时区后 AlarmManager 闹钟清空或漂移，需重建通知渠道并重新排程提醒与回收站清理。
                AppLogger.w("BootReceiver", "收到 ${intent?.action}，重建渠道并全量重排闹钟")
                NotificationHelper.createChannels(ctx)
                RecycleBinScheduler.schedule(ctx)
                // goAsync：rescheduleAll 涉及 DB 全量查询，可能超过广播 10s 限制，需保持进程存活。
                val pending = goAsync()
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                scope.launch {
                    try {
                        // 四个子步骤各自独立兜底：
                        // ① 单步失败不得拖垮后续（原先 AutoBackupScheduler 抛错会让提醒/习惯/位置全都不排）；
                        // ② 更关键的是——这里只有 try/finally 没有 catch，任一步抛异常都会变成协程未捕获异常
                        //    直接闪退（曾因 setAlarmClock 缺精确闹钟权限抛 SecurityException 崩在此处）。
                        runCatching { AutoBackupScheduler.sync(ctx) }
                            .onFailure { AppLogger.e("BootReceiver", "自动备份重排失败：${it.message}") }
                        runCatching { ReminderScheduler.rescheduleAll(ctx) }
                            .onFailure { AppLogger.e("BootReceiver", "任务提醒重排失败：${it.message}") }
                        runCatching { HabitReminderScheduler.rescheduleAll(ctx) }
                            .onFailure { AppLogger.e("BootReceiver", "习惯提醒重排失败：${it.message}") }
                        // V2.9：工具箱间隔提醒（喝水 / 休息）同样依赖 AlarmManager，重启/升级/改时区后须重排。
                        runCatching { IntervalReminderScheduler.rescheduleAll(ctx) }
                            .onFailure { AppLogger.e("BootReceiver", "间隔提醒重排失败：${it.message}") }
                        // V2.9++：到期提醒同样依赖 AlarmManager，重启/升级/改时区后须重排。
                        runCatching { ExpiryScheduler.rescheduleAll(ctx) }
                            .onFailure { AppLogger.e("BootReceiver", "到期提醒重排失败：${it.message}") }
                        // V2.9++：重要日子倒计时同样依赖 AlarmManager，重启/升级/改时区后须重排。
                        runCatching { CountdownScheduler.rescheduleAll(ctx) }
                            .onFailure { AppLogger.e("BootReceiver", "倒计时重排失败：${it.message}") }
                        // V2.13：重启后位置提醒改为前台轮询服务，需重新评估并启停。
                        runCatching { GeofenceScheduler.sync(ctx) }
                            .onFailure { AppLogger.e("BootReceiver", "位置提醒同步失败：${it.message}") }
                    } finally {
                        pending.finish()
                        scope.cancel() // 单次广播完成即回收作用域（L1）
                    }
                }
            }
        }
    }
}
