package com.tickclear.app.di

import com.tickclear.app.data.repositories.CheckInRepositoryImpl
import com.tickclear.app.data.repositories.CompletionRepositoryImpl
import com.tickclear.app.data.repositories.GroupRepositoryImpl
import com.tickclear.app.data.repositories.MedalRepositoryImpl
import com.tickclear.app.data.repositories.RecycleBinRepositoryImpl
import com.tickclear.app.data.repositories.SettingsRepositoryImpl
import com.tickclear.app.data.repositories.TaskRepositoryImpl
import com.tickclear.app.domain.repository.CheckInRepository
import com.tickclear.app.domain.repository.CompletionRepository
import com.tickclear.app.domain.repository.GroupRepository
import com.tickclear.app.domain.repository.MedalRepository
import com.tickclear.app.domain.repository.RecycleBinRepository
import com.tickclear.app.domain.repository.SettingsRepository
import com.tickclear.app.domain.repository.TaskRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 仓库接口 -> 实现 的 Hilt 绑定（DIP：UI/domain 仅依赖 domain.repository.* 接口）。
 * 新增仓库时在此追加一个 @Binds。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindTaskRepository(impl: TaskRepositoryImpl): TaskRepository

    @Binds
    @Singleton
    abstract fun bindGroupRepository(impl: GroupRepositoryImpl): GroupRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindCheckInRepository(impl: CheckInRepositoryImpl): CheckInRepository

    @Binds
    @Singleton
    abstract fun bindCompletionRepository(impl: CompletionRepositoryImpl): CompletionRepository

    @Binds
    @Singleton
    abstract fun bindMedalRepository(impl: MedalRepositoryImpl): MedalRepository

    @Binds
    @Singleton
    abstract fun bindRecycleBinRepository(impl: RecycleBinRepositoryImpl): RecycleBinRepository
}
