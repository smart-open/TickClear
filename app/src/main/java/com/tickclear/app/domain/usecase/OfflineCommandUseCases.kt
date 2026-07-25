package com.tickclear.app.domain.usecase

import com.tickclear.app.domain.assistant.OfflineAction
import com.tickclear.app.domain.assistant.OfflineCommand
import com.tickclear.app.domain.assistant.OfflineCommandRecognizer
import com.tickclear.app.domain.model.Task
import com.tickclear.app.domain.model.TaskStatus
import com.tickclear.app.domain.repository.TaskRepository
import javax.inject.Inject
import javax.inject.Singleton

/** 离线热词指令执行结果（V2.42）。 */
sealed interface OfflineCommandResult {
    /** 已对 [task] 执行 [action]。 */
    data class Applied(val task: Task, val action: OfflineAction) : OfflineCommandResult

    /** 指令动作需要目标任务，但未匹配到任何真实任务。 */
    data object NotFound : OfflineCommandResult

    /** 指令中未给出目标任务名（无法定位）。 */
    data object NoTarget : OfflineCommandResult

    /** 无法解析为已知指令。 */
    data object Unknown : OfflineCommandResult
}

@Singleton
class ApplyOfflineCommandUseCase @Inject constructor(
    private val taskRepo: TaskRepository,
) {
    /**
     * 把解析出的 [command] 作用到 [tasks]（当前任务全集）。
     * - 未给出任务名 → [OfflineCommandResult.NoTarget]；
     * - 任务名无匹配 → [OfflineCommandResult.NotFound]（删除不会误伤）；
     * - 命中 → 执行并返回 [OfflineCommandResult.Applied]。
     */
    suspend operator fun invoke(command: OfflineCommand, tasks: List<Task>): OfflineCommandResult {
        return when (command) {
            is OfflineCommand.Unknown -> OfflineCommandResult.Unknown
            is OfflineCommand.Pause -> applyTo(command.keyword, tasks, OfflineAction.PAUSE) { task ->
                taskRepo.setStatus(task.id, TaskStatus.PAUSED, null)
            }
            is OfflineCommand.Resume -> applyTo(command.keyword, tasks, OfflineAction.RESUME) { task ->
                taskRepo.setStatus(task.id, TaskStatus.ACTIVE, null)
            }
            is OfflineCommand.Delete -> applyTo(command.keyword, tasks, OfflineAction.DELETE) { task ->
                taskRepo.softDelete(task.id)
            }
        }
    }

    private suspend fun applyTo(
        keyword: String?,
        tasks: List<Task>,
        action: OfflineAction,
        block: suspend (Task) -> Unit,
    ): OfflineCommandResult {
        if (keyword.isNullOrBlank()) return OfflineCommandResult.NoTarget
        val target = OfflineCommandRecognizer.matchTask(tasks, keyword) ?: return OfflineCommandResult.NotFound
        block(target)
        return OfflineCommandResult.Applied(target, action)
    }
}
