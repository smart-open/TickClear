package com.tickclear.app.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 任务实例：重复任务不进入终态，每次「发生」由调度/视图懒生成一个实例，
 * 实例才拥有完成态。CompletionLog 由已完成实例历史派生。
 *
 * 主键 id = "${taskId}@${dueDateLocal}"，保证同一天同一任务唯一，配合 upsert IGNORE 去重。
 */
@Entity(
    tableName = "task_instance",
    indices = [
        Index("taskId"),
        Index("dueDateLocal"),
        Index(value = ["taskId", "dueDateLocal"], unique = true),
    ],
)
data class TaskInstanceEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(defaultValue = "") val taskId: String,
    @ColumnInfo(defaultValue = "") val dueDateLocal: String, // YYYY-MM-DD
    @ColumnInfo(defaultValue = "0") val dueMinute: Int? = null, // 当日生效分钟（开始）
    @ColumnInfo(defaultValue = "0") val status: Int = 0, // 0 active, 2 completed（实例无 paused）
    val completedAt: Long? = null,
    @ColumnInfo(defaultValue = "manual") val source: String = "manual", // manual/voice/llm/xiaozhi
    val createdAt: Long = System.currentTimeMillis(),
)
