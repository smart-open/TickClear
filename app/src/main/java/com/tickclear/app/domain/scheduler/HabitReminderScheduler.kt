package com.tickclear.app.domain.scheduler

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.tickclear.app.domain.log.AppLogger
import com.tickclear.app.domain.model.Habit
import com.tickclear.app.domain.repository.HabitRepository
import com.tickclear.app.domain.repository.SettingsRepository
import com.tickclear.app.domain.util.isHabitDueOn
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import java.util.Calendar
import kotlinx.coroutines.flow.first

/**
 * 习惯提醒调度：基于 AlarmManager 精确闹钟（setExactAndAllowWhileIdle）。
 * - 习惯按 repeatDays（ISO 星期，0/空=每天）在每日 reminderMin 时刻提醒；
 * - 每次触发后由 [HabitReminderReceiver] 自动续排下一天（保证每天到点）；
 * - 开机（BootReceiver）与应用启动各重排一次，避免进程被杀后闹钟丢失。
 */
object HabitReminderScheduler {
    private const val TAG = "HabitReminderScheduler"
    private const val ACTION_SHOW = "com.tickclear.app.habit.reminder.SHOW"
    const val EXTRA_HABIT_ID = "habit_id"

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface HabitReminderEntryPoint {
        fun habitRepository(): HabitRepository
        fun settingsRepository(): SettingsRepository
    }

    private fun entryPoint(context: Context): HabitReminderEntryPoint =
        EntryPointAccessors.fromApplication(context.applicationContext, HabitReminderEntryPoint::class.java)

    /**
     * 从 [from]（含）起最近一个"未来"应提醒时刻（毫秒）。
     * 窗口 7 天；仅排未来时刻，过去时间不补响（避免启动/续排即轰炸）。
     */
    private fun nextTriggerMillis(habit: Habit, from: LocalDate): Long? {
        val now = System.currentTimeMillis()
        for (i in 0..7) {
            val date = from.plusDays(i.toLong())
            if (!isHabitDueOn(habit, date)) continue
            val cal = Calendar.getInstance().apply {
                set(Calendar.YEAR, date.year)
                set(Calendar.MONTH, date.monthValue - 1)
                set(Calendar.DAY_OF_MONTH, date.dayOfMonth)
                set(Calendar.HOUR_OF_DAY, habit.reminderMin / 60)
                set(Calendar.MINUTE, habit.reminderMin % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val t = cal.timeInMillis
            if (t > now) return t
        }
        return null
    }

    /** 为单个习惯排程「最近一次」提醒（reminderMin<0 或已归档则撤闹钟）。 */
    suspend fun scheduleForHabit(context: Context, habit: Habit) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (habit.archived || habit.reminderMin < 0) {
            cancelForHabit(context, habit.id)
            return
        }
        val trigger = nextTriggerMillis(habit, LocalDate.now()) ?: return
        val pi = showPendingIntent(context, habit.id)
        val canExact = canUseExactAlarms(am)
        AppLogger.w(TAG, "scheduleForHabit habit=${habit.id} title=${habit.title} trigger=$trigger now=${System.currentTimeMillis()} exact=$canExact")
        setExact(am, context, trigger, pi)
    }

    /** 触发后续排下一天（接收器调用）；习惯不存在/已撤则静默。 */
    suspend fun scheduleNext(context: Context, habitId: String) {
        val habit = entryPoint(context).habitRepository().getHabit(habitId) ?: return
        if (habit.archived || habit.reminderMin < 0) return
        val trigger = nextTriggerMillis(habit, LocalDate.now().plusDays(1)) ?: return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        setExact(am, context, trigger, showPendingIntent(context, habit.id))
    }

    /** 取消某习惯所有已排程提醒（幂等）。 */
    fun cancelForHabit(context: Context, habitId: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        runCatching { am.cancel(showPendingIntent(context, habitId)) }
    }

    /** 全量重排：所有未归档且 reminderMin>=0 的习惯（开机/启动各一次）。 */
    suspend fun rescheduleAll(context: Context) {
        val habits = entryPoint(context).habitRepository().observeHabits().first()
            .filter { !it.archived && it.reminderMin >= 0 }
        if (habits.isEmpty()) return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()
        for (habit in habits) {
            val trigger = nextTriggerMillis(habit, LocalDate.now()) ?: continue
            if (trigger <= now) continue
            setExact(am, context, trigger, showPendingIntent(context, habit.id))
        }
        AppLogger.w(TAG, "rescheduleAll 排程习惯数=${habits.size}")
    }

    /** Android 12+ 是否可设置精确闹钟（无权限 / 调用异常一律按 false，避免误报导致崩溃）。 */
    private fun canUseExactAlarms(am: AlarmManager): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return runCatching { am.canScheduleExactAlarms() }.getOrDefault(false)
    }

    /**
     * 习惯提醒排程，三级降级（与 ReminderScheduler 的 setExact 保持一致的可靠性策略）：
     * setAlarmClock → setExactAndAllowWhileIdle → setAndAllowWhileIdle。
     *
     * ⚠️ 早期注释「setAlarmClock 无需精确闹钟权限」是**错的**：Android 12(S) 起 setAlarmClock 同样受
     * SCHEDULE_EXACT_ALARM 门禁，Android 14 起该权限对新装应用默认拒绝，裸调用会抛 SecurityException
     * 打崩 rescheduleAll 所在协程（App 启动即闪退）。这里必须先判权限再逐级降级，且全程 runCatching。
     */
    // 权限守卫在 canUseExactAlarms 内完成 + 全程 runCatching；lint 无法跨函数识别，故抑制 MissingPermission。
    @SuppressLint("MissingPermission")
    private fun setExact(am: AlarmManager, context: Context, trigger: Long, pi: PendingIntent) {
        if (canUseExactAlarms(am)) {
            val ok = runCatching {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(trigger, openAppIntent(context)), pi)
                true
            }.getOrElse { e ->
                AppLogger.e(TAG, "setExact 习惯 setAlarmClock 被拒（${e.message}），继续降级 trigger=$trigger")
                false
            }
            if (ok) {
                AppLogger.w(TAG, "setExact 习惯走 setAlarmClock trigger=$trigger")
                return
            }
            val exactOk = runCatching {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
                true
            }.getOrElse { e ->
                AppLogger.e(TAG, "setExact 习惯精确闹钟被拒（${e.message}），退化非精确 trigger=$trigger")
                false
            }
            if (exactOk) return
        } else {
            AppLogger.w(TAG, "setExact 习惯无精确闹钟权限，退化 setAndAllowWhileIdle trigger=$trigger")
        }
        runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi) }
            .onFailure { AppLogger.e(TAG, "setExact 习惯非精确闹钟也失败（${it.message}） trigger=$trigger") }
    }

    /** 闹钟点击状态栏闹钟指示时打开 App 的意图（setAlarmClock 的 showIntent）。 */
    private fun openAppIntent(context: Context): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            ?: Intent(context, Class.forName("com.tickclear.app.MainActivity"))
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun showPendingIntent(context: Context, habitId: String): PendingIntent {
        val intent = Intent(context, HabitReminderReceiver::class.java).apply {
            action = ACTION_SHOW
            putExtra(EXTRA_HABIT_ID, habitId)
        }
        return PendingIntent.getBroadcast(
            context, ReminderIds.fnv1a("habit:$habitId"), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
