package com.tickclear.app.domain.conflict

import com.tickclear.app.data.local.entities.TaskEntity
import com.tickclear.app.domain.model.RepeatType
import com.tickclear.app.domain.model.TaskStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 重复/定时任务在指定日期是否「应生成一个实例」。
 * - NONE：scheduledDate 命中当天；scheduledDate 为 null 视为「随时任务」，每天生成（每日待办清单）
 * - DAILY：每天
 * - WEEKLY：weekdays 命中（csv "1,3,5"，1=周一）
 * - MONTHLY：dayOfMonth 命中；>当月天数时回退到月末最后一天（避免 31 号在短月永不触发）
 * - INTERVAL：基于锚点日 repeatAnchorDate 起算 (天数差 % interval == 0)；无锚点日不生成
 */
fun shouldGenerateInstance(task: TaskEntity, date: LocalDate): Boolean {
    return when (RepeatType.fromCode(task.repeatType)) {
        RepeatType.NONE -> {
            val sd = task.scheduledDate
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            sd == null || sd == date
        }
        RepeatType.DAILY -> true
        RepeatType.WEEKLY -> {
            val days = task.repeatWeekdays
                ?.split(",")
                ?.mapNotNull { it.toIntOrNull() }
                ?.toSet()
                ?: return true
            days.contains(date.dayOfWeek.isoDayNumber())
        }
        RepeatType.MONTHLY -> {
            val md = task.repeatMonthDay ?: return true
            md == date.dayOfMonth ||
                (md > date.lengthOfMonth() && date.dayOfMonth == date.lengthOfMonth())
        }
        RepeatType.INTERVAL -> {
            val interval = (task.repeatIntervalDays ?: 1).coerceAtLeast(1)
            val anchor = task.repeatAnchorDate
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: return false
            val days = ChronoUnit.DAYS.between(anchor, date)
            days >= 0 && days % interval == 0L
        }
    }
}

/** 实例在当日的生效开始分钟（非重复= scheduledStartMin；重复= repeatAnchorMin）。 */
fun TaskEntity.instanceDueMinute(): Int? {
    return if (RepeatType.fromCode(repeatType) == RepeatType.NONE) scheduledStartMin
    else repeatAnchorMin
}

/** 任务是否处于启用态（active，非软删、非暂停）。 */
fun TaskEntity.isEnabled(): Boolean =
    deletedAt == null && status != TaskStatus.PAUSED.code

/**
 * 兼容旧调用：一次性/重复任务在「今天」是否发生（基于 scheduledDate / repeat 规则）。
 * 新代码请优先使用 [shouldGenerateInstance]。
 */
@Deprecated("使用 shouldGenerateInstance(task, date)", ReplaceWith("shouldGenerateInstance(task, date)"))
fun TaskEntity.occursOn(date: LocalDate): Boolean = shouldGenerateInstance(this, date)

/** 当日生效的结束分钟（非重复= scheduledEndMin；重复= anchor+默认 0）。 */
fun TaskEntity.effectiveStartMin(): Int? = instanceDueMinute()

/**
 * 当日生效的结束分钟。
 * - 非重复：scheduledEndMin ?: scheduledStartMin ?: 0
 * - 重复：scheduledEndMin ?: (anchor + 30)
 * 与 GetTodayTasksUseCase 的今日视图冲突窗口保持一致（重复任务默认取 30 分钟时长），
 * 否则编辑期 findConflicts 对重复任务恒判不冲突（语义分裂 bug M1）。
 */
fun TaskEntity.effectiveEndMin(): Int {
    val start = instanceDueMinute() ?: 0
    val end = if (RepeatType.fromCode(repeatType) == RepeatType.NONE) {
        (scheduledEndMin ?: scheduledStartMin ?: 0)
    } else {
        scheduledEndMin ?: (start + 30)
    }
    // 跨午夜：结束分钟早于开始分钟时视为次日（+1440），避免晚间接续任务漏判冲突。
    return if (end < start) end + 1440 else end
}

private fun DayOfWeek.isoDayNumber(): Int = value // Monday=1 ... Sunday=7
