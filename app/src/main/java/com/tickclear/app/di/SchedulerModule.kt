package com.tickclear.app.di

import com.tickclear.app.data.scheduler.TaskSchedulerImpl
import com.tickclear.app.domain.scheduler.TaskScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 调度抽象绑定：TaskScheduler 接口 -> TaskSchedulerImpl（桥接 ReminderScheduler）。
 * 新增调度实现时在此追加 @Binds。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SchedulerModule {
    @Binds
    @Singleton
    abstract fun bindTaskScheduler(impl: TaskSchedulerImpl): TaskScheduler
}
