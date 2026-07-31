package com.tickclear.app.domain.scheduler

import com.tickclear.app.domain.model.Task

/**
 * 任务提醒排程抽象（DIP）：将 Android 强耦合的 AlarmManager 排程从业务 UseCase 中解耦，
 * 便于单元测试用普通类 mock 替代 object 形式的 [ReminderScheduler]，避免 mockkObject
 * 在「带默认参数 + companion EntryPoint」的 object 上行为不稳定（单测需打桩隔离）。
 *
 * 实现见 [com.tickclear.app.data.scheduler.TaskSchedulerImpl]（桥接 ReminderScheduler）。
 */
interface TaskScheduler {
    /** 撤掉某任务的全部系统闹钟（更新/删除前调用，幂等）。 */
    suspend fun cancelForTask(taskId: String)

    /** 为任务排程其"最近发生日"的系统闹钟（内部按 enabled / reminderEnabled 自守卫）。 */
    suspend fun scheduleForTask(task: Task)
}
