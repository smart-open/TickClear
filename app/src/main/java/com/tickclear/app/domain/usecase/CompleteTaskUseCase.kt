package com.tickclear.app.domain.usecase

import com.tickclear.app.data.local.entities.CompletionLogEntity
import com.tickclear.app.data.local.entities.TaskEntity
import com.tickclear.app.data.local.entities.TaskInstanceEntity
import com.tickclear.app.data.repositories.CompletionRepository
import com.tickclear.app.data.repositories.TaskInstanceRepository
import com.tickclear.app.data.repositories.TaskRepository
import com.tickclear.app.domain.conflict.instanceDueMinute
import com.tickclear.app.domain.model.RepeatType
import com.tickclear.app.domain.model.TaskStatus
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
    private val taskRepository: TaskRepository,
    private val instanceRepository: TaskInstanceRepository,
    private val completionRepository: CompletionRepository,
    private val checkMedalsUseCase: CheckMedalsUseCase,
    private val recordCheckInUseCase: RecordCheckInUseCase,
) {
    suspend operator fun invoke(task: TaskEntity, source: String = "manual") {
        val today = LocalDate.now()
        val dateStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val instanceId = "${task.id}@$dateStr"

        // 确保当日实例存在并完成
        instanceRepository.upsert(
            TaskInstanceEntity(
                id = instanceId,
                taskId = task.id,
                dueDateLocal = dateStr,
                dueMinute = task.instanceDueMinute(),
            ),
        )
        instanceRepository.complete(
            TaskInstanceEntity(id = instanceId, taskId = task.id, dueDateLocal = dateStr),
        )

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

        checkMedalsUseCase()
        recordCheckInUseCase()
    }
}
