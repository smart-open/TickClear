package com.tickclear.app.domain.assistant

import com.tickclear.app.domain.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * OfflineCommandRecognizer 纯函数单测（V2.42）。
 * 覆盖「动作词 + 任务名」两种语序、连接词剥离、无任务名、未识别、以及 matchTask 匹配优先级。
 */
class OfflineCommandRecognizerTest {

    @Test
    fun `暂停 + 任务名 解析为 Pause 带关键词`() {
        val cmd = OfflineCommandRecognizer.parse("暂停买菜")
        assertEquals(OfflineCommand.Pause("买菜"), cmd)
    }

    @Test
    fun `任务名在前 动作在后 也能解析`() {
        val cmd = OfflineCommandRecognizer.parse("买菜暂停")
        assertEquals(OfflineCommand.Pause("买菜"), cmd)
    }

    @Test
    fun `连接词被剥离 仅留任务名`() {
        val cmd = OfflineCommandRecognizer.parse("帮我把买菜暂停一下")
        assertEquals(OfflineCommand.Pause("买菜"), cmd)
    }

    @Test
    fun `启用 + 任务名 解析为 Resume`() {
        val cmd = OfflineCommandRecognizer.parse("启用开会")
        assertEquals(OfflineCommand.Resume("开会"), cmd)
    }

    @Test
    fun `继续 + 任务名 解析为 Resume`() {
        val cmd = OfflineCommandRecognizer.parse("继续健身")
        assertEquals(OfflineCommand.Resume("健身"), cmd)
    }

    @Test
    fun `删除 + 任务名 解析为 Delete`() {
        val cmd = OfflineCommandRecognizer.parse("删除垃圾邮件任务")
        assertEquals(OfflineCommand.Delete("垃圾邮件任务"), cmd)
    }

    @Test
    fun `仅有动作词 无任务名 keyword 为 null`() {
        val cmd = OfflineCommandRecognizer.parse("暂停")
        assertEquals(OfflineCommand.Pause(null), cmd)
    }

    @Test
    fun `空文本 解析为 Unknown`() {
        assertEquals(OfflineCommand.Unknown, OfflineCommandRecognizer.parse(""))
        assertEquals(OfflineCommand.Unknown, OfflineCommandRecognizer.parse("   "))
    }

    @Test
    fun `不含动作词 解析为 Unknown`() {
        assertEquals(OfflineCommand.Unknown, OfflineCommandRecognizer.parse("请帮我提醒明天开会"))
        assertEquals(OfflineCommand.Unknown, OfflineCommandRecognizer.parse("你好"))
    }

    @Test
    fun `matchTask 精确标题优先匹配`() {
        val tasks = listOf(
            Task(id = "1", title = "买菜"),
            Task(id = "2", title = "买水果"),
        )
        assertEquals("1", OfflineCommandRecognizer.matchTask(tasks, "买菜")?.id)
    }

    @Test
    fun `matchTask 前缀匹配`() {
        val tasks = listOf(Task(id = "1", title = "买菜"))
        assertEquals("1", OfflineCommandRecognizer.matchTask(tasks, "买")?.id)
    }

    @Test
    fun `matchTask 包含匹配忽略大小写`() {
        val tasks = listOf(Task(id = "2", title = "Buy Milk"))
        assertEquals("2", OfflineCommandRecognizer.matchTask(tasks, "milk")?.id)
        assertEquals("2", OfflineCommandRecognizer.matchTask(tasks, "BUY")?.id)
    }

    @Test
    fun `matchTask 跳过软删任务`() {
        val tasks = listOf(
            Task(id = "3", title = "已删", deletedAt = 1L),
            Task(id = "1", title = "买菜"),
        )
        assertNull(OfflineCommandRecognizer.matchTask(tasks, "已删"))
        assertEquals("1", OfflineCommandRecognizer.matchTask(tasks, "买菜")?.id)
    }

    @Test
    fun `matchTask 空关键词返回 null`() {
        val tasks = listOf(Task(id = "1", title = "买菜"))
        assertNull(OfflineCommandRecognizer.matchTask(tasks, null))
        assertNull(OfflineCommandRecognizer.matchTask(tasks, ""))
    }
}
