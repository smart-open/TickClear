package com.tickclear.app.domain.model

enum class TaskStatus(val code: Int) {
    ACTIVE(0), PAUSED(1), COMPLETED(2), SKIPPED(3);

    companion object {
        fun fromCode(c: Int) = entries.firstOrNull { it.code == c } ?: ACTIVE
    }
}

enum class RepeatType(val code: String) {
    NONE("NONE"), DAILY("DAILY"), WEEKLY("WEEKLY"), MONTHLY("MONTHLY"), INTERVAL("INTERVAL");

    companion object {
        fun fromCode(c: String?) = entries.firstOrNull { it.code == c } ?: NONE
    }
}

enum class TaskSource(val code: String) {
    MANUAL("manual"), VOICE("voice"), LLM("llm"), XIAOZHI("xiaozhi");

    companion object {
        fun fromCode(c: String?) = entries.firstOrNull { it.code == c } ?: MANUAL
    }
}

/** 回收站投影项（非独立表，由 Task/Group 软删记录映射）。 */
data class RecycleBinItem(
    val id: String,
    val type: String, // "task" | "group"
    val name: String,
    val deletedAt: Long,
)
