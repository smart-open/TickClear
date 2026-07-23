package com.tickclear.app.domain.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.tickclear.app.data.local.entities.TaskEntity
import com.tickclear.app.data.repositories.TaskInstanceRepository
import com.tickclear.app.data.repositories.TaskRepository
import com.tickclear.app.domain.conflict.dueMinutesForDate
import com.tickclear.app.domain.conflict.isEnabled
import com.tickclear.app.domain.conflict.shouldGenerateInstance
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
        fun settingsRepository(): com.tickclear.app.data.repositories.SettingsRepository
    }

    private fun entryPoint(context: Context): ReminderEntryPoint =
        EntryPointAccessors.fromApplication(context.applicationContext, ReminderEntryPoint::class.java)

    /** 全量重排（在 IO 调度内调用）：遍历所有启用且开启提醒的任务，排程今日发生的提醒。 */
    suspend fun rescheduleAll(context: Context) {
        val ep = entryPoint(context)
        val today = LocalDate.now()
        val tasks = ep.taskRepository().observeAll().first()
            .filter { it.isEnabled() && it.reminderEnabled }
        for (task in tasks) {
            scheduleForTask(context, task, today)
        }
    }

    /** 调度单个任务当天所有应触发的实例（子日级重复会排多个闹钟）。 */
    suspend fun scheduleForTask(context: Context, task: TaskEntity, today: LocalDate = LocalDate.now()) {
        if (!task.isEnabled() || !task.reminderEnabled) return
        if (!shouldGenerateInstance(task, today)) return
        val ep = entryPoint(context)
        val dateStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        // 先确保当日实例已生成（含子日级多实例），再据此排程。
        ep.taskInstanceRepository().ensureInstancesForDate(today, listOf(task))
        val now = System.currentTimeMillis()
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        ep.taskInstanceRepository().observeOn(today).first()
            .filter { it.taskId == task.id }
            .forEach { inst ->
                val minute = inst.dueMinute ?: return@forEach
                val trigger = triggerMillisForMinute(minute, task.reminderOffsetMin ?: 0) ?: return@forEach
                if (trigger <= now) return@forEach // 过去的提醒不再弹出（避免启动即轰炸）
                val pi = showPendingIntent(context, task.id, inst.id)
                setExact(am, context, trigger, pi)
            }
    }

    /** 取消某任务当天所有已排程的提醒（逐实例 + 兼容旧格式）。 */
    suspend fun cancelForTask(context: Context, taskId: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        // 旧格式（兼容历史闹钟）：requestCode 仅基于 taskId。
        runCatching { am.cancel(showPendingIntentLegacy(context, taskId)) }
        // 新格式：按当日所有实例逐一取消。
        runCatching {
            val ep = entryPoint(context)
            ep.taskInstanceRepository().observeOn(LocalDate.now()).first()
                .filter { it.taskId == taskId }
                .forEach { am.cancel(showPendingIntent(context, taskId, it.id)) }
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
     * requestCode 使用 instanceId 哈希（而非 taskId），避免同一任务多个实例
     * （子日级重复 / 稍后提醒）互相覆盖（PRD 多实例提醒可靠性）。
     */
    private fun showPendingIntent(context: Context, taskId: String, instanceId: String): PendingIntent {
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
