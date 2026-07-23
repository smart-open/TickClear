package com.tickclear.app.domain.conflict

import com.tickclear.app.domain.model.Task
import java.time.LocalDate

/**
 * 时间窗冲突检测：基于 [start,end) 半开区间 + 容差。
 * 同一天、时间窗重叠即视为冲突（重复任务按其当日生效窗口计算）。
 */
object ConflictChecker {
    const val DEFAULT_TOLERANCE_MIN = 0

    private fun overlaps(
        aStart: Int,
        aEnd: Int,
        bStart: Int,
        bEnd: Int,
        toleranceMin: Int,
    ): Boolean = maxOf(aStart, bStart) < minOf(aEnd, bEnd) + toleranceMin

    /** 编辑任务时，与现有任务列表做冲突检测（仅对「今日应发生」的任务比对）。 */
    fun findConflicts(
        candidate: Task,
        existing: List<Task>,
        toleranceMin: Int = DEFAULT_TOLERANCE_MIN,
    ): List<Task> {
        val today = LocalDate.now()
        val cStart = candidate.effectiveStartMin() ?: return emptyList()
        if (!shouldGenerateInstance(candidate, today)) return emptyList()
        val cEnd = candidate.effectiveEndMin()
        return existing
            .filter { it.id != candidate.id && shouldGenerateInstance(it, today) }
            .filter { other ->
                val oStart = other.effectiveStartMin() ?: return@filter false
                overlaps(cStart, cEnd, oStart, other.effectiveEndMin(), toleranceMin)
            }
    }

    fun hasConflict(
        candidate: Task,
        existing: List<Task>,
        toleranceMin: Int = DEFAULT_TOLERANCE_MIN,
    ): Boolean = findConflicts(candidate, existing, toleranceMin).isNotEmpty()

    /**
     * 今日视图冲突：输入 (id, [start,end] 分钟区间) 列表，返回存在重叠的 id 集合。
     * 用于基于 TaskInstance 的当日任务冲突判定。
     */
    fun findConflictIds(
        windows: List<Pair<String, IntRange>>,
        toleranceMin: Int = DEFAULT_TOLERANCE_MIN,
    ): Set<String> {
        val conflicts = mutableSetOf<String>()
        for (i in windows.indices) {
            for (j in i + 1 until windows.size) {
                val (idA, a) = windows[i]
                val (idB, b) = windows[j]
                if (maxOf(a.first, b.first) < minOf(a.last, b.last) + toleranceMin) {
                    conflicts += idA
                    conflicts += idB
                }
            }
        }
        return conflicts
    }
}
