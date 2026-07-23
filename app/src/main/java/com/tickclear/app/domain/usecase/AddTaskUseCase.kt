package com.tickclear.app.domain.usecase

import com.tickclear.app.domain.model.Task
import com.tickclear.app.domain.repository.TaskRepository
import com.tickclear.app.domain.conflict.ConflictChecker
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

data class AddTaskResult(
    val task: Task,
    val conflicts: List<Task>,
)

/** 新增任务：插入并做时间窗冲突检测（冲突仅提示，允许知情保存）。 */
@Singleton
class AddTaskUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
    private val conflictChecker: ConflictChecker,
) {
    suspend operator fun invoke(task: Task): AddTaskResult {
        taskRepository.upsert(task)
        val all = taskRepository.observeAll().first()
        val conflicts = conflictChecker.findConflicts(task, all)
        return AddTaskResult(task, conflicts)
    }
}
