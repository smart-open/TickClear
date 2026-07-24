package com.tickclear.app.domain.ics

import com.tickclear.app.domain.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ICS（RFC5545）导入/导出往返测试（V2.22）：覆盖一次性 / 全天 / 日 / 周 / 月 重复、
 * 特殊字符转义、完成态、非法输入容错，以及 UID 在解析后仍保留（验证拆分 BEGIN:VEVENT
 * 残留 \r 不再吞掉 UID 的修复点）。纯 JVM、零 Android 依赖。
 */
class IcsManagerTest {

    @Test
    fun `一次性任务 导出再解析 保留标题日期与开始分钟`() {
        val task = Task(id = "t1", title = "开会", scheduledDate = "2026-07-24", scheduledStartMin = 540, scheduledEndMin = 570)
        val ics = IcsManager.exportTasksToIcs(listOf(task))
        val parsed = IcsManager.parseIcsToTasks(ics)
        assertEquals(1, parsed.size)
        val p = parsed[0]
        assertEquals("t1", p.id)
        assertEquals("开会", p.title)
        assertEquals("2026-07-24", p.scheduledDate)
        assertEquals(540, p.scheduledStartMin)
        assertEquals(0, p.status)
    }

    @Test
    fun `全天任务 解析后 allDay 为 true 且不含分钟`() {
        val task = Task(id = "t2", title = "生日", scheduledDate = "2026-08-01", allDay = true)
        val ics = IcsManager.exportTasksToIcs(listOf(task))
        val p = IcsManager.parseIcsToTasks(ics).first()
        assertEquals(true, p.allDay)
        assertEquals(null, p.scheduledStartMin)
        assertEquals("2026-08-01", p.scheduledDate)
    }

    @Test
    fun `DAILY 含间隔 导出再解析 转为 INTERVAL`() {
        val task = Task(id = "t3", title = "吃药", repeatType = "DAILY", repeatIntervalDays = 2, scheduledDate = "2026-07-24", scheduledStartMin = 480)
        val ics = IcsManager.exportTasksToIcs(listOf(task))
        val p = IcsManager.parseIcsToTasks(ics).first()
        assertEquals("INTERVAL", p.repeatType)
        assertEquals(2, p.repeatIntervalDays)
    }

    @Test
    fun `WEEKLY 含多周几 往返一致`() {
        val task = Task(id = "t4", title = "健身", repeatType = "WEEKLY", repeatWeekdays = "1,3,5", scheduledDate = "2026-07-24", scheduledStartMin = 600)
        val ics = IcsManager.exportTasksToIcs(listOf(task))
        val p = IcsManager.parseIcsToTasks(ics).first()
        assertEquals("WEEKLY", p.repeatType)
        assertEquals("1,3,5", p.repeatWeekdays)
    }

    @Test
    fun `MONTHLY 含月日 往返一致`() {
        val task = Task(id = "t5", title = "缴费", repeatType = "MONTHLY", repeatMonthDay = 15, scheduledDate = "2026-07-24", scheduledStartMin = 540)
        val ics = IcsManager.exportTasksToIcs(listOf(task))
        val p = IcsManager.parseIcsToTasks(ics).first()
        assertEquals("MONTHLY", p.repeatType)
        assertEquals(15, p.repeatMonthDay)
    }

    @Test
    fun `标题含分号逗号换行 转义后还原`() {
        val title = "会议;讨论,待办\n备注"
        val task = Task(id = "t6", title = title, scheduledDate = "2026-07-24", scheduledStartMin = 540)
        val ics = IcsManager.exportTasksToIcs(listOf(task))
        assertTrue("导出的 ICS 应对分号做转义", ics.contains("\\;"))
        val p = IcsManager.parseIcsToTasks(ics).first()
        assertEquals(title, p.title)
    }

    @Test
    fun `完成态 STATUS 往返保持`() {
        val task = Task(id = "t7", title = "done", status = 2, scheduledDate = "2026-07-24", scheduledStartMin = 540)
        val ics = IcsManager.exportTasksToIcs(listOf(task))
        val p = IcsManager.parseIcsToTasks(ics).first()
        assertEquals(2, p.status)
    }

    @Test
    fun `非法 ICS 返回空列表不崩溃`() {
        assertTrue(IcsManager.parseIcsToTasks("这不是ics").isEmpty())
        assertTrue(IcsManager.parseIcsToTasks("BEGIN:VEVENT\n无字段\nEND:VEVENT").isEmpty())
    }

    @Test
    fun `UID 中 task id 在解析后保留`() {
        // 验证拆分 BEGIN:VEVENT 残留的 \r 不再吞掉 UID（修复点）。
        val task = Task(id = "my-task-123", title = "x", scheduledDate = "2026-07-24", scheduledStartMin = 540)
        val ics = IcsManager.exportTasksToIcs(listOf(task))
        val p = IcsManager.parseIcsToTasks(ics).first()
        assertEquals("my-task-123", p.id)
    }
}
