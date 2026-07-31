package com.tickclear.app.domain.scheduler

/**
 * 提醒相关的纯函数偏好工具（JVM 可测，零依赖）。
 * - [normalizeSnoozeMin]：将任意输入归一到受支持的「稍后提醒」档位（5/15/30 分钟）。
 * - [shouldForceSound]：高优先级提醒是否强制响铃+震动（不受全局「声音」开关约束）。
 */
object ReminderPrefs {
    /** 受支持的稍后提醒档位（分钟）。 */
    val SNOOZE_OPTIONS = listOf(5, 15, 30)

    /** 默认稍后提醒时长（分钟）。 */
    const val DEFAULT_SNOOZE_MIN = 15

    /**
     * 将任意分钟数归一化为受支持档位：
     * - 小于等于最小档 → 最小档；
     * - 大于等于最大档 → 最大档；
     * - 中间 → 距其最近的档（平局取较小档）。
     */
    fun normalizeSnoozeMin(min: Int): Int {
        val opts = SNOOZE_OPTIONS
        if (min <= opts.first()) return opts.first()
        if (min >= opts.last()) return opts.last()
        return opts.minByOrNull { kotlin.math.abs(it - min) } ?: DEFAULT_SNOOZE_MIN
    }

    /**
     * 高优先级提醒是否强制响铃+震动：仅高优先级（level == "high"）为真。
     * 高优先级是用户显式「务必提醒」意图，刻意不受全局「声音」开关约束——
     * 否则与调试页「测试通知」（绕开开关）行为不一致，且违背用户对高优先级提醒的预期。
     * 中/低优先级走通知渠道重要性，统一不在此处强制声音。
     */
    fun shouldForceSound(level: String): Boolean = level == "high"
}
