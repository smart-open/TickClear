package com.tickclear.app.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * 覆盖 [instanceDateOf] 的实例 id → 日期解析：
 * 单实例 / 子日级多实例 取第 2 段；畸形（无 '@' 或非法日期）回退今日。
 * 撤销完成若用错日期去删 CompletionLog，会留下统计脏数据，故此处需锁死。
 */
class UncompleteTaskUseCaseTest {

    @Test
    fun `单实例 id 解析出日期`() {
        assertEquals("2026-08-11", instanceDateOf("task-uuid@2026-08-11"))
    }

    @Test
    fun `子日级多实例 id 解析出日期`() {
        assertEquals("2026-08-11", instanceDateOf("task-uuid@2026-08-11@570"))
    }

    @Test
    fun `无 at 分隔符回退今日`() {
        assertEquals(todayStr(), instanceDateOf("task-uuid-without-date"))
    }

    @Test
    fun `非法日期段回退今日`() {
        assertEquals(todayStr(), instanceDateOf("task-uuid@not-a-date"))
    }

    private fun todayStr() = LocalDate.now().toString()
}
