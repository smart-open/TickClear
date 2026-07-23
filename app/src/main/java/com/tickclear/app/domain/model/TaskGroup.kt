package com.tickclear.app.domain.model

/**
 * 任务组领域模型（与 [com.tickclear.app.data.local.entities.TaskGroupEntity] 解耦）。
 * 字段与实体一一对应，仓库边界负责 entity↔domain 映射。
 */
data class TaskGroup(
    val id: String,
    val name: String,
    val icon: String = "📁",
    val colorKey: String = "blue",
    val orderIndex: Int = 0,
    val repeatType: String = "NONE",
    val repeatAnchorMin: Int? = 540,
    val status: Int = 0, // 0 active, 1 paused
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
)
