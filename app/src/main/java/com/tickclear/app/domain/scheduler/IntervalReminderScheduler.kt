package com.tickclear.app.domain.scheduler

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Build.VERSION_CODES
import com.tickclear.app.domain.log.AppLogger
import com.tickclear.app.domain.repository.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

/**
 * 工具箱「间隔提醒」调度（V2.9）：喝水 / 久坐休息 / 眼保健。
 * 基于 AlarmManager 精确闹钟 + 自调度（每次触发后由 [IntervalReminderReceiver] 续排下一次），
 * 与 WorkManager 不同，保证到点送达；三级降级与 [ReminderScheduler.setExact] 保持一致。
 */
enum class IntervalType { WATER, REST, EYECARE }

object IntervalReminderScheduler {

    private const val TAG = "IntervalReminderScheduler"
    private const val ACTION_WATER = "com.tickclear.app.action.INTERVAL_WATER"
    private const val ACTION_REST = "com.tickclear.app.action.INTERVAL_REST"
    private const val ACTION_EYECARE = "com.tickclear.app.action.INTERVAL_EYECARE"
    const val EXTRA_TYPE = "interval_type"
    const val EXTRA_INTERVAL_MIN = "interval_min"

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface IntervalEntryPoint {
        fun settingsRepository(): SettingsRepository
    }

    private fun ep(context: Context) =
        EntryPointAccessors.fromApplication(context.applicationContext, IntervalEntryPoint::class.java)

    private fun actionFor(type: IntervalType) = when (type) {
        IntervalType.WATER -> ACTION_WATER
        IntervalType.REST -> ACTION_REST
        IntervalType.EYECARE -> ACTION_EYECARE
    }

    private fun reqCodeFor(type: IntervalType) = when (type) {
        IntervalType.WATER -> 9101
        IntervalType.REST -> 9102
        IntervalType.EYECARE -> 9103
    }

    /** 依据当前设置（开关 + 间隔）排程；关闭则取消。供 UI 开关/间隔变更时调用。 */
    suspend fun schedule(context: Context, type: IntervalType) {
        val settings = ep(context).settingsRepository()
        val (enabled, intervalMin) = when (type) {
            IntervalType.WATER -> settings.waterEnabled.first() to settings.waterIntervalMin.first()
            IntervalType.REST -> settings.restEnabled.first() to settings.restIntervalMin.first()
            IntervalType.EYECARE -> settings.eyecareEnabled.first() to settings.eyecareIntervalMin.first()
        }
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context, type, intervalMin)
        if (!enabled) {
            runCatching { am.cancel(pi) }
            return
        }
        val trigger = System.currentTimeMillis() + intervalMin * 60_000L
        setExact(am, context, trigger, pi)
    }

    fun cancel(context: Context, type: IntervalType) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        runCatching { am.cancel(pendingIntent(context, type, 0)) }
    }

    /** 开机 / 升级 / 改时区后全量重排（供 [BootReceiver] 调用）。 */
    suspend fun rescheduleAll(context: Context) {
        schedule(context, IntervalType.WATER)
        schedule(context, IntervalType.REST)
        schedule(context, IntervalType.EYECARE)
    }

    /** 接收器触发后自我续排（读取 Intent 携带的间隔）。 */
    fun rearm(context: Context, type: IntervalType, intervalMin: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val trigger = System.currentTimeMillis() + intervalMin * 60_000L
        setExact(am, context, trigger, pendingIntent(context, type, intervalMin))
    }

    private fun pendingIntent(context: Context, type: IntervalType, intervalMin: Int): PendingIntent {
        val intent = Intent(context, IntervalReminderReceiver::class.java).apply {
            action = actionFor(type)
            putExtra(EXTRA_TYPE, type.name)
            putExtra(EXTRA_INTERVAL_MIN, intervalMin)
        }
        return PendingIntent.getBroadcast(
            context,
            reqCodeFor(type),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * 三级降级（与 [ReminderScheduler] 的 setExact 保持一致）：
     * 有精确闹钟权限 → setExactAndAllowWhileIdle；否则退化 setAndAllowWhileIdle。
     */
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
            AppLogger.e(TAG, "setExact 精确闹钟被拒（${e.message}），退化非精确 trigger=$trigger")
            runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi) }
                .onFailure { AppLogger.e(TAG, "setExact 非精确也失败（${it.message}） trigger=$trigger") }
        }
    }
}
