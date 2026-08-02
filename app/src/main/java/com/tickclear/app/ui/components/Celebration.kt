package com.tickclear.app.ui.components

/**
 * 庆祝事件：由 ViewModel 在「打卡/完成任务」时发射，UI 层据此播放撒花+震动，
 * 并在 [medalKeys] 非空时提示新解锁的勋章。
 */
data class CelebrationEvent(
    /** 本次操作新解锁的勋章 key（对应 [com.tickclear.app.domain.model.MedalCatalog]）。 */
    val medalKeys: List<String> = emptyList(),
)
