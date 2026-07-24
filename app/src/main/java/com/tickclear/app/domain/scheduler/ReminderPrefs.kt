package com.tickclear.app.domain.scheduler

/**
 * 提醒相关的纯函数偏好工具（JVM 可测，零依赖）。
 * - [normalizeSnoozeMin]：将任意输入归一到受支持的「稍后提醒」档位（5/15/30 分钟）。
 * - [shouldPlaySound]：仅在开启音效且高优先级时播放声音/震动。
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
     * 是否播放声音/震动：仅高优先级（level == "high"）且用户开启音效（soundEnabled）时为真。
     * 中/低优先级走通知渠道重要性，统一不在此处强制声音。
     */
    fun shouldPlaySound(soundEnabled: Boolean, level: String): Boolean =
        soundEnabled && level == "high"
}
