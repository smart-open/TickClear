package com.tickclear.app.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "habit")
data class HabitEntity(
    @PrimaryKey val id: String,
    val title: String,
    val emoji: String = "",
    // 重复星期（ISO：1=周一..7=周日），CSV，如 "1,3,5"；空或含 "0" 表示每天
    val repeatDays: String = "1,2,3,4,5,6,7",
    val reminderMin: Int = -1, // -1=不提醒
    val colorIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val archived: Boolean = false,
    val orderIndex: Int = 0,
)
