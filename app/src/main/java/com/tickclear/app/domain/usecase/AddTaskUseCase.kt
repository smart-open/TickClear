package com.tickclear.app.domain.usecase

import com.tickclear.app.domain.log.AppLogger
import com.tickclear.app.domain.model.Task
import com.tickclear.app.domain.repository.TaskRepository
import com.tickclear.app.domain.conflict.ConflictChecker
import com.tickclear.app.domain.scheduler.TaskScheduler
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
    private val taskScheduler: TaskScheduler,
    private val taskRepository: TaskRepository,
    private val conflictChecker: ConflictChecker,
) {
    private companion object {
        const val TAG = "AddTaskUseCase"
    }

    suspend operator fun invoke(task: Task): AddTaskResult {
        // 排程诊断（W 级，常显）：出现"建了任务到点不提醒"时，logcat 过滤本标签即可确认
        // 任务关键字段与排程是否触发，快速定位是"没排程"还是"排了没响"。
        AppLogger.w(
            TAG,
            "invoke task=${task.id} title=${task.title} date=${task.scheduledDate} " +
                "minute=${task.scheduledStartMin} repeat=${task.repeatType} " +
                "reminderEnabled=${task.reminderEnabled} level=${task.reminderLevel}",
        )
        taskRepository.upsert(task)
        // 统一兜底排程：先撤旧闹钟再按需重排（scheduleForTask 内部按 enabled/reminderEnabled 自守卫）。
        // 排程依赖 AlarmManager / 系统服务，任何异常（沙箱/权限缺失/系统服务不可用）都不得阻断
        // 任务入库，故 runCatching 兜底——任务创建成功优先于提醒排程成功。
        // 排程逻辑已下沉到注入的 [TaskScheduler]（实现桥接 ReminderScheduler），业务层不耦合 Android。
        runCatching {
            taskScheduler.cancelForTask(task.id)
            if (task.reminderEnabled) {
                taskScheduler.scheduleForTask(task)
            } else {
                AppLogger.w(TAG, "invoke 任务未开启提醒，跳过排程 task=${task.id}")
            }
        }.onFailure {
            AppLogger.w(TAG, "invoke 提醒排程失败（任务已入库）task=${task.id}", it)
        }
        val all = taskRepository.observeAll().first()
        val conflicts = conflictChecker.findConflicts(task, all)
        return AddTaskResult(task, conflicts)
    }
}
