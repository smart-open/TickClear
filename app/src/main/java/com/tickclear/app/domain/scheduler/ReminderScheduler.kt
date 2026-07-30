package com.tickclear.app.domain.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.tickclear.app.domain.model.Task
import com.tickclear.app.data.repositories.TaskInstanceRepository
import com.tickclear.app.domain.repository.TaskRepository
import com.tickclear.app.domain.conflict.dueMinutesForDate
import com.tickclear.app.domain.conflict.isEnabled
import com.tickclear.app.domain.conflict.shouldGenerateInstance
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import java.util.Calendar
import kotlinx.coroutines.flow.first

/**
 * 提醒调度：基于 AlarmManager 精确闹钟（setExactAndAllowWhileIdle）。
 * - 按任务当日发生时间（含提前量 reminderOffsetMin）排程；
 * - 支持单任务排程 / 全量重排 / 稍后重排 / 取消；
 * - 开机（BootReceiver）与应用启动各重排一次。
 *
 * 与 WorkManager 不同，AlarmManager 保证定点送达（PRD §7.2 硬性要求），
 * 因此即便在 WorkManager 不可用的环境也走此路径。
 */
object ReminderScheduler {
    private const val ACTION_SHOW = "com.tickclear.app.reminder.SHOW"
    const val EXTRA_TASK_ID = "task_id"
    const val EXTRA_INSTANCE_ID = "instance_id"

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ReminderEntryPoint {
        fun taskRepository(): TaskRepository
        fun taskInstanceRepository(): TaskInstanceRepository
        fun completeTaskUseCase(): com.tickclear.app.domain.usecase.CompleteTaskUseCase
        fun settingsRepository(): com.tickclear.app.domain.repository.SettingsRepository
    }

    private fun entryPoint(context: Context): ReminderEntryPoint =
        EntryPointAccessors.fromApplication(context.applicationContext, ReminderEntryPoint::class.java)

    /** V2.8X++：未来发生日向后查找窗口（天）。覆盖「明天/下周提醒」等未来任务，60 天足够常见口语范围。 */
    private const val LOOKAHEAD_DAYS = 60L

    /** 从 [from]（含当天）起向后找任务最近一个发生日；窗口内无发生返回 null。 */
    private fun nextOccurrenceDate(task: Task, from: LocalDate): LocalDate? {
        for (i in 0..LOOKAHEAD_DAYS) {
            val d = from.plusDays(i)
            if (shouldGenerateInstance(task, d)) return d
        }
        return null
    }

    /**
     * 全量重排（在 IO 调度内调用）：遍历所有启用且开启提醒的任务，排程今日发生的提醒。
     * 批量优化：实例生成与当日实例查询各只做一次（原先逐任务各查一次 → N+1 查询），
     * 与任务数无关，固定 3 次 DB 访问；单任务排程仍走 [scheduleForTask]。
     */
    suspend fun rescheduleAll(context: Context) {
        val ep = entryPoint(context)
        val today = LocalDate.now()
        val tasks = ep.taskRepository().observeAll().first()
            .filter { it.isEnabled() && it.reminderEnabled }
        if (tasks.isEmpty()) return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // 一次性为全部任务生成当日实例（原逐任务调用 → N 次，现 1 次）。
        ep.taskInstanceRepository().ensureInstancesForDate(today, tasks)
        val now = System.currentTimeMillis()
        // 一次性取出当日全部实例并按 taskId 分组（原每任务各查 1 次 → N 次，现 1 次）。
        val instancesByTask = ep.taskInstanceRepository().observeOn(today).first()
            .groupBy { it.taskId }
        for (task in tasks) {
            (instancesByTask[task.id] ?: continue).forEach { inst ->
                val minute = inst.dueMinute ?: return@forEach
                val trigger = triggerMillisForMinute(minute, task.reminderOffsetMin ?: 0) ?: return@forEach
                if (trigger <= now) return@forEach // 过去的提醒不再弹出（避免启动即轰炸）
                val pi = showPendingIntent(context, task.id, inst.id)
                setExact(am, context, trigger, pi)
            }
        }
        // V2.8X++：今日不发生、但未来会发生的任务（如「明天 8 点」单次提醒）也要预排——
        // 否则重启/进程被杀后，若用户次日不打开 App，闹钟丢失。此类任务通常极少，逐个排即可。
        for (task in tasks) {
            if (shouldGenerateInstance(task, today)) continue // 今日发生的已在上方批量排程
            val next = nextOccurrenceDate(task, today) ?: continue
            ep.taskInstanceRepository().ensureInstancesForDate(next, listOf(task))
            ep.taskInstanceRepository().observeOn(next).first()
                .filter { it.taskId == task.id }
                .forEach { inst ->
                    val minute = inst.dueMinute ?: return@forEach
                    val trigger = triggerMillisForDateMinute(next, minute, task.reminderOffsetMin ?: 0)
                    if (trigger <= now) return@forEach
                    setExact(am, context, trigger, showPendingIntent(context, task.id, inst.id))
                }
        }
    }

    /**
     * 调度单个任务「最近一个发生日」的所有应触发实例（子日级重复会排多个闹钟）。
     *
     * V2.8X++：不再只排"今天"。此前 `shouldGenerateInstance(task, today)=false` 直接 return，
     * 导致「明天 8 点提醒起床」这类未来单次任务落库后闹钟根本排不上——除非用户次日恰好在
     * 闹点前打开 App（触发 rescheduleAll）。现从 today 起向后找最近发生日（60 天窗口）排程；
     * 今日发生的任务行为与旧版完全一致（target == today）。
     */
    suspend fun scheduleForTask(context: Context, task: Task, today: LocalDate = LocalDate.now()) {
        if (!task.isEnabled() || !task.reminderEnabled) return
        val target = nextOccurrenceDate(task, today) ?: return
        val ep = entryPoint(context)
        // 先确保目标日实例已生成（含子日级多实例），再据此排程。
        ep.taskInstanceRepository().ensureInstancesForDate(target, listOf(task))
        val now = System.currentTimeMillis()
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        ep.taskInstanceRepository().observeOn(target).first()
            .filter { it.taskId == task.id }
            .forEach { inst ->
                val minute = inst.dueMinute ?: return@forEach
                val trigger = triggerMillisForDateMinute(target, minute, task.reminderOffsetMin ?: 0)
                if (trigger <= now) return@forEach // 过去的提醒不再弹出（避免启动即轰炸）
                val pi = showPendingIntent(context, task.id, inst.id)
                setExact(am, context, trigger, pi)
            }
    }

    /** 取消某任务当天所有已排程的提醒（逐实例 + 兼容旧格式）。 */
    suspend fun cancelForTask(context: Context, taskId: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // 旧旧格式（兼容历史闹钟）：requestCode 仅基于 taskId。
        runCatching { am.cancel(showPendingIntentLegacy(context, taskId)) }
        runCatching {
            val ep = entryPoint(context)
            ep.taskInstanceRepository().observeOn(LocalDate.now()).first()
                .filter { it.taskId == taskId }
                .forEach { inst ->
                    // 新格式（FNV-1a）。
                    runCatching { am.cancel(showPendingIntent(context, taskId, inst.id)) }
                    // 上一版本遗留格式（instanceId.hashCode），升级后一并撤销。
                    runCatching { am.cancel(showPendingIntentOldHash(context, taskId, inst.id)) }
                }
        }
        // V2.8X++：scheduleForTask 现可能把闹钟排在未来发生日 → 取消时也要覆盖该日期，
        // 否则「先建明天提醒再删除」会留下孤立闹钟照常响。
        runCatching {
            val ep = entryPoint(context)
            val task = ep.taskRepository().getById(taskId) ?: return@runCatching
            val today = LocalDate.now()
            val next = nextOccurrenceDate(task, today) ?: return@runCatching
            if (next == today) return@runCatching // 今日的已在上方撤销
            ep.taskInstanceRepository().observeOn(next).first()
                .filter { it.taskId == taskId }
                .forEach { inst ->
                    runCatching { am.cancel(showPendingIntent(context, taskId, inst.id)) }
                }
            // 实例行可能尚未生成：按单实例 id 约定 "${taskId}@$date" 直接撤销（幂等无害）。
            runCatching { am.cancel(showPendingIntent(context, taskId, "$taskId@$next")) }
        }
    }

    /** 稍后提醒：delayMin 分钟后再次弹出（同一任务，覆盖原闹钟）。 */
    fun scheduleSnooze(context: Context, instanceId: String, taskId: String, delayMin: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val trigger = System.currentTimeMillis() + delayMin * 60_000L
        val pi = showPendingIntent(context, taskId, instanceId)
        setExact(am, context, trigger, pi)
    }

    private fun setExact(am: AlarmManager, context: Context, trigger: Long, pi: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            // 无精确闹钟权限：退化为非精确（仍尽量准时），避免崩溃。
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        }
    }

    /** V2.8X++：按具体日期计算触发时刻（毫秒），供未来发生日排程使用。 */
    private fun triggerMillisForDateMinute(date: LocalDate, minute: Int, offsetMin: Int = 0): Long {
        val fireMinute = (minute - offsetMin).coerceAtLeast(0)
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, date.year)
            set(Calendar.MONTH, date.monthValue - 1)
            set(Calendar.DAY_OF_MONTH, date.dayOfMonth)
            set(Calendar.HOUR_OF_DAY, fireMinute / 60)
            set(Calendar.MINUTE, fireMinute % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun triggerMillisForMinute(minute: Int, offsetMin: Int = 0): Long {
        val fireMinute = (minute - offsetMin).coerceAtLeast(0)
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, fireMinute / 60)
            set(Calendar.MINUTE, fireMinute % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return now.timeInMillis
    }

    /**
     * 构造提醒广播 PendingIntent。
     * requestCode 使用 [ReminderIds.notificationId]（instanceId 的 FNV-1a 哈希，而非 taskId），
     * 避免同一任务多个实例（子日级重复 / 稍后提醒）互相覆盖（PRD 多实例提醒可靠性）。
     */
    private fun showPendingIntent(context: Context, taskId: String, instanceId: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_SHOW
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_INSTANCE_ID, instanceId)
        }
        return PendingIntent.getBroadcast(
            context, ReminderIds.notificationId(instanceId), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * 兼容上一版本（v2.4.0 及更早）的 PendingIntent：requestCode 直接取 [instanceId.hashCode]。
     * 升级后旧闹钟仍可能 pending，cancel 时一并撤销，避免孤立闹钟触发旧映射。
     */
    private fun showPendingIntentOldHash(context: Context, taskId: String, instanceId: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_SHOW
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_INSTANCE_ID, instanceId)
        }
        return PendingIntent.getBroadcast(
            context, instanceId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** 兼容旧版「仅 taskId 哈希」的 PendingIntent（用于取消历史闹钟）。 */
    private fun showPendingIntentLegacy(context: Context, taskId: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_SHOW
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_INSTANCE_ID, "$taskId@any")
        }
        return PendingIntent.getBroadcast(
            context, ("show:$taskId").hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
