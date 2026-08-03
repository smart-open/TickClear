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
        val reminderOffsetMin: Int? = null, // 提前 N 分钟提醒（null/0=准时；>0 提前）
        val tags: List<String> = emptyList(), // 解析出的 #标签（如「#工作 #健康」）
        val notes: String? = null, // 解析出的备注（「备注：xxx」/「说明：xxx」）
        val level: String? = null, // 优先级：high/low/null（来自「重要」/「低优先级」等）
    )

    /** 中文星期单字 → ISO 数字（1=周一 … 7=周日），供单/多/范围匹配复用（正则捕获组仅含单字）。 */
    private val WD_MAP = mapOf(
        "一" to 1, "二" to 2, "三" to 3, "四" to 4,
        "五" to 5, "六" to 6, "日" to 7, "天" to 7,
    )

    private val TRIGGERS = listOf(
        "提醒我", "帮我记", "记一下", "记一个", "加个任务", "加任务",
        "创建任务", "新建任务", "建个任务", "任务：",
    )

    private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    // 正则提升为常量，避免每次 parse/parseClock 调用重复编译（性能）。
    private val RE_HHMM = Regex("""\d{1,2}[:：]\d{2}""")
    private val RE_HH_MM_CN = Regex("""\d{1,2}\s*点\s*\d{1,2}\s*分""")
    private val RE_HH_CN = Regex("""\d{1,2}\s*点""")
    private val RE_TIME_WORDS = Regex("""(今天|明天|后天|大后天|每天|每日|周[一二三四五六日天]|星期[一二三四五六日天]|上午|下午|早上|中午|晚上|凌晨|傍晚|点|分)""")
    private val RE_WS = Regex("""\s+""")
    private val RE_CLOCK_HHMM = Regex("""(\d{1,2})[:：](\d{2})""")
    private val RE_CLOCK_CN = Regex("""(\d{1,2})\s*点(?:\s*(\d{1,2})\s*分)?""")
    // 提前量：「提前15分钟」或「15分钟前(提醒)」
    private val RE_OFFSET = Regex("""提前\s*(\d+)\s*分钟|(\d+)\s*分钟前""")
    // 标签：#工作 / #健康（支持中英文与数字）
    private val RE_TAG = Regex("""#([\w\u4e00-\u9fa5]+)""")
    // 备注：备注：xxx / 说明：xxx / note：xxx（捕获到句末）
    private val RE_NOTE = Regex("""(?:备注|说明|note|memo)[：:]\s*(.+)""", RegexOption.IGNORE_CASE)
    // 优先级词（整词匹配，避免误伤「高/急」单字）。
    private val HIGH_WORDS = listOf("重要", "紧急", "加急", "高优先级", "优先处理")
    private val LOW_WORDS = listOf("低优先级", "不重要", "不急", "普通优先级")

    fun parse(input: String): ParsedTask? {
        val text = input.trim()
        if (text.isEmpty()) return null

        val isTask = TRIGGERS.any { text.contains(it) } ||
            text.contains("提醒") || text.contains("开会") || text.contains("会议")
        if (!isTask) return null

        // 先抽取结构化附加字段（标签 / 备注 / 优先级），随后从标题中剥离对应词。
        val tags = parseTags(text)
        val notes = parseNotes(text)
        val level = parseLevel(text)

        // 去除触发词，得到候选正文
        var body = text
        for (t in TRIGGERS) body = body.replace(t, " ")
        // 去除「提醒/开会/会议」等残留动词，便于取标题
        body = body.replace("提醒", " ").replace("开会", " ").replace("会议", " ")

        // ── 重复 / 日期 ──
        // 归一 星期→周，仅用于星期提取，不影响标题。
        val wkText = text.replace("星期", "周")
        var repeatType = "NONE"
        var weekdays: String? = null
        var dayOffset = 0
        when {
            text.contains("每天") || text.contains("每日") -> repeatType = "DAILY"
            else -> {
                val wd = parseWeekdays(wkText)
                if (wd != null) {
                    repeatType = "WEEKLY"
                    weekdays = wd
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

        // ── 提前量（提醒偏移）──
        var reminderOffsetMin: Int? = null
        RE_OFFSET.find(text)?.let {
            val g1 = it.groupValues[1]
            val g2 = it.groupValues[2]
            reminderOffsetMin = (if (g1.isNotEmpty()) g1 else g2).toIntOrNull()
        }

        // ── 时间 ──
        val (hour, minute) = parseClock(text)
        var h = hour
        if (h != null) {
            h = applyMeridiem(text, h)
        }

        // 从 body 中剥离时间相关词，得到标题
        var title = body
        title = title.replace(RE_HHMM, " ")
        title = title.replace(RE_HH_MM_CN, " ")
        title = title.replace(RE_HH_CN, " ")
        title = title.replace(RE_TIME_WORDS, " ")
        title = title.replace(RE_OFFSET, " ")
        title = title.replace(RE_TAG, " ")
        title = title.replace(RE_NOTE, " ")
        for (w in HIGH_WORDS + LOW_WORDS) title = title.replace(w, "")
        title = title.replace(RE_WS, " ").trim()
        // 标题为空时不在此兜底中文，交由消费端（MockXiaozhiTransport）用 string 资源默认（task_default_title）。

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
            reminderOffsetMin = reminderOffsetMin,
            tags = tags,
            notes = notes,
            level = level,
        )
    }

    /** 提取 #标签（去重保序）。 */
    private fun parseTags(text: String): List<String> =
        RE_TAG.findAll(text).map { it.groupValues[1] }.toSet().toList()

    /** 提取备注（「备注：xxx」/「说明：xxx」/「note：xxx」），无则返回 null。 */
    private fun parseNotes(text: String): String? =
        RE_NOTE.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

    /** 提取优先级：high（重要/紧急等）/ low（低优先级等）/ null。 */
    private fun parseLevel(text: String): String? =
        when {
            HIGH_WORDS.any { text.contains(it) } -> "high"
            LOW_WORDS.any { text.contains(it) } -> "low"
            else -> null
        }

    /**
     * 解析周重复模式（V2.15 增强）：
     * - 「工作日」→ 1,2,3,4,5
     * - 「周一到周五 / 周一至周五」范围 → 闭区间 csv
     * - 「周一三五 / 周一、三、五」多个 → 去重排序 csv
     * 单周几（如「周一」）返回 "1"，与旧行为一致。
     */
    private fun parseWeekdays(wkText: String): String? {
        if (wkText.contains("工作日")) return "1,2,3,4,5"
        val rangeRe = Regex("""周([一二三四五六日天])\s*(?:到|至|-)\s*周([一二三四五六日天])""")
        rangeRe.find(wkText)?.let {
            val (a, b) = it.destructured
            val va = WD_MAP[a] ?: return@let null
            val vb = WD_MAP[b] ?: return@let null
            val lo = minOf(va, vb)
            val hi = maxOf(va, vb)
            return (lo..hi).joinToString(",")
        }
        // 多周几：支持「周一三五」连写与「周一、周三、周五」分写，取每个星期单字。
        val multiRe = Regex("""周((?:[一二三四五六日天][、,，]?)+)""")
        val all = multiRe.findAll(wkText)
            .flatMap { m -> m.groupValues[1].toCharArray().asSequence().mapNotNull { WD_MAP[it.toString()] } }
            .toSet().toList().sorted()
        if (all.isNotEmpty()) return all.joinToString(",")
        return null
    }

    private fun parseClock(text: String): Pair<Int?, Int?> {
        // HH:MM
        RE_CLOCK_HHMM.find(text)?.let {
            val (h, m) = it.destructured
            return h.toIntOrNull() to m.toIntOrNull()
        }
        // HH点MM分 / HH点
        RE_CLOCK_CN.find(text)?.let {
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

    // ══════════ V2.18 多轮任务编辑：对「刚创建的任务」的后续指令解析 ══════════

    /** 编辑意图（对最近一次创建的任务）。 */
    sealed interface ParsedEdit {
        /** 改时间：dateStr 为 null 表示不改日期，minute 为 null 表示只改日期。 */
        data class ChangeTime(val dateStr: String?, val minute: Int?) : ParsedEdit

        /** 改重复：NONE / DAILY / WEEKLY(+weekdays csv)。 */
        data class ChangeRepeat(val repeatType: String, val weekdays: String?) : ParsedEdit

        /** 取消（软删除）刚创建的任务。 */
        data object Cancel : ParsedEdit
    }

    private val RE_EDIT_TIME_HINT = Regex("""改到|改成|改时间|推迟到|提前到|换到|调到|挪到""")
    // 与 OfflineCommandRecognizer.DELETE_WORDS 收敛：删除类动词保持一致，避免同一句口语
    // 在「多轮编辑」路径（此处）与「离线指令」路径（OfflineCommandRecognizer）被判成不同动作。
    private val RE_EDIT_CANCEL = Regex("""取消|删掉|删了|删除|不要了|算了|移除|去掉|清除|干掉""")

    /**
     * 解析对上一个任务的编辑指令；无法识别返回 null（调用方回落到正常对话）。
     * 优先级：取消 > 改重复 > 改时间（「改成每天」须先于时间分支匹配）。
     */
    fun parseEdit(input: String): ParsedEdit? {
        val text = input.trim()
        if (text.isEmpty() || text.length > 30) return null // 长句大概率是新话题

        if (RE_EDIT_CANCEL.containsMatchIn(text) &&
            (text.contains("任务") || text.contains("它") || text.contains("这个") ||
                text.contains("那个") || text.contains("刚才") || text.length <= 6)
        ) {
            return ParsedEdit.Cancel
        }

        val hasEditHint = RE_EDIT_TIME_HINT.containsMatchIn(text)
        if (!hasEditHint) return null

        // 改重复：改成每天 / 改成工作日 / 改成每周一三五 / 改成不重复
        if (text.contains("不重复") || text.contains("只一次")) return ParsedEdit.ChangeRepeat("NONE", null)
        if (text.contains("每天") || text.contains("每日")) return ParsedEdit.ChangeRepeat("DAILY", null)
        val wkText = text.replace("星期", "周")
        parseWeekdays(wkText)?.let { return ParsedEdit.ChangeRepeat("WEEKLY", it) }

        // 改时间：日期偏移 + 时刻，至少解析出其一
        val dayOffset = when {
            text.contains("大后天") -> 3
            text.contains("后天") -> 2
            text.contains("明天") -> 1
            text.contains("今天") -> 0
            else -> null
        }
        val (hour, minute) = parseClock(text)
        val h = hour?.let { applyMeridiem(text, it) }
        val min = if (h != null) h * 60 + (minute ?: 0) else null
        if (dayOffset == null && min == null) return null
        val dateStr = dayOffset?.let { LocalDate.now().plusDays(it.toLong()).format(DATE_FMT) }
        return ParsedEdit.ChangeTime(dateStr, min)
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
