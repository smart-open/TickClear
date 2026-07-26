package com.tickclear.app.domain.model

/**
 * 习惯领域模型（V2.69 习惯养成模式）。
 * 与 [com.tickclear.app.data.local.entities.HabitEntity] 字段一一对应，仓库层负责边界映射。
 */
data class Habit(
    val id: String,
    val title: String,
    val emoji: String = "",
    val repeatDays: String = "1,2,3,4,5,6,7", // CSV 星期，空/"0"=每天
    val reminderMin: Int = -1, // -1=不提醒
    val colorIndex: Int = 0,
    val createdAt: Long = 0,
    val archived: Boolean = false,
    val orderIndex: Int = 0,
)
