package com.tickclear.app.domain.conflict

import com.tickclear.app.domain.model.Task
import com.tickclear.app.domain.model.RepeatType
import com.tickclear.app.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 调度核心逻辑单元测试：覆盖 shouldGenerateInstance 与 dueMinutesForDate。
 * 纯 JVM（无 Android 依赖），直接验证重复/子日级规则，是提醒可靠性的关键回归屏障。
 */
class ScheduleUtilsTest {

    private fun task(
        repeatType: String = "NONE",
        scheduledDate: String? = null,
        repeatIntervalDays: Int? = null,
        repeatIntervalHours: Int? = null,
        repeatWeekdays: String? = null,
        repeatMonthDay: Int? = null,
        repeatAnchorMin: Int? = 540,
        repeatAnchorDate: String? = null,
        scheduledStartMin: Int? = 540,
        allDay: Boolean = false,
        status: Int = 0,
        deletedAt: Long? = null,
    ): Task = Task(
        id = "t1",
        title = "测试",
        status = status,
        scheduledStartMin = scheduledStartMin,
        allDay = allDay,
        scheduledDate = scheduledDate,
        repeatType = repeatType,
        repeatIntervalDays = repeatIntervalDays,
        repeatIntervalHours = repeatIntervalHours,
        repeatWeekdays = repeatWeekdays,
        repeatMonthDay = repeatMonthDay,
        repeatAnchorMin = repeatAnchorMin,
        repeatAnchorDate = repeatAnchorDate,
        deletedAt = deletedAt,
    )

    // ── isEnabled ──
    @Test
    fun `isEnabled 软删或暂停返回 false`() {
        assertFalse(task(status = TaskStatus.PAUSED.code).isEnabled())
        assertFalse(task(deletedAt = 1L).isEnabled())
        assertTrue(task().isEnabled())
    }

    // ── shouldGenerateInstance: NONE ──
    @Test
    fun `NONE 且日期为null 视为随时任务每日生成`() {
        assertTrue(shouldGenerateInstance(task(scheduledDate = null), LocalDate.of(2026, 7, 23)))
    }

    @Test
    fun `NONE 命中指定日期才生成`() {
        val d = LocalDate.of(2026, 7, 23)
        assertTrue(shouldGenerateInstance(task(scheduledDate = "2026-07-23"), d))
        assertFalse(shouldGenerateInstance(task(scheduledDate = "2026-07-24"), d))
    }

    @Test
    fun `NONE 损坏日期字符串解析失败时退化为不生成（不再静默每日）`() {
        assertFalse(shouldGenerateInstance(task(scheduledDate = "not-a-date"), LocalDate.of(2026, 7, 23)))
    }

    // ── shouldGenerateInstance: DAILY / WEEKLY / MONTHLY ──
    @Test
    fun `DAILY 每日生成`() {
        assertTrue(shouldGenerateInstance(task(repeatType = "DAILY"), LocalDate.of(2026, 7, 23)))
    }

    @Test
    fun `WEEKLY 命中星期几才生成`() {
        // 2026-07-23 是周四(iso=4)
        val thu = LocalDate.of(2026, 7, 23)
        assertEquals(DayOfWeek.THURSDAY, thu.dayOfWeek)
        assertTrue(shouldGenerateInstance(task(repeatType = "WEEKLY", repeatWeekdays = "4"), thu))
        assertFalse(shouldGenerateInstance(task(repeatType = "WEEKLY", repeatWeekdays = "1,3,5"), thu))
    }

    @Test
    fun `MONTHLY 月末短月回退到最后一天`() {
        // 2 月 30 号在平年 2 月(28天)应回退到 28 号触发
        assertTrue(shouldGenerateInstance(task(repeatType = "MONTHLY", repeatMonthDay = 30), LocalDate.of(2026, 2, 28)))
        assertFalse(shouldGenerateInstance(task(repeatType = "MONTHLY", repeatMonthDay = 30), LocalDate.of(2026, 2, 27)))
    }

    // ── shouldGenerateInstance: INTERVAL ──
    @Test
    fun `INTERVAL 每N天 从锚点起整除才生成`() {
        val anchor = LocalDate.of(2026, 7, 1)
        val onDay = LocalDate.of(2026, 7, 11) // +10 天, 能被 5 整除
        val offDay = LocalDate.of(2026, 7, 12)
        assertTrue(shouldGenerateInstance(task(repeatType = "INTERVAL", repeatIntervalDays = 5, repeatAnchorDate = "2026-07-01"), onDay))
        assertFalse(shouldGenerateInstance(task(repeatType = "INTERVAL", repeatIntervalDays = 5, repeatAnchorDate = "2026-07-01"), offDay))
        // 锚点之前不生成
        assertFalse(shouldGenerateInstance(task(repeatType = "INTERVAL", repeatIntervalDays = 5, repeatAnchorDate = "2026-07-01"), LocalDate.of(2026, 6, 30)))
    }

    @Test
    fun `INTERVAL 每N天 缺锚点日不生成`() {
        assertFalse(shouldGenerateInstance(task(repeatType = "INTERVAL", repeatIntervalDays = 3, repeatAnchorDate = null), LocalDate.of(2026, 7, 23)))
    }

    @Test
    fun `INTERVAL 每N小时 每日都生成`() {
        assertTrue(shouldGenerateInstance(task(repeatType = "INTERVAL", repeatIntervalHours = 8), LocalDate.of(2026, 7, 23)))
    }

    // ── dueMinutesForDate ──
    @Test
    fun `非重复 单一时刻`() {
        assertEquals(listOf(540), task(scheduledStartMin = 540, repeatType = "NONE").dueMinutesForDate(LocalDate.of(2026, 7, 23)))
    }

    @Test
    fun `全天后 无具体时刻 生成空列表`() {
        // 真实 buildTask 中 allDay 时 scheduledStartMin/repeatAnchorMin 均为 null
        assertEquals(emptyList<Int>(), task(allDay = true, scheduledStartMin = null, repeatAnchorMin = null).dueMinutesForDate(LocalDate.of(2026, 7, 23)))
    }

    @Test
    fun `INTERVAL 每8小时 从0点起拆成三个时刻`() {
        assertEquals(
            listOf(0, 480, 960),
            task(repeatType = "INTERVAL", repeatIntervalHours = 8, repeatAnchorMin = 0).dueMinutesForDate(LocalDate.of(2026, 7, 23)),
        )
    }

    @Test
    fun `INTERVAL 每8小时 从1点起当日内不超过1440`() {
        // 60, 540, 1020 → 下一个 1500 越界被过滤
        assertEquals(
            listOf(60, 540, 1020),
            task(repeatType = "INTERVAL", repeatIntervalHours = 8, repeatAnchorMin = 60).dueMinutesForDate(LocalDate.of(2026, 7, 23)),
        )
    }

    @Test
    fun `DAILY 仅一个时刻`() {
        assertEquals(listOf(540), task(repeatType = "DAILY", repeatAnchorMin = 540).dueMinutesForDate(LocalDate.of(2026, 7, 23)))
    }
}
