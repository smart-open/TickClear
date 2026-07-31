package com.tickclear.app.domain.util

import com.tickclear.app.domain.model.Habit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DATE_FMT = SimpleDateFormat("yyyy-MM-dd", Locale.US)

/** 今天本地日期（yyyy-MM-dd）。 */
fun todayLocal(): String = DATE_FMT.format(Date())

/**
 * yyyy-MM-dd -> epoch 天数。
 * 用 [java.time.LocalDate.toEpochDay] 纯日期运算（工程已启用 core library desugaring，minSdk 24 可用）；
 * 此前的 Calendar 毫秒 /86_400_000 实现会在 DST 切换日（23h/25h 天）偏差 1 天导致 streak 误断。
 */
fun toEpochDay(date: String): Long =
    runCatching { java.time.LocalDate.parse(date).toEpochDay() }.getOrDefault(0L)

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

/** 习惯在某日期是否应打卡（按 repeatDays 星期）。空或含 "0" 视为每天。 */
fun isHabitDueOn(habit: Habit, date: java.time.LocalDate): Boolean {
    val days = habit.repeatDays.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    if (days.isEmpty() || days.contains("0")) return true
    // java.time 的 dayOfWeek.value 即 ISO（1=Mon..7=Sun），与 repeatDays 编码一致。
    return days.contains(date.dayOfWeek.value.toString())
}

/** 习惯今天是否应打卡（按 repeatDays 星期）。空或含 "0" 视为每天。 */
fun isHabitDueToday(habit: Habit): Boolean = isHabitDueOn(habit, java.time.LocalDate.now())
