package com.tickclear.app.domain.usecase

import java.time.LocalDate

/** 连续天数（打卡）计算：基于一组 YYYY-MM-DD 日期字符串，从今天往回数连续天数。 */
object StreakUtils {
    fun computeStreak(dateStrs: List<String>): Int {
        val set = dateStrs
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .toSet()
        if (set.isEmpty()) return 0
        var d = LocalDate.now()
        if (!set.contains(d)) {
            if (set.contains(d.minusDays(1))) d = d.minusDays(1) else return 0
        }
        var streak = 0
        while (set.contains(d)) {
            streak++
            d = d.minusDays(1)
        }
        return streak
    }
}
