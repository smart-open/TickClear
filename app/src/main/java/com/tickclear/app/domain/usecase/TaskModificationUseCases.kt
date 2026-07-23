package com.tickclear.app.domain.usecase

import com.tickclear.app.data.local.entities.TaskEntity
import com.tickclear.app.data.repositories.RecycleBinRepository
import com.tickclear.app.data.repositories.TaskRepository
import com.tickclear.app.domain.conflict.ConflictChecker
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** 修改任务：保存并做冲突检测，返回冲突列表。 */
@Singleton
class UpdateTaskUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
    private val conflictChecker: ConflictChecker,
) {
    suspend operator fun invoke(task: TaskEntity): List<TaskEntity> {
        taskRepository.upsert(task)
        val all = taskRepository.observeAll().first()
        return conflictChecker.findConflicts(task, all)
    }
}

@Singleton
class SoftDeleteTaskUseCase @Inject constructor(
    private val repo: TaskRepository,
) {
    suspend operator fun invoke(id: String) = repo.softDelete(id)
}

@Singleton
class RestoreTaskUseCase @Inject constructor(
    private val repo: TaskRepository,
) {
    suspend operator fun invoke(id: String) = repo.restore(id)
}

/** 立即清理：物理删除 30 天前软删记录。 */
@Singleton
class PurgeRecycleBinUseCase @Inject constructor(
    private val repo: RecycleBinRepository,
) {
    suspend operator fun invoke() {
        val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        repo.purgeExpired(cutoff)
    }
}
