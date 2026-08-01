package com.tickclear.app.domain.scheduler

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.tickclear.app.domain.log.AppLogger
import com.tickclear.app.domain.model.Task
import com.tickclear.app.data.repositories.TaskInstanceRepository
import com.tickclear.app.domain.repository.TaskRepository
import com.tickclear.app.domain.conflict.dueMinutesForDate
import com.tickclear.app.domain.conflict.isEnabled
import com.tickclear.app.domain.conflict.shouldGenerateInstance
import com.tickclear.app.domain.model.RepeatType
import com.tickclear.app.domain.model.TaskStatus
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
    private const val TAG = "ReminderScheduler"
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

    /** 一次性提醒"刚过点"宽限：落库/重排时目标时刻已过去但在该窗口内，仍视为有效（迟到提醒优于丢失）。 */
    private const val PAST_GRACE_MS = 5 * 60_000L

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
    suspend fun rescheduleAll(context: Context): Int {
        val ep = entryPoint(context)
        val today = LocalDate.now()
        val tasks = ep.taskRepository().observeAll().first()
            .filter { it.isEnabled() && it.reminderEnabled }
        if (tasks.isEmpty()) return 0
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // 一次性为全部任务生成当日实例（原逐任务调用 → N 次，现 1 次）。
        ep.taskInstanceRepository().ensureInstancesForDate(today, tasks)
        val now = System.currentTimeMillis()
        // 一次性取出当日全部实例并按 taskId 分组（原每任务各查 1 次 → N 次，现 1 次）。
        val instancesByTask = ep.taskInstanceRepository().observeOn(today).first()
            .groupBy { it.taskId }
        var scheduled = 0
        for (task in tasks) {
            (instancesByTask[task.id] ?: continue).forEach { inst ->
                val minute = inst.dueMinute ?: return@forEach
                // 已完成/已跳过的实例不再为"本次发生"排闹钟（重复任务会自动顺延到下一发生日）。
                val done = inst.status != TaskStatus.ACTIVE.code
                val trigger = resolveTrigger(task, today, minute, task.reminderOffsetMin ?: 0, now, done) ?: return@forEach
                setExact(am, context, trigger, showPendingIntent(context, task.id, inst.id), useAlarmClock = task.reminderLevel == "high")
                scheduled++
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
                    val done = inst.status != TaskStatus.ACTIVE.code
                    val trigger = resolveTrigger(task, next, minute, task.reminderOffsetMin ?: 0, now, done) ?: return@forEach
                    setExact(am, context, trigger, showPendingIntent(context, task.id, inst.id), useAlarmClock = task.reminderLevel == "high")
                    scheduled++
                }
        }
        return scheduled
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
        AppLogger.w(TAG, "scheduleForTask task=${task.id} enabled=${task.isEnabled()} reminder=${task.reminderEnabled} target=$target")
        val ep = entryPoint(context)
        // 先确保目标日实例已生成（含子日级多实例），再据此排程。
        ep.taskInstanceRepository().ensureInstancesForDate(target, listOf(task))
        val now = System.currentTimeMillis()
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val canExact = canUseExactAlarms(am)
        ep.taskInstanceRepository().observeOn(target).first()
            .filter { it.taskId == task.id }
            .forEach { inst ->
                val minute = inst.dueMinute ?: return@forEach
                val done = inst.status != TaskStatus.ACTIVE.code
                val trigger = resolveTrigger(task, target, minute, task.reminderOffsetMin ?: 0, now, done) ?: run {
                    AppLogger.w(TAG, "scheduleForTask 跳过（已过期/已完成/无需） task=${task.id} inst=${inst.id} minute=$minute target=$target done=$done")
                    return@forEach
                }
                val pi = showPendingIntent(context, task.id, inst.id)
                AppLogger.w(TAG, "scheduleForTask 排程 task=${task.id} inst=${inst.id} minute=$minute trigger=$trigger now=$now exact=$canExact")
                setExact(am, context, trigger, pi, useAlarmClock = task.reminderLevel == "high")
            }
    }

    /**
     * 任务触发后自动续排「下一个发生日」的提醒（由 [ReminderReceiver] 在弹出通知后调用）。
     *
     * 先前 [rescheduleAll]/[scheduleForTask] 只排"最近一次发生日"，任务触发后若不再续排，
     * 重复任务（DAILY/WEEKLY/INTERVAL）会在首次响铃后永久停摆——只能依赖用户每日打开 App
     * （触发 rescheduleAll）或被杀进程后重启才能补排，否则后续提醒全部丢失。
     * 现每次触发后严格从"次日"起找下一发生日续排，保证重复任务持续提醒；
     * 一次性任务无后续发生日，[nextOccurrenceDate] 返回 null，自然跳过。
     */
    suspend fun scheduleNext(context: Context, task: Task) {
        if (!task.isEnabled() || !task.reminderEnabled) return
        val target = nextOccurrenceDate(task, LocalDate.now().plusDays(1)) ?: return
        val ep = entryPoint(context)
        ep.taskInstanceRepository().ensureInstancesForDate(target, listOf(task))
        val now = System.currentTimeMillis()
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        ep.taskInstanceRepository().observeOn(target).first()
            .filter { it.taskId == task.id }
            .forEach { inst ->
                val minute = inst.dueMinute ?: return@forEach
                val done = inst.status != TaskStatus.ACTIVE.code
                val trigger = resolveTrigger(task, target, minute, task.reminderOffsetMin ?: 0, now, done) ?: return@forEach
                setExact(am, context, trigger, showPendingIntent(context, task.id, inst.id), useAlarmClock = task.reminderLevel == "high")
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
            // V2.8X 修复：此前 `next == today` 直接 return，导致「今天也发生」的重复任务
            // 被 scheduleNext 预排在"下一发生日"的闹钟撤不掉 —— 删除/关闭提醒后次日仍会响。
            // 现同时覆盖「最近发生日」与「次日起的下一发生日」两个候选日期。
            val targets = listOfNotNull(
                nextOccurrenceDate(task, today),
                nextOccurrenceDate(task, today.plusDays(1)),
            ).filter { it != today }.distinct()
            for (day in targets) {
                ep.taskInstanceRepository().observeOn(day).first()
                    .filter { it.taskId == taskId }
                    .forEach { inst ->
                        runCatching { am.cancel(showPendingIntent(context, taskId, inst.id)) }
                    }
                // 实例行可能尚未生成：按单实例 id 约定 "${taskId}@$date" 直接撤销（幂等无害）。
                runCatching { am.cancel(showPendingIntent(context, taskId, "$taskId@$day")) }
            }
        }
    }

    /**
     * 稍后提醒：delayMin 分钟后再次弹出（同一任务，覆盖原闹钟）。
     *
     * V2.8X 修复：此前一律走 setExactAndAllowWhileIdle，高优先级任务点「稍后提醒」后
     * 会从"必响"的 setAlarmClock 静默降级为可被 Doze 延迟的精确闹钟——锁屏久置时经常晚点甚至不响。
     * 现按任务级别沿用与首次排程完全一致的通道（high → setAlarmClock）。
     */
    fun scheduleSnooze(context: Context, instanceId: String, taskId: String, delayMin: Int, level: String = "mid") {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val trigger = System.currentTimeMillis() + delayMin * 60_000L
        val pi = showPendingIntent(context, taskId, instanceId)
        setExact(am, context, trigger, pi, useAlarmClock = level == "high")
    }

    /** Android 12+ 是否可设置精确闹钟（无权限 / 调用异常一律按 false 处理，避免 canScheduleExactAlarms 误报导致崩溃）。 */
    private fun canUseExactAlarms(am: AlarmManager): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return runCatching { am.canScheduleExactAlarms() }.getOrDefault(false)
    }

    /**
     * 统一排程入口，按「可靠性从高到低」三级降级，任何一级失败都不会把异常抛给调用方。
     *
     * ⚠️ 血泪教训（V2.8X 闪崩修复）：早期注释写的「setAlarmClock 无需 SCHEDULE_EXACT_ALARM」是**错的**。
     * 自 Android 12(S) 起，`setAlarmClock` 与 `setExact*` 一样受精确闹钟权限门禁；且 Android 14 起
     * SCHEDULE_EXACT_ALARM 对新装应用默认「拒绝」，未授权时 `setAlarmClock` 会抛 SecurityException，
     * 而它此前是裸调用 —— 直接把 `rescheduleAll` 所在协程打崩（App 启动即闪退）。
     *
     * 降级链：
     * 1. setAlarmClock（有精确闹钟权限时）：绕 Doze、状态栏显示闹钟图标，到点必响；
     * 2. setExactAndAllowWhileIdle（有精确闹钟权限时）：精确但可能被系统轻度约束；
     * 3. setAndAllowWhileIdle（无权限 / 前两级被系统实时拒绝）：非精确，可能延迟数分钟，但绝不丢失、绝不崩溃。
     */
    // 权限守卫在 [canUseExactAlarms] 内完成、且每次调用都被 runCatching 包裹；
    // lint 的 MissingPermission 无法跨函数识别该守卫（Manifest 中 SCHEDULE_EXACT_ALARM 已限制到 API 32），故此处抑制。
    @SuppressLint("MissingPermission")
    private fun setExact(am: AlarmManager, context: Context, trigger: Long, pi: PendingIntent, useAlarmClock: Boolean = false) {
        val canExact = canUseExactAlarms(am)
        if (useAlarmClock && canExact) {
            // 高优先级：setAlarmClock 不受 Doze 限制，是 Android 上最可靠的提醒路径。
            val ok = runCatching {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(trigger, openAppIntent(context)), pi)
                true
            }.getOrElse { e ->
                AppLogger.e(TAG, "setExact setAlarmClock 被拒（${e.message}），继续降级 trigger=$trigger")
                false
            }
            if (ok) {
                AppLogger.w(TAG, "setExact 高优先级走 setAlarmClock trigger=$trigger")
                return
            }
        }
        // 第二级：精确闹钟。无权限（Android 12+ 未授予 / Android 14 默认拒绝）或被系统实时拒绝时继续降级。
        if (canExact) {
            val exactOk = runCatching {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
                true
            }.getOrElse { e ->
                AppLogger.e(TAG, "setExact 精确闹钟被拒（${e.message}），退化非精确 trigger=$trigger")
                false
            }
            if (exactOk) return
        } else {
            AppLogger.w(TAG, "setExact 无精确闹钟权限，退化 setAndAllowWhileIdle trigger=$trigger")
        }
        // 第三级：非精确兜底。仍用 runCatching —— 极端机型（厂商定制）连它都可能抛异常，宁可漏一次提醒也不闪退。
        runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi) }
            .onFailure { AppLogger.e(TAG, "setExact 非精确闹钟也失败（${it.message}） trigger=$trigger") }
    }

    /** 高优先级闹钟点击状态栏闹钟指示时打开 App 的意图（setAlarmClock 的 showIntent）。 */
    private fun openAppIntent(context: Context): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            ?: Intent(context, Class.forName("com.tickclear.app.MainActivity"))
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
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

    /**
     * 解析实例真实触发时刻（毫秒）。集中处理"目标时刻已过"的兜底，避免提醒被静默丢弃：
     * - 未来：直接返回；
     * - 已过去且为重复任务（DAILY/WEEKLY/INTERVAL）：从次日（含）起找下一个应发生日重排，
     *   否则"今天过了就再也不响"；
     * - 已过去且为一次性任务：在 [PAST_GRACE_MS] 宽限内 → now+2s（迟到提醒优于丢失），否则 null（已过期）。
     * 返回 null 表示无需排程。
     */
    private fun resolveTrigger(
        task: Task,
        target: LocalDate,
        minute: Int,
        offsetMin: Int,
        now: Long,
        occurrenceDone: Boolean = false,
    ): Long? {
        val base = triggerMillisForDateMinute(target, minute, offsetMin)
        if (base > now && !occurrenceDone) return base
        val isRepeating = RepeatType.fromCode(task.repeatType) != RepeatType.NONE
        if (isRepeating) {
            var d = target.plusDays(1)
            repeat(LOOKAHEAD_DAYS.toInt()) {
                if (shouldGenerateInstance(task, d)) {
                    val t = triggerMillisForDateMinute(d, minute, offsetMin)
                    if (t > now) return t
                }
                d = d.plusDays(1)
            }
            return null
        }
        // 一次性任务：本次发生已被完成/跳过 → 不再补发（否则"提前完成"后仍会到点响铃）。
        if (occurrenceDone) return null
        return if (now - base <= PAST_GRACE_MS) now + 2000L else null
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
