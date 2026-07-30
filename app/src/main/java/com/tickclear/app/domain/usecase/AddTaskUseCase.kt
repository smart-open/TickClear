package com.tickclear.app.domain.usecase

import android.content.Context
import com.tickclear.app.domain.model.Task
import com.tickclear.app.domain.repository.TaskRepository
import com.tickclear.app.domain.conflict.ConflictChecker
import com.tickclear.app.domain.scheduler.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

data class AddTaskResult(
    val task: Task,
    val conflicts: List<Task>,
)

/**
 * 新增任务：插入并做时间窗冲突检测（冲突仅提示，允许知情保存）。
 *
 * V2.8X++：提醒排程下沉到本 UseCase —— 任何路径（任务 Tab / 今日 Tab / 小智语音 MCP）
 * 新建带提醒的任务后都统一兜底排程系统闹钟，不再依赖各调用方手动排程。
 * 调用方（TasksViewModel/TodayViewModel.saveTask）为覆盖「更新」分支保留的
 * cancel+schedule 与此幂等（同 PendingIntent 重设即替换），不会产生重复闹钟。
 */
@Singleton
class AddTaskUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val taskRepository: TaskRepository,
    private val conflictChecker: ConflictChecker,
) {
    suspend operator fun invoke(task: Task): AddTaskResult {
        taskRepository.upsert(task)
        // 统一兜底排程：先撤旧闹钟再按需重排（scheduleForTask 内部按 enabled/reminderEnabled 自守卫）。
        ReminderScheduler.cancelForTask(context, task.id)
        if (task.reminderEnabled) {
            ReminderScheduler.scheduleForTask(context, task)
        }
        val all = taskRepository.observeAll().first()
        val conflicts = conflictChecker.findConflicts(task, all)
        return AddTaskResult(task, conflicts)
    }
}
