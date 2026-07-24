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
    fun `shouldPlaySound only when enabled and high`() {
        assertEquals(true, ReminderPrefs.shouldPlaySound(true, "high"))
        assertEquals(false, ReminderPrefs.shouldPlaySound(false, "high"))
        assertEquals(false, ReminderPrefs.shouldPlaySound(true, "mid"))
        assertEquals(false, ReminderPrefs.shouldPlaySound(false, "low"))
    }
}
