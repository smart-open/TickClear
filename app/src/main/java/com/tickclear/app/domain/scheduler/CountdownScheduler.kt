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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 重要日子倒计时提醒调度（V2.9++ 扩展通知）：
 * 每条开启提醒的事件在「到期日 - 提前天数 … 到期日」区间内的每日 [hour]:[minute] 触发一次性精确闹钟，
 * 由 [CountdownReceiver] 弹通知。
 *
 * - daily=true：提前期内每天提醒；daily=false：仅「提前当天 + 到期当天」两次。
 * - advanceDays=0 且 daily=false：仅到期当天一次。
 * 三级降级（精确闹钟被拒时退化非精确）与 [ExpiryScheduler.setExact] 一致。
 */
object CountdownScheduler {

    private const val TAG = "CountdownScheduler"
    const val ACTION_COUNTDOWN = "com.tickclear.app.action.COUNTDOWN"
    const val EXTRA_EVENT_ID = "cd_event_id"
    const val EXTRA_NAME = "cd_name"
    const val EXTRA_DAYS_LEFT = "cd_days_left"
    private const val REQ_BASE = 9700
    /** 提前天数上限（用于删除事件时遍历取消 alarm）。 */
    private const val MAX_DAY_INDEX = 90

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface CountdownEntryPoint {
        fun settingsRepository(): SettingsRepository
    }

    private fun ep(context: Context) =
        EntryPointAccessors.fromApplication(context.applicationContext, CountdownEntryPoint::class.java)

    /**
     * 该事件某一天提醒的 PendingIntent requestCode。
     *
     * 走 [ReminderIds.fnv1a] 全 int 空间取值：原实现用 `id.hashCode()` 再 `% 9000`，
     * 把取值压到 9000 个槽内，不同事件极易撞码；配合 [PendingIntent.FLAG_UPDATE_CURRENT]
     * 后注册者会直接覆盖前者的 PendingIntent，导致前一个事件的闹钟被静默丢弃。
     */
    private fun requestCodeFor(id: String, dayIndex: Int): Int =
        ReminderIds.fnv1a("cd:$id:$dayIndex")

    private fun baseIntent(context: Context, id: String, event: CountdownEvent?, daysLeft: Int): Intent =
        Intent(context, CountdownReceiver::class.java).apply {
            action = ACTION_COUNTDOWN
            putExtra(EXTRA_EVENT_ID, id)
            if (event != null) {
                putExtra(EXTRA_NAME, event.name)
                putExtra(EXTRA_DAYS_LEFT, daysLeft)
            }
        }

    private fun pendingIntent(
        context: Context,
        id: String,
        dayIndex: Int,
        event: CountdownEvent?,
        daysLeft: Int,
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCodeFor(id, dayIndex),
        baseIntent(context, id, event, daysLeft),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** 该事件所有待触发通知（时刻 + 剩余天数），已滤掉过期时刻。 */
    private fun triggers(event: CountdownEvent): List<Pair<Long, Int>> {
        // 必须按本地时区换算：targetEpochMs 是绝对时间戳，直接整除 86400000 得到的是 UTC 日，
        // 东八区用户设在 1/1 00:30 的事件会被算成 12/31，倒计时天数与提醒日整体错一天
        // （负时间戳还会因 Java 截断除法再错一天）。下面的 atZone 已用系统时区，此处须一致。
        val target = Instant.ofEpochMilli(event.targetEpochMs).atZone(ZoneId.systemDefault()).toLocalDate()
        val days = if (event.daily) {
            (0..event.advanceDays).map { target.minusDays(it.toLong()) }
        } else {
            listOf(target.minusDays(event.advanceDays.toLong()), target)
        }
        val now = System.currentTimeMillis()
        return days.mapNotNull { d ->
            val trigger = d.atTime(event.hour, event.minute)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            if (trigger <= now) null
            else {
                val left = ChronoUnit.DAYS.between(LocalDate.now(), d).toInt()
                trigger to left
            }
        }
    }

    fun schedule(context: Context, event: CountdownEvent) {
        if (!event.notify) {
            cancelEvent(context, event.id)
            return
        }
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        triggers(event).forEachIndexed { idx, (trigger, left) ->
            setExact(am, context, trigger, pendingIntent(context, event.id, idx, event, left))
        }
    }

    fun cancelEvent(context: Context, id: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (i in 0..MAX_DAY_INDEX) {
            runCatching { am.cancel(pendingIntent(context, id, i, null, 0)) }
        }
    }

    fun scheduleAll(context: Context, events: List<CountdownEvent>) {
        events.forEach { e ->
            runCatching { schedule(context, e) }
                .onFailure { AppLogger.e(TAG, "scheduleAll 失败 id=${e.id}: ${it.message}") }
        }
    }

    /** 事件增删改后全量重排：先取消 removed，再排当前（幂等）。 */
    fun reschedule(context: Context, events: List<CountdownEvent>, removed: Set<String> = emptySet()) {
        removed.forEach { cancelEvent(context, it) }
        scheduleAll(context, events)
    }

    /** 开机 / 升级 / 改时区后全量重排（供 [BootReceiver] 调用）。 */
    suspend fun rescheduleAll(context: Context) {
        val repo = ep(context).settingsRepository()
        val raw = runCatching { repo.countdownEvents.first() }.getOrDefault("")
        val events = raw.lines().filter { it.isNotBlank() }
            .mapNotNull { CountdownEvent.parse(it) }
        scheduleAll(context, events)
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
            AppLogger.e(TAG, "setExact 精确闹钟被拒（${e.message}），退化非精确")
            runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi) }
                .onFailure { AppLogger.e(TAG, "setExact 非精确也失败（${it.message}）") }
        }
    }
}
