package com.tickclear.app.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "habit_checkin", primaryKeys = ["habitId", "dateLocal"])
data class HabitCheckInEntity(
    val habitId: String,
    val dateLocal: String, // yyyy-MM-dd
    val checkedAt: Long = System.currentTimeMillis(),
)
