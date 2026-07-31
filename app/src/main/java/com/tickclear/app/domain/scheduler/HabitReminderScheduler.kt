package com.tickclear.app.domain.scheduler

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
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
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

    private fun setExact(am: AlarmManager, context: Context, trigger: Long, pi: PendingIntent) {
        // 习惯提醒本质「每天必提醒」，统一走 setAlarmClock：不受 Doze 限制、无需精确闹钟权限、到点必响。
        val info = AlarmManager.AlarmClockInfo(trigger, openAppIntent(context))
        AppLogger.w(TAG, "setExact 习惯走 setAlarmClock trigger=$trigger")
        am.setAlarmClock(info, pi)
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
