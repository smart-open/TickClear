package com.tickclear.app.data.scheduler

import android.content.Context
import com.tickclear.app.domain.model.Task
import com.tickclear.app.domain.scheduler.ReminderScheduler
import com.tickclear.app.domain.scheduler.TaskScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [TaskScheduler] 的生产实现：桥接到既有 AlarmManager 调度器 [ReminderScheduler]（object）。
 * 把 Android 上下文与系统闹钟细节锁在 data 层，domain/UI 只依赖 [TaskScheduler] 接口。
 */
@Singleton
class TaskSchedulerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : TaskScheduler {
    override suspend fun cancelForTask(taskId: String) {
        ReminderScheduler.cancelForTask(context, taskId)
    }

    override suspend fun scheduleForTask(task: Task) {
        ReminderScheduler.scheduleForTask(context, task, LocalDate.now())
    }
}
