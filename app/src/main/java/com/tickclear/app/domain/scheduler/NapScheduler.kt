package com.tickclear.app.domain.scheduler

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Build.VERSION_CODES
import com.tickclear.app.domain.log.AppLogger

/**
 * 工具箱「午休小憩」调度（V2.9++）：一次性精确闹钟。
 * 用户选定时长后调用 [schedule] 在 now+duration 触发 [NapReceiver] 弹通知唤醒；
 * 非持久化（午休是临时行为），重启后不自动续排。
 */
object NapScheduler {
    private const val TAG = "NapScheduler"
    private const val ACTION_NAP = "com.tickclear.app.action.NAP"
    private const val REQ_NAP = 9301
    const val EXTRA_DURATION = "nap_duration_min"

    /** 排程一次「X 分钟后唤醒」的一次性闹钟。 */
    fun schedule(context: Context, durationMin: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val trigger = System.currentTimeMillis() + durationMin * 60_000L
        setExact(am, context, trigger, pendingIntent(context, durationMin))
    }

    /** 取消尚未触发的午休闹钟（如用户主动取消）。 */
    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        runCatching { am.cancel(pendingIntent(context, 0)) }
    }

    private fun pendingIntent(context: Context, durationMin: Int): PendingIntent {
        val intent = Intent(context, NapReceiver::class.java).apply {
            action = ACTION_NAP
            putExtra(EXTRA_DURATION, durationMin)
        }
        return PendingIntent.getBroadcast(
            context,
            REQ_NAP,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    @SuppressLint("MissingPermission")
    private fun setExact(am: AlarmManager, context: Context, trigger: Long, pi: PendingIntent) {
        if (trigger <= System.currentTimeMillis()) return
        val canExact = if (Build.VERSION.SDK_INT < VERSION_CODES.S) {
            true
        } else {
            runCatching { am.canScheduleExactAlarms() }.getOrDefault(false)
        }
        try {
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            }
        } catch (e: SecurityException) {
            AppLogger.e(TAG, "精确闹钟被拒（${e.message}），退化非精确")
            runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi) }
                .onFailure { AppLogger.e(TAG, "非精确也失败（${it.message}）") }
        }
    }
}
