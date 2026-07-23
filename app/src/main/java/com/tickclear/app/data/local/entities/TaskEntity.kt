package com.tickclear.app.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "task",
    indices = [Index("groupId"), Index("deletedAt"), Index("scheduledStartMin")],
    foreignKeys = [ForeignKey(
        entity = TaskGroupEntity::class,
        parentColumns = ["id"],
        childColumns = ["groupId"],
        onDelete = ForeignKey.NO_ACTION,
    )],
)
data class TaskEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(defaultValue = "") val groupId: String? = null,
    val title: String,
    @ColumnInfo(defaultValue = "") val notes: String = "",
    @ColumnInfo(defaultValue = "0") val status: Int = 0, // 0 active, 1 paused, 2 completed
    @ColumnInfo(defaultValue = "0") val scheduledStartMin: Int? = null,
    @ColumnInfo(defaultValue = "0") val scheduledEndMin: Int? = null,
    @ColumnInfo(defaultValue = "0") val allDay: Boolean = false,
    @ColumnInfo(defaultValue = "") val scheduledDate: String? = null, // 一次性任务的日历日期 YYYY-MM-DD；null=随时任务(每日生成)
    @ColumnInfo(defaultValue = "NONE") val repeatType: String = "NONE",
    @ColumnInfo(defaultValue = "0") val repeatIntervalDays: Int? = null,
    @ColumnInfo(defaultValue = "") val repeatWeekdays: String? = null, // csv "1,3,5"
    @ColumnInfo(defaultValue = "0") val repeatMonthDay: Int? = null,
    @ColumnInfo(defaultValue = "540") val repeatAnchorMin: Int? = null,
    @ColumnInfo(defaultValue = "") val repeatAnchorDate: String? = null, // INTERVAL 锚点日 YYYY-MM-DD（从这天起每 N 天）
    @ColumnInfo(defaultValue = "0") val reminderEnabled: Boolean = false,
    @ColumnInfo(defaultValue = "mid") val reminderLevel: String = "mid", // high/mid/low
    @ColumnInfo(defaultValue = "0") val reminderOffsetMin: Int? = null, // 提前 N 分钟提醒（null/0=准时；>0 提前）
    @ColumnInfo(defaultValue = "manual") val source: String = "manual", // manual/voice/llm/xiaozhi
    // ── 位置提醒（地理围栏；nullable 表示该任务无位置触发）──
    val geoLat: Double? = null,  // 纬度
    val geoLng: Double? = null,  // 经度
    val geoRadius: Int? = null,  // 触发半径（米），非空表示启用位置提醒
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val completedAt: Long? = null,
    @ColumnInfo(defaultValue = "0") val deletedAt: Long? = null,
)
