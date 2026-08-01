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
                        // V2.5：重新同步自动备份闹钟（开关开启才排程）。
                        AutoBackupScheduler.sync(ctx)
                        ReminderScheduler.rescheduleAll(ctx)
                        HabitReminderScheduler.rescheduleAll(ctx)
                        // V2.13：重启后位置提醒改为前台轮询服务，需重新评估并启停。
                        GeofenceScheduler.sync(ctx)
                    } finally {
                        pending.finish()
                        scope.cancel() // 单次广播完成即回收作用域（L1）
                    }
                }
            }
        }
    }
}
