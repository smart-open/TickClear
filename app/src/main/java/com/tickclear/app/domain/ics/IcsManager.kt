package com.tickclear.app.domain.ics

import com.tickclear.app.domain.model.RepeatType
import com.tickclear.app.domain.model.Task
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * ICS（iCalendar, RFC 5545）导入/导出（V2.7）：手写解析与序列化，零新依赖（复用 java.time，由 coreLibraryDesugaring 支撑 minSdk 24）。
 *
 * 支持字段：
 * - 标题/备注/UID/完成态。
 * - 日期：一次性（`scheduledDate` + 起止分钟）→ `DTSTART`/`DTEND`（`VALUE=DATE` 全天 或 `YYYYMMDDTHHMMSS` 本地浮动时间）。
 * - 重复：`RRULE` 覆盖 DAILY/WEEKLY/MONTHLY（含 INTERVAL、BYDAY、BYMONTHDAY）；每 N 小时重复（INTERVAL 型）无标准 RFC 表达，降级导出为 DAILY+INTERVAL 或跳过并注明。
 * - 无日历锚点（随时任务，`scheduledDate==null` 且非重复）不写入 ICS（其在日历中无固定位置）。
 *
 * 兼容性边界：仅解析常见主流日历（Google/Apple/Outlook）产生的 VEVENT；罕见扩展属性忽略。
 */
object IcsManager {

    private val DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val WEEKDAY_CODES = mapOf(1 to "MO", 2 to "TU", 3 to "WE", 4 to "TH", 5 to "FR", 6 to "SA", 7 to "SU")

    /** 任务列表 → `.ics` 文本（含 VCALENDAR 包裹）。 */
    fun exportTasksToIcs(tasks: List<Task>): String {
        val lines = mutableListOf(
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "PRODID:-//TickClear//TickClear App//CN",
            "CALSCALE:GREGORIAN",
            "METHOD:PUBLISH",
        )
        tasks.forEach { task ->
            val effectiveDate = task.scheduledDate ?: task.repeatAnchorDate
            if (effectiveDate == null && RepeatType.fromCode(task.repeatType) == RepeatType.NONE) return@forEach
            val date = effectiveDate ?: LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            lines.add("BEGIN:VEVENT")
            lines.add("UID:${task.id}@tickclear")
            lines.add("SUMMARY:${escape(task.title)}")
            if (task.notes.isNotBlank()) lines.add("DESCRIPTION:${escape(task.notes)}")
            if (task.status == 2) lines.add("STATUS:COMPLETED")

            if (task.allDay) {
                lines.add("DTSTART;VALUE=DATE:${date.replace("-", "")}")
            } else {
                val start = task.scheduledStartMin ?: 0
                lines.add("DTSTART:${date.replace("-", "")}T${fmtMin(start)}00")
                val end = task.scheduledEndMin ?: (start + 30)
                lines.add("DTEND:${date.replace("-", "")}T${fmtMin(end)}00")
            }
            buildRrule(task)?.let { lines.add("RRULE:$it") }
            lines.add("END:VEVENT")
        }
        lines.add("END:VCALENDAR")
        return lines.joinToString("\r\n")
    }

    private fun fmtMin(min: Int): String {
        val m = min.coerceIn(0, 1439)
        return "%02d%02d".format(m / 60, m % 60)
    }

    private fun buildRrule(task: Task): String? = when (RepeatType.fromCode(task.repeatType)) {
        RepeatType.DAILY -> {
            val interval = task.repeatIntervalDays
            "FREQ=DAILY" + (if (interval != null && interval > 1) ";INTERVAL=$interval" else "")
        }
        RepeatType.INTERVAL -> {
            // 每 N 天（小时无标准表达，按天近似）。
            val interval = task.repeatIntervalDays ?: 1
            "FREQ=DAILY;INTERVAL=$interval"
        }
        RepeatType.WEEKLY -> {
            val byday = task.repeatWeekdays?.split(",")?.mapNotNull { WEEKDAY_CODES[it.toIntOrNull()] }
                ?.joinToString(",")
            "FREQ=WEEKLY" + (if (!byday.isNullOrBlank()) ";BYDAY=$byday" else "")
        }
        RepeatType.MONTHLY -> {
            val byMonthDay = task.repeatMonthDay
            "FREQ=MONTHLY" + (if (byMonthDay != null) ";BYMONTHDAY=$byMonthDay" else "")
        }
        else -> null
    }

    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")

    /** `.ics` 文本 → 任务列表（合并导入时由调用方 upsert）。 */
    fun parseIcsToTasks(ics: String): List<Task> {
        val events = ics.split("BEGIN:VEVENT").drop(1).mapNotNull { block ->
            block.substringBefore("END:VEVENT")
        }
        return events.mapNotNull { parseEvent(it) }
    }

    private fun parseEvent(block: String): Task? {
        // 归一化换行：导出用 \r\n，部分日历仅用 \n；不归一化会导致首行 UID 带前导 \r 被静默丢弃。
        val normalized = block.replace("\r\n", "\n").replace("\r", "\n")
        val get = { key: String ->
            normalized.lineSequence().firstOrNull { it.startsWith("$key:") || it.startsWith("$key;") }
                ?.substringAfter(":")
        }
        val dtStartLine = normalized.lineSequence().firstOrNull { it.startsWith("DTSTART:") || it.startsWith("DTSTART;") }
        val dtStartRaw = dtStartLine?.substringAfter(":") ?: return null
        val summary = get("SUMMARY") ?: return null
        val uid = get("UID")?.substringBefore("@") ?: ""
        val description = get("DESCRIPTION")?.let { unescape(it) } ?: ""
        val (date, startMin) = parseDt(dtStartRaw)
        val dtEndRaw = get("DTEND")
        val endMin = dtEndRaw?.let { parseDt(it).second }
        // allDay 须从 DTSTART 的参数 VALUE=DATE 判断（值本身不含该信息），否则全天任务会被误判为定时。
        val allDay = dtStartLine.contains("VALUE=DATE")
        val statusCompleted = normalized.lineSequence().any { it.startsWith("STATUS:COMPLETED") }
        val rrule = get("RRULE")
        val (repeatType, intervalDays, weekdays, monthDay) = parseRrule(rrule)

        return Task(
            id = uid.ifBlank { "ics_${date}_${startMin}_${System.nanoTime()}" },
            title = unescape(summary),
            notes = description,
            status = if (statusCompleted) 2 else 0,
            scheduledStartMin = if (allDay) null else startMin,
            scheduledEndMin = if (allDay || endMin == null) null else endMin,
            allDay = allDay,
            scheduledDate = date,
            repeatType = repeatType,
            repeatIntervalDays = intervalDays,
            repeatWeekdays = weekdays,
            repeatMonthDay = monthDay,
        )
    }

    /** 解析 DTSTART/DTEND 行，返回 (YYYY-MM-DD, 当日分钟)。 */
    private fun parseDt(raw: String): Pair<String, Int> {
        val value = raw.substringAfter(":")
        return if (raw.contains("VALUE=DATE") || value.length == 8) {
            val y = value.substring(0, 4); val m = value.substring(4, 6); val d = value.substring(6, 8)
            "$y-$m-$d" to 0
        } else {
            // YYYYMMDDTHHMMSS
            val y = value.substring(0, 4); val mo = value.substring(4, 6); val d = value.substring(6, 8)
            val hh = value.substring(9, 11).toIntOrNull() ?: 0
            val mm = value.substring(11, 13).toIntOrNull() ?: 0
            "$y-$mo-$d" to hh * 60 + mm
        }
    }

    private fun parseRrule(rrule: String?): TupleRrule {
        if (rrule == null) return TupleRrule("NONE", null, null, null)
        val map = rrule.split(";").associate { part ->
            val (k, v) = part.split("=", limit = 2).let { it[0] to it.getOrNull(1).orEmpty() }
            k to v
        }
        val freq = map["FREQ"] ?: return TupleRrule("NONE", null, null, null)
        val interval = map["INTERVAL"]?.toIntOrNull()
        val byday = map["BYDAY"]?.split(",")?.mapNotNull { code ->
            WEEKDAY_CODES.entries.firstOrNull { it.value == code }?.key
        }?.joinToString(",")
        val byMonthDay = map["BYMONTHDAY"]?.toIntOrNull()
        val repeatType = when (freq) {
            "DAILY" -> if (interval != null && interval > 1) "INTERVAL" else "DAILY"
            "WEEKLY" -> "WEEKLY"
            "MONTHLY" -> "MONTHLY"
            "YEARLY" -> "MONTHLY" // 年重复无对应，降级为月（注：主流程极少用）
            else -> "NONE"
        }
        return TupleRrule(repeatType, if (repeatType == "INTERVAL" || repeatType == "DAILY") interval else null, byday, byMonthDay)
    }

    private data class TupleRrule(
        val repeatType: String,
        val intervalDays: Int?,
        val weekdays: String?,
        val monthDay: Int?,
    )

    private fun unescape(s: String): String =
        s.replace("\\n", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")
}
