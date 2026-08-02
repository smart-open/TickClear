package com.tickclear.app.domain.usecase

import com.tickclear.app.data.local.entities.CompletionLogEntity
import com.tickclear.app.data.local.AppDatabase
import com.tickclear.app.domain.model.Task
import com.tickclear.app.domain.repository.CompletionRepository
import com.tickclear.app.data.repositories.TaskInstanceRepository
import com.tickclear.app.domain.repository.TaskRepository
import com.tickclear.app.domain.conflict.instanceDueMinute
import com.tickclear.app.domain.model.RepeatType
import com.tickclear.app.domain.model.TaskStatus
import androidx.room.withTransaction
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 完成任务：基于 TaskInstance 两层模型。
 * 1) 当天实例 upsert 并置完成态（重复任务不进 Task 终态，次日重新生成）；
 * 2) 幂等写入 CompletionLog（(taskId, dateLocal) 唯一，重复勾选不重复计数）；
 * 3) 评估勋章 + 记录打卡。
 */
@Singleton
class CompleteTaskUseCase @Inject constructor(
    private val db: AppDatabase,
    private val taskRepository: TaskRepository,
    private val instanceRepository: TaskInstanceRepository,
    private val completionRepository: CompletionRepository,
    private val checkMedalsUseCase: CheckMedalsUseCase,
    private val recordCheckInUseCase: RecordCheckInUseCase,
) {
    suspend operator fun invoke(task: Task, instanceId: String, source: String = "manual"): List<String> {
        val today = LocalDate.now()
        val dateStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)

        // 跨表原子写（AGENT.md：跨多表写必须用 @Transaction）：实例完成 + CompletionLog + Task 终态
        // 要么全部成功，要么全部回滚，避免「实例已完但无完成记录/未记打卡/终态不一致」。
        db.withTransaction {
            // 完成指定实例（保留既有 dueMinute；仅缺失时以 anchor 兜底创建），避免子日级重复
            // （每 N 小时）任务误写「单实例」id 而与界面实际点击的 per-minute 实例脱节。
            instanceRepository.completeInstance(instanceId, task.id, dateStr, task.instanceDueMinute())

            // 幂等 CompletionLog（(taskId, dateLocal) 唯一 → IGNORE 去重）
            completionRepository.insert(
                CompletionLogEntity(
                    id = instanceId,
                    taskId = task.id,
                    completedAt = System.currentTimeMillis(),
                    dateLocal = dateStr,
                    source = source,
                ),
            )

            // 仅非重复任务置 Task 终态；重复任务保持 active，次日重新生成实例
            if (RepeatType.fromCode(task.repeatType) == RepeatType.NONE) {
                taskRepository.setStatus(task.id, TaskStatus.COMPLETED, System.currentTimeMillis())
            }
        }

        // 返回本次新解锁的勋章 key，供 UI 层播撒花/震动并提示解锁（此前此处结果被丢弃）。
        val unlocked = checkMedalsUseCase()
        recordCheckInUseCase()
        return unlocked
    }
}
