package com.tickclear.app.domain.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

/**
 * 每日回收站清理调度。
 *
 * 原方案使用 WorkManager，但当前构建环境的 Maven 镜像缺失 androidx.work 分组，
 * 故改用 AlarmManager 每日（约）触发 [RecycleBinPurgeReceiver] 执行物理清理。
 * 采用非精确重复闹钟（INTERVAL_DAY）以兼顾省电与每日触发。
 */
object RecycleBinScheduler {
    const val ACTION_PURGE = "com.tickclear.app.action.RECYCLE_BIN_PURGE"

    /** 调度每日清理（首次约为次日 03:00 附近）。幂等：重复调用会更新既有闹钟。 */
    fun schedule(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            nextTriggerMillis(),
            AlarmManager.INTERVAL_DAY,
            pendingIntent(context),
        )
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, RecycleBinPurgeReceiver::class.java).apply {
            action = ACTION_PURGE
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    private fun nextTriggerMillis(): Long {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 3)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now) add(Calendar.DAY_OF_MONTH, 1)
        }
        return cal.timeInMillis
    }
}
