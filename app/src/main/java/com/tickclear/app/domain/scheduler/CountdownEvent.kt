package com.tickclear.app.domain.scheduler

/**
 * 重要日子倒计时事件（V2.9++ 扩展通知能力）。
 *
 * 序列化格式（每行一条，'|' 分隔）：
 *   id|targetEpochMs|notify|advanceDays|daily|hour|minute|name
 * 旧格式（name|epoch）仍兼容解析：补默认通知设置并派生稳定 id，避免每次打开随机变化导致闹钟重复。
 *
 * 字段含义：
 * - notify：是否开启到期提醒。
 * - advanceDays：提前多少天开始提醒（0=仅到期当天）。
 * - daily：true=提前期内每天提醒；false=仅「提前当天 + 到期当天」两次。
 * - hour/minute：每日提醒触发时刻（本地时区）。
 */
data class CountdownEvent(
    val id: String,
    val name: String,
    val targetEpochMs: Long,
    val notify: Boolean = false,
    val advanceDays: Int = 1,
    val daily: Boolean = false,
    val hour: Int = 9,
    val minute: Int = 0,
) {
    companion object {
        private const val SEP = '|'

        fun serialize(list: List<CountdownEvent>): String =
            list.joinToString("\n") { e ->
                "${e.id}$SEP${e.targetEpochMs}$SEP${e.notify}$SEP${e.advanceDays}" +
                    "$SEP${e.daily}$SEP${e.hour}$SEP${e.minute}$SEP${e.name}"
            }

        fun parse(line: String): CountdownEvent? {
            val parts = line.split(SEP)
            return if (parts.size >= 8) {
                val ms = parts[1].toLongOrNull() ?: return null
                CountdownEvent(
                    id = parts[0],
                    targetEpochMs = ms,
                    notify = parts[2] == "true",
                    advanceDays = parts[3].toIntOrNull()?.coerceIn(0, 365) ?: 1,
                    daily = parts[4] == "true",
                    hour = parts[5].toIntOrNull()?.coerceIn(0, 23) ?: 9,
                    minute = parts[6].toIntOrNull()?.coerceIn(0, 59) ?: 0,
                    // name 置于末尾，即便含 '|' 也能完整还原
                    name = parts.subList(7, parts.size).joinToString(SEP.toString()),
                )
            } else {
                val i = line.indexOf(SEP)
                if (i <= 0) return null
                val name = line.substring(0, i)
                val ms = line.substring(i + 1).toLongOrNull() ?: return null
                CountdownEvent(deriveId(name, ms), name, ms)
            }
        }

        /** 从名称与时刻派生稳定 id（旧数据无 id 时使用）。 */
        fun deriveId(name: String, epochMs: Long): String =
            "cd_${kotlin.math.abs(name.hashCode())}_$epochMs".replace('-', '0')
    }
}
