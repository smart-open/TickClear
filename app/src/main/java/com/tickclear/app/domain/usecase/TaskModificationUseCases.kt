package com.tickclear.app.domain.usecase

import com.tickclear.app.data.repositories.TaskInstanceRepository
import com.tickclear.app.domain.log.AppLogger
import com.tickclear.app.domain.model.Task
import com.tickclear.app.domain.repository.RecycleBinRepository
import com.tickclear.app.domain.repository.TaskRepository
import com.tickclear.app.domain.conflict.ConflictChecker
import com.tickclear.app.domain.scheduler.TaskScheduler
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TaskModificationUseCases"

/** 排程副作用兜底：闹钟失败不得回滚已落库的业务改动。 */
private suspend fun safeSchedule(block: suspend () -> Unit) {
    try {
        block()
    } catch (e: Exception) {
        AppLogger.e(TAG, "排程副作用失败：${e.message}")
    }
}

/**
 * 修改任务：保存并做冲突检测，返回冲突列表。
 *
 * V2.8X 修复：此前只写库，既不重排闹钟也不重建实例，于是「改时间」有两个后果：
 * 1. 旧闹钟仍按旧时刻响、新时刻不响（[AddTaskUseCase] 早已下沉排程，编辑路径却漏了）；
 * 2. 实例是 `@Insert(IGNORE)` 懒生成的，当天旧实例不会被覆盖 → 列表仍显示旧时间。
 * 现与 [AddTaskUseCase] 对齐：先清掉未完成的当天及未来实例，再 cancel + 重排。
 */
@Singleton
class UpdateTaskUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
    private val conflictChecker: ConflictChecker,
    private val taskInstanceRepository: TaskInstanceRepository,
    private val taskScheduler: TaskScheduler,
) {
    suspend operator fun invoke(task: Task): List<Task> {
        taskRepository.upsert(task)
        safeSchedule {
            // 顺序有讲究：先撤旧闹钟（此时旧实例仍在，能按旧 id 精确 cancel），
            // 再删旧实例，最后按新规则重排。
            taskScheduler.cancelForTask(task.id)
            taskInstanceRepository.deletePendingFrom(task.id)
            if (task.reminderEnabled) taskScheduler.scheduleForTask(task)
        }
        val all = taskRepository.observeAll().first()
        return conflictChecker.findConflicts(task, all)
    }
}

/** 软删任务：同时撤销其全部系统闹钟，避免回收站里的任务仍到点弹"幽灵提醒"。 */
@Singleton
class SoftDeleteTaskUseCase @Inject constructor(
    private val repo: TaskRepository,
    private val taskScheduler: TaskScheduler,
) {
    suspend operator fun invoke(id: String) {
        // 先撤闹钟：cancelForTask 需要读取任务与实例来定位 PendingIntent，软删后仍可读，
        // 但保持"先撤后删"更符合直觉且对后续实现变化更健壮。
        safeSchedule { taskScheduler.cancelForTask(id) }
        repo.softDelete(id)
    }
}

/** 从回收站恢复任务：同步重排闹钟，否则要等下次冷启动 App 才补得回提醒。 */
@Singleton
class RestoreTaskUseCase @Inject constructor(
    private val repo: TaskRepository,
    private val taskScheduler: TaskScheduler,
) {
    suspend operator fun invoke(id: String) {
        repo.restore(id)
        safeSchedule {
            val task = repo.getById(id) ?: return@safeSchedule
            if (task.reminderEnabled) taskScheduler.scheduleForTask(task)
        }
    }
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
