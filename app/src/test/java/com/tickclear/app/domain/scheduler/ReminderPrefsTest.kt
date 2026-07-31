package com.tickclear.app.domain.scheduler

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ReminderPrefs 纯函数单测：稍后提醒档位归一化 + 音效播放判定。
 */
class ReminderPrefsTest {

    @Test
    fun `normalize clamps below min to 5`() {
        assertEquals(5, ReminderPrefs.normalizeSnoozeMin(0))
        assertEquals(5, ReminderPrefs.normalizeSnoozeMin(-10))
        assertEquals(5, ReminderPrefs.normalizeSnoozeMin(5))
    }

    @Test
    fun `normalize picks nearest of 5 15 30`() {
        assertEquals(5, ReminderPrefs.normalizeSnoozeMin(7))
        assertEquals(15, ReminderPrefs.normalizeSnoozeMin(15))
        assertEquals(15, ReminderPrefs.normalizeSnoozeMin(20))
        assertEquals(30, ReminderPrefs.normalizeSnoozeMin(30))
        assertEquals(30, ReminderPrefs.normalizeSnoozeMin(99))
    }

    @Test
    fun `shouldForceSound only for high level`() {
        // 高优先级强制响铃+震动，不受全局「声音」开关约束；中/低优先级不在此强制。
        assertEquals(true, ReminderPrefs.shouldForceSound("high"))
        assertEquals(false, ReminderPrefs.shouldForceSound("mid"))
        assertEquals(false, ReminderPrefs.shouldForceSound("low"))
    }
}
