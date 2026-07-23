package com.tickclear.app.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "task_group")
data class TaskGroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(defaultValue = "📁") val icon: String = "📁",
    @ColumnInfo(defaultValue = "blue") val colorKey: String = "blue",
    @ColumnInfo(defaultValue = "0") val orderIndex: Int = 0,
    @ColumnInfo(defaultValue = "NONE") val repeatType: String = "NONE",
    @ColumnInfo(defaultValue = "540") val repeatAnchorMin: Int? = 540,
    @ColumnInfo(defaultValue = "0") val status: Int = 0, // 0 active, 1 paused
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val deletedAt: Long? = null,
)
