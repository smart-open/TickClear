package com.tickclear.app.domain.model

data class Medal(
    val key: String,
    val name: String,
    val desc: String,
    val icon: String,
)

/** 勋章进度：当前值 / 目标值；current < 0 表示当前环境无法直接计算（详情页仅展示条件）。 */
data class MedalProgress(val current: Int, val target: Int)

/** 勋章目录（静态定义；解锁态存 MedalUnlockEntity）。 */
object MedalCatalog {
    val ALL = listOf(
        Medal("FIRST_TASK", "初次清空", "完成第一个任务", "🌟"),
        Medal("STREAK_3", "三日打卡", "连续打卡 3 天", "🔥"),
        Medal("STREAK_7", "七日打卡", "连续打卡 7 天", "⚡"),
        Medal("RATE_100", "满分一日", "某日任务 100% 完成", "💯"),
        Medal("LIGHTNING", "雷厉风行", "单日完成 5 个任务", "⚡"),
        Medal("ONTIME_10", "准时达人", "完成 10 个定时任务", "⏰"),
        Medal("GROUPS_3", "分类高手", "创建 3 个任务组", "🗂️"),
        Medal("MONTH_MVP", "月度 MVP", "单月完成 30 个任务", "🏆"),
    )

    fun get(key: String): Medal? = ALL.firstOrNull { it.key == key }
}
