package com.tickclear.app.domain.model

import androidx.annotation.StringRes
import com.tickclear.app.R

data class Medal(
    val key: String,
    @StringRes val nameRes: Int,
    @StringRes val descRes: Int,
    val icon: String,
)

/** 勋章进度：当前值 / 目标值；current < 0 表示当前环境无法直接计算（详情页仅展示条件）。 */
data class MedalProgress(val current: Int, val target: Int)

/** 勋章目录（静态定义；解锁态存 MedalUnlockEntity）。名称/描述经 strings.xml 本地化。 */
object MedalCatalog {
    val ALL = listOf(
        Medal("FIRST_TASK", R.string.medal_first_task_name, R.string.medal_first_task_desc, "🌟"),
        Medal("STREAK_3", R.string.medal_streak_3_name, R.string.medal_streak_3_desc, "🔥"),
        Medal("STREAK_7", R.string.medal_streak_7_name, R.string.medal_streak_7_desc, "⚡"),
        Medal("RATE_100", R.string.medal_rate_100_name, R.string.medal_rate_100_desc, "💯"),
        Medal("LIGHTNING", R.string.medal_lightning_name, R.string.medal_lightning_desc, "⚡"),
        Medal("ONTIME_10", R.string.medal_ontime_10_name, R.string.medal_ontime_10_desc, "⏰"),
        Medal("GROUPS_3", R.string.medal_groups_3_name, R.string.medal_groups_3_desc, "🗂️"),
        Medal("MONTH_MVP", R.string.medal_month_mvp_name, R.string.medal_month_mvp_desc, "🏆"),
    )

    fun get(key: String): Medal? = ALL.firstOrNull { it.key == key }
}
