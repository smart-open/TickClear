package com.tickclear.app.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "completion_log",
    indices = [
        Index("dateLocal"),
        Index(value = ["taskId", "dateLocal"], unique = true),
    ],
)
data class CompletionLogEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val completedAt: Long,
    val dateLocal: String, // yyyy-MM-dd
    @ColumnInfo(defaultValue = "manual") val source: String = "manual", // manual/voice/llm/xiaozhi
)
