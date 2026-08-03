package com.tickclear.app.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 到期提醒（V2.9++ 工具箱）：会员 / 账号 / 续费 等到期日记录。
 * 列表按到期日升序展示；提醒到点弹通知（[ExpiryScheduler] + [ExpiryReceiver]）。
 * [category] 存抽离后的显示文案（来自 strings.xml 的 expiry_category_entries），
 * 不在此硬编码中文。
 */
@Entity(tableName = "expiry_reminders")
data class ExpiryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "category") val category: String,
    /** 到期日，java.time.LocalDate.toEpochDay()。 */
    @ColumnInfo(name = "expire_epoch_day") val expireEpochDay: Long,
    @ColumnInfo(name = "note") val note: String = "",
    /** 是否开启到期提醒（默认开启）。 */
    @ColumnInfo(name = "reminder_enabled") val reminderEnabled: Boolean = true,
    /** 提前提醒天数（0=当天，默认 1）。 */
    @ColumnInfo(name = "reminder_days_before") val reminderDaysBefore: Int = 1,
    /** 每年重复（会员/续费类）：到期后自动顺延一年并续排。 */
    @ColumnInfo(name = "recurring") val recurring: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
