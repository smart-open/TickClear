package com.tickclear.app.domain.usecase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TodayListPrefs 纯函数单测：已完成数量超过阈值才显示折叠开关。
 */
class TodayListPrefsTest {

    @Test
    fun `show collapse only when done exceeds threshold`() {
        assertFalse(TodayListPrefs.shouldShowCollapseByDoneCount(0))
        assertFalse(TodayListPrefs.shouldShowCollapseByDoneCount(20))
        assertTrue(TodayListPrefs.shouldShowCollapseByDoneCount(21))
        assertTrue(TodayListPrefs.shouldShowCollapseByDoneCount(100))
    }
}
