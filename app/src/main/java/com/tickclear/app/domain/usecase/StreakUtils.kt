package com.tickclear.app.domain.usecase

import com.tickclear.app.domain.util.HabitDates

/**
 * 连续天数（打卡）计算：基于一组 YYYY-MM-DD 日期字符串，从今天往回数连续天数。
 *
 * 实现统一委托给 [HabitDates.computeStreak]（DST 安全的 java.time 实现，项目单一事实来源），
 * 本对象仅保留为调用方兼容入口，避免历史调用点批量改动。
 */
object StreakUtils {
    fun computeStreak(dateStrs: List<String>): Int = HabitDates.computeStreak(dateStrs)
}
