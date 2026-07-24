package com.tickclear.app.domain.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * V2.15 本地规则 NLU 解析单测：覆盖工作日 / 周范围 / 多周几 / 提前量 等新增口语。
 * 纯 JVM，无 Android 依赖，直接验证 [TaskIntentParser]，是离线意图解析的回归屏障。
 */
class TaskIntentParserTest {

    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    @Test
    fun `每天 解析为 DAILY`() {
        val p = TaskIntentParser.parse("提醒我每天9点开会")!!
        assertEquals("DAILY", p.repeatType)
        assertEquals(540, p.minute)
    }

    @Test
    fun `单周几 保持 WEEKLY 单值`() {
        val p = TaskIntentParser.parse("帮我记周一9点健身")!!
        assertEquals("WEEKLY", p.repeatType)
        assertEquals("1", p.weekdays)
        assertEquals(540, p.minute)
    }

    @Test
    fun `工作日 解析为 1-5`() {
        val p = TaskIntentParser.parse("记一下工作日早上8点30分晨会")!!
        assertEquals("WEEKLY", p.repeatType)
        assertEquals("1,2,3,4,5", p.weekdays)
        assertEquals(510, p.minute)
    }

    @Test
    fun `周范围 解析为闭区间`() {
        val p = TaskIntentParser.parse("加个任务 周一到周五 晚上7点 复盘")!!
        assertEquals("WEEKLY", p.repeatType)
        assertEquals("1,2,3,4,5", p.weekdays)
        assertEquals(1140, p.minute) // 19:00
    }

    @Test
    fun `多周几 解析为去重排序 csv`() {
        val p = TaskIntentParser.parse("建个任务 周一三五 上午10点 周会")!!
        assertEquals("WEEKLY", p.repeatType)
        assertEquals("1,3,5", p.weekdays)
        assertEquals(600, p.minute)
    }

    @Test
    fun `提前N分钟 解析为提醒偏移`() {
        val p = TaskIntentParser.parse("提醒我提前15分钟 明天14点 面试")!!
        assertEquals(15, p.reminderOffsetMin)
        assertEquals(840, p.minute)
        assertEquals(LocalDate.now().plusDays(1).format(fmt), p.dateStr)
    }

    @Test
    fun `N分钟前 同样解析为偏移`() {
        val p = TaskIntentParser.parse("提前30分钟提醒我后天9点体检")!!
        assertEquals(30, p.reminderOffsetMin)
        assertEquals(540, p.minute)
        assertEquals(LocalDate.now().plusDays(2).format(fmt), p.dateStr)
    }

    @Test
    fun `非任务语句 返回 null`() {
        assertNull(TaskIntentParser.parse("今天天气不错"))
    }

    @Test
    fun `标题 去除时间词`() {
        val p = TaskIntentParser.parse("提醒我明天9点交报告")!!
        assertTrue(p.title.contains("报告"))
    }
}
