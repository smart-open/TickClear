package com.tickclear.app.domain.usecase

import androidx.room.withTransaction
import com.tickclear.app.data.local.AppDatabase
import com.tickclear.app.data.repositories.TaskInstanceRepository
import com.tickclear.app.domain.model.RepeatType
import com.tickclear.app.domain.model.Task
import com.tickclear.app.domain.model.TaskStatus
import com.tickclear.app.domain.repository.CompletionRepository
import com.tickclear.app.domain.repository.TaskRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 撤销完成（与 [CompleteTaskUseCase] 严格对称）：今日页对已完成任务再次点击勾选框时恢复为未完成。
 *
 * 1) 实例回到 active 并清空 completedAt；
 * 2) 删除当日 CompletionLog（否则统计仍按已完成计数）；
 * 3) 非重复任务把 Task 终态回退为 ACTIVE（重复任务本就不进终态）。
 *
 * 三步跨 task_instance / completion_log / task 三张表，必须同一事务，
 * 半成功会留下「实例未完成但统计仍计数」这类不可自愈的脏数据。
 * 勋章与每日打卡记录不回滚：二者是「历史发生过」的行为记录，撤销单条完成不应抹掉。
 */
@Singleton
class UncompleteTaskUseCase @Inject constructor(
    private val db: AppDatabase,
    private val taskRepository: TaskRepository,
    private val instanceRepository: TaskInstanceRepository,
    private val completionRepository: CompletionRepository,
) {
    suspend operator fun invoke(task: Task, instanceId: String) {
        val dateStr = instanceDateOf(instanceId)
        db.withTransaction {
            instanceRepository.uncompleteInstance(instanceId)
            completionRepository.delete(task.id, dateStr)
            if (RepeatType.fromCode(task.repeatType) == RepeatType.NONE) {
                taskRepository.setStatus(task.id, TaskStatus.ACTIVE, null)
            }
        }
    }
}

/**
 * 由实例 id 还原所属日期："taskId@yyyy-MM-dd"（单实例）或 "taskId@yyyy-MM-dd@minute"（子日级）。
 * taskId 为 UUID 不含 '@'，故取第 2 段即日期；解析失败回退今日，避免 LocalDate.parse 抛异常。
 * 提为文件级 internal 函数以便单测覆盖（含子日级/畸形 id 的回退分支）。
 */
internal fun instanceDateOf(instanceId: String): String {
    val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    val segment = instanceId.split("@").getOrNull(1) ?: return today
    return runCatching { LocalDate.parse(segment).format(DateTimeFormatter.ISO_LOCAL_DATE) }
        .getOrDefault(today)
}
