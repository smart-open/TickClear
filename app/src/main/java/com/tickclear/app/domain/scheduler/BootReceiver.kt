package com.tickclear.app.domain.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tickclear.app.domain.backup.AutoBackupScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            context?.let { ctx ->
                // 重启后 AlarmManager 闹钟清空，需重建通知渠道并重新排程提醒与回收站清理。
                NotificationHelper.createChannels(ctx)
                RecycleBinScheduler.schedule(ctx)
                // goAsync：rescheduleAll 涉及 DB 全量查询，可能超过广播 10s 限制，需保持进程存活。
                val pending = goAsync()
                scope.launch {
                    try {
                        // V2.5：重新同步自动备份闹钟（开关开启才排程）。
                        AutoBackupScheduler.sync(ctx)
                        ReminderScheduler.rescheduleAll(ctx)
                    } finally {
                        pending.finish()
                        scope.cancel() // 单次广播完成即回收作用域（L1）
                    }
                }
            }
        }
    }
}
