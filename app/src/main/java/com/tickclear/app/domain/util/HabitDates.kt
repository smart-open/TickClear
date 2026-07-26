package com.tickclear.app.domain.util

import com.tickclear.app.domain.model.Habit
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val DATE_FMT = SimpleDateFormat("yyyy-MM-dd", Locale.US)

/** 今天本地日期（yyyy-MM-dd）。 */
fun todayLocal(): String = DATE_FMT.format(Date())

/** yyyy-MM-dd -> epoch 天数（平台无关的纯计算，避免 java.time 的 minSdk 限制）。 */
fun toEpochDay(date: String): Long {
    val parts = date.split("-")
    if (parts.size != 3) return 0L
    val (y, m, d) = parts.map { it.toInt() }
    val cal = Calendar.getInstance().apply {
        set(y, m - 1, d, 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis / 86_400_000L
}

/**
 * 当前连续打卡天数：从今天（若已打卡）或昨天往前数连续天数；若今天与昨天都未打卡则断裂为 0。
 */
fun computeStreak(dates: List<String>): Int {
    if (dates.isEmpty()) return 0
    val days = dates.map { toEpochDay(it) }.toSet()
    val today = toEpochDay(todayLocal())
    val start = when {
        today in days -> today
        (today - 1) in days -> today - 1
        else -> return 0
    }
    var count = 0L
    var d = start
    while (d in days) {
        count++
        d -= 1
    }
    return count.toInt()
}

/** 习惯今天是否应打卡（按 repeatDays 星期）。空或含 "0" 视为每天。 */
fun isHabitDueToday(habit: Habit): Boolean {
    val days = habit.repeatDays.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    if (days.isEmpty() || days.contains("0")) return true
    val cal = Calendar.getInstance()
    val dow = cal.get(Calendar.DAY_OF_WEEK) // 1=Sun..7=Sat
    val iso = if (dow == Calendar.SUNDAY) 7 else dow - 1 // 1=Mon..7=Sun
    return days.contains(iso.toString())
}
