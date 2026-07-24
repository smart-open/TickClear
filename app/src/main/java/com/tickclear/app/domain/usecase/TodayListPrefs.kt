package com.tickclear.app.domain.usecase

/**
 * 今日列表展示偏好（JVM 可测，零依赖）。
 * 当已完成任务数量超过阈值时，展示「折叠已完成」开关，避免长列表拖累体验。
 */
object TodayListPrefs {
    /** 已完成数量超过该阈值时显示「折叠已完成」按钮。 */
    const val COLLAPSE_THRESHOLD = 20

    /** 是否应显示折叠开关：已完成数量超过阈值。 */
    fun shouldShowCollapseByDoneCount(doneCount: Int): Boolean = doneCount > COLLAPSE_THRESHOLD
}
