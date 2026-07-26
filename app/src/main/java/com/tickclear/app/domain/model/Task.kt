package com.tickclear.app.domain.model

/**
 * 任务领域模型（UI / domain 层使用，与持久化实体 [com.tickclear.app.data.local.entities.TaskEntity] 解耦）。
 *
 * 字段与实体一一对应，且类型保持与存储一致（status 仍为 Int、repeatType 仍为 String 等），
 * 这样消费方（ViewModel / use-case / 冲突检测）的字段访问无需改动，仓库边界
 * ([com.tickclear.app.data.repositories.TaskRepositoryImpl]) 负责 entity↔domain 映射。
 *
 * 后续若希望进一步消除存储编码，可将 status/repeatType/source 升级为
 * [TaskStatus]/[RepeatType]/[TaskSource] 富类型——那是独立于本次分层重构的增强项。
 */
data class Task(
    val id: String,
    val groupId: String? = null,
    val title: String,
    val notes: String = "",
    val status: Int = 0, // 0 active, 1 paused, 2 completed, 3 skipped
    val scheduledStartMin: Int? = null,
    val scheduledEndMin: Int? = null,
    val allDay: Boolean = false,
    val scheduledDate: String? = null, // 一次性任务的日历日期 YYYY-MM-DD；null=随时任务(每日生成)
    val repeatType: String = "NONE",
    val repeatIntervalDays: Int? = null,
    val repeatIntervalHours: Int? = null,
    val repeatWeekdays: String? = null, // csv "1,3,5"
    val repeatMonthDay: Int? = null,
    val repeatAnchorMin: Int? = null,
    val repeatAnchorDate: String? = null,
    val reminderEnabled: Boolean = false,
    val reminderLevel: String = "mid", // high/mid/low
    val reminderOffsetMin: Int? = null,
    val source: String = "manual", // manual/voice/llm/xiaozhi
    val tags: List<String> = emptyList(), // V2.67 标签列表（领域侧用 List，持久化边界转 CSV）
    val geoLat: Double? = null,
    val geoLng: Double? = null,
    val geoRadius: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val deletedAt: Long? = null,
)
