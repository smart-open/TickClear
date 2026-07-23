package com.tickclear.app.domain.conflict

import com.tickclear.app.domain.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 时间窗冲突检测单元测试（纯 JVM）。
 * 覆盖半开区间 [start,end) 重叠判定、容差、跨任务比对与 id 集合冲突。
 */
class ConflictCheckerTest {

    private val today: LocalDate = LocalDate.now()

    private fun task(
        id: String,
        start: Int,
        end: Int,
    ): Task = Task(
        id = id,
        title = id,
        status = 0,
        scheduledStartMin = start,
        scheduledEndMin = end,
        allDay = false,
        // 用「随时任务」(NONE + scheduledDate=today) 保证 shouldGenerateInstance 今日为真
        scheduledDate = today.toString(),
        repeatType = "NONE",
        deletedAt = null,
    )

    @Test
    fun `窗口重叠视为冲突`() {
        val a = task("a", 540, 600) // 09:00-10:00
        val b = task("b", 570, 630) // 09:30-10:30
        assertTrue(ConflictChecker.hasConflict(a, listOf(b)))
        assertEquals(listOf("b"), ConflictChecker.findConflicts(a, listOf(b)).map { it.id })
    }

    @Test
    fun `首尾相接不冲突（半开区间）`() {
        val a = task("a", 540, 600) // 09:00-10:00
        val b = task("b", 600, 660) // 10:00-11:00，紧邻但不重叠
        assertFalse(ConflictChecker.hasConflict(a, listOf(b)))
    }

    @Test
    fun `完全不重叠不冲突`() {
        val a = task("a", 540, 600)
        val b = task("b", 720, 780) // 12:00-13:00
        assertFalse(ConflictChecker.hasConflict(a, listOf(b)))
    }

    @Test
    fun `排除自身id`() {
        val a = task("a", 540, 600)
        // 列表里含自身，不应与自己冲突
        assertTrue(ConflictChecker.findConflicts(a, listOf(a)).isEmpty())
    }

    @Test
    fun `候选无开始时间返回空`() {
        val noStart = Task(
            id = "x", title = "x", status = 0,
            scheduledStartMin = null, allDay = true,
            scheduledDate = today.toString(), repeatType = "NONE", deletedAt = null,
        )
        assertTrue(ConflictChecker.findConflicts(noStart, listOf(task("b", 540, 600))).isEmpty())
    }

    @Test
    fun `容差扩大冲突判定`() {
        val a = task("a", 540, 600) // 09:00-10:00
        val b = task("b", 605, 660) // 10:05-11:00，间隔 5 分钟
        assertFalse(ConflictChecker.hasConflict(a, listOf(b), toleranceMin = 0))
        assertTrue(ConflictChecker.hasConflict(a, listOf(b), toleranceMin = 10))
    }

    @Test
    fun `findConflictIds 返回所有重叠成员`() {
        val windows = listOf(
            "a" to 540..600,   // 09:00-10:00
            "b" to 570..630,   // 09:30-10:30  与 a 重叠
            "c" to 700..760,   // 独立
        )
        val ids = ConflictChecker.findConflictIds(windows)
        assertEquals(setOf("a", "b"), ids)
    }

    @Test
    fun `findConflictIds 无重叠返回空集`() {
        val windows = listOf(
            "a" to 540..600,
            "b" to 601..660,
        )
        assertTrue(ConflictChecker.findConflictIds(windows).isEmpty())
    }
}
