package com.tickclear.app.domain.scheduler

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Build.VERSION_CODES
import com.tickclear.app.data.local.entities.ExpiryEntity
import com.tickclear.app.domain.log.AppLogger
import com.tickclear.app.domain.repository.ExpiryRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import java.time.ZoneId

/**
 * 到期提醒调度（V2.9++）：每条开启提醒的条目在「到期日 - 提前天数」09:00 触发一次性精确闹钟，
 * 由 [ExpiryReceiver] 弹通知；若为每年重复项，触发后续排下一年。
 * 三级降级与 [ReminderScheduler.setExact] 保持一致。
 */
object ExpiryScheduler {

    private const val TAG = "ExpiryScheduler"
    const val ACTION_EXPIRY = "com.tickclear.app.action.EXPIRY"
    const val EXTRA_EXPIRY_ID = "expiry_id"
    private const val REQ_BASE = 9400
    private const val REMINDER_HOUR = 9

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ExpiryEntryPoint {
        fun expiryRepository(): ExpiryRepository
    }

    private fun ep(context: Context) =
        EntryPointAccessors.fromApplication(context.applicationContext, ExpiryEntryPoint::class.java)

    private fun requestCodeFor(id: Long) = REQ_BASE + (id and 0x7FFF_FFFF).toInt()

    private fun pendingIntent(context: Context, id: Long): PendingIntent {
        val intent = Intent(context, ExpiryReceiver::class.java).apply {
            action = ACTION_EXPIRY
            putExtra(EXTRA_EXPIRY_ID, id)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCodeFor(id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** 计算提醒触发时刻（到期日 - 提前天数 的 09:00，本地时区）。 */
    private fun triggerAt(entity: ExpiryEntity): Long {
        val expireDate = LocalDate.ofEpochDay(entity.expireEpochDay)
        val remindDate = expireDate.minusDays(entity.reminderDaysBefore.toLong())
        return remindDate.atTime(REMINDER_HOUR, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    /** 为单条条目排程；未开启提醒则取消；时间已过则跳过（重复项顺延一年）。 */
    fun schedule(context: Context, entity: ExpiryEntity) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context, entity.id)
        if (!entity.reminderEnabled) {
            runCatching { am.cancel(pi) }
            return
        }
        var trigger = triggerAt(entity)
        if (trigger <= System.currentTimeMillis()) {
            if (!entity.recurring) return // 一次性且已过期：不再提醒
            // 重复项：逐年顺延至未来
            var expire = LocalDate.ofEpochDay(entity.expireEpochDay)
            val now = LocalDate.now()
            while (true) {
                expire = expire.plusYears(1)
                if (expire.isAfter(now)) break
            }
            trigger = expire.minusDays(entity.reminderDaysBefore.toLong())
                .atTime(REMINDER_HOUR, 0)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }
        setExact(am, context, trigger, pi)
    }

    fun cancel(context: Context, id: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        runCatching { am.cancel(pendingIntent(context, id)) }
    }

    /** 开机 / 升级 / 改时区后全量重排（供 [BootReceiver] 调用）。 */
    suspend fun rescheduleAll(context: Context) {
        val repo = ep(context).expiryRepository()
        repo.getEnabled().forEach { entity ->
            runCatching { schedule(context, entity) }
                .onFailure { AppLogger.e(TAG, "rescheduleAll 失败 id=${entity.id}: ${it.message}") }
        }
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
            AppLogger.e(TAG, "setExact 精确闹钟被拒（${e.message}），退化非精确 trigger=$trigger")
            runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi) }
                .onFailure { AppLogger.e(TAG, "setExact 非精确也失败（${it.message}） trigger=$trigger") }
        }
    }
}
