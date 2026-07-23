package com.tickclear.app.domain.assistant

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 轻量中文意图解析：将「提醒我明天9点开会」之类的口语转为结构化建任务参数。
 * 仅覆盖常见模式，作为 Mock 模式下 MCP create_task 的参数来源。
 */
object TaskIntentParser {

    data class ParsedTask(
        val title: String,
        val dateStr: String?, // 一次性任务日期 YYYY-MM-DD；重复任务为 null
        val minute: Int?, // 0..1439
        val repeatType: String, // NONE / DAILY / WEEKLY
        val weekdays: String?, // 周重复 csv "1..7"
    )

    private val TRIGGERS = listOf(
        "提醒我", "帮我记", "记一下", "记一个", "加个任务", "加任务",
        "创建任务", "新建任务", "建个任务", "任务：",
    )

    private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun parse(input: String): ParsedTask? {
        val text = input.trim()
        if (text.isEmpty()) return null

        val isTask = TRIGGERS.any { text.contains(it) } ||
            text.contains("提醒") || text.contains("开会") || text.contains("会议")
        if (!isTask) return null

        // 去除触发词，得到候选正文
        var body = text
        for (t in TRIGGERS) body = body.replace(t, " ")
        // 去除「提醒/开会/会议」等残留动词，便于取标题
        body = body.replace("提醒", " ").replace("开会", " ").replace("会议", " ")

        // ── 重复 / 日期 ──
        var repeatType = "NONE"
        var weekdays: String? = null
        var dayOffset = 0
        when {
            text.contains("每天") || text.contains("每日") -> repeatType = "DAILY"
            else -> {
                val wd = parseWeekday(text)
                if (wd != null) {
                    repeatType = "WEEKLY"
                    weekdays = wd.toString()
                } else {
                    dayOffset = when {
                        text.contains("大后天") -> 3
                        text.contains("后天") -> 2
                        text.contains("明天") -> 1
                        text.contains("今天") -> 0
                        else -> 0
                    }
                }
            }
        }

        // ── 时间 ──
        val (hour, minute) = parseClock(text)
        var h = hour
        if (h != null) {
            h = applyMeridiem(text, h)
        }

        // 从 body 中剥离时间相关词，得到标题
        var title = body
        title = title.replace(Regex("""\d{1,2}[:：]\d{2}"""), " ")
        title = title.replace(Regex("""\d{1,2}点\d{1,2}分"""), " ")
        title = title.replace(Regex("""\d{1,2}点"""), " ")
        title = title.replace(Regex("""(今天|明天|后天|大后天|每天|每日|周[一二三四五六日天]|星期[一二三四五六日天]|上午|下午|早上|中午|晚上|凌晨|傍晚|点|分)"""), " ")
        title = title.replace(Regex("""\s+"""), " ").trim()
        if (title.isEmpty()) title = "新任务"

        val dateStr = if (repeatType == "NONE") {
            LocalDate.now().plusDays(dayOffset.toLong()).format(DATE_FMT)
        } else {
            null
        }

        return ParsedTask(
            title = title,
            dateStr = dateStr,
            minute = if (h != null) h * 60 + (minute ?: 0) else null,
            repeatType = repeatType,
            weekdays = weekdays,
        )
    }

    private fun parseWeekday(text: String): Int? {
        val map = mapOf(
            "周一" to 1, "星期一" to 1, "周二" to 2, "星期二" to 2,
            "周三" to 3, "星期三" to 3, "周四" to 4, "星期四" to 4,
            "周五" to 5, "星期五" to 5, "周六" to 6, "星期六" to 6,
            "周日" to 7, "周天" to 7, "星期日" to 7, "星期天" to 7,
        )
        for ((k, v) in map) if (text.contains(k)) return v
        return null
    }

    private fun parseClock(text: String): Pair<Int?, Int?> {
        // HH:MM
        Regex("""(\d{1,2})[:：](\d{2})""").find(text)?.let {
            val (h, m) = it.destructured
            return h.toIntOrNull() to m.toIntOrNull()
        }
        // HH点MM分 / HH点
        Regex("""(\d{1,2})点(?:(\d{1,2})分)?""").find(text)?.let {
            val (h, m) = it.destructured
            return h.toIntOrNull() to m.toIntOrNull()?.let { if (it > 59) null else it }
        }
        return null to null
    }

    private fun applyMeridiem(text: String, hour: Int): Int {
        val lower = when {
            text.contains("中午") -> 12
            text.contains("下午") || text.contains("晚上") || text.contains("傍晚") ->
                if (hour < 12) hour + 12 else hour
            text.contains("凌晨") -> if (hour == 12) 0 else hour
            else -> hour // 上午/早上/无修饰：保持
        }
        return if (lower > 23) 23 else lower
    }

    fun weekdayName(dow: DayOfWeek): String = when (dow) {
        DayOfWeek.MONDAY -> "周一"
        DayOfWeek.TUESDAY -> "周二"
        DayOfWeek.WEDNESDAY -> "周三"
        DayOfWeek.THURSDAY -> "周四"
        DayOfWeek.FRIDAY -> "周五"
        DayOfWeek.SATURDAY -> "周六"
        DayOfWeek.SUNDAY -> "周日"
    }
}
