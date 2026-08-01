package com.tickclear.app.domain.usecase

import com.tickclear.app.domain.log.AppLogger
import com.tickclear.app.domain.model.Task
import com.tickclear.app.domain.model.TaskGroup
import com.tickclear.app.domain.model.TaskStatus
import com.tickclear.app.domain.repository.GroupRepository
import com.tickclear.app.domain.repository.TaskRepository
import com.tickclear.app.domain.scheduler.TaskScheduler
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AddGroupUseCase @Inject constructor(
    private val repo: GroupRepository,
) {
    suspend operator fun invoke(group: TaskGroup) = repo.upsert(group)
}

@Singleton
class UpdateGroupUseCase @Inject constructor(
    private val repo: GroupRepository,
) {
    suspend operator fun invoke(group: TaskGroup) = repo.upsert(group)
}

/** 软删组：组内任务脱离组（保留数据），不物理删除。 */
@Singleton
class SoftDeleteGroupUseCase @Inject constructor(
    private val repo: GroupRepository,
) {
    suspend operator fun invoke(id: String) = repo.softDelete(id)
}

@Singleton
class RestoreGroupUseCase @Inject constructor(
    private val repo: GroupRepository,
) {
    suspend operator fun invoke(id: String) = repo.restore(id)
}

/**
 * 单个任务暂停：状态置为 PAUSED（仍保留数据，定时不再触发）。
 *
 * V2.8X 修复：此前只改库不动闹钟 —— 暂停后系统闹钟仍在，到点照常弹提醒（"暂停了还在响"）。
 * 现同步撤销该任务的全部闹钟。
 */
@Singleton
class PauseTaskUseCase @Inject constructor(
    private val repo: TaskRepository,
    private val taskScheduler: TaskScheduler,
) {
    suspend operator fun invoke(task: Task) {
        if (task.deletedAt != null) return
        repo.setStatus(task.id, TaskStatus.PAUSED, null)
        runCatchingScheduler { taskScheduler.cancelForTask(task.id) }
    }
}

/**
 * 单个任务启用：状态恢复为 ACTIVE。
 *
 * V2.8X 修复：此前恢复后不重排闹钟 —— 暂停期间闹钟链已断，必须等用户下次冷启动 App
 * 触发 rescheduleAll 才补得回来，中间的提醒全部丢失。现恢复即重排。
 */
@Singleton
class ResumeTaskUseCase @Inject constructor(
    private val repo: TaskRepository,
    private val taskScheduler: TaskScheduler,
) {
    suspend operator fun invoke(task: Task) {
        if (task.deletedAt != null) return
        repo.setStatus(task.id, TaskStatus.ACTIVE, null)
        runCatchingScheduler {
            val revived = task.copy(status = TaskStatus.ACTIVE.code)
            taskScheduler.cancelForTask(revived.id)
            if (revived.reminderEnabled) taskScheduler.scheduleForTask(revived)
        }
    }
}

/** 任务组级暂停：级联暂停组内所有未软删任务（V2.33），并同步撤销其闹钟。 */
@Singleton
class PauseGroupUseCase @Inject constructor(
    private val taskRepo: TaskRepository,
    private val taskScheduler: TaskScheduler,
) {
    suspend operator fun invoke(groupId: String) {
        taskRepo.observeByGroup(groupId).first()
            .filter { it.deletedAt == null }
            .forEach {
                taskRepo.setStatus(it.id, TaskStatus.PAUSED, null)
                runCatchingScheduler { taskScheduler.cancelForTask(it.id) }
            }
    }
}

/** 任务组级启用：级联恢复组内所有未软删任务为 ACTIVE（V2.33），并同步重排其闹钟。 */
@Singleton
class ResumeGroupUseCase @Inject constructor(
    private val taskRepo: TaskRepository,
    private val taskScheduler: TaskScheduler,
) {
    suspend operator fun invoke(groupId: String) {
        taskRepo.observeByGroup(groupId).first()
            .filter { it.deletedAt == null }
            .forEach {
                taskRepo.setStatus(it.id, TaskStatus.ACTIVE, null)
                runCatchingScheduler {
                    val revived = it.copy(status = TaskStatus.ACTIVE.code)
                    taskScheduler.cancelForTask(revived.id)
                    if (revived.reminderEnabled) taskScheduler.scheduleForTask(revived)
                }
            }
    }
}

/**
 * 排程副作用的统一兜底：闹钟操作失败不得影响主业务（状态已落库）。
 * 形参显式声明为 `suspend () -> Unit`，避免与非挂起 lambda 的调用约束纠缠。
 */
private suspend fun runCatchingScheduler(block: suspend () -> Unit) {
    try {
        block()
    } catch (e: Exception) {
        AppLogger.e("GroupUseCases", "排程副作用失败：${e.message}")
    }
}

/** 任务组级删除：级联软删组内所有未软删任务，再软删组本身（V2.33）。 */
@Singleton
class DeleteGroupCascadeUseCase @Inject constructor(
    private val groupRepo: GroupRepository,
    private val taskRepo: TaskRepository,
) {
    suspend operator fun invoke(groupId: String) {
        val tasks = taskRepo.observeByGroup(groupId).first()
        // V2.41 对称修正：组内此前单独软删的任务先脱离组（groupId 置空），
        // 否则后续「恢复组」会经 restoreByGroup 把它们一并复活，超出本次删除意图。
        tasks.filter { it.deletedAt != null }.forEach { taskRepo.detachFromGroup(it.id) }
        tasks.filter { it.deletedAt == null }.forEach { taskRepo.softDelete(it.id) }
        groupRepo.softDelete(groupId)
    }
}

/** 任务组级恢复：先恢复组本身，再级联恢复其下被软删的成员任务（V2.41，与 DeleteGroupCascadeUseCase 对称）。 */
@Singleton
class RestoreGroupCascadeUseCase @Inject constructor(
    private val groupRepo: GroupRepository,
    private val taskRepo: TaskRepository,
) {
    suspend operator fun invoke(groupId: String) {
        groupRepo.restore(groupId)
        taskRepo.restoreByGroup(groupId)
    }
}
