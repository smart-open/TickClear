package com.tickclear.app.domain.util

import com.tickclear.app.domain.model.Habit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 习惯日期工具单测（纯 JVM）。
 *
 * [computeStreak] 是习惯模块唯一的连续天数事实来源（勋章、统计、首页均依赖它），
 * 此前零测试覆盖。它的两个易错点必须被钉死：
 *  1. 今天未打卡但昨天打了 → streak 应保留（当天还没到，不算断）；
 *  2. 今天与昨天都没打 → 必须归零，不能沿用更早的连续段。
 * 同时验证 DST 安全（走 LocalDate.toEpochDay，而非毫秒 /86400000）。
 */
class HabitDatesTest {

    private val today: LocalDate = LocalDate.now()

    private fun d(offsetDays: Long): String = today.minusDays(offsetDays).toString()

    // ── computeStreak ──

    @Test
    fun `空列表 streak 为 0`() {
        assertEquals(0, computeStreak(emptyList()))
    }

    @Test
    fun `今天起连续三天 streak 为 3`() {
        assertEquals(3, computeStreak(listOf(d(0), d(1), d(2))))
    }

    @Test
    fun `今天未打但昨天起连续两天 streak 为 2`() {
        // 当天尚未打卡不算断裂，否则用户每天凌晨都会看到 streak 归零
        assertEquals(2, computeStreak(listOf(d(1), d(2))))
    }

    @Test
    fun `今天与昨天都未打 streak 归零`() {
        assertEquals(0, computeStreak(listOf(d(2), d(3), d(4))))
    }

    @Test
    fun `中间断档只计最近连续段`() {
        // 今天、昨天连续，前天缺席 → 只算 2，不能把更早的 5 天并进来
        assertEquals(2, computeStreak(listOf(d(0), d(1), d(3), d(4), d(5), d(6), d(7))))
    }

    @Test
    fun `重复日期不重复计数`() {
        assertEquals(2, computeStreak(listOf(d(0), d(0), d(1), d(1))))
    }

    @Test
    fun `乱序输入结果一致`() {
        assertEquals(3, computeStreak(listOf(d(2), d(0), d(1))))
    }

    @Test
    fun `未来日期不影响今天起算的连续段`() {
        assertEquals(2, computeStreak(listOf(today.plusDays(5).toString(), d(0), d(1))))
    }

    @Test
    fun `非法日期字符串不崩溃`() {
        // toEpochDay 对非法输入兜底为 0（1970-01-01），不得抛异常拖垮习惯页
        assertEquals(1, computeStreak(listOf(d(0), "not-a-date", "")))
    }

    // ── toEpochDay ──

    @Test
    fun `toEpochDay 相邻两天恰好差 1（DST 安全）`() {
        // 旧的毫秒 /86_400_000 实现会在夏令时切换日（23h/25h）偏差 1 天
        assertEquals(1L, toEpochDay("2026-03-08") - toEpochDay("2026-03-07")) // 美区 DST 开始
        assertEquals(1L, toEpochDay("2026-11-02") - toEpochDay("2026-11-01")) // 美区 DST 结束
    }

    @Test
    fun `toEpochDay 非法输入返回 0`() {
        assertEquals(0L, toEpochDay("2026-13-45"))
        assertEquals(0L, toEpochDay(""))
    }

    @Test
    fun `todayLocal 与 LocalDate now 一致`() {
        assertEquals(LocalDate.now().toString(), todayLocal())
    }

    // ── isHabitDueOn（纯函数，完全确定性）──

    private fun habit(repeatDays: String) = Habit(id = "h", title = "喝水", repeatDays = repeatDays)

    @Test
    fun `repeatDays 为空视为每天`() {
        val monday = LocalDate.of(2026, 8, 3) // 周一
        assertTrue(isHabitDueOn(habit(""), monday))
        assertTrue(isHabitDueOn(habit(""), monday.plusDays(6)))
    }

    @Test
    fun `repeatDays 含 0 视为每天`() {
        val sunday = LocalDate.of(2026, 8, 9) // 周日
        assertTrue(isHabitDueOn(habit("0"), sunday))
    }

    @Test
    fun `工作日习惯周末不应打卡`() {
        val h = habit("1,2,3,4,5")
        assertTrue(isHabitDueOn(h, LocalDate.of(2026, 8, 3)))  // 周一
        assertTrue(isHabitDueOn(h, LocalDate.of(2026, 8, 7)))  // 周五
        assertFalse(isHabitDueOn(h, LocalDate.of(2026, 8, 8))) // 周六
        assertFalse(isHabitDueOn(h, LocalDate.of(2026, 8, 9))) // 周日
    }

    @Test
    fun `周日编码为 7 而非 0（ISO 口径）`() {
        // java.time 的 dayOfWeek.value 是 1=Mon..7=Sun，与 Calendar 的 1=Sun 不同，最易踩错
        assertTrue(isHabitDueOn(habit("7"), LocalDate.of(2026, 8, 9)))
        assertFalse(isHabitDueOn(habit("7"), LocalDate.of(2026, 8, 3)))
    }

    @Test
    fun `repeatDays 含空格仍可解析`() {
        assertTrue(isHabitDueOn(habit("1, 3 ,5"), LocalDate.of(2026, 8, 5))) // 周三
        assertFalse(isHabitDueOn(habit("1, 3 ,5"), LocalDate.of(2026, 8, 4))) // 周二
    }
}
